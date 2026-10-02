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
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.Condensation
import app.nightbrief.score.DewCopy
import app.nightbrief.score.DigestComposer
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class DewUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun tonightShowsDewFromAndTheOvernightLow() {
        val report = planned { epoch, dark ->
            when {
                epoch == dark.first() -> 8.0 to 6.0
                epoch == dark[dark.size / 2] -> -4.2 to 10.0
                epoch in dark -> 3.0 to 0.0
                else -> 15.0 to 0.0
            }
        }
        assertEquals(Condensation.DEW, report.dew!!.onset!!.kind)
        show(report)
        val warning = warning(report)
        assertTrue(warning, warning.matches(Regex("Dew likely from \\d{2}:\\d{2} — bring a heater")))
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dress for −4 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("/ 100").assertIsDisplayed()
    }

    @Test
    fun tonightShowsFrostWhenTheFirstDarkHourIsFreezing() {
        val report = planned { epoch, dark ->
            if (epoch in dark) -4.0 to -5.0 else 12.0 to 0.0
        }
        assertEquals(Condensation.FROST, report.dew!!.onset!!.kind)
        show(report)
        val warning = warning(report)
        assertTrue(warning, warning.matches(Regex("Frost likely from \\d{2}:\\d{2} — bring a heater")))
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dress for −4 °C").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tonightShowsTheOvernightLowWhenTheAirStaysDry() {
        val report = planned { epoch, dark ->
            if (epoch in dark) 6.2 to 0.0 else 18.0 to 17.0
        }
        assertNull(report.dew!!.onset)
        show(report)
        compose.onNodeWithText("Dress for 6 °C").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("bring a heater", substring = true).assertDoesNotExist()
    }

    @Test
    fun tonightOmitsDewWhenTemperatureIsMissing() {
        val report = NightPlanner.plan(home, date, forecast(), kit)
        assertNull(report.dew)
        show(report)
        compose.onNodeWithText("/ 100").assertIsDisplayed()
        compose.onNodeWithText("bring a heater", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Dress for", substring = true).assertDoesNotExist()
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

    private fun warning(report: NightReport): String {
        val fmt = DigestComposer.DEFAULT_TIME_FORMAT.withZone(home.zone)
        return DewCopy.warning(report.dew!!) { fmt.format(it) }!!
    }

    private fun planned(temps: (Long, List<Long>) -> Pair<Double, Double?>): NightReport {
        val bare = NightPlanner.plan(home, date, forecast(), kit)
        val dark = bare.timeline.filter { it.isDark }.map { it.time.epochSecond }
        assertTrue(dark.size >= 3)
        return NightPlanner.plan(home, date, forecast { epoch -> temps(epoch, dark) }, kit)
    }

    private fun forecast(temps: ((Long) -> Pair<Double, Double?>)? = null): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                val epoch = start + i * 3600L
                val pair = temps?.invoke(epoch)
                HourlyWeather(
                    epochSecond = epoch,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                    temperatureC = pair?.first,
                    dewPointC = pair?.second,
                )
            },
        )
    }
}
