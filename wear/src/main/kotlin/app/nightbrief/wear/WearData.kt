package app.nightbrief.wear

import android.content.Context
import android.net.Uri
import app.nightbrief.score.WearGlance
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/** Reads the snapshot the phone published. Null when the phone has not published one yet. */
object WearData {
    fun read(context: Context): WearGlance? {
        val uri = Uri.parse("wear://*${WearGlance.PATH}")
        val buffer = Tasks.await(Wearable.getDataClient(context).getDataItems(uri), 3, TimeUnit.SECONDS)
        buffer.use { items ->
            if (items.count == 0) return null
            val map = DataMapItem.fromDataItem(items[0]).dataMap
            return WearGlance(
                ready = map.getBoolean(WearGlance.KEY_READY),
                siteName = map.getString(WearGlance.KEY_SITE, ""),
                scoreText = map.getString(WearGlance.KEY_SCORE, "—"),
                verdictText = map.getString(WearGlance.KEY_VERDICT, WearGlance.SETUP),
            )
        }
    }
}
