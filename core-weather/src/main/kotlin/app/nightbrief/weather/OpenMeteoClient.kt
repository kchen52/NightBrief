package app.nightbrief.weather

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
import kotlin.math.roundToInt

/** Client for the Open-Meteo forecast API (no key required). */
class OpenMeteoClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://api.open-meteo.com/v1/forecast",
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun fetch(
        latitude: Double,
        longitude: Double,
        days: Int = DEFAULT_FORECAST_DAYS,
        model: WeatherModel = WeatherModel.forLocation(latitude, longitude),
    ): Forecast {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.4f".format(java.util.Locale.ROOT, latitude))
            .addQueryParameter("longitude", "%.4f".format(java.util.Locale.ROOT, longitude))
            .addQueryParameter("hourly", HOURLY_FIELDS.joinToString(","))
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("timezone", "GMT")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("forecast_days", days.coerceIn(1, 16).toString())
            .addQueryParameter("models", model.apiName)
            .build()
        val body = http.getString(url.toString())
        return parse(body, latitude, longitude, model)
    }

    internal fun parse(body: String, latitude: Double, longitude: Double, model: WeatherModel): Forecast {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw WeatherApiException("malformed Open-Meteo response", e)
        }
        if (root["error"]?.jsonPrimitive?.content == "true") {
            throw WeatherApiException("Open-Meteo error: ${root["reason"]?.jsonPrimitive?.content}")
        }
        val hourly = root["hourly"]?.jsonObject ?: throw WeatherApiException("Open-Meteo response missing hourly data")
        val times = hourly["time"]?.jsonArray ?: throw WeatherApiException("Open-Meteo response missing time")

        fun series(name: String): JsonArray? = hourly[name] as? JsonArray
        fun JsonArray?.num(i: Int): Double? = this?.getOrNull(i)?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull
        fun JsonArray?.int(i: Int): Int? = num(i)?.roundToInt()

        val cloud = series("cloud_cover")
        val low = series("cloud_cover_low")
        val mid = series("cloud_cover_mid")
        val high = series("cloud_cover_high")
        val rh = series("relative_humidity_2m")
        val temp = series("temperature_2m")
        val dew = series("dew_point_2m")
        val wind = series("wind_speed_10m")
        val gust = series("wind_gusts_10m")
        val jet = series("wind_speed_250hPa")

        val hours = times.mapIndexedNotNull { i, t ->
            val epoch = t.jsonPrimitive.longOrNull ?: return@mapIndexedNotNull null
            HourlyWeather(
                epochSecond = epoch,
                cloudCover = cloud.int(i),
                cloudLow = low.int(i),
                cloudMid = mid.int(i),
                cloudHigh = high.int(i),
                humidity = rh.int(i),
                temperatureC = temp.num(i),
                dewPointC = dew.num(i),
                windKmh = wind.num(i),
                gustKmh = gust.num(i),
                jetStreamKmh = jet.num(i),
            )
        }
        return Forecast(latitude, longitude, model.apiName, clock.instant().epochSecond, hours)
    }

    companion object {
        /** Open-Meteo `forecast_days` used by the app. Planning dates run from tonight through tonight + (this - 1). */
        const val DEFAULT_FORECAST_DAYS = 8

        val HOURLY_FIELDS = listOf(
            "cloud_cover", "cloud_cover_low", "cloud_cover_mid", "cloud_cover_high",
            "relative_humidity_2m", "temperature_2m", "dew_point_2m",
            "wind_speed_10m", "wind_gusts_10m", "wind_speed_250hPa",
        )
    }
}
