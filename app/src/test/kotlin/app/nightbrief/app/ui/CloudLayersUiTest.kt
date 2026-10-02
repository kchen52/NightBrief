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
import app.nightbrief.score.CloudReason
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
class CloudLayersUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun tonightShowsTheLayerSplitAndHighThinCloud() {
        val report = planned { _, dark -> if (dark) Triple(0, 0, 40) else Triple(40, 0, 0) }
        assertEquals(CloudReason.HIGH_THIN, report.cloudReason)
        show(report)
        compose.onNodeWithText("/ 100").assertIsDisplayed()
        compose.onNodeWithText("Low").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("High").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("40%")[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("high thin cloud").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tonightHidesTheReasonWhenLowCloudDominates() {
        val report = planned { _, dark -> if (dark) Triple(40, 0, 0) else Triple(0, 0, 80) }
        assertNull(report.cloudReason)
        show(report)
        compose.onNodeWithText("Low").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("high thin cloud").assertDoesNotExist()
    }

    @Test
    fun tonightShowsTheSplitWithoutAReasonWhenLayersAreMissing() {
        val report = NightPlanner.plan(home, date, forecast(), kit)
        assertNull(report.cloudReason)
        show(report)
        compose.onNodeWithText("HOUR BY HOUR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Low").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("high thin cloud").assertDoesNotExist()
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

    private fun planned(layers: (Long, Boolean) -> Triple<Int, Int, Int>): NightReport {
        val bare = NightPlanner.plan(home, date, forecast(), kit)
        val dark = bare.timeline.filter { it.isDark }.map { it.time.epochSecond }.toSet()
        assertTrue(dark.size >= 3)
        return NightPlanner.plan(home, date, forecast { epoch -> layers(epoch, epoch in dark) }, kit)
    }

    private fun forecast(layers: ((Long) -> Triple<Int, Int, Int>)? = null): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                val epoch = start + i * 3600L
                val layer = layers?.invoke(epoch)
                HourlyWeather(
                    epochSecond = epoch,
                    cloudCover = 40,
                    cloudLow = layer?.first,
                    cloudMid = layer?.second,
                    cloudHigh = layer?.third,
                    humidity = 40,
                    windKmh = 6.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
