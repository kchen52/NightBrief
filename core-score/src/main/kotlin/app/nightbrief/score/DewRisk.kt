package app.nightbrief.score

import java.time.Instant
import kotlin.math.roundToInt

/** One hour of temperature and dew point. Either field may be missing. */
data class DewSample(
    val time: Instant,
    val temperatureC: Double?,
    val dewPointC: Double?,
)

/**
 * Moisture on the optics during darkness, plus the overnight low.
 * Not a [NightScoreEngine] factor — the night score is unchanged.
 */
data class DewOutlook(
    /** First sampled hour where temperature − dew point is at most [DewRisk.SPREAD_C]. */
    val dewFrom: Instant?,
    /** First dew hour whose temperature is at or below 0 °C. */
    val frostFrom: Instant?,
    /** Lowest temperature among the sampled hours, when any hour reported one. */
    val overnightLowC: Double?,
) {
    val hasContent: Boolean get() = dewFrom != null || frostFrom != null || overnightLowC != null
}

object DewRisk {
    /** Dew is likely when the air is this close to saturation, in °C. */
    const val SPREAD_C = 2.0

    fun assess(samples: List<DewSample>): DewOutlook {
        val dewy = samples.mapNotNull { sample ->
            val temperature = sample.temperatureC ?: return@mapNotNull null
            val dewPoint = sample.dewPointC ?: return@mapNotNull null
            if (temperature - dewPoint <= SPREAD_C) sample.time to temperature else null
        }
        return DewOutlook(
            dewFrom = dewy.firstOrNull()?.first,
            frostFrom = dewy.firstOrNull { it.second <= 0.0 }?.first,
            overnightLowC = samples.mapNotNull { it.temperatureC }.minOrNull(),
        )
    }

    /** "Dew likely from 23:40 — bring a heater", or the frost wording when it freezes. */
    fun line(outlook: DewOutlook, fmt: (Instant) -> String): String? {
        val dew = outlook.dewFrom
        val frost = outlook.frostFrom
        return when {
            frost != null && (dew == null || !frost.isAfter(dew)) ->
                "Frost likely from ${fmt(frost)} — bring a heater"
            dew != null && frost != null ->
                "Dew likely from ${fmt(dew)} — frost from ${fmt(frost)} — bring a heater"
            dew != null -> "Dew likely from ${fmt(dew)} — bring a heater"
            else -> null
        }
    }

    /** "Dress for −4 °C", or null when no hour reported a temperature. */
    fun dressLine(outlook: DewOutlook): String? {
        val low = outlook.overnightLowC ?: return null
        return "Dress for ${formatCelsius(low)}"
    }

    internal fun formatCelsius(celsius: Double): String {
        val rounded = celsius.roundToInt()
        val number = if (rounded < 0) "−${-rounded}" else rounded.toString()
        return "$number °C"
    }
}
