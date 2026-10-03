package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * Paved-road accessibility of one darker-sky candidate.
 *
 * [DRIVE_UP] means a mapped car road with a paved surface was found nearby.
 * [HIKE_IN] means only unpaved/track/path roads were found nearby, or no mapped
 * road at all. [UNKNOWN] means the lookup did not run or failed; it is never
 * returned by [OverpassRoadAccessClient] itself (failures throw), only by callers
 * that swallow the failure so the night score still returns.
 */
enum class RoadAccess { DRIVE_UP, HIKE_IN, UNKNOWN }

/**
 * Paved-road lookup for a coordinate. Throws [WeatherApiException] when the fetch
 * or parse fails; callers map that to [RoadAccess.UNKNOWN] so one bad cell never
 * fails the briefing.
 */
fun interface RoadAccessSource {
    suspend fun accessFor(latitude: Double, longitude: Double): RoadAccess
}

/**
 * OpenStreetMap road lookup via the Overpass API.
 *
 * Queries `way(around:[radiusM],[lat],[lon])[highway]` and classifies the returned
 * tags (see [classify]). An empty element list means no mapped road nearby, which
 * classifies as [RoadAccess.HIKE_IN] — that is data, not a failure. Malformed
 * bodies and HTTP failures throw [WeatherApiException].
 *
 * Road data is © OpenStreetMap contributors (ODbL); the picker already carries
 * that credit.
 */
class OverpassRoadAccessClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = DEFAULT_URL,
    private val radiusM: Int = DEFAULT_RADIUS_M,
) : RoadAccessSource {
    override suspend fun accessFor(latitude: Double, longitude: Double): RoadAccess {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("data", buildQuery(latitude, longitude, radiusM))
            .build()
        return parse(http.getString(url.toString()))
    }

    internal fun parse(body: String): RoadAccess {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw WeatherApiException("malformed Overpass response", e)
        }
        val elements = root["elements"]?.jsonArray
            ?: throw WeatherApiException("Overpass response missing elements")
        val tagMaps = elements.mapNotNull { element ->
            try {
                val tags = element.jsonObject["tags"]?.jsonObject ?: return@mapNotNull null
                val highway = tags["highway"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val surface = tags["surface"]?.jsonPrimitive?.contentOrNull
                mapOf("highway" to highway, "surface" to (surface ?: ""))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        return classify(tagMaps)
    }

    companion object {
        const val DEFAULT_URL = "https://overpass-api.de/api/interpreter"
        const val DEFAULT_RADIUS_M = 500

        /** Car-suitable highway values. Track/path/footway and friends never count as drive-up. */
        internal val MOTORABLE_HIGHWAYS = setOf(
            "motorway", "motorway_link",
            "trunk", "trunk_link",
            "primary", "primary_link",
            "secondary", "secondary_link",
            "tertiary", "tertiary_link",
            "unclassified", "residential", "living_street", "service",
        )

        /** Explicitly paved surfaces. Anything else on a motorable road (gravel, dirt, compacted, …) is not paved. */
        internal val PAVED_SURFACES = setOf(
            "paved", "asphalt", "concrete", "paving_stones", "sett", "cobblestone",
        )

        internal fun buildQuery(latitude: Double, longitude: Double, radiusM: Int): String {
            val lat = "%.5f".format(Locale.ROOT, latitude)
            val lon = "%.5f".format(Locale.ROOT, longitude)
            return "[out:json][timeout:10];way(around:$radiusM,$lat,$lon)[highway];out tags 40;"
        }

        /**
         * True when [tags] describe a paved car road. A missing surface on a motorable
         * highway counts as paved (OSM often omits the tag on urban paved roads); an
         * explicit unpaved surface never does.
         */
        internal fun isDriveUp(tags: Map<String, String>): Boolean {
            val highway = tags["highway"]?.lowercase(Locale.ROOT)?.trim() ?: return false
            if (highway !in MOTORABLE_HIGHWAYS) return false
            val surface = tags["surface"]?.lowercase(Locale.ROOT)?.trim().orEmpty()
            if (surface.isEmpty()) return true
            if (surface in PAVED_SURFACES) return true
            // Variants such as "concrete:plates" / "concrete:lanes" stay paved.
            if (surface.startsWith("paved") || surface.startsWith("asphalt") || surface.startsWith("concrete")) return true
            return false
        }

        /** [RoadAccess.DRIVE_UP] when any nearby way is a paved car road, else [RoadAccess.HIKE_IN]. */
        internal fun classify(tagMaps: List<Map<String, String>>): RoadAccess =
            if (tagMaps.any(::isDriveUp)) RoadAccess.DRIVE_UP else RoadAccess.HIKE_IN
    }
}
