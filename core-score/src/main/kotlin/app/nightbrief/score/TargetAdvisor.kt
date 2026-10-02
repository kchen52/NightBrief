package app.nightbrief.score

import app.nightbrief.astro.Ephemeris
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.Target
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.astro.TargetKind
import app.nightbrief.astro.TimeWindow
import app.nightbrief.gear.ExposureCalculator
import app.nightbrief.gear.ExposureSuggestion
import app.nightbrief.gear.GearKit
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

data class TargetSuggestion(
    val target: Target,
    /** When the target is high enough, the sky is dark and moonlight is tolerable for it. */
    val window: TimeWindow,
    val bestTime: Instant,
    val peakAltitudeDeg: Double,
    val peakAzimuthDeg: Double,
    val moonUpDuringWindow: Boolean,
    val exposure: ExposureSuggestion?,
    val reason: String,
)

/** Suggests targets for a night based on moon phase, Bortle class, target altitude and the user's gear. */
object TargetAdvisor {
    private val STEP: Duration = Duration.ofMinutes(10)

    fun suggest(
        eph: NightEphemeris,
        bortle: Int,
        kit: GearKit,
        catalog: List<Target> = TargetCatalog.all,
        limit: Int = 4,
        horizonObstructionDeg: (Double) -> Double = { 0.0 },
    ): List<TargetSuggestion> {
        val dark = eph.darkWindow ?: return emptyList()
        return catalog
            .filter { bortle <= it.maxBortle }
            .filter { !it.requiresBrightMoon || eph.moonIllumination > TargetCatalog.BRIGHT_MOON_ILLUMINATION }
            .mapNotNull { evaluate(it, eph, dark, bortle, kit, horizonObstructionDeg) }
            .sortedByDescending { rank(it) }
            .take(limit)
    }

    private fun evaluate(
        target: Target,
        eph: NightEphemeris,
        dark: TimeWindow,
        bortle: Int,
        kit: GearKit,
        horizonObstructionDeg: (Double) -> Double,
    ): TargetSuggestion? {
        val lat = eph.latitudeDeg
        val lon = eph.longitudeDeg
        val usable = mutableListOf<Instant>()
        var moonUp = false
        var bestTime: Instant? = null
        var bestAlt = Double.NEGATIVE_INFINITY
        var bestAz = 0.0
        var t = dark.start
        while (!t.isAfter(dark.end)) {
            val moon = Ephemeris.moon(t, lat, lon)
            val skyAlt: Double
            val skyAz: Double
            if (target.tracksMoon) {
                skyAlt = moon.altitudeDeg
                skyAz = moon.azimuthDeg
            } else {
                val pos = Ephemeris.position(target.position, t, lat, lon)
                skyAlt = pos.altitudeDeg
                skyAz = pos.azimuthDeg
            }
            val moonOk = target.tracksMoon || moon.altitudeDeg <= 0 || moon.illumination <= target.maxMoonIllumination
            val floor = maxOf(target.minAltitudeDeg, horizonObstructionDeg(skyAz))
            if (skyAlt >= floor && moonOk) {
                usable += t
                if (moon.altitudeDeg > 0 && moon.illumination > NightEphemeris.NEGLIGIBLE_MOON) moonUp = true
                if (skyAlt > bestAlt) {
                    bestAlt = skyAlt
                    bestAz = skyAz
                    bestTime = t
                }
            }
            t = t.plus(STEP)
        }
        if (usable.size < 3 || bestTime == null) return null

        val brightMoon = moonUp && eph.moonIllumination > TargetCatalog.BRIGHT_MOON_ILLUMINATION
        val declination = if (target.tracksMoon) Ephemeris.moonDeclination(bestTime) else target.position.decDeg
        val exposure = ExposureCalculator.bestFor(
            kit,
            idealFocalFullFrameMm = target.idealFocalMm.toDouble(),
            minFocalFullFrameMm = target.minFocalMm.toDouble(),
            bortle = bortle,
            brightMoon = brightMoon,
            declinationDeg = declination,
        )
        val terminator = if (target.kind == TargetKind.MOON) {
            Ephemeris.moonTerminatorAngleDeg(bestTime).roundToInt().mod(360)
        } else {
            null
        }
        return TargetSuggestion(
            target = target,
            window = TimeWindow(usable.first(), usable.last()),
            bestTime = bestTime,
            peakAltitudeDeg = bestAlt,
            peakAzimuthDeg = bestAz,
            moonUpDuringWindow = moonUp,
            exposure = exposure,
            reason = reason(target, eph, bortle, moonUp, terminator),
        )
    }

    private fun reason(
        target: Target,
        eph: NightEphemeris,
        bortle: Int,
        moonUp: Boolean,
        terminatorDeg: Int?,
    ): String {
        val moonPct = (eph.moonIllumination * 100).toInt()
        val phase = eph.moonPhaseName.label
        return when {
            target.kind == TargetKind.MOON && terminatorDeg != null ->
                "$phase ($moonPct%), terminator at $terminatorDeg°"
            target.kind == TargetKind.MOONLIT ->
                "$phase ($moonPct%) lights the foreground"
            target.kind == TargetKind.MILKY_WAY && !moonUp -> "Moon-free core with Bortle $bortle skies"
            target.kind == TargetKind.MILKY_WAY -> "Core is up, but a $moonPct% Moon will brighten the sky"
            target.maxMoonIllumination >= 1.0 && eph.moonIllumination > TargetCatalog.BRIGHT_MOON_ILLUMINATION ->
                "Holds up well under a $moonPct% Moon"
            target.maxBortle >= 7 && bortle >= 6 -> "Bright enough to punch through Bortle $bortle skies"
            !moonUp -> "Well placed in a moon-free sky"
            else -> "Well placed tonight"
        }
    }

    private fun rank(s: TargetSuggestion): Double {
        val hours = s.window.duration.toMinutes() / 60.0
        var r = s.peakAltitudeDeg.coerceAtMost(70.0) + 6 * hours.coerceAtMost(6.0)
        if (s.target.kind == TargetKind.MILKY_WAY && !s.moonUpDuringWindow) r += 40
        if (s.target.kind == TargetKind.MILKY_WAY) r += 10
        if (s.target.maxMoonIllumination < 0.5 && !s.moonUpDuringWindow) r += 10
        if (s.target.kind == TargetKind.MOONLIT) r += 32
        if (s.target.kind == TargetKind.MOON) r += if (s.exposure?.reachesTarget == true) 40 else 24
        if (s.exposure != null && !s.exposure.reachesTarget && !s.target.tracksMoon) r -= 45
        return r
    }
}
