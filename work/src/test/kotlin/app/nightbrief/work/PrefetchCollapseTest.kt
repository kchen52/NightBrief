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
import app.nightbrief.score.NightCollapse
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchCollapseTest {
    private val site = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val night = LocalDate.of(2026, 8, 12)
    private var tonightScore = BigNightAlerts.threshold

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
    fun aBigNightThatFallsReplacesTheAlertWithACollapse() = runBlocking {
        val context = context()
        val graph = prepare(context, score = BigNightAlerts.threshold)
        assertEquals(ListenableWorker.Result.success(), work(context))
        // The Big Night alert went out first.
        assertEquals(1, notifications(context).activeNotifications.size)
        assertEquals(
            DigestNotifier.bigNightNotificationId(graph.settings.current().notificationSlots.getValue(site.id)),
            notifications(context).activeNotifications.single().id,
        )

        tonightScore = BigNightAlerts.threshold - NightCollapse.MARGIN - 1
        assertEquals(ListenableWorker.Result.success(), work(context))

        val posted = notifications(context).activeNotifications
        assertEquals("the stale Big Night alert should be gone", 1, posted.size)
        val status = posted.single()
        val slot = graph.settings.current().notificationSlots.getValue(site.id)
        assertEquals(DigestNotifier.collapseNotificationId(slot), status.id)
        assertEquals(DigestNotifier.CHANNEL_BIG_NIGHT, status.notification.channelId)
        assertEquals(
            "Tonight collapsed at Home",
            status.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        val day = night.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault()))
        assertEquals(
            "The refreshed score fell to ${BigNightAlerts.threshold - NightCollapse.MARGIN - 1}. $day",
            status.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        assertEquals(night.toString(), graph.settings.current().lastCollapseAlerts[site.id])
    }

    @Test
    fun aSecondPrefetchDoesNotRepeatTheCollapse() = runBlocking {
        val context = context()
        val graph = prepare(
            context,
            score = BigNightAlerts.threshold - NightCollapse.MARGIN - 1,
            bigNightAlerts = mapOf(site.id to night.toString()),
        )
        assertEquals(ListenableWorker.Result.success(), work(context))
        assertEquals(1, notifications(context).activeNotifications.size)

        notifications(context).cancelAll()
        assertEquals(ListenableWorker.Result.success(), work(context))
        assertEquals(0, notifications(context).activeNotifications.size)
        assertEquals(night.toString(), graph.settings.current().lastCollapseAlerts[site.id])
    }

    @Test
    fun aFallInsideTheMarginStaysQuiet() = runBlocking {
        val context = context()
        val graph = prepare(
            context,
            score = BigNightAlerts.threshold - NightCollapse.MARGIN,
            bigNightAlerts = mapOf(site.id to night.toString()),
        )
        assertEquals(ListenableWorker.Result.success(), work(context))
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastCollapseAlerts.isEmpty())
    }

    @Test
    fun aLowScoreWithoutAPriorAlertStaysQuiet() = runBlocking {
        val context = context()
        val graph = prepare(context, score = 30)
        assertEquals(ListenableWorker.Result.success(), work(context))
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastCollapseAlerts.isEmpty())
    }

    @Test
    fun disabledBigNightAlertsPostNoCollapse() = runBlocking {
        val context = context()
        val graph = prepare(
            context,
            score = BigNightAlerts.threshold - NightCollapse.MARGIN - 1,
            bigNightAlerts = mapOf(site.id to night.toString()),
            enabled = false,
        )
        assertEquals(ListenableWorker.Result.success(), work(context))
        assertEquals(0, notifications(context).activeNotifications.size)
        assertTrue(graph.settings.current().lastCollapseAlerts.isEmpty())
    }

    private suspend fun work(context: Context): ListenableWorker.Result =
        TestListenableWorkerBuilder<PrefetchWorker>(context).build().doWork()

    private suspend fun prepare(
        context: Context,
        score: Int,
        bigNightAlerts: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
    ): AppGraph {
        tonightScore = score
        val graph = AppGraph.get(context)
        graph.settings.update {
            AppState(
                onboardingComplete = true,
                sites = SiteBook().add(site, makePrimary = true),
                bigNightAlertsEnabled = enabled,
                lastBigNightAlerts = bigNightAlerts,
            )
        }
        graph.replaceSourcesForTest(OkForecasts, MutableBriefings())
        return graph
    }

    private inner class MutableBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing =
            Briefing(Instant.parse("2026-08-12T12:00:00Z"), listOf(report(tonightScore)), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport = report(tonightScore)
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
}
