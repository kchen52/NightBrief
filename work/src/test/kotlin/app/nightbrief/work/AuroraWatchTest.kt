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
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSample
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.KpStatus
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuroraWatchTest {
    private val yellowknife = Site("yk", "Yellowknife", 62.45, -114.35, "America/Yellowknife", bortle = 2)
    private var fixedBin = 0L

    @Before
    fun setUp() {
        val context = context()
        File(context.filesDir, "datastore").mkdirs()
        initWorkManager(context)
        val app = context.applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(context).cancelAll()
    }

    @After
    fun tearDown() {
        AppGraph.get(context()).resetSourcesForTest()
    }

    @Test
    fun aStormPostsOnceThenDedupesInsideTheBin() = runBlocking {
        val context = context()
        val graph = prepare(context, kp = 6.0)
        val first = TestListenableWorkerBuilder<AuroraWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), first)

        val posted = notifications(context).activeNotifications
        assertEquals("expected one nowcast push, got ${posted.size}", 1, posted.size)
        val status = posted.single()
        val slot = graph.settings.current().notificationSlots.getValue(yellowknife.id)
        assertEquals(DigestNotifier.auroraNotificationId(slot), status.id)
        assertEquals(DigestNotifier.CHANNEL_AURORA, status.notification.channelId)
        assertEquals(
            "Aurora likely at Yellowknife",
            status.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        assertEquals(
            "Observed Kp 6 — look north now",
            status.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertEquals(binEpochSecond(), graph.settings.current().lastAuroraAlerts[yellowknife.id])

        notifications(context).cancelAll()
        val second = TestListenableWorkerBuilder<AuroraWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), second)
        assertEquals(
            "the same 3-hour bin must not post twice, got ${notifications(context).activeNotifications.size}",
            0,
            notifications(context).activeNotifications.size,
        )
    }

    @Test
    fun theSwitchBeingOffLeavesTheWorkerQuiet() = runBlocking {
        val context = context()
        val graph = prepare(context, kp = 9.0, enabled = false)
        val result = TestListenableWorkerBuilder<AuroraWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastAuroraAlerts.isEmpty())
    }

    @Test
    fun activityBelowTheSavedThresholdIsQuiet() = runBlocking {
        val context = context()
        val graph = prepare(context, kp = 4.0, threshold = 5)
        val result = TestListenableWorkerBuilder<AuroraWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastAuroraAlerts.isEmpty())
    }

    @Test
    fun anOfflineFetchSucceedsWithoutPostingAndDoesNotRetry() = runBlocking {
        val context = context()
        val graph = prepare(context, kp = 9.0)
        graph.replaceObservedKpForTest(object : KpSource {
            override suspend fun fetch(): KpForecast = throw WeatherApiException("offline")
        })
        val result = TestListenableWorkerBuilder<AuroraWorker>(context).build().doWork()
        assertEquals(
            "an offline nowcast is left to the next 15-minute run, not a retry",
            ListenableWorker.Result.success(),
            result,
        )
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastAuroraAlerts.isEmpty())
    }

    @Test
    fun theSchedulerIsSafeWithTheSwitchOnOffAndBeforeOnboarding() {
        val context = context()
        val onboarded = AppState(
            onboardingComplete = true,
            sites = SiteBook().add(yellowknife, makePrimary = true),
        )
        // Only that each call returns: the test WorkManager instance is a JVM-wide singleton whose
        // in-memory database does not survive between Robolectric classes, so its queued state
        // cannot be read back here. The worker tests above cover what runs when it is enabled.
        AuroraWatch.sync(context, onboarded.copy(auroraAlertsEnabled = true))
        AuroraWatch.sync(context, onboarded.copy(auroraAlertsEnabled = false))
        AuroraWatch.sync(context, AppState())
    }

    private suspend fun prepare(
        context: Context,
        kp: Double,
        enabled: Boolean = true,
        threshold: Int = 5,
    ): AppGraph {
        val graph = AppGraph.get(context)
        graph.settings.update {
            AppState(
                onboardingComplete = true,
                sites = SiteBook().add(yellowknife, makePrimary = true),
                auroraAlertsEnabled = enabled,
                auroraKpThreshold = threshold,
                lastAuroraAlerts = emptyMap(),
            )
        }
        // One fixed bin for the whole test: the dedupe key is the bin's exact epoch second, so a
        // series rebuilt per run would move by a second and look like a new bin.
        fixedBin = Instant.now().epochSecond - 60
        graph.replaceObservedKpForTest(FixedObserved(kp, fixedBin))
        return graph
    }

    /** The bin [prepare] handed the worker, a minute old so `AuroraAlerts.currentBin` accepts it. */
    private fun binEpochSecond(): Long = fixedBin

    private class FixedObserved(private val kp: Double, private val binEpochSecond: Long) : KpSource {
        override suspend fun fetch(): KpForecast =
            KpForecast(listOf(KpSample(binEpochSecond, kp, KpStatus.OBSERVED)))
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
