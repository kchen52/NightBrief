package app.nightbrief.astro

import app.nightbrief.astro.sgp4.Sgp4
import app.nightbrief.astro.sgp4.Sgp4Model
import app.nightbrief.astro.sgp4.greenwichMeanSiderealRad
import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One ISS pass as seen from a site. [rise] is null when the pass is already
 * above the horizon at the start of the requested window; [set] is null when
 * it is still up at the end. Azimuth is measured from north through east.
 */
data class IssPass(
    val rise: Instant?,
    val set: Instant?,
    val peak: Instant,
    val peakAltitudeDeg: Double,
    val peakAzimuthDeg: Double,
)

/**
 * Topocentric ISS passes from an SGP4 TEME state.
 *
 * The caller supplies the time window (typically the dark window), so no
 * twilight filter is applied. A pass is a contiguous interval with altitude
 * above the horizon. Only passes whose peak falls inside `[start, end]` and
 * whose peak altitude is at least [minPeakAltitudeDeg] are returned.
 */
object IssPasses {
    /**
     * Passes whose peak is inside [start, end] and whose peak altitude is at least
     * [minPeakAltitudeDeg] (default 10). The caller passes the dark window, so you do
     * not apply a separate twilight filter.
     *
     * Propagation: SGP4 TEME position, Greenwich mean sidereal time to ECEF (ignore polar
     * motion), then topocentric alt/az for an observer at [latitudeDeg], [longitudeDeg],
     * [siteAltitudeM] meters above the WGS-84 ellipsoid.
     * Step about 30s to find peaks; refine the peak to about 1s.
     * A pass is a contiguous interval where altitude > 0.
     */
    fun during(
        tleLine1: String,
        tleLine2: String,
        start: Instant,
        end: Instant,
        latitudeDeg: Double,
        longitudeDeg: Double,
        siteAltitudeM: Double = 0.0,
        minPeakAltitudeDeg: Double = 10.0,
    ): List<IssPass> {
        require(!end.isBefore(start)) { "end before start" }
        val model = Sgp4.model(tleLine1, tleLine2)
        val samples = coarseSamples(model, start, end, latitudeDeg, longitudeDeg, siteAltitudeM)
        val passes = ArrayList<IssPass>()
        var index = 0
        while (index < samples.size) {
            if (samples[index].altitudeDeg <= 0.0) {
                index++
                continue
            }
            val first = index
            while (index < samples.size && samples[index].altitudeDeg > 0.0) index++
            val last = index - 1
            toPass(
                model, samples, first, last, start, end,
                latitudeDeg, longitudeDeg, siteAltitudeM, minPeakAltitudeDeg,
            )?.let { passes += it }
        }
        return passes
    }
}

internal fun issAltAz(
    line1: String,
    line2: String,
    at: Instant,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double = 0.0,
): AltAz = observe(Sgp4.model(line1, line2), at, latitudeDeg, longitudeDeg, siteAltitudeM)

private const val COARSE_SECONDS = 30L
private const val WGS84_A_KM = 6378.137
private const val WGS84_F = 1.0 / 298.257223563

private data class AltSample(val time: Instant, val altitudeDeg: Double)

private fun coarseSamples(
    model: Sgp4Model,
    start: Instant,
    end: Instant,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
): List<AltSample> {
    val samples = ArrayList<AltSample>()
    var time = start
    while (!time.isAfter(end)) {
        samples += AltSample(time, observe(model, time, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg)
        val next = time.plusSeconds(COARSE_SECONDS)
        if (next.isAfter(end)) break
        time = next
    }
    if (samples.isEmpty() || samples.last().time != end) {
        samples += AltSample(end, observe(model, end, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg)
    }
    return samples
}

private fun toPass(
    model: Sgp4Model,
    samples: List<AltSample>,
    first: Int,
    last: Int,
    start: Instant,
    end: Instant,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
    minPeakAltitudeDeg: Double,
): IssPass? {
    val aboveAtStart = first == 0 && samples.first().altitudeDeg > 0.0
    val aboveAtEnd = last == samples.lastIndex && samples.last().altitudeDeg > 0.0
    val left = if (aboveAtStart) {
        walkToHorizon(model, samples.first().time, -COARSE_SECONDS, latitudeDeg, longitudeDeg, siteAltitudeM)
    } else {
        samples[first - 1].time
    }
    val right = if (aboveAtEnd) {
        walkToHorizon(model, samples.last().time, COARSE_SECONDS, latitudeDeg, longitudeDeg, siteAltitudeM)
    } else {
        samples[last + 1].time
    }
    val peak = refinePeak(model, left, right, latitudeDeg, longitudeDeg, siteAltitudeM)
    if (peak.time.isBefore(start) || peak.time.isAfter(end)) return null
    if (peak.altitudeDeg < minPeakAltitudeDeg) return null
    val rise = if (aboveAtStart) {
        null
    } else {
        refineCrossing(model, samples[first - 1].time, samples[first].time, rising = true, latitudeDeg, longitudeDeg, siteAltitudeM)
    }
    val set = if (aboveAtEnd) {
        null
    } else {
        refineCrossing(model, samples[last].time, samples[last + 1].time, rising = false, latitudeDeg, longitudeDeg, siteAltitudeM)
    }
    return IssPass(rise, set, peak.time, peak.altitudeDeg, peak.azimuthDeg)
}

/** Walk away from the window until the satellite is on or below the horizon, at the coarse step. */
private fun walkToHorizon(
    model: Sgp4Model,
    from: Instant,
    stepSeconds: Long,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
): Instant {
    var cursor = from
    repeat(60) {
        cursor = cursor.plusSeconds(stepSeconds)
        val altitude = observe(model, cursor, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg
        if (altitude <= 0.0) return cursor
    }
    return cursor
}

private data class Peak(val time: Instant, val altitudeDeg: Double, val azimuthDeg: Double)

private fun refinePeak(
    model: Sgp4Model,
    left: Instant,
    right: Instant,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
): Peak {
    val grid = ArrayList<AltSample>()
    var time = left
    while (!time.isAfter(right)) {
        grid += AltSample(time, observe(model, time, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg)
        val next = time.plusSeconds(COARSE_SECONDS)
        if (next.isAfter(right)) break
        time = next
    }
    if (grid.last().time != right) {
        grid += AltSample(right, observe(model, right, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg)
    }
    var best = 0
    for (i in grid.indices) if (grid[i].altitudeDeg > grid[best].altitudeDeg) best = i
    val from = grid[(best - 1).coerceAtLeast(0)].time
    val to = grid[(best + 1).coerceAtMost(grid.lastIndex)].time
    var bestTime = from
    var bestLook = observe(model, from, latitudeDeg, longitudeDeg, siteAltitudeM)
    var cursor = from.plusSeconds(1)
    while (!cursor.isAfter(to)) {
        val look = observe(model, cursor, latitudeDeg, longitudeDeg, siteAltitudeM)
        if (look.altitudeDeg > bestLook.altitudeDeg) {
            bestLook = look
            bestTime = cursor
        }
        cursor = cursor.plusSeconds(1)
    }
    return Peak(bestTime, bestLook.altitudeDeg, bestLook.azimuthDeg)
}

private fun refineCrossing(
    model: Sgp4Model,
    lo: Instant,
    hi: Instant,
    rising: Boolean,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
): Instant {
    var low = lo
    var high = hi
    while (Duration.between(low, high).toNanos() > 1_000_000_000L) {
        val mid = low.plusNanos(Duration.between(low, high).toNanos() / 2)
        val up = observe(model, mid, latitudeDeg, longitudeDeg, siteAltitudeM).altitudeDeg > 0.0
        if (rising) {
            if (up) high = mid else low = mid
        } else if (up) {
            low = mid
        } else {
            high = mid
        }
    }
    return if (rising) high else low
}

private fun observe(
    model: Sgp4Model,
    at: Instant,
    latitudeDeg: Double,
    longitudeDeg: Double,
    siteAltitudeM: Double,
): AltAz {
    val tem = model.propagate(at)
    val gmst = greenwichMeanSiderealRad(at)
    val cg = cos(gmst)
    val sg = sin(gmst)
    val x = cg * tem.xKm + sg * tem.yKm
    val y = -sg * tem.xKm + cg * tem.yKm
    val z = tem.zKm

    val lat = Math.toRadians(latitudeDeg)
    val lon = Math.toRadians(longitudeDeg)
    val e2 = WGS84_F * (2.0 - WGS84_F)
    val sinLat = sin(lat)
    val cosLat = cos(lat)
    val sinLon = sin(lon)
    val cosLon = cos(lon)
    val radius = WGS84_A_KM / sqrt(1.0 - e2 * sinLat * sinLat)
    val heightKm = siteAltitudeM / 1000.0
    val sx = (radius + heightKm) * cosLat * cosLon
    val sy = (radius + heightKm) * cosLat * sinLon
    val sz = (radius * (1.0 - e2) + heightKm) * sinLat
    val dx = x - sx
    val dy = y - sy
    val dz = z - sz
    val south = sinLat * cosLon * dx + sinLat * sinLon * dy - cosLat * dz
    val east = -sinLon * dx + cosLon * dy
    val up = cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz
    val range = sqrt(south * south + east * east + up * up).coerceAtLeast(1e-9)
    val altitude = Math.toDegrees(asin((up / range).coerceIn(-1.0, 1.0)))
    val azimuth = norm360(Math.toDegrees(atan2(east, -south)))
    return AltAz(altitude, azimuth)
}
