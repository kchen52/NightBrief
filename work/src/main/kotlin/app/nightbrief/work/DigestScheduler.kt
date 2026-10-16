package app.nightbrief.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the morning digest.
 *
 * The digest fires from one `AlarmManager` alarm, which hands off to [DigestWorker]. Exact alarms
 * need `SCHEDULE_EXACT_ALARM` on API 31+, and Play does not allow that permission for a daily
 * digest, so those versions use a Doze-tolerant inexact alarm. Below API 31 the platform still
 * allows an exact alarm without the permission. A periodic [PrefetchWorker] keeps the forecast
 * cache warm so a digest can still be built offline.
 */
object DigestScheduler {
    private const val ALARM_REQUEST_CODE = 1001
    internal const val ACTION_DIGEST = "app.nightbrief.action.DIGEST"
    internal const val EXTRA_TIME = "digest_time"
    private const val PREFETCH_WORK = "forecast-prefetch"
    internal const val DIGEST_WORK = "digest"
    internal const val REFRESH_WORK = "digest-refresh"

    suspend fun reschedule(context: Context) {
        val state = AppGraph.get(context).settings.current()
        reschedule(context, state)
    }

    fun reschedule(context: Context, state: AppState) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val next = DigestTimes.next(state, Instant.now(), ZoneId.systemDefault())
        if (next == null || !state.onboardingComplete) {
            alarms.cancel(alarmIntent(context, null))
            WorkManager.getInstance(context).cancelUniqueWork(PREFETCH_WORK)
            return
        }
        val (at, time) = next
        armDigestAlarm(alarms, at.toEpochMilli(), alarmIntent(context, time))
        schedulePrefetch(context)
    }

    /**
     * Arms [pending] for [triggerAtMillis].
     *
     * Below API 31 the alarm is exact. A [SecurityException] from that call means the device
     * refused it, and the same instant is armed with [AlarmManager.setAndAllowWhileIdle] instead.
     * API 31+ always uses that inexact alarm: `SCHEDULE_EXACT_ALARM` is absent, and
     * [AlarmManager.setExactAndAllowWhileIdle] throws without it.
     */
    internal fun armDigestAlarm(alarms: AlarmManager, triggerAtMillis: Long, pending: PendingIntent) {
        runDigestAlarm(
            sdkInt = Build.VERSION.SDK_INT,
            exact = { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending) },
            inexact = { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending) },
        )
    }

    internal fun runDigestAlarm(sdkInt: Int, exact: () -> Unit, inexact: () -> Unit) {
        if (sdkInt < Build.VERSION_CODES.S) {
            try {
                exact()
                return
            } catch (_: SecurityException) {
                // Exact alarms are unavailable. Fall through to the inexact alarm.
            }
        }
        inexact()
    }

    /** Runs a digest immediately for the given sites (or the sites due at [time]). */
    fun runNow(context: Context, time: LocalTime? = null, siteIds: List<String>? = null) {
        val request = digestWorkRequest(time, siteIds)
        WorkManager.getInstance(context).enqueueUniqueWork(DIGEST_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Builds the one-shot digest request.
     *
     * On API 31+ the request is expedited and, past the expedited quota, runs as ordinary work.
     * WorkManager implements expedited work below API 31 as a foreground service. That needs
     * `FOREGROUND_SERVICE`, which is not declared, so those versions enqueue ordinary work.
     */
    internal fun digestWorkRequest(
        time: LocalTime?,
        siteIds: List<String>?,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInputData(
                workDataOf(
                    DigestWorker.KEY_TIME to time?.toString(),
                    DigestWorker.KEY_SITE_IDS to siteIds?.toTypedArray(),
                ),
            )
        if (sdkInt >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        return builder.build()
    }

    /** Re-computes digests once the network is back; replaces the notification in place. */
    internal fun scheduleRefresh(context: Context, siteIds: List<String>) {
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    DigestWorker.KEY_SITE_IDS to siteIds.toTypedArray(),
                    DigestWorker.KEY_REFRESH to true,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedulePrefetch(context: Context) {
        val request = PeriodicWorkRequestBuilder<PrefetchWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PREFETCH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun alarmIntent(context: Context, time: LocalTime?): PendingIntent {
        val intent = Intent(context, DigestAlarmReceiver::class.java).setAction(ACTION_DIGEST)
        time?.let { intent.putExtra(EXTRA_TIME, it.toString()) }
        return PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
