package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.week.NightRow
import app.nightbrief.astro.Darkness
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConditionsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        initWorkManager(app)
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun tonightShowsDewDressAndTheCloudLayerSplit() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 8, 12), forecast(), GearCatalog.exampleKit)
        assertEquals("high thin cloud", report.cloudReason)

        compose.setContent {
            NightBriefTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
        compose.onNodeWithText("high thin cloud").assertIsDisplayed()
        compose.onNodeWithText("Dress for −4 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("bring a heater", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Low").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("High").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("11%").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("22%").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("77%").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun weekRowMarksAnEstimatedNight() {
        val estimated = OutlookNight(
            date = LocalDate.of(2024, 8, 16),
            score = 72,
            moonIllumination = 0.2,
            darkness = Darkness.ASTRONOMICAL,
            coverage = ForecastCoverage.FULL,
            estimated = true,
        )
        val firm = estimated.copy(date = estimated.date.minusDays(1), estimated = false)
        compose.setContent {
            NightBriefTheme {
                Column {
                    NightRow(estimated, isBest = false, modifier = Modifier, onClick = {})
                    NightRow(firm, isBest = false, modifier = Modifier, onClick = {})
                }
            }
        }
        compose.onNodeWithText("Good · est.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Good · ◔ 20% moon").assertIsDisplayed()
    }

    @Test
    fun settingsShowsTheSavedBigNightThreshold() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                    bigNightThreshold = 72,
                )
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SettingsScreen(vm, onBack = {}) }
        }
        compose.onNodeWithText("Notify when a site reaches 72 tonight").assertIsDisplayed()
        compose.onNodeWithText("Big Night at 72 or above").performScrollTo().assertIsDisplayed()
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 80,
                    cloudLow = 11,
                    cloudMid = 22,
                    cloudHigh = 77,
                    humidity = 40,
                    temperatureC = -4.2,
                    dewPointC = -5.0,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
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
