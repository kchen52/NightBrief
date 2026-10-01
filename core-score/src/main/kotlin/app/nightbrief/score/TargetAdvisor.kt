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
    ): List<TargetSuggestion> {
        val dark = eph.darkWindow ?: return emptyList()
        return catalog
            .filter { bortle <= it.maxBortle }
            .mapNotNull { evaluate(it, eph, dark, bortle, kit) }
            .sortedByDescending { rank(it) }
            .take(limit)
    }

    private fun evaluate(
        target: Target,
        eph: NightEphemeris,
        dark: TimeWindow,
        bortle: Int,
        kit: GearKit,
    ): TargetSuggestion? {
        val lat = eph.latitudeDeg
        val lon = eph.longitudeDeg
        val usable = mutableListOf<Instant>()
        var moonUp = false
        var bestTime: Instant? = null
        var bestAlt = Double.NEGATIVE_INFINITY
        var t = dark.start
        while (!t.isAfter(dark.end)) {
            val alt = Ephemeris.position(target.position, t, lat, lon).altitudeDeg
            val moon = Ephemeris.moon(t, lat, lon)
            val moonOk = moon.altitudeDeg <= 0 || moon.illumination <= target.maxMoonIllumination
            if (alt >= target.minAltitudeDeg && moonOk) {
                usable += t
                if (moon.altitudeDeg > 0 && moon.illumination > NightEphemeris.NEGLIGIBLE_MOON) moonUp = true
                if (alt > bestAlt) {
                    bestAlt = alt
                    bestTime = t
                }
            }
            t = t.plus(STEP)
        }
        if (usable.size < 3 || bestTime == null) return null

        val brightMoon = moonUp && eph.moonIllumination > 0.5
        val exposure = ExposureCalculator.bestFor(
            kit,
            idealFocalFullFrameMm = target.idealFocalMm.toDouble(),
            minFocalFullFrameMm = target.minFocalMm.toDouble(),
            bortle = bortle,
            brightMoon = brightMoon,
            declinationDeg = target.position.decDeg,
        )
        return TargetSuggestion(
            target = target,
            window = TimeWindow(usable.first(), usable.last()),
            bestTime = bestTime,
            peakAltitudeDeg = bestAlt,
            peakAzimuthDeg = Ephemeris.position(target.position, bestTime, lat, lon).azimuthDeg,
            moonUpDuringWindow = moonUp,
            exposure = exposure,
            reason = reason(target, eph, bortle, moonUp),
        )
    }

    private fun reason(target: Target, eph: NightEphemeris, bortle: Int, moonUp: Boolean): String {
        val moonPct = (eph.moonIllumination * 100).toInt()
        return when {
            target.kind == TargetKind.MILKY_WAY && !moonUp -> "Moon-free core with Bortle $bortle skies"
            target.kind == TargetKind.MILKY_WAY -> "Core is up, but a $moonPct% Moon will brighten the sky"
            target.maxMoonIllumination >= 1.0 && eph.moonIllumination > 0.5 -> "Holds up well under a $moonPct% Moon"
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
        if (s.exposure != null && !s.exposure.reachesTarget) r -= 45
        return r
    }
}
