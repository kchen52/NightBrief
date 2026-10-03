package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.DarkerSkyUiState
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.DarkerSkyCard
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.DarkSkyCandidate
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.RoadAccess
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Goldens for the "Darker sky nearby" card. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.DarkerSkyScreenshotTest`
 * and check with `./gradlew :app:verifyPaparazziDebug --tests app.nightbrief.app.ui.DarkerSkyScreenshotTest`.
 * After recording, copy the results PNG to `docs/screenshots/darker-sky.png`.
 */
class DarkerSkyScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 8)
    private val date = LocalDate.of(2024, 8, 12)

    @Test
    fun darkerSkyWithResults() {
        val candidates = listOf(
            candidate(bortle = 3, lat = 43.75, lon = -79.10, distanceKm = 28.0, delta = 12, access = RoadAccess.DRIVE_UP),
            candidate(bortle = 4, lat = 43.50, lon = -79.70, distanceKm = 41.0, delta = 7, access = RoadAccess.HIKE_IN),
        )
        paparazzi.snapshot("darkerSkyWithResults") {
            NightBriefTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    DarkerSkyCard(
                        site = home,
                        state = DarkerSkyUiState(candidates = candidates, searchedSiteId = home.id),
                        onSearch = {},
                        onSave = {},
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }

    @Test
    fun darkerSkySearching() {
        paparazzi.snapshot("darkerSkySearching") {
            NightBriefTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    DarkerSkyCard(
                        site = home,
                        state = DarkerSkyUiState(searching = true, searchedSiteId = home.id),
                        onSearch = {},
                        onSave = {},
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }

    @Test
    fun darkerSkyEmpty() {
        paparazzi.snapshot("darkerSkyEmpty") {
            NightBriefTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    DarkerSkyCard(
                        site = home,
                        state = DarkerSkyUiState(candidates = emptyList(), searchedSiteId = home.id),
                        onSearch = {},
                        onSave = {},
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }

    private fun candidate(
        bortle: Int,
        lat: Double,
        lon: Double,
        distanceKm: Double,
        delta: Int,
        access: RoadAccess = RoadAccess.UNKNOWN,
    ): DarkSkyCandidate {
        val site = Site("darksky-$bortle", "candidate", lat, lon, "America/Toronto", bortle = bortle, bortleSource = BortleSource.MAP)
        val report = NightPlanner.plan(site, date, forecast(), GearCatalog.exampleKit)
        return DarkSkyCandidate(site, distanceKm, bortle, report, delta, access)
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
