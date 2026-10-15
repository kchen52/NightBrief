package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * One geocoding hit. [region] is the first administrative area (a province or state).
 */
data class PlaceMatch(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
    val country: String? = null,
) {
    /**
     * Second line under [name]. A region that repeats the place name is left out,
     * so a city-state reads as the country only.
     */
    fun detail(): String? {
        val regionPart = region?.trim()?.takeUnless { it.isEmpty() || it.equals(name, ignoreCase = true) }
        val countryPart = country?.trim()?.takeUnless { it.isEmpty() }
        val line = listOfNotNull(regionPart, countryPart).joinToString(", ")
        return line.ifEmpty { null }
    }
}

/**
 * Outcome of one place-name search.
 * [failed] means the geocoder could not answer; [places] is empty and the map can still be tapped.
 */
data class PlaceSearchResult(
    val places: List<PlaceMatch> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Fuzzy place search via the Open-Meteo geocoding API (GeoNames, CC BY 4.0).
 *
 * Matching is the API's fuzzy search. This client sends the trimmed query unchanged,
 * so a typo is the server's job. A query shorter than [MIN_QUERY_LENGTH] returns no
 * places and does not use the network. A transport or parse failure returns
 * [PlaceSearchResult.failed] and does not throw, except [CancellationException].
 * The query is a place name sent to Open-Meteo, not a coordinate.
 */
class PlaceSearch(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun search(
        name: String,
        language: String = Locale.getDefault().language.ifBlank { "en" },
    ): PlaceSearchResult {
        val query = name.trim()
        if (query.length < MIN_QUERY_LENGTH) return PlaceSearchResult()
        return try {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("count", RESULT_COUNT.toString())
                .addQueryParameter("language", language.ifBlank { "en" })
                .addQueryParameter("format", "json")
                .build()
            PlaceSearchResult(places = parse(http.getString(url.toString())))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlaceSearchResult(failed = true)
        }
    }

    private fun parse(body: String): List<PlaceMatch> {
        val root = Json.parseToJsonElement(body).jsonObject
        val results = root["results"] ?: return emptyList()
        if (results is JsonNull) return emptyList()
        return results.jsonArray.mapNotNull { element ->
            if (element is JsonNull) return@mapNotNull null
            match(element.jsonObject)
        }
    }

    private fun match(obj: JsonObject): PlaceMatch? {
        val place = obj.text("name") ?: return null
        val lat = obj.number("latitude") ?: return null
        val lon = obj.number("longitude") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return PlaceMatch(
            name = place,
            latitude = lat,
            longitude = lon,
            region = obj.text("admin1"),
            country = obj.text("country"),
        )
    }

    private fun JsonObject.text(key: String): String? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.contentOrNull?.trim()?.ifEmpty { null }
    }

    private fun JsonObject.number(key: String): Double? {
        val value = this[key] ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.doubleOrNull
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
        /** Open-Meteo rejects anything shorter than this and does no fuzzy match. */
        const val MIN_QUERY_LENGTH = 2
        const val RESULT_COUNT = 8
    }
}
