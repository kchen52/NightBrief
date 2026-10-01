package app.nightbrief.score

import app.nightbrief.astro.TimeWindow
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Text the home-screen widget shows for the primary site. */
object WidgetCopy {
    fun scoreLine(score: Int?): String {
        val value = score ?: return "No score"
        return "$value ${Band.of(value).label}"
    }

    fun milkyWayLine(window: TimeWindow?, zone: ZoneId): String {
        if (window == null) return "Milky Way core not up"
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        return "Milky Way ${fmt.format(window.start)}–${fmt.format(window.end)}"
    }
}
