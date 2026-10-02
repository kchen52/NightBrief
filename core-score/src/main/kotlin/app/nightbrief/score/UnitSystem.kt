package app.nightbrief.score

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Display units for wind and temperature. Scoring stays in km/h and °C.
 *
 * [METRIC] and [IMPERIAL] switch both together: km/h with °C, or mph with °F.
 * A null preference follows [fromLocale]. Regions that use Fahrenheit day to day
 * (the US and a few others) default to imperial; everywhere else, including the
 * UK, defaults to metric.
 */
@Serializable
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    fun formatTemperature(celsius: Double): String = when (this) {
        METRIC -> DewCopy.formatCelsius(celsius)
        IMPERIAL -> formatSigned(celsiusToFahrenheit(celsius), "°F")
    }

    /** Nearest whole number for the timeline. The stored forecast value stays km/h. */
    fun formatWind(kmh: Double): String = when (this) {
        METRIC -> roundHalfAwayFromZero(kmh).toString()
        IMPERIAL -> roundHalfAwayFromZero(kmhToMph(kmh)).toString()
    }

    val windUnit: String
        get() = when (this) {
            METRIC -> "km/h"
            IMPERIAL -> "mph"
        }

    /** How wide [Dew.SPREAD_C] is, in the unit this system shows. */
    fun dewSpread(): String = when (this) {
        METRIC -> "2 °C"
        IMPERIAL -> "${roundHalfAwayFromZero(celsiusDeltaToFahrenheit(Dew.SPREAD_C))} °F"
    }

    companion object {
        /** International mile. Display only. */
        const val KM_PER_MILE = 1.609344

        /**
         * Countries and territories where everyday temperature is Fahrenheit.
         * Wind follows the same choice so one setting covers both.
         */
        private val FAHRENHEIT_REGIONS = setOf(
            "US", "PR", "GU", "VI", "AS", "MP",
            "BS", "BZ", "KY", "PW", "FM", "MH", "LR",
        )

        fun fromLocale(locale: Locale = Locale.getDefault()): UnitSystem {
            val country = locale.country.uppercase(Locale.ROOT)
            return if (country in FAHRENHEIT_REGIONS) IMPERIAL else METRIC
        }

        fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

        fun celsiusDeltaToFahrenheit(deltaCelsius: Double): Double = deltaCelsius * 9.0 / 5.0

        fun kmhToMph(kmh: Double): Double = kmh / KM_PER_MILE
    }
}

/** Nearest integer. A tie rounds away from zero, matching [DewCopy.formatCelsius]. */
internal fun roundHalfAwayFromZero(value: Double): Int = if (value >= 0.0) {
    floor(value + 0.5)
} else {
    ceil(value - 0.5)
}.toInt()

internal fun formatSigned(value: Double, unit: String): String {
    val rounded = roundHalfAwayFromZero(value)
    val number = if (rounded < 0) "−${abs(rounded)}" else rounded.toString()
    return "$number $unit"
}
