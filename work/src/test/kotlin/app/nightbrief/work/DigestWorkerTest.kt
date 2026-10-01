package app.nightbrief.work

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.io.File

/**
 * DigestWorker reaches the network through AppGraph.forecasts. Offline, the briefing still
 * produces a notification titled "Tonight at <name>: forecast not available yet". A live
 * forecast titles the same notification "Tonight: …". Either way the title starts with "Tonight".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DigestWorkerTest {

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val context = context()
        File(context.filesDir, "datastore").mkdirs()
        initWorkManager(context)
    }

    @Test(timeout = 180_000)
    fun incompleteOnboardingPostsNothing() {
        val context = context()
        notifications(context).cancelAll()
        val result = runBlocking {
            AppGraph.get(context).settings.update { AppState(onboardingComplete = false) }
            TestListenableWorkerBuilder<DigestWorker>(context).build().doWork()
        }
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
    }

    @Test(timeout = 180_000)
    fun onboardedSitePostsTonightDigest() {
        val context = context()
        val app = context.applicationContext as Application
        val shadowApp: ShadowApplication = shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(context).cancelAll()

        val state = AppState(onboardingComplete = true, sites = SiteBook().add(home))
        val result = runBlocking {
            AppGraph.get(context).settings.update { state }
            TestListenableWorkerBuilder<DigestWorker>(context).build().doWork()
        }
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)

        val posted = notifications(context).activeNotifications
        assertEquals(1, posted.size)
        val notification = posted.single().notification
        assertEquals(DigestNotifier.CHANNEL_DIGEST, notification.channelId)
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        assertTrue("title was '$title'", title.startsWith("Tonight"))
        if ("forecast not available" in title) {
            assertEquals("Tonight at Home: forecast not available yet", title)
        }
    }

    @Test
    fun rescheduleArmsAlarmOnlyWhenDigestEnabled() {
        val context = context()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val onboarded = AppState(onboardingComplete = true, sites = SiteBook().add(home), digestEnabled = true)

        DigestScheduler.reschedule(context, onboarded)
        // peek, rather than nextScheduledAlarm, which removes the alarm it returns.
        assertNotNull(alarms.peekNextScheduledAlarm())

        DigestScheduler.reschedule(context, onboarded.copy(digestEnabled = false))
        assertNull(alarms.peekNextScheduledAlarm())
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
