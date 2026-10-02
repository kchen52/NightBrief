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
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchBigNightTest {
    private val site = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val night = LocalDate.of(2026, 8, 12)

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
    fun scoreOf85PostsOnceAndStoresTheNightKey() = runBlocking {
        val context = context()
        val graph = prepare(context, score = BigNightAlerts.threshold)
        val first = TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), first)

        val posted = notifications(context).activeNotifications
        assertEquals(1, posted.size)
        val status = posted.single()
        val slot = graph.settings.current().notificationSlots.getValue(site.id)
        assertEquals(DigestNotifier.bigNightNotificationId(slot), status.id)
        assertEquals(DigestNotifier.CHANNEL_BIG_NIGHT, status.notification.channelId)
        assertEquals(
            "Big Night at Home",
            status.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        val day = night.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        assertEquals(
            "Tonight scores ${BigNightAlerts.threshold}. $day $night",
            status.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertEquals(night.toString(), graph.settings.current().lastBigNightAlerts[site.id])

        notifications(context).cancelAll()
        val second = TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), second)
        assertEquals(0, notifications(context).activeNotifications.size)
        assertEquals(night.toString(), graph.settings.current().lastBigNightAlerts[site.id])
    }

    @Test
    fun scoreBelowTheThresholdPostsNothing() = runBlocking {
        val context = context()
        val graph = prepare(context, score = BigNightAlerts.threshold - 1)
        val result = TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastBigNightAlerts.isEmpty())
    }

    @Test
    fun scoringFailureDoesNotRetryASavedPrefetch() = runBlocking {
        val context = context()
        val graph = AppGraph.get(context)
        graph.settings.update {
            AppState(onboardingComplete = true, sites = SiteBook().add(site, makePrimary = true))
        }
        graph.replaceSourcesForTest(OkForecasts, FailingBriefings)
        val result = TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifications(context).activeNotifications.size)
    }

    private suspend fun prepare(context: Context, score: Int): AppGraph {
        val graph = AppGraph.get(context)
        graph.settings.update {
            AppState(
                onboardingComplete = true,
                sites = SiteBook().add(site, makePrimary = true),
                bigNightAlertsEnabled = true,
                lastBigNightAlerts = emptyMap(),
            )
        }
        graph.replaceSourcesForTest(OkForecasts, FixedBriefings(report(score)))
        return graph
    }

    private fun report(score: Int): NightReport {
        val planned = NightPlanner.plan(site, night, clearForecast(), GearCatalog.exampleKit)
        val scored = planned.score ?: error("fixture should score")
        return planned.copy(score = scored.copy(score = score))
    }

    private fun clearForecast(): Forecast {
        val start = Instant.parse("2026-08-12T00:00:00Z").epochSecond
        return Forecast(
            site.latitude, site.longitude, "test", start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 5, humidity = 40,
                    windKmh = 6.0, gustKmh = 8.0, jetStreamKmh = 70.0,
                    seeing = 2, transparency = 2,
                )
            },
        )
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

    private object OkForecasts : ForecastSource {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            forceRefresh: Boolean,
        ): ForecastResult = ForecastResult(
            Forecast(latitude, longitude, "test", Instant.now().epochSecond, emptyList()),
            ForecastStatus.FRESH,
        )
    }

    private class FixedBriefings(private val tonight: NightReport) : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(Instant.parse("2026-08-12T12:00:00Z"), listOf(tonight), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport = tonight
    }

    private object FailingBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = error("score failed")

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            error("score failed")
    }
}
