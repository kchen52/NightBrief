package app.nightbrief.score

import app.nightbrief.weather.AirQualitySeries
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Wildfire smoke during one night's dark window. Display only: this is not a
 * [NightScoreEngine] factor, so the score and its fixtures are unchanged.
 *
 * A dark hour is flagged when its PM2.5 reading is above [Smoke.PM25_BAR]. [thinningAt] is the
 * first dark hour after [from] back at or below the bar, which is the part of the night worth
 * staying out for. Readings are joined to the nearest CAMS sample within
 * [app.nightbrief.weather.AirQualitySeries.TOLERANCE_SECONDS], so a 3-hourly model never
 * describes an hour it did not measure.
 */
data class SmokeOutlook(
    /** First flagged dark hour, or null when PM2.5 stays at or below [Smoke.PM25_BAR]. */
    val from: Instant?,
    /** Highest PM2.5 among dark hours, µg/m³. At least one dark hour reported a reading. */
    val peakPm25: Double,
    /** First dark hour after [from] back at or below the bar, or null when it stays smoky. */
    val thinningAt: Instant? = null,
) {
    val smoky: Boolean get() = from != null
}

object Smoke {
    /**
     * PM2.5 above which the air counts as smoky, µg/m³.
     * The US 24-hour standard is 35 µg/m³; a night above it is visibly milky.
     */
    const val PM25_BAR = 35.0

    /**
     * Null when [series] is null or no dark hour has a PM2.5 reading, so the surface leaves
     * the card out rather than claiming the air is clear.
     */
    fun assess(darkHours: List<Instant>, series: AirQualitySeries?): SmokeOutlook? {
        if (series == null) return null
        val readings = darkHours.distinct().sorted().mapNotNull { time ->
            series.at(time)?.pm25?.let { time to it }
        }
        if (readings.isEmpty()) return null
        val from = readings.firstOrNull { it.second > PM25_BAR }?.first
        return SmokeOutlook(
            from = from,
            peakPm25 = readings.maxOf { it.second },
            thinningAt = from?.let { start ->
                readings.firstOrNull { it.first > start && it.second <= PM25_BAR }?.first
            },
        )
    }
}

object SmokeCopy {
    /** One line naming when smoke starts and, when it lifts, when it thins. Null when the air is clear. */
    fun riskLine(smoke: SmokeOutlook, fmt: (Instant) -> String): String? {
        val from = smoke.from ?: return null
        val thinning = smoke.thinningAt
        return if (thinning != null) {
            "Smoke from ${fmt(from)}, thinning after ${fmt(thinning)}"
        } else {
            "Smoke from ${fmt(from)} through the night"
        }
    }

    /** The peak PM2.5 reading, named so a number is never presented without its bar. */
    fun peakLine(smoke: SmokeOutlook): String =
        "Peak PM2.5 ${smoke.peakPm25.roundToInt()} µg/m³ (smoky above ${Smoke.PM25_BAR.roundToInt()})"

    fun detail(smoke: SmokeOutlook): String = when {
        smoke.from != null && smoke.thinningAt != null ->
            "Smoke scatters light and cuts transparency even when the humidity reads dry. " +
                "It thins later tonight, so aim for the second half."
        smoke.from != null ->
            "Smoke scatters light and cuts transparency even when the humidity reads dry. " +
                "Expect a milky sky and softer contrast all night."
        else ->
            "PM2.5 stays at or below ${Smoke.PM25_BAR.roundToInt()} µg/m³ during darkness, " +
                "so smoke is not dulling the sky tonight."
    }
}
