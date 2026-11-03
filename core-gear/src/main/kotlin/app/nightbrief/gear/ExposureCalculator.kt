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
    /** Recommended sub-exposure (NPF, or NPF×4 on a tracker, rounded down to a camera value). */
    val shutterSeconds: Double,
    val npfSeconds: Double,
    val rule500Seconds: Double,
    val iso: Int,
    /** False when the lens can't reach the target's minimum useful focal length. */
    val reachesTarget: Boolean,
    /** True when a tracker was in the kit, so [shutterSeconds] may exceed the 30 s untracked cap. */
    val tracked: Boolean = false,
    /** Suggested stack count for a tracked exposure. Null when untracked. */
    val stackCount: Int? = null,
    /** Suggested total integration in minutes. Null when untracked. */
    val integrationMinutes: Int? = null,
    /**
     * Set when the chosen lens is designed for a smaller sensor than the body.
     * Null when the lens fits, or when its format is unknown.
     */
    val formatWarning: String? = null,
) {
    val fullFrameEquivalentMm: Double get() = focalMm * body.cropFactor

    /**
     * Technical line when both nicknames are null or blank.
     * A set nickname reads as "Old Faithful + the 10mm: 20 s at ISO 3200."
     * A lens nickname replaces "the {focal}mm". A tracked stack plan is appended either way.
     */
    val summary: String
        get() = withFormatWarning(exposureLine())

    private fun exposureLine(): String {
        val bodyNick = body.nickname.displayNick()
        val lensNick = lens.nickname.displayNick()
        if (bodyNick == null && lensNick == null) return technicalSummary()
        val lensPart = lensNick ?: "the ${focalMm.fmt()}mm"
        val who = if (bodyNick != null) "$bodyNick + $lensPart" else lensPart
        val friendly = "$who: ${shutterSeconds.fmt()} s at ISO $iso."
        if (tracked && stackCount != null && integrationMinutes != null) {
            return "$friendly · ${stackCount}×${ExposureCalculator.shutterLabel(shutterSeconds)} ($integrationMinutes min)"
        }
        return friendly
    }

    private fun withFormatWarning(line: String): String {
        val note = formatWarning?.takeIf { it.isNotBlank() } ?: return line
        return if (line.endsWith(".")) "$line $note" else "$line. $note"
    }

    private fun technicalSummary(): String = buildString {
        append("${focalMm.fmt()}mm · f/${aperture.fmt()} · ")
        append(ExposureCalculator.shutterLabel(shutterSeconds))
        append(" · ISO $iso")
        if (tracked && stackCount != null && integrationMinutes != null) {
            append(" · ${stackCount}×${ExposureCalculator.shutterLabel(shutterSeconds)} ($integrationMinutes min)")
        }
    }
}

/** Blank is the same as null: the exposure line stays technical. */
private fun String?.displayNick(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

object ExposureCalculator {

    private val STANDARD_SHUTTERS = listOf(
        1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0,
    )
    private val TRACKED_SHUTTERS = STANDARD_SHUTTERS + listOf(40.0, 50.0, 60.0, 90.0, 120.0, 180.0)
    /** Untracked subs stop at 30 s; a tracker lifts the cap to 120 s (polar alignment + skyfog, not NPF). */
    const val UNTRACKED_CAP_SECONDS = 30.0
    const val TRACKED_CAP_SECONDS = 120.0
    /** Tracked subs run this multiple of the NPF limit, so a 10 s untracked sub becomes ~40 s tracked. */
    const val TRACKED_NPF_FACTOR = 4.0
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

    fun roundDownToTrackedShutter(seconds: Double): Double =
        TRACKED_SHUTTERS.lastOrNull { it <= seconds + 1e-9 } ?: TRACKED_SHUTTERS.first()

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
        tracked: Boolean = false,
    ): ExposureSuggestion {
        val f = Math.round(focalMm).toDouble().coerceIn(lens.minFocalMm, lens.maxFocalMm)
        val n = lens.maxApertureAt(f)
        val npf = npf(f, n, body.pixelPitchUm, declinationDeg)
        if (!tracked) {
            val shutter = roundDownToStandardShutter(npf.coerceAtMost(UNTRACKED_CAP_SECONDS))
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
        val sub = roundDownToTrackedShutter((npf * TRACKED_NPF_FACTOR).coerceIn(UNTRACKED_CAP_SECONDS, TRACKED_CAP_SECONDS))
        val integrationMinutes = if ((targetMinFocalFullFrameMm ?: 0.0) >= 100.0) 60 else 30
        val stackCount = ((integrationMinutes * 60.0) / sub).toInt().coerceAtLeast(1)
        return ExposureSuggestion(
            body = body,
            lens = lens,
            focalMm = f,
            aperture = n,
            shutterSeconds = sub,
            npfSeconds = npf,
            rule500Seconds = rule500(f, body.cropFactor),
            iso = suggestIso(sub, n, bortle, brightMoon),
            reachesTarget = targetMinFocalFullFrameMm == null ||
                lens.maxFocalMm * body.cropFactor >= targetMinFocalFullFrameMm - 1e-6,
            tracked = true,
            stackCount = stackCount,
            integrationMinutes = integrationMinutes,
        )
    }

    /**
     * Picks the best lens in [kit] for a target whose useful framing is
     * [minFocalFullFrameMm]..[idealFocalFullFrameMm] (full-frame equivalent).
     *
     * A lens designed for a smaller sensor ranks after one that covers this body.
     * For a wide target (ideal at most [WIDE_IDEAL_FULL_FRAME_MM]), a faster lens anywhere in
     * that range wins, so a 15 mm f/2 beats an 18 mm f/2.8. Longer targets stay closest to the
     * ideal, then faster. A tracker in [kit] lifts the sub-exposure cap and adds a stack plan;
     * pass [tracked] to override.
     */
    fun bestFor(
        kit: GearKit,
        idealFocalFullFrameMm: Double,
        minFocalFullFrameMm: Double,
        bortle: Int,
        brightMoon: Boolean,
        declinationDeg: Double = 0.0,
        tracked: Boolean? = null,
    ): ExposureSuggestion? {
        val body = kit.primaryBody ?: return null
        if (kit.lenses.isEmpty()) return null
        val idealActual = idealFocalFullFrameMm / body.cropFactor
        val minActual = minFocalFullFrameMm / body.cropFactor
        val overlapping = kit.lenses.filter { it.maxFocalMm >= minActual && it.minFocalMm <= idealActual }
        val wide = idealFocalFullFrameMm <= WIDE_IDEAL_FULL_FRAME_MM
        val lens = if (overlapping.isNotEmpty()) {
            overlapping.minWith(
                if (wide) {
                    compareBy<Lens>(
                        { if (it.fitsBody(body)) 0 else 1 },
                        { it.apertureAt(idealActual) },
                        { distanceToIdeal(it, idealActual) },
                    )
                } else {
                    compareBy(
                        { if (it.fitsBody(body)) 0 else 1 },
                        { distanceToIdeal(it, idealActual) },
                        { it.apertureAt(idealActual) },
                    )
                },
            )
        } else {
            kit.lenses.minWith(
                compareBy<Lens>(
                    { if (it.fitsBody(body)) 0 else 1 },
                    { if (it.covers(idealActual)) 0.0 else distanceToRange(it, idealActual) },
                    { it.apertureAt(idealActual) },
                ),
            )
        }
        return suggest(
            body, lens, idealActual, bortle, brightMoon, declinationDeg, minFocalFullFrameMm,
            tracked = tracked ?: kit.isTracked,
        ).copy(formatWarning = formatWarning(body, lens))
    }

    /** Null when [lens] covers [body], or when the lens format is unknown. */
    fun formatWarning(body: CameraBody, lens: Lens): String? {
        if (lens.fitsBody(body)) return null
        val designed = lens.designedForCrop ?: return null
        return "${SensorFormat.fromCrop(designed).label} lens on a ${body.format.label} body."
    }

    private fun distanceToRange(lens: Lens, focal: Double): Double = when {
        focal < lens.minFocalMm -> ln(lens.minFocalMm / focal)
        else -> ln(focal / lens.maxFocalMm)
    }

    private fun distanceToIdeal(lens: Lens, focal: Double): Double {
        val closest = focal.coerceIn(lens.minFocalMm, lens.maxFocalMm)
        return abs(ln(closest / focal))
    }

    private const val WIDE_IDEAL_FULL_FRAME_MM = 40.0

    fun shutterLabel(seconds: Double): String = "${seconds.fmt()}s"
}

/** True when the lens format is unknown or its image circle covers [body]. */
private fun Lens.fitsBody(body: CameraBody): Boolean {
    val designed = designedForCrop ?: return true
    return body.cropFactor + 0.15 >= designed
}

private fun Lens.apertureAt(focal: Double): Double =
    maxApertureAt(focal.coerceIn(minFocalMm, maxFocalMm))
