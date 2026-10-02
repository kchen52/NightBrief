package app.nightbrief.score

import app.nightbrief.weather.HourlyWeather
import java.time.Instant
import kotlin.math.roundToInt

/** What condensation on the glass is doing. Frost is dew that is also at or below freezing. */
enum class Condensation { DEW, FROST }

/** First dark hour that can fog the glass. */
data class DewOnset(
    val time: Instant,
    val kind: Condensation,
)

/**
 * Dew, frost, and the overnight low for one night.
 *
 * Not a [NightScoreEngine] factor. The session log has to calibrate it before it can move a score.
 */
data class DewOutlook(
    /** First dark hour where temperature − dew point ≤ [DewRisk.SPREAD_C], if any. */
    val onset: DewOnset?,
    /** Coldest temperature among dark hours that reported one. */
    val overnightLowC: Double?,
)

/**
 * Flags dark hours that are close enough to saturation to dew the front element.
 *
 * A hour is dew when `temperature − dewPoint ≤ 2 °C`. It is frost when that is also true and
 * `temperature ≤ 0 °C`. Hours missing either reading are not flagged. The overnight low uses
 * every dark hour that has a temperature, including hours that stay dry.
 */
object DewRisk {
    const val SPREAD_C = 2.0

    fun fromDarkHours(hours: List<HourlyWeather>): DewOutlook? {
        if (hours.isEmpty()) return null
        val onset = hours
            .asSequence()
            .mapNotNull { hour ->
                val kind = kindOf(hour.temperatureC, hour.dewPointC) ?: return@mapNotNull null
                DewOnset(hour.time, kind)
            }
            .minByOrNull { it.time }
        val low = hours.mapNotNull { it.temperatureC }.minOrNull()
        if (onset == null && low == null) return null
        return DewOutlook(onset, low)
    }

    /** Dew, frost, or null when the spread is wider than [SPREAD_C] or either reading is missing. */
    fun kindOf(temperatureC: Double?, dewPointC: Double?): Condensation? {
        if (temperatureC == null || dewPointC == null) return null
        if (temperatureC - dewPointC > SPREAD_C) return null
        return if (temperatureC <= 0.0) Condensation.FROST else Condensation.DEW
    }
}

object DewCopy {
    fun warning(outlook: DewOutlook, fmt: (Instant) -> String): String? {
        val onset = outlook.onset ?: return null
        val kind = if (onset.kind == Condensation.FROST) "Frost" else "Dew"
        return "$kind likely from ${fmt(onset.time)} — bring a heater"
    }

    fun dress(outlook: DewOutlook): String? = outlook.overnightLowC?.let(::dress)

    fun dress(celsius: Double): String = "Dress for ${formatCelsius(celsius)}"

    /** Whole degrees. Ties follow [roundToInt] (half toward +∞). Negatives use a minus sign, not a hyphen. */
    fun formatCelsius(celsius: Double): String {
        val rounded = celsius.roundToInt()
        val body = if (rounded < 0) "−${-rounded}" else rounded.toString()
        return "$body °C"
    }
}
