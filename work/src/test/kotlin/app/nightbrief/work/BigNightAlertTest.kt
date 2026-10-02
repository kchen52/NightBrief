package app.nightbrief.work

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BigNightAlertTest {

    @Before
    fun setUp() {
        val context = context()
        File(context.filesDir, "datastore").mkdirs()
        initWorkManager(context)
    }

    @Test
    fun postBigNightUsesBigNightChannel() {
        val context = context()
        val app = context.applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(context).cancelAll()

        val siteId = "home"
        DigestNotifier(context).postBigNight(siteId, "Home", 92, "Wednesday 2026-08-12", slot = 1)

        val posted = notifications(context).activeNotifications
        assertEquals(1, posted.size)
        val status = posted.single()
        assertEquals(DigestNotifier.bigNightNotificationId(1), status.id)
        val notification = status.notification
        assertEquals(DigestNotifier.CHANNEL_BIG_NIGHT, notification.channelId)
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue("title was '$title'", title.startsWith("Big Night at "))
        assertEquals(
            "Tonight scores 92. Wednesday 2026-08-12",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }

    @Test(timeout = 30_000)
    fun incompleteOnboardingPostsNothing() {
        val context = context()
        notifications(context).cancelAll()
        val result = runBlocking {
            AppGraph.get(context).settings.update { AppState(onboardingComplete = false) }
            TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        }
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
    }

    @Test
    fun emptySiteListPostsNothing() {
        val context = context()
        notifications(context).cancelAll()
        val result = runBlocking {
            AppGraph.get(context).settings.update { AppState(onboardingComplete = true) }
            TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        }
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
    }

    @Test
    fun widgetRefreshBroadcastStaysInTheApp() {
        val context = context()
        val app = context.applicationContext as Application
        shadowOf(app).clearBroadcastIntents()
        WidgetRefresh.request(context)
        val refresh = shadowOf(app).broadcastIntents.single { it.action == WidgetRefresh.ACTION }
        assertEquals(context.packageName, refresh.`package`)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun notifications(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun initWorkManager(context: Context) {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder()
                    .setExecutor(SynchronousExecutor())
                    .setTaskExecutor(SynchronousExecutor())
                    .build(),
            )
        }
    }
}
