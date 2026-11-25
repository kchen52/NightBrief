package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.app.ui.week.WeekScreen
import app.nightbrief.astro.Darkness
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NightPlanShareUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

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
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun tonightHidesShareUntilANightIsLoaded() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, EmptyBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Pull fresh data with the refresh button").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Share night plan").assertDoesNotExist()
    }

    @Test
    fun tonightOffersShareOnceTheNightIsLoaded() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, PlannedBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Share night plan").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Share night plan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share night plan").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Share or print").assertDoesNotExist()
        compose.onNodeWithText("Print").assertDoesNotExist()
    }

    @Test
    fun weekHidesShareUntilAnOutlookIsLoaded() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, EmptyBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme {
                WeekScreen(vm, onOpenNight = { _, _ -> }, onOpenEvents = {}, contentPadding = PaddingValues())
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Share week").assertDoesNotExist()
    }

    @Test
    fun weekOffersDirectShareOnceAnOutlookIsLoaded() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, PlannedBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme {
                WeekScreen(vm, onOpenNight = { _, _ -> }, onOpenEvents = {}, contentPadding = PaddingValues())
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Share week").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Share week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share week").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Share or print").assertDoesNotExist()
        compose.onNodeWithText("Print").assertDoesNotExist()
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

    private object EmptyBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(Instant.EPOCH, emptyList(), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }

    private object PlannedBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing {
            val site = sites.first()
            val date = LocalDate.of(2024, 8, 10)
            val report = NightPlanner.plan(site, date, null, kit)
            val outlook = WeeklyOutlook(
                site,
                listOf(OutlookNight(date, 84, 0.25, Darkness.ASTRONOMICAL, ForecastCoverage.FULL)),
            )
            return Briefing(Instant.EPOCH, listOf(report), listOf(outlook))
        }

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
