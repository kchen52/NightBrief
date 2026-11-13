package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.TargetSampleDetail
import app.nightbrief.app.ui.tonight.TargetSampleThumb
import app.nightbrief.app.ui.tonight.TargetSamples
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * The sample photo on a suggestion. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.TargetSampleScreenshotTest`.
 */
class TargetSampleScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1100),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun orionNebulaSample() {
        val sample = TargetSamples.of("m42")
        assertNotNull(sample)
        snapshot("orionNebulaSample", nightVision = false)
    }

    @Test
    fun orionNebulaSampleInNightVision() {
        snapshot("orionNebulaSampleInNightVision", nightVision = true)
    }

    private fun snapshot(name: String, nightVision: Boolean) {
        val sample = TargetSamples.of("m42")!!
        paparazzi.snapshot(name) {
            NightBriefTheme(nightVision = nightVision) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TargetSampleThumb(sample, "Orion Nebula (M42)")
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Orion Nebula (M42)",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TargetSampleDetail(sample)
                }
            }
        }
    }
}
