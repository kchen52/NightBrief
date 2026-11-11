package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.gear.GearEditor
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
class ExposureRuleUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun expandedSuggestionPrintsNpfAlone() {
        val report = NightPlanner.plan(home, date, forecast(), kit)
        val withExposure = report.suggestions.firstOrNull { it.exposure != null }
        assertTrue("need a suggestion with an exposure, had ${report.suggestions.size}", withExposure != null)
        show(report)
        compose.onNodeWithText(withExposure!!.target.name, substring = true).performScrollTo().performClick()
        compose.onNodeWithText("NPF", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("500-rule", substring = true).assertDoesNotExist()
    }

    @Test
    fun gearLensRowsPrintNpfAlone() {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GearEditor(kit, onChange = {})
                }
            }
        }
        compose.onNodeWithText("NPF at widest", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("500 rule", substring = true).assertDoesNotExist()
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

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600,
                    cloudCover = 10,
                    humidity = 40,
                    windKmh = 6.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
