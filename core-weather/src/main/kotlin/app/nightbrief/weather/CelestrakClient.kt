package app.nightbrief.weather

import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

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
        val line1 = lines.firstOrNull { it.startsWith("1 ") }
            ?: throw WeatherApiException("Celestrak TLE response has no line starting with \"1 \"")
        val line2 = lines.firstOrNull { it.startsWith("2 ") }
            ?: throw WeatherApiException("Celestrak TLE response has no line starting with \"2 \"")
        requireTleLine(line1, '1')
        requireTleLine(line2, '2')
        return IssTle(line1, line2, fetchedAt)
    }

    companion object {
        const val DEFAULT_URL = "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=tle"
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
 * directories are created). If [origin] throws [WeatherApiException] and the
 * cache is at most [staleMaxAge] old, that cached element is returned. A missing
 * cache, an unreadable cache, or a cache older than [staleMaxAge] rethrows the
 * failure.
 */
class CachingTleSource(
    private val origin: TleSource,
    private val cacheFile: File,
    private val maxAge: Duration = Duration.ofHours(12),
    private val staleMaxAge: Duration = Duration.ofDays(7),
    private val clock: Clock = Clock.systemUTC(),
) : TleSource {
    override suspend fun fetchIss(): IssTle {
        val now = clock.instant()
        val cached = readCache()
        if (cached != null && ageOf(cached, now) <= maxAge) return cached
        return try {
            val fresh = origin.fetchIss()
            writeCache(fresh)
            fresh
        } catch (failure: WeatherApiException) {
            if (cached != null && ageOf(cached, now) <= staleMaxAge) cached else throw failure
        }
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
