package app.nightbrief.app.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.week.WeekScreen
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FoldableLayoutTest {
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
                androidx.work.Configuration.Builder()
                    .setExecutor(androidx.work.impl.utils.SynchronousExecutor())
                    .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                    .build(),
            )
        }
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
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
    fun phoneWidthShowsTheBottomBar() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            NightBriefTheme { NightBriefNavHost(vm) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-bar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-bar").assertIsDisplayed()
        compose.onNodeWithTag("nav-rail").assertDoesNotExist()
    }

    @Test
    fun unfoldedWidthShowsTheNavigationRail() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            WindowSize(widthDp = 700, heightDp = 900, landscape = false) {
                NightBriefTheme { NightBriefNavHost(vm) }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-rail").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-rail").assertIsDisplayed()
        compose.onNodeWithTag("nav-bar").assertDoesNotExist()
    }

    @Test
    fun unfoldedNightUsesTwoColumns() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 8, 12), null, GearCatalog.exampleKit)
        compose.setContent {
            WindowSize(widthDp = 700, heightDp = 900, landscape = false) {
                NightBriefTheme { NightDetail(report, alternative = null, onOpenAlternative = {}) }
            }
        }
        compose.onNodeWithTag("night-panes").assertIsDisplayed()
        compose.onNodeWithTag("hero-stacked").assertIsDisplayed()
        compose.onNodeWithTag("night-header").assertDoesNotExist()
    }

    @Test
    fun landscapePhoneNightSplitsOnlyTheLead() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 8, 12), null, GearCatalog.exampleKit)
        compose.setContent {
            WindowSize(widthDp = 800, heightDp = 400, landscape = true) {
                NightBriefTheme { NightDetail(report, alternative = null, onOpenAlternative = {}) }
            }
        }
        compose.onNodeWithTag("night-header").assertIsDisplayed()
        compose.onNodeWithTag("night-panes").assertDoesNotExist()
    }

    @Test
    fun unfoldedWeekUsesTwoColumns() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            WindowSize(widthDp = 700, heightDp = 900, landscape = false) {
                NightBriefTheme {
                    WeekScreen(vm, onOpenNight = { _, _ -> }, onOpenEvents = {}, contentPadding = PaddingValues())
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week-grid").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("week-grid").assertIsDisplayed()
    }

    @Test
    fun phoneWeekStaysOneColumn() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            NightBriefTheme {
                WeekScreen(vm, onOpenNight = { _, _ -> }, onOpenEvents = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithTag("week-grid").assertDoesNotExist()
    }
}

@Composable
private fun WindowSize(
    widthDp: Int,
    heightDp: Int,
    landscape: Boolean,
    content: @Composable () -> Unit,
) {
    val override = Configuration(LocalConfiguration.current).apply {
        screenWidthDp = widthDp
        screenHeightDp = heightDp
        orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
    }
    CompositionLocalProvider(LocalConfiguration provides override, content = content)
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
