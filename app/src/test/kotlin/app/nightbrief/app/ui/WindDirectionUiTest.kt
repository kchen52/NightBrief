package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.UnitSystem
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
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
class WindDirectionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun timelineWindRowShowsSpeedAndCompass() {
        val report = NightPlanner.plan(home, date, forecast(315), kit)
        show(report)
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        // Timeline cells sit inside a horizontal scroll, so presence in the tree is the check
        // (RoadmapUiTest's score cells use the same convention).
        assertTrue(
            "expected the wind cells to read '6 NW'",
            compose.onAllNodesWithText("6 NW").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun timelineWindRowKeepsTheNumberWhenDirectionIsMissing() {
        val report = NightPlanner.plan(home, date, forecast(null), kit)
        show(report)
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        assertTrue(
            "expected the wind cells to keep the bare speed",
            compose.onAllNodesWithText("6").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "wind cells should not grow a compass without a direction",
            compose.onAllNodesWithText("6 NW").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun imperialUnitsKeepTheCompassBesideTheMphSpeed() {
        val report = NightPlanner.plan(home, date, forecast(315), kit)
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {}, units = UnitSystem.IMPERIAL)
                }
            }
        }
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        assertTrue(
            "expected the wind cells to read '4 NW' in mph",
            compose.onAllNodesWithText("4 NW").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun show(report: NightReport) {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
    }

    private fun forecast(direction: Int?): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600L,
                    cloudCover = 40,
                    humidity = 40,
                    windKmh = 6.0,
                    windDirectionDeg = direction,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
