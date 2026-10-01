package app.nightbrief.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.nightbrief.data.AppGraph
import app.nightbrief.score.DigestComposer
import app.nightbrief.weather.ForecastStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Fetches fresh forecasts for every saved site, scores tonight, and posts a digest for each site due.
 * When a site's forecast had to come from cache (or failed), a network-constrained refresh is queued
 * that rebuilds the same notification once connectivity returns.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val state = graph.settings.current()
        if (!state.onboardingComplete || state.sites.sites.isEmpty()) return Result.success()

        val refresh = inputData.getBoolean(KEY_REFRESH, false)
        val explicitIds = inputData.getStringArray(KEY_SITE_IDS)?.toList()
        val time = inputData.getString(KEY_TIME)?.let(LocalTime::parse)
        val due = when {
            explicitIds != null -> explicitIds.mapNotNull { state.sites[it] }
            time != null -> DigestTimes.sitesDueAt(state, time)
            else -> listOfNotNull(state.sites.primary)
        }
        if (due.isEmpty()) return Result.success()

        val briefing = graph.briefings.brief(
            sites = state.sites.primaryFirst(),
            kit = state.gear,
            outlookDays = 1,
            forceRefresh = true,
        )
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val notifier = DigestNotifier(applicationContext)
        val needsRetry = mutableListOf<String>()
        for (site in due) {
            val report = briefing.reportFor(site.id) ?: continue
            val digest = DigestComposer.compose(report, briefing.tonight, timeFormat, state.alternativeThreshold)
            notifier.post(digest, silent = refresh)
            if (report.forecastStatus != ForecastStatus.FRESH) needsRetry += site.id
        }

        if (needsRetry.isNotEmpty()) {
            if (refresh) return Result.retry()
            DigestScheduler.scheduleRefresh(applicationContext, needsRetry)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = DigestNotifier(applicationContext).progressNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(DigestNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(DigestNotifier.PROGRESS_ID, notification)
        }
    }

    companion object {
        const val KEY_TIME = "time"
        const val KEY_SITE_IDS = "site_ids"
        const val KEY_REFRESH = "refresh"
    }
}

/** Keeps the forecast cache warm so the morning digest has data even if the network is down at 08:00. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val sites = graph.settings.current().sites.sites
        var failures = 0
        for (site in sites) {
            runCatching { graph.forecasts.forecast(site.latitude, site.longitude) }.onFailure { failures++ }
        }
        return if (failures > 0 && failures == sites.size) Result.retry() else Result.success()
    }
}
