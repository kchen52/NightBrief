package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface ForecastCache {
    suspend fun get(key: String): Forecast?
    suspend fun put(key: String, forecast: Forecast)
}

class InMemoryForecastCache : ForecastCache {
    private val map = ConcurrentHashMap<String, Forecast>()
    override suspend fun get(key: String): Forecast? = map[key]
    override suspend fun put(key: String, forecast: Forecast) {
        map[key] = forecast
    }
}

/**
 * Stores one JSON file per location so the last good forecast survives process death and offline use.
 *
 * Puts share one temp file per key, so they take a lock. A failed rename writes the text already
 * in memory. [IOException] means the directory could not be created or the file could not be written;
 * the caller still has the forecast and should not treat that as a failed fetch.
 */
class FileForecastCache(private val directory: File) : ForecastCache {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    override suspend fun get(key: String): Forecast? = withContext(Dispatchers.IO) {
        val file = File(directory, "$key.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(Forecast.serializer(), file.readText()) }.getOrNull()
    }

    override suspend fun put(key: String, forecast: Forecast) {
        val text = json.encodeToString(Forecast.serializer(), forecast)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("could not create forecast cache directory ${directory.path}")
                }
                val dest = File(directory, "$key.json")
                val tmp = File(directory, "$key.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(dest)) {
                    dest.writeText(text)
                    tmp.delete()
                }
            }
        }
    }
}

enum class ForecastStatus {
    /** Fetched just now. */
    FRESH,
    /** Served from cache because it is recent enough. */
    CACHED,
    /** Network fetch failed; serving the last cached forecast, however old. */
    STALE,
}

data class ForecastResult(
    val forecast: Forecast,
    val status: ForecastStatus,
    /** Non-fatal problems, e.g. 7Timer unavailable or the network fetch that forced a stale fallback. */
    val warnings: List<String> = emptyList(),
)

interface ForecastSource {
    /** Throws [WeatherApiException] when no forecast (fresh or cached) is available. */
    suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean = false): ForecastResult
}

/**
 * One file per location. A file younger than [maxCacheAge] is served with no network call
 * unless refresh is forced. After that the network is tried and a success replaces the file.
 * Calls for the same cache cell share one in-flight fetch.
 * A failed fetch serves the last file at any age as [ForecastStatus.STALE]. No file still throws.
 * HTTP 429 records a short cooldown: until it passes, a later call serves that file (or throws
 * [ForecastProblem.BUSY]) and does not hit the network again, unless refresh is forced.
 * A forecast that was fetched but could not be written to disk is still returned as [ForecastStatus.FRESH].
 */
class ForecastRepository(
    private val openMeteo: OpenMeteoClient = OpenMeteoClient(),
    private val sevenTimer: SevenTimerClient = SevenTimerClient(),
    private val cache: ForecastCache = InMemoryForecastCache(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxCacheAge: Duration = Duration.ofMinutes(60),
    private val busyCooldown: Duration = Duration.ofSeconds(30),
) : ForecastSource {
    private val flights = ConcurrentHashMap<String, Deferred<ForecastResult>>()
    private val busyUntil = ConcurrentHashMap<String, Instant>()

    override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
        val key = cacheKey(latitude, longitude)
        val cached = cache.get(key)
        val age = cached?.let { Duration.between(it.fetchedAt, clock.instant()) }
        if (!forceRefresh && cached != null && age != null && age < maxCacheAge) {
            return ForecastResult(cached, ForecastStatus.CACHED)
        }
        val quietUntil = busyUntil[key]
        if (!forceRefresh && quietUntil != null && clock.instant().isBefore(quietUntil)) {
            if (cached != null) {
                return ForecastResult(cached, ForecastStatus.STALE, listOf(ForecastProblem.BUSY.userText()))
            }
            throw WeatherApiException("HTTP 429", problem = ForecastProblem.BUSY)
        }

        val created = CompletableDeferred<ForecastResult>()
        val inFlight = flights.putIfAbsent(key, created)
        if (inFlight != null) return inFlight.await()
        return try {
            val result = load(latitude, longitude, key, cached)
            created.complete(result)
            result
        } catch (e: Throwable) {
            created.completeExceptionally(e)
            throw e
        } finally {
            flights.remove(key, created)
        }
    }

    private suspend fun load(
        latitude: Double,
        longitude: Double,
        key: String,
        cached: Forecast?,
    ): ForecastResult = try {
        val (forecast, warnings) = fetch(latitude, longitude)
        try {
            cache.put(key, forecast)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // The night is already in hand. A disk failure must not blank Tonight.
        }
        busyUntil.remove(key)
        ForecastResult(forecast, ForecastStatus.FRESH, warnings)
    } catch (e: WeatherApiException) {
        if (e.problem == ForecastProblem.BUSY) {
            busyUntil[key] = clock.instant().plus(busyCooldown)
        }
        if (cached != null) {
            ForecastResult(cached, ForecastStatus.STALE, listOf(e.userText()))
        } else {
            throw e
        }
    }

    private suspend fun fetch(latitude: Double, longitude: Double): Pair<Forecast, List<String>> = coroutineScope {
        val astro = async { runCatching { sevenTimer.fetch(latitude, longitude) } }
        val primary = WeatherModel.forLocation(latitude, longitude)
        val second = async {
            runCatching {
                openMeteo.fetch(
                    latitude,
                    longitude,
                    model = WeatherModel.secondaryFor(primary),
                    hourly = OpenMeteoClient.SECONDARY_HOURLY_FIELDS,
                )
            }
        }
        val weather = openMeteo.fetch(latitude, longitude)
        val points = astro.await()
        val warnings = points.exceptionOrNull()?.let { listOf("Seeing and transparency unavailable") }.orEmpty()
        // A failed second model leaves cloudCoverSecondary null; the briefing still succeeds.
        val merged = second.await().getOrNull()?.let { weather.withSecondary(it) } ?: weather
        merged.withSevenTimer(points.getOrDefault(emptyList())) to warnings
    }

    companion object {
        /** ~1 km grid so tiny GPS jitter reuses the same cache entry. */
        fun cacheKey(latitude: Double, longitude: Double): String =
            String.format(Locale.ROOT, "fc_%.2f_%.2f", latitude, longitude)
    }
}
