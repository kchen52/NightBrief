package app.nightbrief.app

import android.app.Application
import android.os.StrictMode
import app.nightbrief.app.wear.WearPublisher
import app.nightbrief.data.AppGraph
import app.nightbrief.work.DigestNotifier
import app.nightbrief.work.DigestScheduler
import kotlinx.coroutines.launch

class NightBriefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug-only disk policy for PERF-05: import/export and settings backup must stay off Main.
        // Filter logcat for "StrictMode policy violation" frames under app.nightbrief.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
        }
        val graph = AppGraph.get(this)
        DigestNotifier.ensureChannels(this)
        graph.appScope.launch { DigestScheduler.reschedule(this@NightBriefApp) }
        WearPublisher.enqueue(this)
    }
}
