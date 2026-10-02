package app.nightbrief.score

import app.nightbrief.astro.TimeWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text the home-screen widget shows for the primary site. */
object WidgetCopy {
    const val BEHIND_TREELINE = "Milky Way core stays behind the treeline"

    fun scoreLine(score: Int?): String {
        val value = score ?: return "No score"
        return "$value ${Band.of(value).label}"
    }

    fun clearsTreeline(at: Instant, zone: ZoneId): String {
        val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone)
        return "The core clears the treeline at ${fmt.format(at)}"
    }

    fun milkyWayLine(
        window: TimeWindow?,
        zone: ZoneId,
        clearsHorizonAt: Instant? = null,
        blockedByHorizon: Boolean = false,
    ): String {
        if (window == null) return if (blockedByHorizon) BEHIND_TREELINE else "Milky Way core not up"
        val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone)
        val span = "Milky Way ${fmt.format(window.start)}–${fmt.format(window.end)}"
        return if (clearsHorizonAt != null) "$span · ${clearsTreeline(clearsHorizonAt, zone)}" else span
    }
}
