package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.sites.Site
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSample
import app.nightbrief.weather.KpStatus
import java.time.Instant
import kotlin.math.abs

/** Planning-grade chance of seeing aurora from a site, based on planetary Kp and geographic latitude. */
enum class AuroraChance { UNLIKELY, POSSIBLE, LIKELY }

/**
 * Kp during one night at one site. This is not a [NightScoreEngine] factor; the night score is unchanged.
 *
 * [highLatitude] is geographic |latitude| ≥ [Aurora.HIGH_LATITUDE_DEG]. It is not the Open-Meteo GEM box.
 */
data class AuroraOutlook(
    val peakKp: Double,
    val noaaScale: String?,
    val status: KpStatus,
    val highLatitude: Boolean,
    val chance: AuroraChance,
    val latitudeDeg: Double,
) {
    /**
     * Lead the digest and tint the Tonight row.
     * High-latitude sites reach this at lower Kp than mid-latitude sites; see [Aurora.chance].
     */
    val prominent: Boolean get() = chance != AuroraChance.UNLIKELY
}

object Aurora {
    /**
     * Geographic |latitude| at or above this is high-latitude for aurora copy and the prominence boost.
     * Chosen so the auroral-zone side of ~55° (Yellowknife, Fairbanks, Reykjavik) is included and the
     * GEM forecast box (which also covers Toronto and the northern US border) is not.
     */
    const val HIGH_LATITUDE_DEG = 55.0

    /** Kp at or above this is "active" and, at high latitude, upgrades [chance] by one step. */
    const val ACTIVE_KP = 4.0

    /**
     * Equatorward geographic latitude where aurora may show on the poleward horizon.
     * Kp 3 → 62°, Kp 5 → 54°, Kp 7 → 46°, Kp 9 → 38°. Formula: 74 − 4×Kp, clamped to 35°..70°.
     * A planning stand-in, not a geomagnetic conversion.
     */
    fun viewLineDeg(kp: Double): Double = (74.0 - 4.0 * kp).coerceIn(35.0, 70.0)

    fun chance(absLatitude: Double, kp: Double, highLatitude: Boolean = absLatitude >= HIGH_LATITUDE_DEG): AuroraChance {
        val line = viewLineDeg(kp)
        val base = when {
            absLatitude >= line + 5.0 -> AuroraChance.LIKELY
            absLatitude >= line -> AuroraChance.POSSIBLE
            else -> AuroraChance.UNLIKELY
        }
        return if (highLatitude && kp >= ACTIVE_KP) base.boost() else base
    }

    /**
     * Peak Kp whose 3-hour bin overlaps [NightEphemeris.darkWindow], or null when the series
     * does not cover that window (or the night never gets dark).
     */
    fun forNight(site: Site, ephemeris: NightEphemeris, forecast: KpForecast): AuroraOutlook? {
        val window = ephemeris.darkWindow ?: return null
        return forWindow(site, window.start, window.end, forecast)
    }

    fun forWindow(site: Site, start: Instant, end: Instant, forecast: KpForecast): AuroraOutlook? {
        val covering = forecast.covering(start, end)
        if (covering.isEmpty()) return null
        val peak = covering.maxWith(compareBy<KpSample> { it.kp }.thenBy { it.epochSecond })
        val scale = covering.mapNotNull { it.noaaScale }.maxByOrNull { scaleRank(it) }
        val absLat = abs(site.latitude)
        val high = absLat >= HIGH_LATITUDE_DEG
        return AuroraOutlook(
            peakKp = peak.kp,
            noaaScale = scale,
            status = peak.status,
            highLatitude = high,
            chance = chance(absLat, peak.kp, high),
            latitudeDeg = site.latitude,
        )
    }

    private fun AuroraChance.boost(): AuroraChance = when (this) {
        AuroraChance.UNLIKELY -> AuroraChance.POSSIBLE
        AuroraChance.POSSIBLE -> AuroraChance.LIKELY
        AuroraChance.LIKELY -> AuroraChance.LIKELY
    }

    private fun scaleRank(scale: String): Int = scale.drop(1).toIntOrNull() ?: 0
}

object AuroraCopy {
    fun digestLine(aurora: AuroraOutlook): String {
        val kp = "Kp ${formatKp(aurora.peakKp)}"
        val scale = aurora.noaaScale?.let { " $it" }.orEmpty()
        val status = statusWord(aurora.status)
        val reading = "$kp$scale ($status)"
        return when (aurora.chance) {
            AuroraChance.LIKELY -> "Aurora likely: $reading"
            AuroraChance.POSSIBLE ->
                if (aurora.highLatitude) "Aurora watch: $reading — possible at this latitude"
                else "Aurora possible: $reading"
            AuroraChance.UNLIKELY ->
                if (aurora.highLatitude) "$reading — aurora quiet tonight"
                else "$reading — aurora unlikely ${awayFromPole(aurora.latitudeDeg)}"
        }
    }

    fun detail(aurora: AuroraOutlook): String {
        val pole = if (aurora.latitudeDeg >= 0) "north" else "south"
        return when (aurora.chance) {
            AuroraChance.LIKELY ->
                "Peak planetary Kp during darkness is high enough that aurora should be visible in a clear sky. Look $pole."
            AuroraChance.POSSIBLE ->
                if (aurora.highLatitude) "Active enough to watch the $pole sky while it is dark."
                else "A storm this strong can reach this latitude. Look $pole while it is dark."
            AuroraChance.UNLIKELY ->
                if (aurora.highLatitude) "Activity is too low for a display tonight."
                else "Activity stays well poleward of this site."
        }
    }

    fun formatKp(kp: Double): String {
        val tenths = kotlin.math.round(kp * 10.0).toInt()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) whole.toString() else "$whole.$frac"
    }

    private fun statusWord(status: KpStatus): String = when (status) {
        KpStatus.OBSERVED -> "observed"
        KpStatus.ESTIMATED -> "estimated"
        KpStatus.PREDICTED -> "forecast"
    }

    private fun awayFromPole(latitudeDeg: Double): String =
        if (latitudeDeg >= 0) "this far south" else "this far north"
}
