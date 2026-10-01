package app.nightbrief.work

import app.nightbrief.data.AppState
import app.nightbrief.sites.Site
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Digest times are wall-clock times in the device's zone. */
object DigestTimes {

    /** Which sites send a digest at each time. The primary uses the global time unless it has its own override. */
    fun schedule(state: AppState): Map<LocalTime, List<Site>> {
        if (!state.digestEnabled) return emptyMap()
        val primaryId = state.sites.primaryId
        val entries = state.sites.sites.mapNotNull { site ->
            val time = site.digestTime ?: if (site.id == primaryId) state.digestLocalTime else null
            time?.let { it.withSecond(0).withNano(0) to site }
        }
        return entries.groupBy({ it.first }, { it.second }).toSortedMap()
    }

    fun sitesDueAt(state: AppState, time: LocalTime): List<Site> =
        schedule(state)[time.withSecond(0).withNano(0)].orEmpty()

    /** Next trigger strictly after [now], or null when no digest is configured. */
    fun next(state: AppState, now: Instant, zone: ZoneId): Pair<Instant, LocalTime>? {
        val times = schedule(state).keys
        if (times.isEmpty()) return null
        val local = now.atZone(zone)
        return times.map { t -> nextOccurrence(local, t) to t }.minBy { it.first }
    }

    private fun nextOccurrence(now: ZonedDateTime, time: LocalTime): Instant {
        var candidate = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!candidate.toInstant().isAfter(now.toInstant())) {
            candidate = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        }
        return candidate.toInstant()
    }
}
