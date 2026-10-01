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
    private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val hourOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH", Locale.getDefault())
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun time(t: Instant?, zone: ZoneId): String = t?.let { shortTime.withZone(zone).format(it) } ?: "—"
    fun hour(t: Instant, zone: ZoneId): String = hourOnly.withZone(zone).format(t)
    fun window(w: TimeWindow?, zone: ZoneId): String = w?.let { "${time(it.start, zone)} – ${time(it.end, zone)}" } ?: "—"

    fun duration(d: Duration): String {
        val m = d.toMinutes()
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    fun shortDayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    fun degrees(d: Double): String = "${d.roundToInt()}°"
    fun azimuth(az: Double): String = "${az.roundToInt()}° ${DigestComposer.compass(az)}"
    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.4f°%s, %.4f°%s", kotlin.math.abs(lat), if (lat >= 0) "N" else "S", kotlin.math.abs(lon), if (lon >= 0) "E" else "W")
}
