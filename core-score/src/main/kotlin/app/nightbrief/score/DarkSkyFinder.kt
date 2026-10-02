package app.nightbrief.score

import app.nightbrief.gear.GearKit
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.Site
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A darker-sky candidate near a saved site, scored for the same night.
 *
 * [report] is null only when built by tests; [search] returns scored candidates only.
 * [delta] is candidate score minus the primary score, null when either score is missing.
 */
data class DarkSkyCandidate(
    val site: Site,
    val distanceKm: Double,
    val bortle: Int,
    val report: NightReport,
    val delta: Int?,
)

/** One ring sample before the grid lookup runs. */
data class DarkSkySample(
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val bearingDeg: Double,
)

object DarkSkyFinder {
    /** Outer search radius. */
    const val SEARCH_RADIUS_KM = 60.0

    /** Rings sampled around the primary site; 4 rings x 8 bearings keeps grid lookups bounded. */
    val RINGS_KM = listOf(15.0, 30.0, 45.0, 60.0)

    /** Bearings per ring (cardinals + intercardinals). */
    const val BEARINGS_PER_RING = 8

    /** Only this many of the darkest samples are scored against the forecast. */
    const val MAX_CANDIDATES_TO_SCORE = 5

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Samples the Bortle grid in rings around the primary site and scores the darkest
     * samples for [primary]'s night.
     *
     * Grid lookups run in parallel on [Dispatchers.IO]; the grids stay streamed (no in-memory
     * cache). One sample's grid or forecast failure skips that sample only. Returns at most
     * [maxToScore] candidates, darkest Bortle first, scored best first on ties.
     */
    suspend fun search(
        primary: NightReport,
        kit: GearKit,
        lookup: BortleLookup,
        briefings: BriefingSource,
        date: LocalDate = primary.date,
        radiusKm: Double = SEARCH_RADIUS_KM,
        ringsKm: List<Double> = RINGS_KM,
        bearingsPerRing: Int = BEARINGS_PER_RING,
        maxToScore: Int = MAX_CANDIDATES_TO_SCORE,
    ): List<DarkSkyCandidate> = coroutineScope {
        val samples = samplePoints(primary.site, radiusKm, ringsKm, bearingsPerRing)
        if (samples.isEmpty()) return@coroutineScope emptyList()
        val primaryBortle = primary.site.effectiveBortle
        val lookedUp = withContext(Dispatchers.IO) {
            samples.map { sample ->
                async {
                    try {
                        val bortle = lookup.lookup(sample.latitude, sample.longitude)
                        if (bortle == null || bortle >= primaryBortle) null
                        else sample to bortle
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val darkest = lookedUp
            .sortedWith(compareBy({ it.second }, { it.first.distanceKm }))
            .take(maxToScore.coerceAtLeast(0))
        darkest.map { (sample, bortle) ->
            async {
                val site = candidateSite(primary.site, sample, bortle)
                try {
                    val report = briefings.plan(site, date, kit)
                    val delta = report.scoreValue?.let { scored ->
                        primary.scoreValue?.let { scored - it }
                    }
                    DarkSkyCandidate(site, sample.distanceKm, bortle, report, delta)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
            .sortedWith(compareByDescending<DarkSkyCandidate> { it.report.scoreValue ?: Int.MIN_VALUE })
    }

    /**
     * Ring offsets around [site], innermost first. Pure geometry (no I/O) so the sweep
     * is unit-testable without a grid.
     */
    fun samplePoints(
        site: Site,
        radiusKm: Double = SEARCH_RADIUS_KM,
        ringsKm: List<Double> = RINGS_KM,
        bearingsPerRing: Int = BEARINGS_PER_RING,
    ): List<DarkSkySample> {
        if (radiusKm <= 0 || bearingsPerRing <= 0) return emptyList()
        val rings = ringsKm.filter { it > 0 && it <= radiusKm }.distinct().sorted()
        if (rings.isEmpty()) return emptyList()
        val step = 360.0 / bearingsPerRing
        return rings.flatMap { ring ->
            (0 until bearingsPerRing).map { i ->
                val bearing = i * step
                val (lat, lon) = destination(site.latitude, site.longitude, ring, bearing)
                DarkSkySample(lat, lon, ring, bearing)
            }
        }
    }

    /** Transient site for a sample. Same zone as the primary; a 60 km hop rarely crosses a zone. */
    internal fun candidateSite(primary: Site, sample: DarkSkySample, bortle: Int): Site = Site(
        id = "darksky-${"%.3f".format(sample.latitude)}-${"%.3f".format(sample.longitude)}",
        name = "Bortle $bortle · ${sample.distanceKm.toInt()} km ${DigestComposer.compass(sample.bearingDeg)}",
        latitude = sample.latitude,
        longitude = sample.longitude,
        zoneId = primary.zoneId,
        bortle = bortle,
        bortleSource = BortleSource.MAP,
        horizon = primary.horizon,
    )

    /** Great-circle destination from ([latitude], [longitude]) along [bearingDeg] for [distanceKm]. */
    fun destination(latitude: Double, longitude: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val angular = distanceKm / EARTH_RADIUS_KM
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val bearing = Math.toRadians(bearingDeg)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    /** Great-circle distance between two points. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1) / 2
        val dLon = Math.toRadians(lon2 - lon1) / 2
        val h = sin(dLat) * sin(dLat) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon) * sin(dLon)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from ([lat1], [lon1]) to ([lat2], [lon2]), in degrees east of north. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}
