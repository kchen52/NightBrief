package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.SmokeOutlook
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
class SmokeUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit
    private val late = Instant.parse("2024-08-13T03:00:00Z") // 23:00 EDT

    @Test
    fun smokyNightShowsTheCardWithTheStartAndThePeak() {
        show(NightPlanner.plan(home, date, forecast(), kit).copy(smoke = smoke(from = late)))
        val start = Format.time(late, home.zone)
        compose.onNodeWithText("SMOKE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Smoke from $start through the night").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Peak PM2.5 80 µg/m³ (smoky above 35)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clearAirStillNamesThePeakSoTheNumberCarriesItsBar() {
        show(NightPlanner.plan(home, date, forecast(), kit).copy(smoke = smoke(from = null, peak = 12.0)))
        compose.onNodeWithText("SMOKE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Peak PM2.5 12 µg/m³ (smoky above 35)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aReportWithoutAnAirQualityReadingLeavesTheCardOut() {
        show(NightPlanner.plan(home, date, forecast(), kit))
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SMOKE").assertDoesNotExist()
    }

    private fun smoke(from: Instant?, peak: Double = 80.0) = SmokeOutlook(
        from = from,
        peakPm25 = peak,
        thinningAt = null,
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
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(epochSecond = start + i * 3600L, cloudCover = 30, humidity = 40, windKmh = 6.0)
            },
        )
    }
}
