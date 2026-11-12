package app.nightbrief.score

import app.nightbrief.astro.CometGeometry
import app.nightbrief.astro.CometOrbit
import app.nightbrief.astro.Comets
import app.nightbrief.astro.Ephemeris
import app.nightbrief.astro.NightEphemeris
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One comet bright enough, and high enough, to plan around during the dark window.
 * Not a night-score input.
 */
data class CometOutlook(
    val name: String,
    /** Total magnitude at [peakTime], from the IAU law. Lower is brighter. */
    val magnitude: Double,
    val peakAltitudeDeg: Double,
    val peakTime: Instant,
    val peakAzimuthDeg: Double,
) {
    /**
     * True when the comet is at least as bright as magnitude [CometAdvisor.MAGNITUDE_BAR]
     * and at least [CometAdvisor.MIN_ALTITUDE_DEG] up.
     */
    val worthShowing: Boolean
        get() = magnitude <= CometAdvisor.MAGNITUDE_BAR && peakAltitudeDeg >= CometAdvisor.MIN_ALTITUDE_DEG
}

/**
 * Picks one comet for a night from an MPC element list.
 *
 * A comet is a candidate when some dark-window sample is at least
 * [MIN_ALTITUDE_DEG] up and at least as bright as magnitude [MAGNITUDE_BAR]
 * (magnitude 8.0 counts; 8.1 does not). The card and the digest use the brightest
 * such sample. Comets more than two magnitudes fainter than the bar at the start,
 * middle, and end of the window are not scanned. An empty list, or a night with
 * no dark window, returns null.
 */
object CometAdvisor {
    /** Brighter than or equal to this total magnitude is worth a card. */
    const val MAGNITUDE_BAR = 8.0

    /** Samples below this altitude do not count, even when the comet is bright. */
    const val MIN_ALTITUDE_DEG = 10.0

    private val STEP: Duration = Duration.ofMinutes(10)
    private const val PREFILTER_MARGIN = 2.0

    fun forNight(ephemeris: NightEphemeris, orbits: List<CometOrbit>): CometOutlook? {
        val dark = ephemeris.darkWindow ?: return null
        if (orbits.isEmpty()) return null
        val mid = dark.start.plusMillis(dark.duration.toMillis() / 2)
        var best: CometOutlook? = null
        for (orbit in orbits) {
            if (fainterThanTheScan(orbit, dark.start, mid, dark.end)) continue
            val sample = bestSample(orbit, ephemeris, dark.start, dark.end) ?: continue
            best = brighter(best, sample)
        }
        return best
    }

    fun digestLine(outlook: CometOutlook): String {
        val mag = "%.1f".format(Locale.ROOT, outlook.magnitude)
        val alt = outlook.peakAltitudeDeg.roundToInt()
        return "${outlook.name} · mag $mag · $alt°"
    }

    fun detail(outlook: CometOutlook): String {
        val mag = "%.1f".format(Locale.ROOT, outlook.magnitude)
        val alt = outlook.peakAltitudeDeg.roundToInt()
        val bar = MAGNITUDE_BAR.toInt()
        return "${outlook.name} is about magnitude $mag when it reaches $alt°. " +
            "Fainter than magnitude $bar stays off this card. " +
            "The position ignores non-gravitational forces, so it can drift a little near perihelion."
    }

    private fun brighter(current: CometOutlook?, candidate: CometOutlook): CometOutlook {
        val best = current ?: return candidate
        val brighterMag = candidate.magnitude < best.magnitude - 1e-6
        val sameMagHigher = abs(candidate.magnitude - best.magnitude) <= 1e-6 &&
            candidate.peakAltitudeDeg > best.peakAltitudeDeg
        return if (brighterMag || sameMagHigher) candidate else best
    }

    /** True when every preview sample is too faint to bother scanning the window. */
    private fun fainterThanTheScan(orbit: CometOrbit, start: Instant, mid: Instant, end: Instant): Boolean {
        var brightest = Double.POSITIVE_INFINITY
        for (time in listOf(start, mid, end)) {
            val magnitude = previewMagnitude(orbit, time) ?: continue
            if (magnitude < brightest) brightest = magnitude
        }
        return brightest > MAGNITUDE_BAR + PREFILTER_MARGIN
    }

    private fun previewMagnitude(orbit: CometOrbit, time: Instant): Double? =
        try {
            Comets.geometry(orbit, time).magnitude
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun bestSample(
        orbit: CometOrbit,
        ephemeris: NightEphemeris,
        start: Instant,
        end: Instant,
    ): CometOutlook? {
        var best: CometOutlook? = null
        var time = start
        while (!time.isAfter(end)) {
            val geometry = try {
                Comets.geometry(orbit, time)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (geometry != null) {
                val outlook = outlookAt(orbit, geometry, time, ephemeris)
                if (outlook.worthShowing) best = brighter(best, outlook)
            }
            time = time.plus(STEP)
        }
        return best
    }

    private fun outlookAt(
        orbit: CometOrbit,
        geometry: CometGeometry,
        time: Instant,
        ephemeris: NightEphemeris,
    ): CometOutlook {
        val horizontal = Ephemeris.position(
            geometry.raDec,
            time,
            ephemeris.latitudeDeg,
            ephemeris.longitudeDeg,
        )
        return CometOutlook(
            name = orbit.name,
            magnitude = geometry.magnitude,
            peakAltitudeDeg = horizontal.altitudeDeg,
            peakTime = time,
            peakAzimuthDeg = horizontal.azimuthDeg,
        )
    }
}
