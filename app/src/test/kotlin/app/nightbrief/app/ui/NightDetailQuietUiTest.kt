package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
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
class NightDetailQuietUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun suggestedTargetsStartCollapsedAndOneOpensWithoutTheOthers() {
        val report = planned(temperatureC = 10.0, dewPointC = 0.0)
        val suggestions = report.suggestions
        assertTrue("need two targets to check one stays collapsed, had ${suggestions.size}", suggestions.size >= 2)
        show(report)
        suggestions.forEach { s ->
            compose.onNodeWithText(s.target.name, substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(s.target.tip).assertDoesNotExist()
        }
        val first = suggestions[0]
        val second = suggestions[1]
        compose.onNodeWithText(first.target.name, substring = true).performScrollTo().performClick()
        compose.onNodeWithText(first.target.tip).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(first.reason).assertIsDisplayed()
        compose.onNodeWithText(second.target.tip).assertDoesNotExist()
        compose.onNodeWithText(first.target.name, substring = true).performScrollTo().performClick()
        compose.onNodeWithText(first.target.tip).assertDoesNotExist()
    }

    @Test
    fun dewExplanationStartsHiddenWhileTheTemperatureStays() {
        val report = planned(temperatureC = 2.0, dewPointC = 1.0)
        assertTrue(report.dew?.risk == true)
        show(report)
        compose.onNodeWithText("Dress for 2 °C", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("front element", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show Dew details").performScrollTo().performClick()
        compose.onNodeWithText("front element", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide Dew details").performScrollTo().performClick()
        compose.onNodeWithText("front element", substring = true).assertDoesNotExist()
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

    private fun planned(temperatureC: Double, dewPointC: Double): NightReport =
        NightPlanner.plan(home, date, forecast(temperatureC, dewPointC), kit)

    private fun forecast(temperatureC: Double, dewPointC: Double): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600L,
                    cloudCover = 10,
                    humidity = 40,
                    windKmh = 6.0,
                    temperatureC = temperatureC,
                    dewPointC = dewPointC,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
