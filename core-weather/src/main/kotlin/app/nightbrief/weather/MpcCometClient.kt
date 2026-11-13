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

/**
 * The observable-comets file as downloaded, plus when it was fetched.
 * The text is the MPC column layout; parsing it is not this module's job.
 */
data class CometElementsFile(
    val text: String,
    val fetchedAt: Instant,
)

interface CometSource {
    /**
     * Observable-comet elements. Throws [WeatherApiException] on network or
     * when the body is not that file.
     */
    suspend fun fetch(): CometElementsFile
}

/**
 * Observable-comet orbital elements from the IAU Minor Planet Center
 * (`Soft00Cmt.txt`, the MPC-format file on the orbital-elements page).
 *
 * That page says the file is for personal use and must not be redistributed.
 * The MPC web policy allows a freely available dataset to be redistributed when
 * the source is named. NightBrief does not ship the file: each device downloads
 * it for that user. Settings credits the Minor Planet Center.
 *
 * A body that is not the elements file (an HTML error page, an empty reply)
 * throws [WeatherApiException] instead of being cached.
 */
class MpcCometClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val url: String = DEFAULT_URL,
    private val clock: Clock = Clock.systemUTC(),
) : CometSource {
    override suspend fun fetch(): CometElementsFile = parse(http.getString(url), clock.instant())

    internal fun parse(body: String, fetchedAt: Instant = clock.instant()): CometElementsFile {
        if (!looksLikeObservableComets(body)) {
            val preview = body.take(80).replace('\n', ' ')
            throw WeatherApiException(
                "MPC comet elements response is not the observable-comets file (starts with: $preview)",
            )
        }
        return CometElementsFile(body, fetchedAt)
    }

    companion object {
        const val DEFAULT_URL = "https://minorplanetcenter.net/iau/Ephemerides/Comets/Soft00Cmt.txt"
    }
}

/**
 * True when [body] has at least one line long enough to be an MPC comet-element
 * row: a letter in the orbit-type column and a four-digit perihelion year.
 */
internal fun looksLikeObservableComets(body: String): Boolean =
    body.lineSequence().any { line ->
        line.length >= 100 &&
            line[4].isLetter() &&
            line.substring(14, 18).all { it.isDigit() }
    }

/**
 * File cache in front of a [CometSource].
 *
 * The cache file is UTF-8 text: a `v1` line, `fetchedAt` as Unix epoch seconds,
 * then the raw elements file. A cache whose age is at most [maxAge] is returned
 * without calling [origin]. Otherwise [origin] is called and, on success, the
 * file is replaced (parent directories are created). One origin call is bounded
 * by [originTimeout]: a timeout or any other [WeatherApiException] serves the
 * cache when it is at most [staleMaxAge] old, and rethrows otherwise. Concurrent
 * callers share that one call, and after a failure later calls fail fast for
 * [failureCooldown] — serving the stale file when one exists — instead of waiting
 * out another doomed fetch. A missing cache, an unreadable cache, or a cache
 * older than [staleMaxAge] rethrows the failure.
 */
class CachingCometSource(
    private val origin: CometSource,
    private val cacheFile: File,
    private val maxAge: Duration = Duration.ofHours(12),
    private val staleMaxAge: Duration = Duration.ofDays(7),
    private val clock: Clock = Clock.systemUTC(),
    private val originTimeout: Duration = Duration.ofSeconds(10),
    private val failureCooldown: Duration = Duration.ofMinutes(30),
) : CometSource {
    private val mutex = Mutex()
    private var lastFailure: Instant? = null

    override suspend fun fetch(): CometElementsFile = mutex.withLock {
        val now = clock.instant()
        val cached = readCache()
        if (cached != null && ageOf(cached, now) <= maxAge) return cached
        val fallback = if (cached != null && ageOf(cached, now) <= staleMaxAge) cached else null
        if (failedRecently(now)) {
            if (fallback != null) return fallback
            throw WeatherApiException("MPC comet elements unavailable: the last fetch failed recently; retrying later")
        }
        try {
            val fresh = withTimeout(originTimeout.toMillis()) { origin.fetch() }
            writeCache(fresh)
            lastFailure = null
            fresh
        } catch (e: TimeoutCancellationException) {
            lastFailure = clock.instant()
            if (fallback != null) fallback else throw WeatherApiException("MPC comet elements fetch timed out", e)
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

    private fun ageOf(file: CometElementsFile, now: Instant): Duration = Duration.between(file.fetchedAt, now)

    private fun readCache(): CometElementsFile? {
        if (!cacheFile.isFile) return null
        return try {
            val text = cacheFile.readText()
            val headerEnd = text.indexOf('\n')
            if (headerEnd < 0) return null
            val second = text.indexOf('\n', headerEnd + 1)
            if (second < 0 || text.substring(0, headerEnd) != "v1") return null
            val fetchedAt = Instant.ofEpochSecond(text.substring(headerEnd + 1, second).toLong())
            val body = text.substring(second + 1)
            if (!looksLikeObservableComets(body)) return null
            CometElementsFile(body, fetchedAt)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(file: CometElementsFile) {
        val parent = cacheFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw WeatherApiException("could not create comet cache directory ${parent.path}")
        }
        try {
            cacheFile.writeText("v1\n${file.fetchedAt.epochSecond}\n${file.text}")
        } catch (e: IOException) {
            throw WeatherApiException("could not write comet cache ${cacheFile.path}", e)
        }
    }
}
