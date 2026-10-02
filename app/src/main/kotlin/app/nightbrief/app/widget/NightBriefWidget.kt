package app.nightbrief.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.nightbrief.app.MainActivity
import app.nightbrief.data.AppGraph
import app.nightbrief.score.WidgetCopy

class NightBriefWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = load(context)
        provideContent {
            NightBriefWidgetContent(content)
        }
    }

    private suspend fun load(context: Context): WidgetContent {
        val graph = AppGraph.get(context)
        val state = graph.settings.current()
        val primary = state.sites.primary
        if (!state.onboardingComplete || primary == null) return WidgetContent.Setup
        val briefing = graph.briefings.brief(
            sites = listOf(primary),
            kit = state.gear,
            outlookDays = 1,
            forceRefresh = false,
        )
        val report = briefing.reportFor(primary.id)
        return WidgetContent.Night(
            siteName = primary.name,
            scoreLine = WidgetCopy.scoreLine(report?.scoreValue),
            milkyWayLine = WidgetCopy.milkyWayLine(report?.ephemeris?.milkyWay?.window, primary.zone),
        )
    }
}

internal sealed interface WidgetContent {
    data object Setup : WidgetContent
    data class Night(val siteName: String, val scoreLine: String, val milkyWayLine: String) : WidgetContent
}

@Composable
internal fun NightBriefWidgetContent(content: WidgetContent) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF12141C)))
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            .padding(12.dp),
    ) {
        when (content) {
            WidgetContent.Setup -> Text(
                context.getString(app.nightbrief.app.R.string.widget_setup),
                style = TextStyle(color = ColorProvider(Color(0xFFE8EAF0)), fontSize = 16.sp),
            )
            is WidgetContent.Night -> {
                Text(content.siteName, style = TextStyle(color = ColorProvider(Color(0xFFF4F6FB)), fontSize = 16.sp))
                Text(content.scoreLine, style = TextStyle(color = ColorProvider(Color(0xFFE8EAF0)), fontSize = 14.sp))
                Text(content.milkyWayLine, style = TextStyle(color = ColorProvider(Color(0xFFB7BDD0)), fontSize = 14.sp))
            }
        }
    }
}
