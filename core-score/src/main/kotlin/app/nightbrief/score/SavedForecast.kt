package app.nightbrief.score

import app.nightbrief.weather.ForecastStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How a forecast is labeled once a refresh has failed and the last file is on screen.
 * A [ForecastStatus.CACHED] file younger than an hour is left unlabeled.
 */
object SavedForecast {
    private val clockFormat = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT)

    /** Weekday and clock time in [zone], e.g. "Tue 18:40". */
    fun clock(savedAt: Instant, zone: ZoneId): String = clockFormat.withZone(zone).format(savedAt)

    /** Tonight, the opened night, Week, and the digest. */
    fun detail(savedAt: Instant, zone: ZoneId): String = "Offline — forecast saved ${clock(savedAt, zone)}"

    /**
     * Widget and watch line. Null when the forecast is fresh or still inside the hourly cache window.
     * A stale file with no timestamp still says it was saved, so the score is not shown as new.
     */
    fun shortLabel(status: ForecastStatus?, savedAt: Instant?, zone: ZoneId): String? {
        if (status != ForecastStatus.STALE) return null
        return if (savedAt == null) "Saved forecast" else "Saved ${clock(savedAt, zone)}"
    }
}
