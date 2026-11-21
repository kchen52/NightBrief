package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.theme.NightBriefTheme
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
class TabTopBarTest {
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
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, ReportingBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun everyTabShowsTheSameTopBarActions() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            NightBriefTheme { NightBriefNavHost(vm) }
        }
        waitForNavBar()
        waitForShare()
        assertTopBar(refresh = true, share = true)

        tab("Week").performClick()
        compose.waitForIdle()
        assertTopBar(refresh = true, share = false)
        compose.onNodeWithContentDescription("Share week").assertIsDisplayed()

        tab("Sites").performClick()
        compose.waitForIdle()
        assertTopBar(refresh = false, share = false)

        tab("Gear").performClick()
        compose.waitForIdle()
        assertTopBar(refresh = false, share = false)
    }

    @Test
    fun nightVisionToggledOnOneTabStaysOnForTheOthers() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            NightBriefTheme { NightBriefNavHost(vm) }
        }
        waitForNavBar()

        compose.onNodeWithContentDescription("Night vision off").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Night vision on")).fetchSemanticsNodes().isNotEmpty()
        }

        tab("Gear").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Night vision on").assertIsDisplayed()
    }

    private fun assertTopBar(refresh: Boolean, share: Boolean) {
        compose.onNodeWithContentDescription("Night vision off").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        assertEquals(
            "Refresh actions on this tab",
            if (refresh) 1 else 0,
            compose.onAllNodes(hasContentDescription("Refresh forecast")).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Share actions on this tab",
            if (share) 1 else 0,
            compose.onAllNodes(hasContentDescription("Share night plan")).fetchSemanticsNodes().size,
        )
    }

    private fun waitForNavBar() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-bar").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForShare() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Share night plan")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tab(label: String) =
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("nav-bar")))

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

    private object ReportingBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing {
            val report = plan(sites.first(), LocalDate.of(2024, 8, 12), kit)
            return Briefing(Instant.EPOCH, listOf(report), emptyList())
        }

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
