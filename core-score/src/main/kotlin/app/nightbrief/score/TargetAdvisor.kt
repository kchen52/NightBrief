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

    /** Dual-band lets emission nebulae punch through ~2 Bortle classes and ~0.4 more Moon; LP filter adds ~1 class. */
    const val DUAL_BAND_BORTLE_BONUS = 2
    const val LIGHT_POLLUTION_BORTLE_BONUS = 1
    const val DUAL_BAND_MOON_BONUS = 0.4

    fun effectiveMaxBortle(target: Target, kit: GearKit): Int {
        if (target.kind != TargetKind.EMISSION_NEBULA) return target.maxBortle
        var bonus = 0
        if (kit.hasDualBandFilter) bonus += DUAL_BAND_BORTLE_BONUS
        // A broad LP filter helps emission nebulae a little, but never stacks past the dual-band headroom.
        else if (kit.hasLightPollutionFilter) bonus += LIGHT_POLLUTION_BORTLE_BONUS
        return (target.maxBortle + bonus).coerceAtMost(9)
    }

    fun effectiveMaxMoon(target: Target, kit: GearKit): Double {
        if (target.kind != TargetKind.EMISSION_NEBULA || !kit.hasDualBandFilter) return target.maxMoonIllumination
        return (target.maxMoonIllumination + DUAL_BAND_MOON_BONUS).coerceAtMost(1.0)
    }

    fun suggest(
        eph: NightEphemeris,
        bortle: Int,
        kit: GearKit,
        catalog: List<Target> = TargetCatalog.all,
        limit: Int = 4,
        horizonObstructionDeg: (Double) -> Double = { 0.0 },
    ): List<TargetSuggestion> {
        val dark = eph.darkWindow ?: return emptyList()
        val tracker = kit.tracker
        return catalog
            .filter { bortle <= effectiveMaxBortle(it, kit) }
            .filter { !it.requiresBrightMoon || eph.moonIllumination > TargetCatalog.BRIGHT_MOON_ILLUMINATION }
            // Trails need the sky to drift. A tighter target than the mount can track is not a plan.
            .filter { tracker == null || !it.untrackedOnly }
            .filter { tracker == null || it.tracksMoon || it.minFocalMm <= tracker.planningFocalMm }
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
            val moonOk = target.tracksMoon || moon.altitudeDeg <= 0 || moon.illumination <= effectiveMaxMoon(target, kit)
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
        // The Moon is a short exposure at its own rate. Don't shorten that framing to the mount's ceiling.
        val ideal = target.idealFocalMm.toDouble().let { raw ->
            val cap = kit.tracker?.takeIf { !target.tracksMoon }?.planningFocalMm?.toDouble()
            if (cap != null) raw.coerceAtMost(cap) else raw
        }
        val exposure = ExposureCalculator.bestFor(
            kit,
            idealFocalFullFrameMm = ideal,
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
        val trackedName = kit.tracker?.name?.takeIf {
            exposure?.tracked == true &&
                exposure.reachesTarget &&
                !target.tracksMoon &&
                target.minFocalMm >= 50 &&
                target.kind != TargetKind.MILKY_WAY &&
                target.kind != TargetKind.CONSTELLATION
        }
        return TargetSuggestion(
            target = target,
            window = TimeWindow(usable.first(), usable.last()),
            bestTime = bestTime,
            peakAltitudeDeg = bestAlt,
            peakAzimuthDeg = bestAz,
            moonUpDuringWindow = moonUp,
            exposure = exposure,
            reason = reason(target, eph, bortle, moonUp, terminator, kit, trackedName),
        )
    }

    private fun reason(
        target: Target,
        eph: NightEphemeris,
        bortle: Int,
        moonUp: Boolean,
        terminatorDeg: Int?,
        kit: GearKit,
        trackedName: String?,
    ): String {
        val moonPct = (eph.moonIllumination * 100).toInt()
        val phase = eph.moonPhaseName.label
        // Name the filter only when it changed the outcome: an emission target kept despite Bortle or Moon.
        val filterHelped = target.kind == TargetKind.EMISSION_NEBULA &&
            (kit.hasDualBandFilter || kit.hasLightPollutionFilter) &&
            (bortle > target.maxBortle || eph.moonIllumination > target.maxMoonIllumination)
        if (filterHelped) {
            val filterName = if (kit.hasDualBandFilter) "Dual-band filter" else "LP filter"
            return "$filterName helps under Bortle $bortle skies"
        }
        // The filter sentence wins: it is why the target is still on the list.
        if (trackedName != null) return "Tracked on the $trackedName"
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
        // A mount that can hold a tight target should surface it ahead of a constellation.
        if (s.exposure?.tracked == true && s.exposure.reachesTarget && s.target.minFocalMm >= 85) r += 20
        return r
    }
}
