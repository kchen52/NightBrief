package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * One hourly CAMS air-quality point. Every value may be null when the model did not report it.
 * Not a [app.nightbrief.score.NightScoreEngine] input.
 */
data class AirQualitySample(
    val epochSecond: Long,
    /** Fine particulate matter, µg/m³. The smoke reading the app grades. */
    val pm25: Double? = null,
    /** Coarse particulate matter, µg/m³. */
    val pm10: Double? = null,
    /** Aerosol optical depth at 550 nm, unitless. */
    val aerosolOpticalDepth: Double? = null,
) {
    val time: Instant get() = Instant.ofEpochSecond(epochSecond)
}

/** Air-quality series for one location. The global CAMS domain is 3-hourly, so read it by nearest sample. */
data class AirQualitySeries(val samples: List<AirQualitySample>) {
    /** Sorted once per series; [samples] may arrive unsorted. */
    private val sorted: List<AirQualitySample> by lazy { samples.sortedBy { it.epochSecond } }

    /**
     * Nearest sample to [time] within [TOLERANCE_SECONDS]. Ties go to the earlier sample.
     * Null when no sample is close enough, so a forecast hour is never joined to a reading
     * from another part of the night.
     */
    fun at(time: Instant): AirQualitySample? {
        if (samples.isEmpty()) return null
        val sortedSamples = sorted
        val epochs = sortedSamples.map { it.epochSecond }
        val hit = epochs.binarySearch(time.epochSecond)
        if (hit >= 0) return sortedSamples[hit]
        val upper = -hit - 1
        val lower = sortedSamples.getOrNull(upper - 1)
        val above = sortedSamples.getOrNull(upper)
        val nearest = when {
            lower == null -> above
            above == null -> lower
            time.epochSecond - lower.epochSecond <= above.epochSecond - time.epochSecond -> lower
            else -> above
        }
        return nearest?.takeIf { abs(it.epochSecond - time.epochSecond) <= TOLERANCE_SECONDS }
    }

    companion object {
        /** Half of CAMS's 3-hour step, so every forecast hour sees its own sample and no neighbour's. */
        const val TOLERANCE_SECONDS = 90 * 60L
    }
}

interface AirQualitySource {
    /** Throws [WeatherApiException] when the series cannot be fetched or parsed. */
    suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries
}

/**
 * In-memory cache in front of an [AirQualitySource], keyed to the same ~1 km cell the forecast
 * cache uses. A result younger than [maxAge] is returned without a network call. One origin call
 * is bounded by [originTimeout]; concurrent callers for the same cell share that call, while
 * different cells fetch at the same time. After a failure later calls for that cell fail fast
 * for [failureCooldown] instead of waiting out another doomed fetch.
 */
class CachingAirQualitySource(
    private val origin: AirQualitySource,
    private val maxAge: Duration = Duration.ofMinutes(60),
    private val clock: Clock = Clock.systemUTC(),
    private val originTimeout: Duration = Duration.ofSeconds(10),
    private val failureCooldown: Duration = Duration.ofMinutes(30),
) : AirQualitySource {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cached = ConcurrentHashMap<String, AirQualitySeries>()
    private val fetchedAt = ConcurrentHashMap<String, Instant>()
    private val lastFailure = ConcurrentHashMap<String, Instant>()

    override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries {
        val key = cacheKey(latitude, longitude)
        return locks.computeIfAbsent(key) { Mutex() }.withLock { fetchLocked(key, latitude, longitude) }
    }

    private suspend fun fetchLocked(key: String, latitude: Double, longitude: Double): AirQualitySeries {
        val now = clock.instant()
        val result = cached[key]
        val at = fetchedAt[key]
        if (result != null && at != null && Duration.between(at, now) < maxAge) return result
        if (failedRecently(key, now)) {
            throw WeatherApiException("Open-Meteo air quality unavailable: the last fetch failed recently; retrying later")
        }
        try {
            val fresh = withTimeout(originTimeout.toMillis()) { origin.fetch(latitude, longitude) }
            cached[key] = fresh
            fetchedAt[key] = clock.instant()
            lastFailure.remove(key)
            return fresh
        } catch (e: TimeoutCancellationException) {
            lastFailure[key] = clock.instant()
            throw WeatherApiException("Open-Meteo air quality fetch timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeatherApiException) {
            lastFailure[key] = clock.instant()
            throw e
        }
    }

    /** True when the last origin failure for [key] is recent. A clock that moved backwards never counts. */
    private fun failedRecently(key: String, now: Instant): Boolean {
        val failedAt = lastFailure[key] ?: return false
        val since = Duration.between(failedAt, now)
        return !since.isNegative && since < failureCooldown
    }

    private fun cacheKey(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "aq_%.2f_%.2f", latitude, longitude)
}

/**
 * Open-Meteo air-quality client (no key required). CAMS data, so a surface that shows it
 * carries the Open-Meteo and CAMS ENSEMBLE credit.
 *
 * `https://air-quality-api.open-meteo.com/v1/air-quality`
 *
 * The response is the same shape as the forecast endpoint: an `hourly` object of parallel arrays.
 * Only `pm2_5`, `pm10`, and `aerosol_optical_depth` are requested. Any value may be null.
 */
class AirQualityClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_URL,
) : AirQualitySource {
    override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.4f".format(Locale.ROOT, latitude))
            .addQueryParameter("longitude", "%.4f".format(Locale.ROOT, longitude))
            .addQueryParameter("hourly", HOURLY_FIELDS.joinToString(","))
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("timezone", "GMT")
            .addQueryParameter("forecast_days", FORECAST_DAYS.toString())
            .build()
        return parse(http.getString(url.toString()))
    }

    internal fun parse(body: String): AirQualitySeries {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw WeatherApiException("malformed Open-Meteo air-quality response", e)
        }
        if (root["error"]?.jsonPrimitive?.content == "true") {
            throw WeatherApiException("Open-Meteo air quality error: ${root["reason"]?.jsonPrimitive?.content}")
        }
        val hourly = root["hourly"]?.jsonObject
            ?: throw WeatherApiException("Open-Meteo air-quality response missing hourly data")
        val times = hourly["time"]?.jsonArray
            ?: throw WeatherApiException("Open-Meteo air-quality response missing time")

        fun series(name: String): JsonArray? = hourly[name] as? JsonArray
        fun JsonArray?.num(i: Int): Double? =
            this?.getOrNull(i)?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull

        val pm25 = series("pm2_5")
        val pm10 = series("pm10")
        val aod = series("aerosol_optical_depth")
        val samples = times.mapIndexedNotNull { i, t ->
            val epoch = t.jsonPrimitive.longOrNull ?: return@mapIndexedNotNull null
            AirQualitySample(
                epochSecond = epoch,
                pm25 = pm25.num(i)?.takeIf { it >= 0.0 },
                pm10 = pm10.num(i)?.takeIf { it >= 0.0 },
                aerosolOpticalDepth = aod.num(i)?.takeIf { it >= 0.0 },
            )
        }
        return AirQualitySeries(samples.sortedBy { it.epochSecond })
    }

    companion object {
        const val DEFAULT_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"

        /** Tonight plus the planner's usual week, so opening a later night still finds a reading. */
        const val FORECAST_DAYS = 7

        val HOURLY_FIELDS = listOf("pm2_5", "pm10", "aerosol_optical_depth")
    }
}
