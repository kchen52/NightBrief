package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
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
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.astro.Darkness
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WhimsyUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun tonightHeroShowsTheFlavourLine() {
        val date = LocalDate.of(2024, 3, 15)
        val start = date.minusDays(1).atStartOfDay(java.time.ZoneId.of("America/Toronto")).toInstant()
        val forecast = Forecast(
            home.latitude,
            home.longitude,
            "test",
            start.epochSecond,
            (0 until 72).map { offset ->
                HourlyWeather(
                    epochSecond = start.epochSecond + offset * 3600L,
                    cloudCover = 100,
                    humidity = 50,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 80.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
        val report = NightPlanner.plan(home, date, forecast, GearCatalog.exampleKit)
        val line = report.flavorLine
        assertNotNull(line)
        compose.setContent {
            NightBriefTheme {
                NightDetail(report, alternative = null, onOpenAlternative = {})
            }
        }
        compose.onNodeWithText(line!!).assertIsDisplayed()
    }

    @Test
    fun weekShowsANightName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        val thursday = LocalDate.of(2024, 8, 15)
        graph.replaceSourcesForTest(
            QuietForecasts,
            object : BriefingSource {
                override suspend fun brief(
                    sites: List<Site>,
                    kit: GearKit,
                    outlookDays: Int,
                    forceRefresh: Boolean,
                ): Briefing {
                    val night = OutlookNight(
                        date = thursday,
                        score = 40,
                        moonIllumination = 0.2,
                        darkness = Darkness.ASTRONOMICAL,
                        coverage = ForecastCoverage.FULL,
                        showerLabel = "Perseid",
                    )
                    return Briefing(
                        Instant.EPOCH,
                        emptyList(),
                        listOf(WeeklyOutlook(home, listOf(night))),
                    )
                }

                override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
                    NightPlanner.plan(site, date, null, kit)
            },
        )
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                )
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme {
                WeekScreen(
                    vm,
                    onOpenNight = { _, _ -> },
                    onOpenEvents = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Perseid Thursday").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Perseid Thursday").assertIsDisplayed()
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
}
