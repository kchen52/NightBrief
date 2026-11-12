package app.nightbrief.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.score.AuroraAlerts
import app.nightbrief.score.AuroraChance
import app.nightbrief.score.AuroraCopy
import app.nightbrief.weather.KpForecast
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Remembers to start and stop the aurora nowcast with the saved setting.
 *
 * The morning digest reports the night ahead, which is useless to an aurora chaser at 08:00:
 * the show is over. While [AppState.auroraAlertsEnabled] is on, [AuroraWorker] checks the
 * observed Kp every [INTERVAL_MINUTES] minutes and pushes when it crosses the saved threshold.
 * WorkManager's floor for periodic work is 15 minutes; the 3-hourly prefetch is far too slow.
 *
 * [DigestScheduler.reschedule] calls [sync] from every state change, so the worker follows the
 * switch without a second hook.
 */
object AuroraWatch {
    internal const val WORK = "aurora-watch"
    internal const val INTERVAL_MINUTES = 15L

    fun sync(context: Context, state: AppState) {
        val work = WorkManager.getInstance(context)
        if (!state.auroraAlertsEnabled || !state.onboardingComplete || state.sites.sites.isEmpty()) {
            work.cancelUniqueWork(WORK)
            return
        }
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request())
    }

    internal fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<AuroraWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
}

/**
 * Pushes one notification per site and 3-hour Kp bin while the observed activity is at or above
 * the saved threshold. Display only: it never scores a night, and it never touches the digest,
 * the widget, or the watch.
 */
class AuroraWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val state = graph.settings.current()
        if (!state.auroraAlertsEnabled || !state.onboardingComplete || state.sites.sites.isEmpty()) {
            return Result.success()
        }
        val forecast = try {
            graph.observedKp.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A nowcast is not worth a retry storm offline: the next 15-minute run tries again.
            return Result.success()
        }
        return post(graph, state, forecast)
    }

    private suspend fun post(graph: AppGraph, state: AppState, forecast: KpForecast): Result {
        val alerts = AuroraAlerts.select(
            sites = state.sites.sites,
            forecast = forecast,
            threshold = state.auroraKpThreshold,
            alreadyAlerted = state.lastAuroraAlerts,
            now = Instant.now(),
        )
        if (alerts.isEmpty()) return Result.success()
        val ready = state.ensureNotificationSlots()
        val notifier = DigestNotifier(applicationContext)
        val announced = alerts.associate { alert ->
            notifier.postAurora(
                siteId = alert.siteId,
                siteName = alert.siteName,
                likely = alert.chance == AuroraChance.LIKELY,
                line = AuroraCopy.alertLine(alert.kp, alert.latitudeDeg),
                slot = ready.notificationSlots.getValue(alert.siteId),
            )
            alert.siteId to alert.binEpochSecond
        }
        graph.settings.update { saved ->
            saved.copy(lastAuroraAlerts = saved.lastAuroraAlerts + announced)
        }
        return Result.success()
    }
}
