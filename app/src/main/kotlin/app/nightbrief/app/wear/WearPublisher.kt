package app.nightbrief.app.wear

import android.content.Context
import app.nightbrief.data.AppGraph
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.SavedForecast
import app.nightbrief.score.WearGlance
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Publishes the primary site's score and verdict for the watch tile.
 * Failures are ignored so a missing Wear runtime cannot break the phone widget or startup.
 */
object WearPublisher {
    /** Test seam to simulate a paired or unpaired watch without Play Services. */
    @Volatile
    internal var hasConnectedNodes: suspend (Context) -> Boolean = ::defaultHasConnectedNodes

    fun enqueue(context: Context) {
        val appContext = context.applicationContext
        val graph = runCatching { AppGraph.get(appContext) }.getOrNull() ?: return
        graph.appScope.launch {
            runCatching { push(appContext) }
        }
    }

    internal suspend fun push(context: Context, clock: Clock = Clock.systemUTC()) {
        try {
            withContext(Dispatchers.IO) {
                if (!hasConnectedNodes(context)) return@withContext
                val glance = load(context, clock)
                val put = PutDataMapRequest.create(WearGlance.PATH).apply {
                    dataMap.putBoolean(WearGlance.KEY_READY, glance.ready)
                    dataMap.putString(WearGlance.KEY_SITE, glance.siteName)
                    dataMap.putString(WearGlance.KEY_SCORE, glance.scoreText)
                    dataMap.putString(WearGlance.KEY_VERDICT, glance.verdictText)
                    dataMap.putString(WearGlance.KEY_SAVED, glance.savedText.orEmpty())
                    dataMap.putString(WearGlance.KEY_FLAVOR, glance.flavorText.orEmpty())
                    dataMap.putLong(WearGlance.KEY_UPDATED, clock.millis())
                }
                Tasks.await(
                    Wearable.getDataClient(context).putDataItem(put.asPutDataRequest().setUrgent()),
                    5,
                    TimeUnit.SECONDS,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Missing Wear runtime or Play Services must never break the widget or startup.
        }
    }

    internal suspend fun load(context: Context, clock: Clock = Clock.systemUTC()): WearGlance {
        val graph = AppGraph.get(context)
        val state = graph.settings.current()
        val primary = state.sites.primary
        if (!state.onboardingComplete || primary == null) {
            return WearGlance.from(onboardingComplete = false, siteName = null, score = null)
        }
        val tonight = NightPlanner.tonight(clock.instant(), primary.zone)
        graph.glanceSnapshot.currentFor(primary, tonight)?.let { snap ->
            return WearGlance.from(
                onboardingComplete = true,
                siteName = snap.siteName,
                score = snap.scoreValue,
                savedText = SavedForecast.shortLabel(
                    snap.parsedForecastStatus,
                    snap.forecastFetchedAt,
                    primary.zone,
                ),
                flavorText = snap.flavorLine,
            )
        }
        val report = graph.briefings.brief(
            sites = listOf(primary),
            kit = state.gear,
            outlookDays = 1,
            forceRefresh = false,
        ).reportFor(primary.id)
        if (report != null) {
            graph.glanceSnapshot.save(primary, report)
        }
        return WearGlance.from(
            onboardingComplete = true,
            siteName = primary.name,
            score = report?.scoreValue,
            savedText = SavedForecast.shortLabel(report?.forecastStatus, report?.forecastFetchedAt, primary.zone),
            flavorText = report?.flavorLine,
        )
    }

    private fun defaultHasConnectedNodes(context: Context): Boolean =
        try {
            val nodes = Tasks.await(
                Wearable.getNodeClient(context).connectedNodes,
                2,
                TimeUnit.SECONDS,
            )
            nodes.isNotEmpty()
        } catch (_: Exception) {
            false
        }
}
