package app.nightbrief.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import app.nightbrief.work.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NightBriefWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NightBriefWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetRefresh.ACTION) {
            val pending = goAsync()
            refreshScope.launch {
                try {
                    NightBriefWidget().updateAll(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    private companion object {
        val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
