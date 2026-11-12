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

    fun post(digest: Digest, slot: Int, silent: Boolean = false) {
        if (!canNotify()) return
        val style = NotificationCompat.InboxStyle().setBigContentTitle(digest.title)
        digest.lines.forEach(style::addLine)
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(digest.title)
            .setContentText(digest.summary)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openTonight(digest.siteId, digestNotificationId(slot)))
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(digestNotificationId(slot), notification)
    }

    fun postBigNight(siteId: String, siteName: String, score: Int, dateLabel: String, slot: Int) {
        if (!canNotify()) return
        val id = bigNightNotificationId(slot)
        val notification = NotificationCompat.Builder(context, CHANNEL_BIG_NIGHT)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(context.getString(R.string.big_night_title, siteName))
            .setContentText(context.getString(R.string.big_night_text, score, dateLabel))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openTonight(siteId, id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun postCollapse(siteId: String, siteName: String, score: Int, dateLabel: String, slot: Int) {
        if (!canNotify()) return
        val id = collapseNotificationId(slot)
        val notification = NotificationCompat.Builder(context, CHANNEL_BIG_NIGHT)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(context.getString(R.string.collapse_title, siteName))
            .setContentText(context.getString(R.string.collapse_text, score, dateLabel))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openTonight(siteId, id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
        // The Big Night alert this cancels must not stay on the shelf beside the cancellation.
        NotificationManagerCompat.from(context).cancel(bigNightNotificationId(slot))
    }

    /**
     * Posts the aurora nowcast. [likely] picks the title; [line] is the observed Kp sentence from
     * `AuroraCopy.alertLine`. The aurora channel is high importance on purpose: the point is to
     * get the user outside while the display is up.
     */
    fun postAurora(siteId: String, siteName: String, likely: Boolean, line: String, slot: Int) {
        if (!canNotify()) return
        val id = auroraNotificationId(slot)
        val title = context.getString(
            if (likely) R.string.aurora_title_likely else R.string.aurora_title_possible,
            siteName,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_AURORA)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(title)
            .setContentText(line)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openTonight(siteId, id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * Posts the one-shot reminder when the best window opens. Tapping the notification opens
     * Tonight for [siteId] through [EXTRA_SITE_ID].
     */
    fun postWindowStart(siteId: String, siteName: String, windowLabel: String, windowScore: Int, slot: Int) {
        if (!canNotify()) return
        val id = windowNotificationId(slot)
        val title = context.getString(R.string.window_open_title, siteName)
        val text = context.getString(R.string.window_open_text, windowLabel, windowScore)
        val notification = NotificationCompat.Builder(context, CHANNEL_WINDOW)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openTonight(siteId, id))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun progressNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_nightbrief)
            .setContentTitle(context.getString(R.string.progress_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun openTonight(siteId: String, requestCode: Int): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SITE_ID, siteId)
        return PendingIntent.getActivity(
            context, requestCode, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_BIG_NIGHT = "big_night"
        const val CHANNEL_AURORA = "aurora"
        const val CHANNEL_WINDOW = "window_start"
        const val CHANNEL_PROGRESS = "progress"
        const val PROGRESS_ID = 9000
        const val EXTRA_SITE_ID = "app.nightbrief.extra.SITE_ID"

        /** Digest ids sit above [PROGRESS_ID]. Big-night, collapse, aurora, and window ids use separate ranges so none collide. */
        private const val DIGEST_BASE = 10_000
        private const val BIG_NIGHT_BASE = 30_000
        private const val COLLAPSE_BASE = 40_000
        private const val AURORA_BASE = 50_000
        private const val WINDOW_BASE = 60_000

        fun digestNotificationId(slot: Int): Int {
            require(slot > 0) { "notification slot must be positive" }
            return DIGEST_BASE + slot
        }

        fun bigNightNotificationId(slot: Int): Int {
            require(slot > 0) { "notification slot must be positive" }
            return BIG_NIGHT_BASE + slot
        }

        fun collapseNotificationId(slot: Int): Int {
            require(slot > 0) { "notification slot must be positive" }
            return COLLAPSE_BASE + slot
        }

        fun auroraNotificationId(slot: Int): Int {
            require(slot > 0) { "notification slot must be positive" }
            return AURORA_BASE + slot
        }

        fun windowNotificationId(slot: Int): Int {
            require(slot > 0) { "notification slot must be positive" }
            return WINDOW_BASE + slot
        }

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DIGEST,
                    context.getString(R.string.channel_digest_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_digest_description)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BIG_NIGHT,
                    context.getString(R.string.channel_big_night_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_big_night_description)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AURORA,
                    context.getString(R.string.channel_aurora_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_aurora_description)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WINDOW,
                    context.getString(R.string.channel_window_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_window_description)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    context.getString(R.string.channel_progress_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = context.getString(R.string.channel_progress_description)
                },
            )
        }
    }
}
