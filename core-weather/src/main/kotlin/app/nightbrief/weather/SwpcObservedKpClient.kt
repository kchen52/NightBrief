package app.nightbrief.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * NOAA SWPC observed planetary K-index, the nowcast beside the forecast product [SwpcKpClient]
 * already reads.
 *
 * `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`
 *
 * The newest row is the 3-hour bin still in progress, so [KpForecast.covering] finds it with the
 * current instant. Every row here is a measurement: the product has no `observed` column, so
 * [KpSample.status] is always [KpStatus.OBSERVED]. Both shipped shapes are accepted, the same two
 * the forecast product uses: an array of objects (`time_tag`, `Kp`, `a_running`, `station_count`),
 * or a header row followed by rows of strings.
 */
class SwpcObservedKpClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val url: String = DEFAULT_URL,
) : KpSource {
    override suspend fun fetch(): KpForecast = parse(http.getString(url))

    internal fun parse(body: String): KpForecast {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw WeatherApiException("malformed SWPC observed Kp response", e)
        }
        val rows = root as? JsonArray ?: throw WeatherApiException("SWPC observed Kp response is not an array")
        if (rows.isEmpty()) return KpForecast(emptyList())
        val samples = when (rows.first()) {
            is JsonArray -> parseTabular(rows)
            is JsonObject -> rows.mapNotNull { (it as? JsonObject)?.let(::parseObject) }
            else -> throw WeatherApiException("SWPC observed Kp response has an unexpected row shape")
        }
        return KpForecast(samples.sortedBy { it.epochSecond })
    }

    private fun parseTabular(rows: JsonArray): List<KpSample> {
        val header = rows.first().jsonArray.map { it.stringOrNull()?.lowercase(Locale.ROOT) }
        val timeCol = header.indexOf("time_tag")
        val kpCol = header.indexOf("kp")
        if (timeCol < 0 || kpCol < 0) {
            throw WeatherApiException("SWPC observed Kp header is missing time_tag or kp")
        }
        return rows.drop(1).mapNotNull { row ->
            val cells = row as? JsonArray ?: return@mapNotNull null
            sample(cells.getOrNull(timeCol)?.stringOrNull(), cells.getOrNull(kpCol)?.stringOrNull())
        }
    }

    private fun parseObject(obj: JsonObject): KpSample? = sample(
        time = obj.field("time_tag"),
        kp = obj.field("Kp") ?: obj.field("kp"),
    )

    private fun sample(time: String?, kp: String?): KpSample? {
        val epoch = time?.let { SwpcKpClient.parseTime(it) } ?: return null
        val value = kp?.toDoubleOrNull()?.takeIf { it in 0.0..9.0 } ?: return null
        return KpSample(epoch.epochSecond, value, KpStatus.OBSERVED)
    }

    companion object {
        const val DEFAULT_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
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
