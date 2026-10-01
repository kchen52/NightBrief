package app.nightbrief.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.score.WearGlance
import org.junit.Rule
import org.junit.Test

/**
 * Round-watch frames for the README. After recording, copy the PNGs to `docs/screenshots/`:
 * `./gradlew :wear:recordPaparazziDebug --tests app.nightbrief.wear.WearScreenshotTest`
 */
class WearScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.GALAXY_WATCH4_CLASSIC_LARGE,
        theme = "android:Theme.DeviceDefault",
    )

    @Test
    fun go() = shot("wear-go", WearGlance.from(onboardingComplete = true, siteName = "Home", score = 92))

    @Test
    fun noGo() = shot("wear-nogo", WearGlance.from(onboardingComplete = true, siteName = "Long Point", score = 15))

    @Test
    fun setup() = shot("wear-setup", WearGlance.from(onboardingComplete = false, siteName = null, score = null))

    private fun shot(name: String, glance: WearGlance) {
        paparazzi.snapshot(name) { WatchFrame { WearGlanceScreen(glance) } }
    }
}

/** Circular mask so the golden looks like a round watch. The tile itself is not clipped. */
@Composable
private fun WatchFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(6.dp)
            .clip(CircleShape),
    ) {
        content()
    }
}
