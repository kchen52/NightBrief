package app.nightbrief.app

import android.app.Application
import app.nightbrief.app.wear.WearPublisher
import app.nightbrief.data.AppGraph
import app.nightbrief.work.DigestNotifier
import app.nightbrief.work.DigestScheduler
import kotlinx.coroutines.launch

class NightBriefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph.get(this)
        DigestNotifier.ensureChannels(this)
        graph.appScope.launch { DigestScheduler.reschedule(this@NightBriefApp) }
        WearPublisher.enqueue(this)
    }
}
