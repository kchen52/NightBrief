package app.nightbrief.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.nightbrief.data.AppGraph
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Fires at digest time: hands the work to WorkManager and arms the next alarm. */
class DigestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DigestScheduler.ACTION_DIGEST) return
        val time = intent.getStringExtra(DigestScheduler.EXTRA_TIME)?.let(LocalTime::parse)
        DigestScheduler.runNow(context, time = time)
        rescheduleAsync(context)
    }
}

/** Re-arms the alarm after reboot, app update, clock/zone changes and exact-alarm permission changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        rescheduleAsync(context)
    }
}

private fun BroadcastReceiver.rescheduleAsync(context: Context) {
    val pending = goAsync()
    AppGraph.get(context).appScope.launch {
        try {
            DigestScheduler.reschedule(context)
        } finally {
            pending.finish()
        }
    }
}
