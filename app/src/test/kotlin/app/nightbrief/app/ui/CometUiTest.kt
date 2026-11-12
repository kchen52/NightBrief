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
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.CometAdvisor
import app.nightbrief.score.CometOutlook
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CometUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 3, 2)
    private val kit = GearCatalog.exampleKit
    private val peak = Instant.parse("2024-03-03T03:00:00Z")

    @Test
    fun aBrightCometShowsTheCardAndTheWhyText() {
        val comet = comet(magnitude = 2.4, altitude = 38.0)
        show(NightPlanner.plan(home, date, forecast(), kit).copy(comet = comet))
        val where = "${Format.azimuth(comet.peakAzimuthDeg)} at ${Format.time(comet.peakTime, home.zone)}"
        compose.onNodeWithText("COMET").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(CometAdvisor.digestLine(comet)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(where).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show Comet details").performScrollTo().performClick()
        compose.onNodeWithText(CometAdvisor.detail(comet)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aCometFainterThanTheBarLeavesTheCardOut() {
        show(NightPlanner.plan(home, date, forecast(), kit).copy(comet = comet(magnitude = 8.1, altitude = 40.0)))
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("COMET").assertDoesNotExist()
    }

    @Test
    fun aNightWithoutACometLeavesTheCardOut() {
        show(NightPlanner.plan(home, date, forecast(), kit))
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("COMET").assertDoesNotExist()
    }

    private fun comet(magnitude: Double, altitude: Double) = CometOutlook(
        name = "C/2023 A3 (Tsuchinshan-ATLAS)",
        magnitude = magnitude,
        peakAltitudeDeg = altitude,
        peakTime = peak,
        peakAzimuthDeg = 250.0,
    )

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
        val start = Instant.parse("2024-03-02T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(epochSecond = start + i * 3600L, cloudCover = 20, humidity = 40, windKmh = 6.0)
            },
        )
    }
}
