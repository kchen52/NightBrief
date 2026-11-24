package app.nightbrief.wear

import android.content.Context
import app.nightbrief.data.AppGraph
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.SavedForecast
import app.nightbrief.score.WearGlance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Prefers the phone's published snapshot. On a miss, reads the local [AppGraph.glanceSnapshot]
 * cache first and only falls back to a lean local briefing (`briefLean`) when no fresh snapshot
 * exists, saving the result so subsequent tile and activity loads return immediately.
 */
object WearGlanceLoader {
    fun load(context: Context): WearGlance = runBlocking { loadSuspend(context) }

    suspend fun loadSuspend(context: Context, clock: Clock = Clock.systemUTC()): WearGlance {
        val fromPhone = withContext(Dispatchers.IO) {
            runCatching { WearData.read(context) }.getOrNull()
        }
        if (fromPhone != null) return fromPhone
        return local(context, clock)
    }

    internal suspend fun local(context: Context, clock: Clock = Clock.systemUTC()): WearGlance = try {
        val graph = AppGraph.get(context)
        val state = graph.settings.current()
        val primary = state.sites.primary
        if (!state.onboardingComplete || primary == null) {
            WearGlance.from(onboardingComplete = false, siteName = null, score = null)
        } else {
            val tonight = NightPlanner.tonight(clock.instant(), primary.zone)
            val cached = graph.glanceSnapshot.currentFor(primary, tonight)
            if (cached != null) {
                WearGlance.from(
                    onboardingComplete = true,
                    siteName = cached.siteName,
                    score = cached.scoreValue,
                    savedText = SavedForecast.shortLabel(
                        cached.parsedForecastStatus,
                        cached.forecastFetchedAt,
                        primary.zone,
                    ),
                    flavorText = cached.flavorLine,
                )
            } else {
                val report = graph.briefings.briefLean(
                    sites = listOf(primary),
                    kit = state.gear,
                    forceRefresh = false,
                ).reportFor(primary.id)
                if (report != null) {
                    graph.glanceSnapshot.save(primary, report)
                }
                WearGlance.from(
                    onboardingComplete = true,
                    siteName = primary.name,
                    score = report?.scoreValue,
                    savedText = SavedForecast.shortLabel(
                        report?.forecastStatus,
                        report?.forecastFetchedAt,
                        primary.zone,
                    ),
                    flavorText = report?.flavorLine,
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        WearGlance.from(onboardingComplete = false, siteName = null, score = null)
    }
}
