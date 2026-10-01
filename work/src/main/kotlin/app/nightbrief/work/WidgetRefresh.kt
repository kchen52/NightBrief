package app.nightbrief.work

import android.content.Context
import android.content.Intent

object WidgetRefresh {
    const val ACTION = "app.nightbrief.action.REFRESH_WIDGET"

    fun request(context: Context) {
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
    }
}
