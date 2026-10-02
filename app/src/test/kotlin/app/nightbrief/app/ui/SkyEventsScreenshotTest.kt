package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.events.EventsList
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.astro.SkyEvents
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Goldens for the year-ahead sky events list. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.SkyEventsScreenshotTest`.
 * After recording, copy the PNG to `docs/screenshots/sky-events.png`.
 */
class SkyEventsScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun skyEvents() {
        val zone = ZoneId.of("UTC")
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 1), zone).take(14)
        paparazzi.snapshot("skyEvents") {
            NightBriefTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    EventsList(events = events, zone = zone)
                }
            }
        }
    }
}
