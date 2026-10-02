package app.nightbrief.weather

import kotlinx.serialization.Serializable
import java.time.Instant

/** One forecast hour. Any field may be null when the source model doesn't provide it. */
@Serializable
data class HourlyWeather(
    val epochSecond: Long,
    /** Total cloud cover, %. */
    val cloudCover: Int? = null,
    /** Total cloud cover from the secondary model, %. Null when that fetch failed or is stale. */
    val cloudCoverSecondary: Int? = null,
    val cloudLow: Int? = null,
    val cloudMid: Int? = null,
    val cloudHigh: Int? = null,
    val humidity: Int? = null,
    val temperatureC: Double? = null,
    val dewPointC: Double? = null,
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    /** Wind speed at 250 hPa (jet-stream level), used as a seeing proxy beyond 7Timer's horizon. */
    val jetStreamKmh: Double? = null,
    /** 7Timer astronomical seeing index, 1 (<0.5") to 8 (>2.5"). */
    val seeing: Int? = null,
    /** 7Timer transparency index, 1 (<0.3 mag/airmass) to 8 (>1 mag/airmass). */
    val transparency: Int? = null,
) {
    val time: Instant get() = Instant.ofEpochSecond(epochSecond)
}

@Serializable
data class Forecast(
    val latitude: Double,
    val longitude: Double,
    /** Open-Meteo model used, e.g. "gem_seamless". */
    val model: String,
    val fetchedAtEpochSecond: Long,
    val hours: List<HourlyWeather>,
) {
    val fetchedAt: Instant get() = Instant.ofEpochSecond(fetchedAtEpochSecond)

    /** Forecast hour at [time] truncated to the hour, if covered. */
    fun at(time: Instant): HourlyWeather? {
        val key = time.epochSecond - Math.floorMod(time.epochSecond, 3600L)
        return hours.firstOrNull { it.epochSecond == key }
    }

    val lastHour: Instant? get() = hours.lastOrNull()?.time
}

enum class WeatherModel(val apiName: String, val label: String) {
    /** Environment Canada GEM (HRDPS → RDPS → GDPS blend). */
    GEM_SEAMLESS("gem_seamless", "Environment Canada GEM"),
    BEST_MATCH("best_match", "Open-Meteo best match"),
    /** German ICON global. Never the primary model; the second opinion beside it. */
    ICON_SEAMLESS("icon_seamless", "German ICON");

    companion object {
        /**
         * GEM is preferred across Canada. The box also takes in the northern US border states,
         * where GEM's high-resolution HRDPS domain still applies.
         */
        fun forLocation(latitude: Double, longitude: Double): WeatherModel =
            if (latitude >= 41.6 && longitude in -141.1..-52.5) GEM_SEAMLESS else BEST_MATCH

        /** Independent second opinion beside [primary]: a different modelling centre either way. */
        fun secondaryFor(primary: WeatherModel): WeatherModel = ICON_SEAMLESS
    }
}
