package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoadmapUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Test
    fun onboardingReachesTheSiteStepAndOpensTheMapDialog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { OnboardingScreen(vm) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Step 1 of 4").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("NightBrief").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your first site").assertIsDisplayed()
        compose.onNodeWithText("Pick on map").performScrollTo().performClick()
        compose.onNodeWithText("Tap to place the site").assertIsDisplayed()
        compose.onNodeWithText("Map data © OpenStreetMap contributors").assertIsDisplayed()
    }

    @Test
    fun tonightShowsScoreStaleBannerAndSiteStrip() {
        val date = LocalDate.of(2024, 8, 12)
        val report = NightPlanner.plan(home, date, forecast(), GearCatalog.exampleKit)
            .copy(forecastStatus = ForecastStatus.STALE)
        val score = report.scoreValue
        assertTrue("fixture should score", score != null)

        compose.setContent {
            NightBriefTheme {
                Column {
                    SiteStrip(
                        sites = listOf(
                            SiteChip(home.id, home.name, score, true),
                            SiteChip("cabin", "Cabin", 40, false),
                        ),
                        selectedId = home.id,
                        onSelect = {},
                    )
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
        compose.onNodeWithText("Cabin").assertIsDisplayed()
        compose.onNodeWithText("Offline — showing the last saved forecast").assertIsDisplayed()
        compose.onNodeWithText("/ 100").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(score.toString()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tonightShowsDewThenFrostAndTheOvernightLow() {
        val date = LocalDate.of(2024, 8, 12)
        val report = NightPlanner.plan(home, date, dewyForecast(), GearCatalog.exampleKit)
        val dew = report.dew
        assertTrue("fixture should flag dew", dew?.from != null)
        assertTrue("fixture should reach frost", dew?.frostFrom != null)

        compose.setContent {
            NightBriefTheme {
                NightDetail(report, alternative = null, onOpenAlternative = {})
            }
        }
        assertTrue(compose.onAllNodesWithText("Dew likely from", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("frost from", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Dress for", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("FROST").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tonightScreenShowsTheSiteStripFromSavedState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        kotlinx.coroutines.runBlocking {
            AppGraph.get(app).settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true)
                        .add(Site("cabin", "Cabin", 44.0, -80.0, "America/Toronto", bortle = 3)),
                )
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Cabin").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tonight").assertIsDisplayed()
        compose.onNodeWithText("Cabin").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty())
    }

    private fun dewyForecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z")
        return Forecast(
            home.latitude, home.longitude, "best_match", start.epochSecond,
            (0 until 72).map { offset ->
                val time = start.plusSeconds(offset * 3600L)
                val localHour = time.atZone(java.time.ZoneId.of("America/Toronto")).hour
                val temperature = when {
                    localHour in 0..5 -> -4.0
                    localHour >= 21 || localHour <= 8 -> 3.0
                    else -> 16.0
                }
                HourlyWeather(
                    epochSecond = time.epochSecond,
                    cloudCover = 5,
                    humidity = 90,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                    temperatureC = temperature,
                    dewPointC = temperature - 1.0,
                )
            },
        )
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 5, humidity = 40,
                    windKmh = 6.0, gustKmh = 8.0, jetStreamKmh = 70.0,
                    seeing = 2, transparency = 2,
                )
            },
        )
    }
}
