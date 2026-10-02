package app.nightbrief.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.Duration
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

/** Stores one JSON file per location so the last good forecast survives process death and offline use. */
class FileForecastCache(private val directory: File) : ForecastCache {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(key: String): Forecast? = withContext(Dispatchers.IO) {
        val file = File(directory, "$key.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(Forecast.serializer(), file.readText()) }.getOrNull()
    }

    override suspend fun put(key: String, forecast: Forecast) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val tmp = File(directory, "$key.json.tmp")
        tmp.writeText(json.encodeToString(Forecast.serializer(), forecast))
        if (!tmp.renameTo(File(directory, "$key.json"))) {
            File(directory, "$key.json").writeText(tmp.readText())
            tmp.delete()
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
 * A failed fetch serves the last file at any age as [ForecastStatus.STALE]. No file still throws.
 */
class ForecastRepository(
    private val openMeteo: OpenMeteoClient = OpenMeteoClient(),
    private val sevenTimer: SevenTimerClient = SevenTimerClient(),
    private val cache: ForecastCache = InMemoryForecastCache(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxCacheAge: Duration = Duration.ofMinutes(60),
) : ForecastSource {

    override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
        val key = cacheKey(latitude, longitude)
        val cached = cache.get(key)
        val age = cached?.let { Duration.between(it.fetchedAt, clock.instant()) }
        if (!forceRefresh && cached != null && age != null && age < maxCacheAge) {
            return ForecastResult(cached, ForecastStatus.CACHED)
        }
        return try {
            val (forecast, warnings) = fetch(latitude, longitude)
            cache.put(key, forecast)
            ForecastResult(forecast, ForecastStatus.FRESH, warnings)
        } catch (e: WeatherApiException) {
            if (cached != null) {
                ForecastResult(cached, ForecastStatus.STALE, listOf("Using cached forecast: ${e.message}"))
            } else {
                throw e
            }
        }
    }

    private suspend fun fetch(latitude: Double, longitude: Double): Pair<Forecast, List<String>> = coroutineScope {
        val astro = async { runCatching { sevenTimer.fetch(latitude, longitude) } }
        val weather = openMeteo.fetch(latitude, longitude)
        val points = astro.await()
        val warnings = points.exceptionOrNull()?.let { listOf("Seeing/transparency unavailable (7Timer): ${it.message}") }
            .orEmpty()
        weather.withSevenTimer(points.getOrDefault(emptyList())) to warnings
    }

    companion object {
        /** ~1 km grid so tiny GPS jitter reuses the same cache entry. */
        fun cacheKey(latitude: Double, longitude: Double): String =
            String.format(Locale.ROOT, "fc_%.2f_%.2f", latitude, longitude)
    }
}
