package app.nightbrief.app.wear

import android.content.Context
import app.nightbrief.data.AppGraph
import app.nightbrief.score.WearGlance
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Publishes the primary site's score and verdict for the watch tile.
 * Failures are ignored so a missing Wear runtime cannot break the phone widget or startup.
 */
object WearPublisher {
    fun enqueue(context: Context) {
        val appContext = context.applicationContext
        val graph = runCatching { AppGraph.get(appContext) }.getOrNull() ?: return
        graph.appScope.launch {
            runCatching { push(appContext) }
        }
    }

    internal suspend fun push(context: Context) {
        runCatching {
            withContext(Dispatchers.IO) {
                val glance = load(context)
                val put = PutDataMapRequest.create(WearGlance.PATH).apply {
                    dataMap.putBoolean(WearGlance.KEY_READY, glance.ready)
                    dataMap.putString(WearGlance.KEY_SITE, glance.siteName)
                    dataMap.putString(WearGlance.KEY_SCORE, glance.scoreText)
                    dataMap.putString(WearGlance.KEY_VERDICT, glance.verdictText)
                    dataMap.putLong(WearGlance.KEY_UPDATED, System.currentTimeMillis())
                }
                Tasks.await(
                    Wearable.getDataClient(context).putDataItem(put.asPutDataRequest().setUrgent()),
                    5,
                    TimeUnit.SECONDS,
                )
            }
        }
    }

    private suspend fun load(context: Context): WearGlance {
        val state = AppGraph.get(context).settings.current()
        val primary = state.sites.primary
        if (!state.onboardingComplete || primary == null) {
            return WearGlance.from(onboardingComplete = false, siteName = null, score = null)
        }
        val score = AppGraph.get(context).briefings.brief(
            sites = listOf(primary),
            kit = state.gear,
            outlookDays = 1,
            forceRefresh = false,
        ).reportFor(primary.id)?.scoreValue
        return WearGlance.from(onboardingComplete = true, siteName = primary.name, score = score)
    }
}
