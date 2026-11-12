package app.nightbrief.score

import app.nightbrief.astro.TimeWindow
import app.nightbrief.sites.SiteBook
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One-shot reminder armed from Tonight for the start of [NightScore.bestWindow].
 *
 * Stored as epoch seconds and a date string so `java.time` types stay off the serialized
 * fields (`AppState.windowAlarm`). [start] and [end] are computed properties.
 */
@Serializable
data class WindowAlarm(
    val siteId: String,
    val nightDate: String,
    val startEpochSecond: Long,
    val endEpochSecond: Long,
    val windowScore: Int,
) {
    val start: Instant get() = Instant.ofEpochSecond(startEpochSecond)
    val end: Instant get() = Instant.ofEpochSecond(endEpochSecond)
    val window: TimeWindow get() = TimeWindow(start, end)
}

/**
 * Pure rules for arming, moving, and cancelling the window-start reminder as forecasts
 * refresh or sites change.
 */
object WindowAlarms {
    /**
     * Builds the reminder for [report], or null when Tonight does not show a best window
     * for that night ([NightPlanCard.showsBestWindow]).
     */
    fun forReport(report: NightReport): WindowAlarm? {
        val score = report.score ?: return null
        if (!NightPlanCard.showsBestWindow(score)) return null
        val window = score.bestWindow ?: return null
        return WindowAlarm(
            siteId = report.site.id,
            nightDate = report.date.toString(),
            startEpochSecond = window.start.epochSecond,
            endEpochSecond = window.end.epochSecond,
            windowScore = score.bestWindowScore,
        )
    }

    fun isArmedFor(alarm: WindowAlarm?, report: NightReport): Boolean =
        alarm != null && alarm.siteId == report.site.id && alarm.nightDate == report.date.toString()

    /** Drops the reminder when its site is no longer in [sites]. */
    fun syncWithSites(current: WindowAlarm?, sites: SiteBook): WindowAlarm? =
        current?.takeIf { sites[it.siteId] != null }

    /**
     * Re-evaluates [current] against a refreshed [briefing].
     *
     * Moves the alarm when `bestWindow` shifts, and cancels it (returns null) when the site
     * was deleted, tonight rolled past [WindowAlarm.nightDate], or the refreshed report no
     * longer has a best window worth showing.
     */
    fun syncWithBriefing(current: WindowAlarm?, briefing: Briefing, sites: SiteBook): WindowAlarm? {
        val active = syncWithSites(current, sites) ?: return null
        val report = briefing.reportFor(active.siteId) ?: return null
        if (report.date.toString() != active.nightDate) return null
        return forReport(report)
    }
}
