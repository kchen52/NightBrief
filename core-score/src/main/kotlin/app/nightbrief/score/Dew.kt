package app.nightbrief.score

import app.nightbrief.weather.Forecast
import java.time.Instant

/**
 * Dew and frost during one night. Display only: this is not a [NightScoreEngine] factor.
 *
 * A dark hour is flagged when temperature − dew point is at or below [Dew.SPREAD_C].
 * A flagged hour at or below [Dew.FROST_C] is frost. [overnightLowC] is the coldest
 * temperature from sunset to sunrise, so twilight counts and daytime does not.
 */
data class DewOutlook(
    /** First flagged dark hour, or null when the spread stays above [Dew.SPREAD_C]. */
    val from: Instant?,
    /** First flagged dark hour at or below freezing, or null when the risk stays above 0 °C. */
    val frostFrom: Instant?,
    /** Lowest temperature among sunset-to-sunrise hours that reported one, in °C. */
    val overnightLowC: Double,
    /** When [overnightLowC] was forecast. Null on an outlook built before this field. */
    val overnightLowAt: Instant? = null,
    /** True when at least one dark hour had both a temperature and a dew point. */
    val spreadKnown: Boolean,
) {
    val risk: Boolean get() = from != null
    val frost: Boolean get() = frostFrom != null
}

object Dew {
    /** Temperature minus dew point, in °C, at or below which dew is likely. */
    const val SPREAD_C = 2.0

    /** Temperature, in °C, at or below which a flagged hour is frost. */
    const val FROST_C = 0.0

    /**
     * @param darkHours hours scored for the night (astronomical, else nautical, else civil)
     * @param overnightHours sunset-to-sunrise hours used for the overnight low
     */
    fun assess(
        darkHours: List<Instant>,
        overnightHours: List<Instant>,
        forecast: Forecast?,
    ): DewOutlook? {
        if (forecast == null) return null
        val overnight = readings(overnightHours, forecast).ifEmpty { readings(darkHours, forecast) }
        if (overnight.isEmpty()) return null
        val dark = readings(darkHours, forecast)
        val flagged = dark.filter { reading ->
            val dewPoint = reading.dewPointC ?: return@filter false
            reading.temperatureC - dewPoint <= SPREAD_C
        }
        val coldest = overnight.minBy { it.temperatureC }
        return DewOutlook(
            from = flagged.firstOrNull()?.time,
            frostFrom = flagged.firstOrNull { it.temperatureC <= FROST_C }?.time,
            overnightLowC = coldest.temperatureC,
            overnightLowAt = coldest.time,
            spreadKnown = dark.any { it.dewPointC != null },
        )
    }

    private data class Reading(val time: Instant, val temperatureC: Double, val dewPointC: Double?)

    private fun readings(times: List<Instant>, forecast: Forecast): List<Reading> =
        times.distinct().sorted().mapNotNull { time ->
            val hour = forecast.at(time) ?: return@mapNotNull null
            val temperature = hour.temperatureC ?: return@mapNotNull null
            Reading(time, temperature, hour.dewPointC)
        }
}

object DewCopy {
    fun riskLine(dew: DewOutlook, fmt: (Instant) -> String): String? {
        val from = dew.from ?: return null
        val frostFrom = dew.frostFrom
        return when {
            frostFrom == null -> "Dew likely from ${fmt(from)} — bring a heater"
            frostFrom == from -> "Frost likely from ${fmt(from)} — bring a heater"
            else -> "Dew likely from ${fmt(from)}, frost from ${fmt(frostFrom)} — bring a heater"
        }
    }

    /**
     * The overnight low, named as such. [fmt] adds the hour it was forecast when the outlook has one.
     */
    fun lowLine(
        dew: DewOutlook,
        units: UnitSystem = UnitSystem.METRIC,
        fmt: ((Instant) -> String)? = null,
    ): String {
        val temp = units.formatTemperature(dew.overnightLowC)
        val whenLow = dew.overnightLowAt?.let { fmt?.invoke(it) }?.takeIf { it.isNotBlank() }
        return if (whenLow != null) "Dress for $temp, the overnight low at $whenLow" else "Dress for $temp, the overnight low"
    }

    fun detail(dew: DewOutlook, units: UnitSystem = UnitSystem.METRIC): String {
        val spread = units.dewSpread()
        return when {
            dew.frostFrom != null && dew.from != null && dew.frostFrom != dew.from ->
                "Dew starts above freezing, then the glass can frost. A heater matters more than a shield."
            dew.frostFrom != null ->
                "The air is at or below freezing and within $spread of the dew point. A heater keeps frost off the glass."
            dew.from != null ->
                "The air is within $spread of the dew point, so moisture can settle on the front element."
            dew.spreadKnown ->
                "The air stays more than $spread above the dew point during darkness."
            else ->
                "This forecast has no dew point for the dark hours, so this is only the overnight low."
        }
    }

    /** Nearest degree, with a minus sign below zero. Half a degree rounds away from zero. */
    fun formatCelsius(celsius: Double): String = formatSigned(celsius, "°C")
}
