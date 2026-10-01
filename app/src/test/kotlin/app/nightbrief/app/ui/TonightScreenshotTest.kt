package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.astro.IssPass
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.MeteorAdvisor
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Golden images live in `src/test/snapshots`. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.TonightScreenshotTest`
 * and check with `./gradlew :app:verifyPaparazziDebug --tests app.nightbrief.app.ui.TonightScreenshotTest`.
 */
class TonightScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3600),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)

    @Test
    fun tonightWithAScore() {
        val report = scored().copy(meteor = null, issPasses = emptyList())
        assertNotNull(report.scoreValue)
        snapshot("tonightWithAScore", report)
    }

    @Test
    fun tonightStaleBanner() {
        val report = scored().copy(
            forecastStatus = ForecastStatus.STALE,
            meteor = null,
            issPasses = emptyList(),
        )
        snapshot("tonightStaleBanner", report)
    }

    @Test
    fun tonightMeteorAndIss() {
        val planned = scored()
        val meteor = planned.meteor
        assertTrue("Perseids should be worth watching on $date", meteor?.worthWatching == true)
        val report = planned.copy(
            issPasses = listOf(
                IssPass(
                    rise = Instant.parse("2024-08-13T02:10:00Z"),
                    set = Instant.parse("2024-08-13T02:16:00Z"),
                    peak = Instant.parse("2024-08-13T02:13:00Z"),
                    peakAltitudeDeg = 62.0,
                    peakAzimuthDeg = 140.0,
                ),
            ),
        )
        assertNotNull(MeteorAdvisor.digestLine(meteor!!))
        snapshot("tonightMeteorAndIss", report)
    }

    private fun snapshot(name: String, report: NightReport) {
        paparazzi.snapshot(name) {
            NightBriefTheme {
                Box(
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
    }

    private fun scored(): NightReport = NightPlanner.plan(home, date, forecast(), GearCatalog.exampleKit)

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
