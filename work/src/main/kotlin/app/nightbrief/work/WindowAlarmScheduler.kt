package app.nightbrief.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.score.Briefing
import app.nightbrief.score.NightReport
import app.nightbrief.score.WindowAlarm
import app.nightbrief.score.WindowAlarms
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Schedules the one-shot reminder for the start of tonight's `bestWindow`.
 *
 * This is the deliberate second alarm slot beside [DigestScheduler] (request code 1001):
 * [ALARM_REQUEST_CODE] is 1002, with its own [WindowAlarmReceiver] `PendingIntent` so arming
 * or cancelling a window reminder never disturbs the morning digest. Like the digest, it uses
 * [DigestScheduler.armDigestAlarm] (`setAndAllowWhileIdle` on API 31+ so `SCHEDULE_EXACT_ALARM`
 * stays out of the manifest, and `setExactAndAllowWhileIdle` with an inexact fallback below 31).
 */
object WindowAlarmScheduler {
    const val ACTION_WINDOW_ALARM = "app.nightbrief.action.WINDOW_ALARM"
    const val EXTRA_SITE_ID = "site_id"
    const val EXTRA_NIGHT_DATE = "night_date"
    internal const val ALARM_REQUEST_CODE = 1002

    suspend fun reschedule(context: Context) {
        val state = AppGraph.get(context).settings.current()
        reschedule(context, state)
    }

    /**
     * Arms the one-shot alarm at [WindowAlarm.start] when [AppState.windowAlarm] is set for a
     * saved site, or cancels the 1002 slot when the reminder is cleared, its site was deleted,
     * or onboarding is incomplete.
     */
    fun reschedule(context: Context, state: AppState) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val alarm = state.windowAlarm?.takeIf {
            state.onboardingComplete && state.sites[it.siteId] != null
        }
        if (alarm == null) {
            alarms.cancel(alarmIntent(context, null))
            return
        }
        DigestScheduler.armDigestAlarm(
            alarms = alarms,
            triggerAtMillis = alarm.start.toEpochMilli(),
            pending = alarmIntent(context, alarm),
        )
    }

    /**
     * Re-evaluates the armed reminder against a refreshed [briefing] and updates the 1002
     * `AlarmManager` slot: re-arms at the new start when `bestWindow` moved, or cancels when
     * the refreshed night no longer has a best window (or the site was deleted).
     */
    fun syncWithBriefing(context: Context, state: AppState, briefing: Briefing): AppState {
        val synced = WindowAlarms.syncWithBriefing(state.windowAlarm, briefing, state.sites)
        val next = if (synced == state.windowAlarm) state else state.copy(windowAlarm = synced)
        reschedule(context, next)
        return next
    }

    /** Persists any window-alarm shift or cancellation from a refreshed [briefing] and re-arms/cancels 1002. */
    suspend fun onBriefingRefreshed(context: Context, briefing: Briefing) {
        val graph = AppGraph.get(context)
        val current = graph.settings.current()
        if (current.windowAlarm == null) return
        val synced = WindowAlarms.syncWithBriefing(current.windowAlarm, briefing, current.sites)
        val updated = if (synced == current.windowAlarm) {
            current
        } else {
            graph.settings.update { it.copy(windowAlarm = synced) }
        }
        reschedule(context, updated)
    }

    /**
     * Toggles the reminder for [report]: arms at `bestWindow.start` when not yet armed for that
     * site and night, or cancels when already armed.
     */
    fun toggle(state: AppState, report: NightReport): AppState {
        val next = if (WindowAlarms.isArmedFor(state.windowAlarm, report)) {
            null
        } else {
            WindowAlarms.forReport(report)
        }
        return state.copy(windowAlarm = next)
    }

    /**
     * Posts the "window is open" notification for the armed reminder and clears the one-shot
     * state so it does not fire again. Quiet when the reminder was already cancelled or its
     * site was deleted.
     */
    suspend fun fire(context: Context) {
        val graph = AppGraph.get(context)
        val state = graph.settings.current()
        val alarm = state.windowAlarm ?: return
        val site = state.sites[alarm.siteId]
        val cleared = graph.settings.update { it.copy(windowAlarm = null) }
        reschedule(context, cleared)
        if (!state.onboardingComplete || site == null) return

        val ready = cleared.ensureNotificationSlots()
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val startText = alarm.start.atZone(site.zone).format(formatter)
        val endText = alarm.end.atZone(site.zone).format(formatter)
        DigestNotifier(context).postWindowStart(
            siteId = site.id,
            siteName = site.name,
            windowLabel = "$startText–$endText",
            windowScore = alarm.windowScore,
            slot = ready.notificationSlots.getValue(site.id),
        )
    }

    internal fun alarmIntent(context: Context, alarm: WindowAlarm?): PendingIntent {
        val intent = Intent(context, WindowAlarmReceiver::class.java).setAction(ACTION_WINDOW_ALARM)
        if (alarm != null) {
            intent.putExtra(EXTRA_SITE_ID, alarm.siteId)
            intent.putExtra(EXTRA_NIGHT_DATE, alarm.nightDate)
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
