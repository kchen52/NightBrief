package app.nightbrief.app.widget

import android.app.AlarmManager
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.SiteDraft
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearKit
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
import app.nightbrief.work.WidgetRefresh
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WidgetRefreshTest {
    private val home = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 4)
    private val other = Site("other", "Toronto Secondary", 43.6532, -79.3832, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        initWorkManager(app)
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true).add(other),
                )
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun renamingOrChangingPrimaryRefreshesTheWidgetAndDigestTimeDoesNot() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        val vm = AppViewModel(app)
        idle()
        shadowOf(app).clearBroadcastIntents()

        // AppViewModel.mutate re-arms the digest alarm and decides on the widget refresh in the same
        // main-thread slice, after the settings write. Seeing the new settings is not enough: wait for
        // the alarm (no refresh expected) or for the broadcast itself.
        vm.setDigestTime("09:30")
        idleUntil("digest alarm at 09:30") { nextDigestAlarm(app) == LocalTime.of(9, 30) }
        assertEquals(0, widgetRefreshes(app))

        vm.saveSite(SiteDraft.from(home, isPrimary = true).copy(name = "Toronto Widget"))
        idleUntil("first widget refresh") { widgetRefreshes(app) >= 1 }
        assertEquals("Toronto Widget", runBlocking { graph.settings.current().sites.primary?.name })
        assertEquals(1, widgetRefreshes(app))

        vm.setPrimary(other.id)
        idleUntil("second widget refresh") { widgetRefreshes(app) >= 2 }
        assertEquals(other.id, runBlocking { graph.settings.current().sites.primaryId })
        assertEquals(2, widgetRefreshes(app))
    }

    private fun nextDigestAlarm(app: Application): LocalTime? {
        val alarm = shadowOf(app.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm() ?: return null
        return Instant.ofEpochMilli(alarm.triggerAtMs).atZone(ZoneId.systemDefault()).toLocalTime()
    }

    private fun widgetRefreshes(app: Application): Int =
        shadowOf(app).broadcastIntents.count { it.action == WidgetRefresh.ACTION }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The settings write runs on DataStore's own thread, so a slow CI runner gets a generous deadline. */
    private fun idleUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (condition()) return
            Thread.sleep(20)
        }
        error("timed out waiting for $what")
    }

    private fun initWorkManager(app: Application) {
        try {
            WorkManager.getInstance(app)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app,
                Configuration.Builder()
                    .setExecutor(SynchronousExecutor())
                    .setTaskExecutor(SynchronousExecutor())
                    .build(),
            )
        }
    }

    private object QuietForecasts : ForecastSource {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            forceRefresh: Boolean,
        ): ForecastResult = ForecastResult(
            Forecast(latitude, longitude, "test", 0L, emptyList()),
            ForecastStatus.CACHED,
        )
    }

    private object QuietBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(Instant.EPOCH, emptyList(), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
