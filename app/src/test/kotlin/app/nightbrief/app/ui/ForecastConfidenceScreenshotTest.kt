package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.ConfidenceCard
import app.nightbrief.score.CloudAgreement
import app.nightbrief.score.ModelConfidence
import org.junit.Rule
import org.junit.Test

/**
 * Goldens for the forecast-confidence card. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.ui.ForecastConfidenceScreenshotTest`.
 * After recording, copy the agree PNG to `docs/screenshots/forecast-confidence.png`.
 */
class ForecastConfidenceScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1600),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun confidenceAgree() = snapshot(
        "confidenceAgree",
        CloudAgreement(ModelConfidence.AGREE, meanAbsDiff = 4, hoursCompared = 8),
    )

    @Test
    fun confidenceMixed() = snapshot(
        "confidenceMixed",
        CloudAgreement(ModelConfidence.MIXED, meanAbsDiff = 15, hoursCompared = 8),
    )

    @Test
    fun confidenceDisagree() = snapshot(
        "confidenceDisagree",
        CloudAgreement(ModelConfidence.DISAGREE, meanAbsDiff = 38, hoursCompared = 8),
    )

    private fun snapshot(name: String, agreement: CloudAgreement) {
        paparazzi.snapshot(name) {
            NightBriefTheme {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ConfidenceCard(agreement)
                }
            }
        }
    }
}
