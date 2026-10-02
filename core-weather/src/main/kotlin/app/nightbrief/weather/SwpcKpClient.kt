package app.nightbrief.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Whether a Kp sample was measured or is still a forecast. */
enum class KpStatus { OBSERVED, ESTIMATED, PREDICTED }

/**
 * One 3-hour planetary Kp bin from NOAA SWPC.
 * [epochSecond] is the start of the bin; the value applies until three hours later.
 */
data class KpSample(
    val epochSecond: Long,
    val kp: Double,
    val status: KpStatus,
    /** NOAA geomagnetic storm scale, "G1".."G5", when this bin is at storm level. */
    val noaaScale: String? = null,
) {
    val time: Instant get() = Instant.ofEpochSecond(epochSecond)
}

/** Planetary Kp series. Not site-specific. */
data class KpForecast(val samples: List<KpSample>) {
    /** Samples whose 3-hour bin overlaps [[start], [end]). */
    fun covering(start: Instant, end: Instant): List<KpSample> =
        samples.filter { sample ->
            val binEnd = sample.epochSecond + BIN_SECONDS
            sample.epochSecond < end.epochSecond && binEnd > start.epochSecond
        }

    companion object {
        const val BIN_SECONDS = 3 * 3600L
    }
}

interface KpSource {
    /** Throws [WeatherApiException] when the forecast cannot be fetched or parsed. */
    suspend fun fetch(): KpForecast
}

/**
 * In-memory cache in front of a [KpSource].
 *
 * Kp is not site-specific, so every briefing, planner open, widget refresh, and Wear publish in
 * the same window would otherwise hit SWPC again. A result younger than [maxAge] is returned
 * without a network call. A failed fetch throws and leaves any cached value untouched, so the
 * briefing still falls back to omitting the aurora row.
 */
class CachingKpSource(
    private val origin: KpSource,
    private val maxAge: Duration = Duration.ofMinutes(60),
    private val clock: Clock = Clock.systemUTC(),
) : KpSource {
    private val lock = Any()
    private var cached: KpForecast? = null
    private var fetchedAt: Instant? = null

    override suspend fun fetch(): KpForecast {
        val now = clock.instant()
        synchronized(lock) {
            val result = cached
            val at = fetchedAt
            if (result != null && at != null && Duration.between(at, now) < maxAge) return result
        }
        val fresh = origin.fetch()
        synchronized(lock) {
            cached = fresh
            fetchedAt = clock.instant()
        }
        return fresh
    }
}

/**
 * NOAA SWPC planetary K-index forecast.
 *
 * `https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json`
 *
 * The product has shipped two shapes. Both are accepted:
 * - current: a JSON array of objects (`time_tag`, `kp`, `observed`, `noaa_scale`)
 * - legacy: a JSON array whose first row is those headers and whose later rows are strings
 *
 * `time_tag` is UTC, either `yyyy-MM-dd'T'HH:mm:ss` or `yyyy-MM-dd HH:mm:ss`.
 * `observed` is `observed`, `estimated`, or `predicted`. `kp` is the planetary index (0..9, in thirds).
 */
class SwpcKpClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val url: String = DEFAULT_URL,
) : KpSource {
    override suspend fun fetch(): KpForecast = parse(http.getString(url))

    internal fun parse(body: String): KpForecast {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw WeatherApiException("malformed SWPC Kp response", e)
        }
        val rows = root as? JsonArray ?: throw WeatherApiException("SWPC Kp response is not an array")
        if (rows.isEmpty()) return KpForecast(emptyList())
        val samples = when (rows.first()) {
            is JsonArray -> parseLegacy(rows)
            is JsonObject -> rows.mapNotNull { (it as? JsonObject)?.let(::parseObject) }
            else -> throw WeatherApiException("SWPC Kp response has an unexpected row shape")
        }
        return KpForecast(samples.sortedBy { it.epochSecond })
    }

    private fun parseLegacy(rows: JsonArray): List<KpSample> {
        val header = rows.first().jsonArray.map { it.stringOrNull()?.lowercase(Locale.ROOT) }
        val timeCol = header.indexOf("time_tag")
        val kpCol = header.indexOf("kp")
        val statusCol = header.indexOf("observed")
        val scaleCol = header.indexOf("noaa_scale")
        if (timeCol < 0 || kpCol < 0 || statusCol < 0) {
            throw WeatherApiException("SWPC Kp header is missing time_tag, kp, or observed")
        }
        return rows.drop(1).mapNotNull { row ->
            val cells = row as? JsonArray ?: return@mapNotNull null
            sample(
                time = cells.getOrNull(timeCol)?.stringOrNull(),
                kp = cells.getOrNull(kpCol)?.stringOrNull(),
                status = cells.getOrNull(statusCol)?.stringOrNull(),
                scale = if (scaleCol >= 0) cells.getOrNull(scaleCol)?.stringOrNull() else null,
            )
        }
    }

    private fun parseObject(obj: JsonObject): KpSample? = sample(
        time = obj.field("time_tag"),
        kp = obj.field("kp") ?: obj.field("Kp"),
        status = obj.field("observed"),
        scale = obj.field("noaa_scale"),
    )

    private fun sample(time: String?, kp: String?, status: String?, scale: String?): KpSample? {
        val epoch = time?.let(::parseTime) ?: return null
        val value = kp?.toDoubleOrNull()?.takeIf { it in 0.0..9.0 } ?: return null
        val parsedStatus = status?.let(::parseStatus) ?: return null
        return KpSample(epoch.epochSecond, value, parsedStatus, parseScale(scale))
    }

    companion object {
        const val DEFAULT_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"

        private val SPACE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        private val SCALE = Regex("G[1-5]")

        internal fun parseTime(raw: String): Instant? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            runCatching { Instant.parse(text) }.getOrNull()?.let { return it }
            val local = runCatching { LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text, SPACE_TIME) }.getOrNull()
                ?: return null
            return local.toInstant(ZoneOffset.UTC)
        }

        internal fun parseStatus(raw: String): KpStatus? = when (raw.trim().lowercase(Locale.ROOT)) {
            "observed" -> KpStatus.OBSERVED
            "estimated" -> KpStatus.ESTIMATED
            "predicted" -> KpStatus.PREDICTED
            else -> null
        }

        internal fun parseScale(raw: String?): String? =
            raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { SCALE.matches(it) }
    }
}

private fun JsonElement.stringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
}

private fun JsonObject.field(name: String): String? {
    val match = entries.firstOrNull { it.key.equals(name, ignoreCase = true) } ?: return null
    return match.value.stringOrNull()
}
