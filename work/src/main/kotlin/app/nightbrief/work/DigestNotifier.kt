package app.nightbrief.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nightbrief.score.Digest

class DigestNotifier(private val context: Context) {

    init {
        ensureChannels(context)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(digest: Digest, silent: Boolean = false) {
        if (!canNotify()) return
        val style = NotificationCompat.InboxStyle().setBigContentTitle(digest.title)
        digest.lines.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(digest.title)
            .setContentText(digest.summary)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openTonight(digest.siteId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId(digest.siteId), notification)
    }

    fun progressNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle("Checking tonight's sky…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun openTonight(siteId: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SITE_ID, siteId)
        return PendingIntent.getActivity(
            context, notificationId(siteId), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_PROGRESS = "progress"
        const val PROGRESS_ID = 9000
        const val EXTRA_SITE_ID = "app.nightbrief.extra.SITE_ID"

        fun notificationId(siteId: String): Int = 1000 + (siteId.hashCode() and 0x0FFF)

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DIGEST, "Daily digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morning go/no-go summary for tonight"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Background updates", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown briefly while forecasts are fetched"
                },
            )
        }
    }
}
