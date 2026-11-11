package app.nightbrief.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.BigNightCandidate
import app.nightbrief.score.Briefing
import app.nightbrief.score.DigestComposer
import app.nightbrief.score.NightCollapse
import app.nightbrief.sites.Site
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

        val dueIds = due.map { it.id }
        BigNightPosts.beginDigest(dueIds)
        try {
            return postDigests(graph, state, due, refresh)
        } finally {
            BigNightPosts.endDigest(dueIds)
        }
    }

    private suspend fun postDigests(
        graph: AppGraph,
        state: AppState,
        due: List<Site>,
        refresh: Boolean,
    ): Result {
        val ready = state.ensureNotificationSlots()
        val briefing = graph.briefings.brief(
            sites = ready.sites.primaryFirst(),
            kit = ready.gear,
            outlookDays = 1,
            forceRefresh = true,
        )
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val notifier = DigestNotifier(applicationContext)
        val needsRetry = mutableListOf<String>()
        for (site in due) {
            val report = briefing.reportFor(site.id) ?: continue
            val digest = DigestComposer.compose(
                report,
                briefing.tonight,
                timeFormat,
                ready.alternativeThreshold,
                ready.resolvedUnits(),
                ready.bigNightThreshold,
            )
            if (ready.bigNightAlertsEnabled) {
                val score = report.scoreValue
                val bar = BigNightAlerts.clamp(ready.bigNightThreshold)
                if (score != null && score >= bar) {
                    val key = report.date.toString()
                    graph.settings.update { current ->
                        if (current.lastBigNightAlerts[site.id] == key) current
                        else current.copy(lastBigNightAlerts = current.lastBigNightAlerts + (site.id to key))
                    }
                }
            }
            notifier.post(digest, ready.notificationSlots.getValue(site.id), silent = refresh)
            if (report.forecastStatus != ForecastStatus.FRESH) needsRetry += site.id
        }

        if (needsRetry.isNotEmpty()) {
            if (refresh) return Result.retry()
            DigestScheduler.scheduleRefresh(applicationContext, needsRetry)
        }
        WidgetRefresh.request(applicationContext)
        return Result.success()
    }

    /**
     * WorkManager reads this only when expedited work runs as a foreground service, which is
     * below API 31. [DigestScheduler] does not expedite on those versions, and
     * `FOREGROUND_SERVICE` is not declared, so this notification is not posted.
     *
     * The notification has no foreground service type. Below API 31 the platform ignores the
     * type, and a `dataSync` type requires that type on `SystemForegroundService` plus
     * `FOREGROUND_SERVICE_DATA_SYNC`.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = DigestNotifier(applicationContext).progressNotification()
        return ForegroundInfo(DigestNotifier.PROGRESS_ID, notification)
    }

    companion object {
        const val KEY_TIME = "time"
        const val KEY_SITE_IDS = "site_ids"
        const val KEY_REFRESH = "refresh"
    }
}

/**
 * Keeps the forecast cache warm so the morning digest has data even if the network is down at 08:00.
 * After a successful fetch, scores tonight from that cache and posts a Big Night alert at or above
 * the saved threshold (default 85).
 */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val state = graph.settings.current()
        val sites = state.sites.sites
        if (!state.onboardingComplete || sites.isEmpty()) return Result.success()

        // Fetch every site concurrently; a sequential loop waits out each timeout in turn.
        val failures = coroutineScope {
            sites.map { site ->
                async {
                    try {
                        graph.forecasts.forecast(site.latitude, site.longitude)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        site.id
                    }
                }
            }.awaitAll().count { it != null }
        }
        if (failures > 0 && failures == sites.size) return Result.retry()

        if (state.bigNightAlertsEnabled) {
            var briefing: Briefing? = null
            try {
                briefing = postBigNightAlerts(graph)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Scoring or notifying must not turn a saved prefetch into a retry.
            }
            try {
                // Reuse the briefing Big Night just scored rather than scoring every site twice.
                if (briefing != null) postCollapseAlerts(graph, briefing)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed collapse check must not fail the prefetch either.
            }
        }
        WidgetRefresh.request(applicationContext)
        return Result.success()
    }

    /** Posts tonight's Big Night alerts and returns the briefing it scored, or null when quiet. */
    private suspend fun postBigNightAlerts(graph: AppGraph): Briefing? {
        val current = graph.settings.current()
        if (bigNightQuiet(current, Instant.now().epochSecond)) return null
        val briefing = graph.briefings.brief(
            sites = current.sites.primaryFirst(),
            kit = current.gear,
            outlookDays = 1,
            forceRefresh = false,
        )
        BigNightPosts.withLock {
            val latest = graph.settings.current()
            if (bigNightQuiet(latest, Instant.now().epochSecond)) return@withLock
            val ready = latest.ensureNotificationSlots()
            val notifier = DigestNotifier(applicationContext)
            val alerts = BigNightAlerts.select(
                candidates = briefing.tonight.map {
                    BigNightCandidate(it.site.id, it.site.name, it.date, it.scoreValue)
                },
                alreadyAlerted = latest.lastBigNightAlerts,
                enabled = true,
                threshold = latest.bigNightThreshold,
            ).filter { alert -> !BigNightPosts.covers(alert.siteId) }
            val locale = Locale.getDefault()
            val newlyAlerted = alerts.associate { alert ->
                notifier.postBigNight(
                    alert.siteId,
                    alert.siteName,
                    alert.score,
                    alert.dateLabel(locale),
                    ready.notificationSlots.getValue(alert.siteId),
                )
                alert.siteId to alert.nightKey
            }
            if (newlyAlerted.isNotEmpty()) {
                graph.settings.update { saved ->
                    saved.copy(lastBigNightAlerts = saved.lastBigNightAlerts + newlyAlerted)
                }
            }
        }
        return briefing
    }

    /**
     * The downward counterpart of [postBigNightAlerts]: a site that already earned a Big Night
     * alert tonight whose refreshed score fell more than [NightCollapse.MARGIN] below the threshold
     * gets one cancellation per site and local night. [DigestNotifier.postCollapse] removes the
     * Big Night alert it supersedes.
     */
    private suspend fun postCollapseAlerts(graph: AppGraph, briefing: Briefing) {
        BigNightPosts.withLock {
            val latest = graph.settings.current()
            if (bigNightQuiet(latest, Instant.now().epochSecond)) return@withLock
            val ready = latest.ensureNotificationSlots()
            val notifier = DigestNotifier(applicationContext)
            val alerts = NightCollapse.select(
                candidates = briefing.tonight.map {
                    BigNightCandidate(it.site.id, it.site.name, it.date, it.scoreValue)
                },
                bigNightAlerts = latest.lastBigNightAlerts,
                alreadyAlerted = latest.lastCollapseAlerts,
                enabled = true,
                threshold = latest.bigNightThreshold,
            ).filter { alert -> !BigNightPosts.covers(alert.siteId) }
            val locale = Locale.getDefault()
            val newlyAlerted = alerts.associate { alert ->
                notifier.postCollapse(
                    alert.siteId,
                    alert.siteName,
                    alert.score,
                    alert.dateLabel(locale),
                    ready.notificationSlots.getValue(alert.siteId),
                )
                alert.siteId to alert.nightKey
            }
            if (newlyAlerted.isNotEmpty()) {
                graph.settings.update { saved ->
                    saved.copy(lastCollapseAlerts = saved.lastCollapseAlerts + newlyAlerted)
                }
            }
        }
    }
}

/**
 * A digest and a Big Night alert share one decision.
 * While a digest is being built for a site, prefetch will not post a second notification for it.
 */
internal object BigNightPosts {
    private val lock = Mutex()
    private val digests = mutableSetOf<String>()

    suspend fun beginDigest(ids: Collection<String>) {
        lock.withLock { digests.addAll(ids) }
    }

    suspend fun endDigest(ids: Collection<String>) {
        lock.withLock { digests.removeAll(ids.toSet()) }
    }

    /** [covers] is only meaningful inside [withLock]. */
    suspend fun <T> withLock(block: suspend () -> T): T = lock.withLock { block() }

    fun covers(siteId: String): Boolean = siteId in digests
}

/**
 * Holds the separate Big Night alert back for an hour after onboarding.
 * Null means the settings file predates the timestamp, so existing installs are not quieted.
 */
internal fun bigNightQuiet(state: AppState, nowEpochSec: Long): Boolean {
    val completed = state.onboardingCompletedAtEpochSec ?: return false
    val age = nowEpochSec - completed
    return age in 0 until BIG_NIGHT_QUIET_SECONDS
}

internal const val BIG_NIGHT_QUIET_SECONDS = 60L * 60L
