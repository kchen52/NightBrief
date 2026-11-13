package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.HourInput
import app.nightbrief.score.NightScore
import app.nightbrief.score.NightScoreEngine
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImagingModeUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 11)

    @Test
    fun aDifferentBandAddsThePlanetaryReadout() {
        val score = score(moonAlt = 40.0, moonIllumination = 1.0)
        show(report(score))
        compose.onNodeWithTag("imaging-modes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${score.score} overall", substring = true).assertIsDisplayed()
        compose.onNodeWithText("for planets", substring = true).assertIsDisplayed()
    }

    @Test
    fun sameBandKeepsTheReadoutOff() {
        val score = score(moonAlt = -20.0, moonIllumination = 0.0)
        show(report(score))
        compose.onNodeWithTag("imaging-modes").assertDoesNotExist()
        compose.onNodeWithText("for planets", substring = true).assertDoesNotExist()
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

    private fun report(score: NightScore): NightReport = NightReport(
        site = home,
        date = date,
        ephemeris = NightEphemeris.compute(date, home.zone, home.latitude, home.longitude),
        score = score,
        timeline = emptyList(),
        suggestions = emptyList(),
        coverage = ForecastCoverage.FULL,
        forecastStatus = null,
    )

    private fun score(moonAlt: Double, moonIllumination: Double): NightScore {
        val start = Instant.parse("2024-08-11T02:00:00Z")
        return NightScoreEngine.scoreNight((0 until 6).map { i ->
            HourInput(
                time = start.plus(Duration.ofHours(i.toLong())),
                cloudCover = 0,
                windKmh = 8.0,
                gustKmh = 10.4,
                seeingIndex = 3,
                transparencyIndex = 2,
                humidity = 60,
                jetStreamKmh = 90.0,
                sunAltitudeDeg = -25.0,
                moonAltitudeDeg = moonAlt,
                moonIllumination = moonIllumination,
                bortle = 4,
            )
        })
    }
}
