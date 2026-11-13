package app.nightbrief.app.ui.common

import app.nightbrief.astro.TimeWindow
import app.nightbrief.score.DigestComposer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    /**
     * Formatters are cached by locale, not initialized once with the process default. A locale
     * change without killing the process must update dates and times.
     */
    private val formatters = java.util.concurrent.ConcurrentHashMap<Pair<Locale, String>, DateTimeFormatter>()

    private fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        formatters.computeIfAbsent(locale to pattern) {
            DateTimeFormatter.ofPattern(pattern, locale)
        }

    private fun localizedTime(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        formatters.computeIfAbsent(locale to "localized-short-time") {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }

    fun time(t: Instant?, zone: ZoneId): String = t?.let { localizedTime().withZone(zone).format(it) } ?: "—"

    /** Hour label for the grid. [twentyFourHour] follows the device clock, so it matches [time]. */
    fun hour(t: Instant, zone: ZoneId, twentyFourHour: Boolean): String {
        val pattern = if (twentyFourHour) "HH" else "h a"
        return formatter(pattern).withZone(zone).format(t)
    }

    /** Stored `HH:mm` shown the way the device clock shows a time. */
    fun clockTime(hhmm: String, twentyFourHour: Boolean): String {
        val parsed = runCatching { java.time.LocalTime.parse(hhmm) }.getOrNull() ?: return hhmm
        val pattern = if (twentyFourHour) "HH:mm" else "h:mm a"
        return formatter(pattern).format(parsed)
    }
    fun window(w: TimeWindow?, zone: ZoneId): String = w?.let { "${time(it.start, zone)} – ${time(it.end, zone)}" } ?: "—"

    fun duration(d: Duration): String {
        val m = d.toMinutes()
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    fun shortDayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    fun dayMonth(date: LocalDate): String = formatter("MMM d").format(date)

    /** Inclusive span. One day is that date. A single month drops the repeated month on the end. */
    fun daySpan(start: LocalDate, end: LocalDate): String {
        if (start == end) return dayMonth(start)
        if (start.year == end.year && start.month == end.month) {
            return "${dayMonth(start)}–${end.dayOfMonth}"
        }
        return "${dayMonth(start)} – ${dayMonth(end)}"
    }

    fun degrees(d: Double): String = "${d.roundToInt()}°"
    fun azimuth(az: Double): String = "${az.roundToInt()}° ${DigestComposer.compass(az)}"
    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.4f°%s, %.4f°%s", kotlin.math.abs(lat), if (lat >= 0) "N" else "S", kotlin.math.abs(lon), if (lon >= 0) "E" else "W")
}
