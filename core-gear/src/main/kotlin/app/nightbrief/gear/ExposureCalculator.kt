package app.nightbrief.gear

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln

data class ExposureSuggestion(
    val body: CameraBody,
    val lens: Lens,
    /** Actual focal length set on the lens. */
    val focalMm: Double,
    val aperture: Double,
    /** Recommended shutter (NPF, rounded down to a standard camera value). */
    val shutterSeconds: Double,
    val npfSeconds: Double,
    val rule500Seconds: Double,
    val iso: Int,
    /** False when the lens can't reach the target's minimum useful focal length. */
    val reachesTarget: Boolean,
) {
    val fullFrameEquivalentMm: Double get() = focalMm * body.cropFactor

    val summary: String
        get() = "${focalMm.fmt()}mm · f/${aperture.fmt()} · " +
            "${ExposureCalculator.shutterLabel(shutterSeconds)} · ISO $iso"
}

object ExposureCalculator {

    private val STANDARD_SHUTTERS = listOf(
        1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0,
    )
    private val STANDARD_ISOS = listOf(
        400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
    )

    /** Classic 500 rule: 500 / (focal length × crop factor). */
    fun rule500(focalMm: Double, cropFactor: Double): Double = 500.0 / (focalMm * cropFactor)

    /**
     * Simplified NPF rule (Frédéric Michaud, Société Astronomique de France):
     * t = (35·N + 30·p) / f, with N the f-number, p the pixel pitch in µm and f the actual focal length.
     * Stars near the celestial pole move more slowly, so [declinationDeg] lengthens the limit by 1/cos(δ).
     */
    fun npf(focalMm: Double, aperture: Double, pixelPitchUm: Double, declinationDeg: Double = 0.0): Double {
        val base = (35.0 * aperture + 30.0 * pixelPitchUm) / focalMm
        val dec = abs(declinationDeg).coerceAtMost(80.0)
        return base / cos(Math.toRadians(dec))
    }

    fun roundDownToStandardShutter(seconds: Double): Double =
        STANDARD_SHUTTERS.lastOrNull { it <= seconds + 1e-9 } ?: STANDARD_SHUTTERS.first()

    /**
     * Heuristic ISO for an untracked exposure, anchored at ISO 3200 for 20 s at f/2.8 under a
     * Bortle 1–3 sky, then scaled for exposure time, aperture, light pollution and moonlight.
     */
    fun suggestIso(shutterSeconds: Double, aperture: Double, bortle: Int, brightMoon: Boolean): Int {
        var iso = 3200.0 * (20.0 / shutterSeconds) * (aperture / 2.8) * (aperture / 2.8)
        iso /= when (bortle) {
            in 1..3 -> 1.0
            in 4..5 -> 2.0
            in 6..7 -> 4.0
            else -> 8.0
        }
        if (brightMoon) iso /= 2.0
        return STANDARD_ISOS.minBy { abs(ln(it.toDouble()) - ln(iso)) }
    }

    fun suggest(
        body: CameraBody,
        lens: Lens,
        focalMm: Double,
        bortle: Int,
        brightMoon: Boolean,
        declinationDeg: Double = 0.0,
        targetMinFocalFullFrameMm: Double? = null,
    ): ExposureSuggestion {
        val f = Math.round(focalMm).toDouble().coerceIn(lens.minFocalMm, lens.maxFocalMm)
        val n = lens.maxApertureAt(f)
        val npf = npf(f, n, body.pixelPitchUm, declinationDeg)
        val shutter = roundDownToStandardShutter(npf.coerceAtMost(30.0))
        return ExposureSuggestion(
            body = body,
            lens = lens,
            focalMm = f,
            aperture = n,
            shutterSeconds = shutter,
            npfSeconds = npf,
            rule500Seconds = rule500(f, body.cropFactor),
            iso = suggestIso(shutter, n, bortle, brightMoon),
            reachesTarget = targetMinFocalFullFrameMm == null ||
                lens.maxFocalMm * body.cropFactor >= targetMinFocalFullFrameMm - 1e-6,
        )
    }

    /**
     * Picks the best lens in [kit] for a target whose ideal framing is [idealFocalFullFrameMm]
     * (full-frame equivalent), preferring lenses that cover that focal length, then faster apertures.
     */
    fun bestFor(
        kit: GearKit,
        idealFocalFullFrameMm: Double,
        minFocalFullFrameMm: Double,
        bortle: Int,
        brightMoon: Boolean,
        declinationDeg: Double = 0.0,
    ): ExposureSuggestion? {
        val body = kit.primaryBody ?: return null
        if (kit.lenses.isEmpty()) return null
        val idealActual = idealFocalFullFrameMm / body.cropFactor
        val lens = kit.lenses.minWith(
            compareBy<Lens>(
                { if (it.covers(idealActual)) 0.0 else distanceToRange(it, idealActual) },
                { it.maxApertureAt(idealActual.coerceIn(it.minFocalMm, it.maxFocalMm)) },
            ),
        )
        return suggest(
            body, lens, idealActual, bortle, brightMoon, declinationDeg, minFocalFullFrameMm,
        )
    }

    private fun distanceToRange(lens: Lens, focal: Double): Double = when {
        focal < lens.minFocalMm -> ln(lens.minFocalMm / focal)
        else -> ln(focal / lens.maxFocalMm)
    }

    fun shutterLabel(seconds: Double): String = "${seconds.fmt()}s"
}
