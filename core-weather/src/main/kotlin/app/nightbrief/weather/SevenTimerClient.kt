package app.nightbrief.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A 3-hourly point from the 7Timer! ASTRO product (72 h horizon). */
data class SevenTimerPoint(
    val epochSecond: Long,
    val seeing: Int?,
    val transparency: Int?,
    /** 7Timer cloud index 1..9 (not percent). */
    val cloudIndex: Int?,
)

class SevenTimerClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://www.7timer.info/bin/api.pl",
) {
    suspend fun fetch(latitude: Double, longitude: Double): List<SevenTimerPoint> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("lon", "%.3f".format(java.util.Locale.ROOT, longitude))
            .addQueryParameter("lat", "%.3f".format(java.util.Locale.ROOT, latitude))
            .addQueryParameter("product", "astro")
            .addQueryParameter("output", "json")
            .build()
        return parse(http.getString(url.toString()))
    }

    internal fun parse(body: String): List<SevenTimerPoint> {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw WeatherApiException("malformed 7Timer response", e)
        }
        val init = root["init"]?.jsonPrimitive?.content
            ?: throw WeatherApiException("7Timer response missing init")
        val initEpoch = LocalDateTime.parse(init, INIT_FORMAT).toEpochSecond(ZoneOffset.UTC)
        val series = root["dataseries"]?.jsonArray ?: throw WeatherApiException("7Timer response missing dataseries")
        return series.mapNotNull { el ->
            val o = el.jsonObject
            val tp = o["timepoint"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            SevenTimerPoint(
                epochSecond = initEpoch + tp * 3600L,
                seeing = o["seeing"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..8 },
                transparency = o["transparency"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..8 },
                cloudIndex = o["cloudcover"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..9 },
            )
        }
    }

    companion object {
        private val INIT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}

/** Attaches 7Timer seeing/transparency to the nearest Open-Meteo hour (within ±90 min). */
fun Forecast.withSevenTimer(points: List<SevenTimerPoint>): Forecast {
    if (points.isEmpty()) return this
    val sorted = points.sortedBy { it.epochSecond }
    return copy(
        hours = hours.map { h ->
            val nearest = sorted.minBy { kotlin.math.abs(it.epochSecond - h.epochSecond) }
            if (kotlin.math.abs(nearest.epochSecond - h.epochSecond) <= 90 * 60) {
                h.copy(seeing = nearest.seeing, transparency = nearest.transparency)
            } else {
                h
            }
        },
    )
}
