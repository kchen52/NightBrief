package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.util.Locale

/**
 * IANA time zone for a coordinate, from Open-Meteo's `timezone=auto`.
 *
 * A forecast request with only latitude, longitude, and `timezone=auto` returns `timezone`
 * (for example `America/Toronto`). Null when the call fails or [ZoneId] rejects the value.
 */
class TimeZoneLookup(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun zoneFor(latitude: Double, longitude: Double): String? {
        try {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", "%.4f".format(Locale.ROOT, latitude))
                .addQueryParameter("longitude", "%.4f".format(Locale.ROOT, longitude))
                .addQueryParameter("timezone", "auto")
                .build()
            val body = http.getString(url.toString())
            val zone = Json.parseToJsonElement(body).jsonObject["timezone"]?.jsonPrimitive?.contentOrNull?.trim()
            if (zone.isNullOrEmpty()) return null
            return zone.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast"
    }
}
