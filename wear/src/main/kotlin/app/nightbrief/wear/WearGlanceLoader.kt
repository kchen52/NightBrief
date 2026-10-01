package app.nightbrief.wear

import android.content.Context
import app.nightbrief.data.AppGraph
import app.nightbrief.score.WearGlance
import kotlinx.coroutines.runBlocking

/**
 * Prefers the phone's published snapshot. Falls back to this watch's own [AppGraph]
 * so a standalone install still has something to show after its own onboarding.
 */
object WearGlanceLoader {
    fun load(context: Context): WearGlance {
        runCatching { WearData.read(context) }.getOrNull()?.let { return it }
        return local(context)
    }

    private fun local(context: Context): WearGlance = runCatching {
        val graph = AppGraph.get(context)
        val state = runBlocking { graph.settings.current() }
        val primary = state.sites.primary
        if (!state.onboardingComplete || primary == null) {
            WearGlance.from(onboardingComplete = false, siteName = null, score = null)
        } else {
            val score = runBlocking {
                graph.briefings.brief(
                    sites = listOf(primary),
                    kit = state.gear,
                    outlookDays = 1,
                    forceRefresh = false,
                )
            }.reportFor(primary.id)?.scoreValue
            WearGlance.from(onboardingComplete = true, siteName = primary.name, score = score)
        }
    }.getOrElse { WearGlance.from(onboardingComplete = false, siteName = null, score = null) }
}
