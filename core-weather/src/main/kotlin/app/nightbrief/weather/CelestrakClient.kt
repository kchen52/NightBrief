package app.nightbrief.weather

import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** ISS two-line elements and the moment they were obtained. */
data class IssTle(
    val line1: String,
    val line2: String,
    val fetchedAt: Instant,
)

interface TleSource {
    /** ISS (NORAD 25544) line 1 and line 2. Throws [WeatherApiException] on network or parse failure. */
    suspend fun fetchIss(): IssTle
}

/**
 * Celestrak GP element fetch for the ISS.
 *
 * [DEFAULT_URL] returns two or three lines: an optional name line, then the
 * lines that start with `"1 "` and `"2 "`. Anything else is a [WeatherApiException],
 * not a crash.
 */
class CelestrakClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val url: String = DEFAULT_URL,
    private val clock: Clock = Clock.systemUTC(),
) : TleSource {
    override suspend fun fetchIss(): IssTle = parse(http.getString(url), clock.instant())

    internal fun parse(body: String, fetchedAt: Instant = clock.instant()): IssTle {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull()?.startsWith("OBJECT_NAME") == true) {
            throw WeatherApiException("Celestrak returned CSV, not TLE; query FORMAT=TLE")
        }
        val preview = body.take(120).replace('\n', ' ')
        val line1 = lines.firstOrNull { it.startsWith("1 ") }
            ?: throw WeatherApiException("Celestrak TLE response has no line starting with \"1 \" (starts with: $preview)")
        val line2 = lines.firstOrNull { it.startsWith("2 ") }
            ?: throw WeatherApiException("Celestrak TLE response has no line starting with \"2 \" (starts with: $preview)")
        requireTleLine(line1, '1')
        requireTleLine(line2, '2')
        return IssTle(line1, line2, fetchedAt)
    }

    companion object {
        /** Documented query form (`gp-data-formats.php` shows `FORMAT=TLE`); the endpoint serves CSV for anything else. */
        const val DEFAULT_URL = "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE"
    }
}

/**
 * File cache in front of a [TleSource].
 *
 * The cache file is UTF-8 text, four lines:
 * 1. `v1`
 * 2. `fetchedAt` as Unix epoch seconds
 * 3. TLE line 1
 * 4. TLE line 2
 *
 * A cache whose age is at most [maxAge] is returned without calling [origin].
 * Otherwise [origin] is called and, on success, the file is replaced (parent
 * directories are created). One origin call is bounded by [originTimeout]: a timeout
 * or any other [WeatherApiException] serves the cache when it is at most [staleMaxAge]
 * old, and rethrows otherwise. Concurrent callers share that one call, and after a
 * failure later calls fail fast for [failureCooldown] — serving the stale file when one
 * exists — instead of waiting out another doomed fetch. A missing cache, an unreadable
 * cache, or a cache older than [staleMaxAge] rethrows the failure.
 */
class CachingTleSource(
    private val origin: TleSource,
    private val cacheFile: File,
    private val maxAge: Duration = Duration.ofHours(12),
    private val staleMaxAge: Duration = Duration.ofDays(7),
    private val clock: Clock = Clock.systemUTC(),
    private val originTimeout: Duration = Duration.ofSeconds(10),
    private val failureCooldown: Duration = Duration.ofMinutes(30),
) : TleSource {
    private val mutex = Mutex()
    private var lastFailure: Instant? = null

    override suspend fun fetchIss(): IssTle = mutex.withLock {
        val now = clock.instant()
        val cached = readCache()
        if (cached != null && ageOf(cached, now) <= maxAge) return cached
        val fallback: IssTle? = if (cached != null && ageOf(cached, now) <= staleMaxAge) cached else null
        if (failedRecently(now)) {
            // The last attempt just failed; a retry now would burn the timeout again.
            if (fallback != null) return fallback
            throw WeatherApiException("Celestrak TLE unavailable: the last fetch failed recently; retrying later")
        }
        try {
            val fresh = withTimeout(originTimeout.toMillis()) { origin.fetchIss() }
            writeCache(fresh)
            lastFailure = null
            fresh
        } catch (e: TimeoutCancellationException) {
            lastFailure = clock.instant()
            if (fallback != null) fallback else throw WeatherApiException("Celestrak TLE fetch timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (failure: WeatherApiException) {
            lastFailure = clock.instant()
            if (fallback != null) fallback else throw failure
        }
    }

    /** True when the last origin failure is recent. A clock that moved backwards never counts. */
    private fun failedRecently(now: Instant): Boolean {
        val failedAt = lastFailure ?: return false
        val since = Duration.between(failedAt, now)
        return !since.isNegative && since < failureCooldown
    }

    private fun ageOf(tle: IssTle, now: Instant): Duration = Duration.between(tle.fetchedAt, now)

    private fun readCache(): IssTle? {
        if (!cacheFile.isFile) return null
        return try {
            val lines = cacheFile.readLines()
            if (lines.size < 4 || lines[0] != "v1") return null
            val fetchedAt = Instant.ofEpochSecond(lines[1].toLong())
            val line1 = lines[2]
            val line2 = lines[3]
            if (!line1.startsWith("1 ") || !line2.startsWith("2 ")) return null
            IssTle(line1, line2, fetchedAt)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeCache(tle: IssTle) {
        val parent = cacheFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw WeatherApiException("could not create TLE cache directory ${parent.path}")
        }
        val text = "v1\n${tle.fetchedAt.epochSecond}\n${tle.line1}\n${tle.line2}\n"
        try {
            cacheFile.writeText(text)
        } catch (e: IOException) {
            throw WeatherApiException("could not write TLE cache ${cacheFile.path}", e)
        }
    }
}

private fun requireTleLine(line: String, number: Char) {
    if (line.length < 69 || line[0] != number) {
        throw WeatherApiException("malformed Celestrak TLE line $number")
    }
    val checksum = line[68]
    if (!checksum.isDigit()) {
        throw WeatherApiException("malformed Celestrak TLE line $number checksum")
    }
    var sum = 0
    for (index in 0 until 68) {
        val c = line[index]
        when {
            c.isDigit() -> sum += c.digitToInt()
            c == '-' -> sum += 1
        }
    }
    if (sum % 10 != checksum.digitToInt()) {
        throw WeatherApiException("Celestrak TLE line $number checksum does not match")
    }
}
