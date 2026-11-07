package app.nightbrief.app.ui

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
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
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SiteUndoDeleteTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val dark = Site("dark", "Dark field", 44.2, -80.1, "America/Toronto", bortle = 2)
    private val both = SiteBook().add(home, makePrimary = true).add(dark)

    private val graph get() = AppGraph.get(ApplicationProvider.getApplicationContext<Application>())
    private fun sites() = runBlocking { graph.settings.current().sites }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
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
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking { graph.settings.update { AppState(onboardingComplete = true, sites = both) } }
    }

    @After
    fun tearDown() = graph.resetSourcesForTest()

    /** Mutations run on the view model scope, so idle the main looper until [done] holds. */
    private fun awaitSites(message: String, done: (SiteBook) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done(sites()) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("$message; book is ${sites()}", done(sites()))
    }

    @Test
    fun undoBringsBackADeletedPrimaryInItsOldPlace() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.deleteSite("home")
        awaitSites("dark becomes primary") { it.primaryId == "dark" && it.sites.size == 1 }
        vm.restoreSites(both, both.delete("home"))
        awaitSites("book restored") { it == both }
    }

    @Test
    fun undoIsDroppedWhenTheBookChangedAfterTheDelete() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.deleteSite("dark")
        awaitSites("dark deleted") { it.sites.size == 1 }
        // Pretend the user renamed the remaining site before tapping Undo: the book no longer matches.
        val changedSinceDelete = both.delete("dark").update(home.copy(name = "Renamed"))
        vm.restoreSites(both, changedSinceDelete)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("home"), sites().sites.map { it.id })
    }

    private object QuietForecasts : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult =
            ForecastResult(Forecast(latitude, longitude, "test", 0L, emptyList()), ForecastStatus.CACHED)
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
