package app.nightbrief.app.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.score.NightPlanCard
import org.junit.Rule
import org.junit.Test

/**
 * Golden images live in `src/test/snapshots`. Record with
 * `./gradlew :app:recordPaparazziDebug --tests app.nightbrief.app.share.ShareCardScreenshotTest`
 * and check with `./gradlew :app:verifyPaparazziDebug --tests app.nightbrief.app.share.ShareCardScreenshotTest`.
 *
 * These snapshots render the real [NightPlanImage] bitmap (Canvas, not a Compose
 * mirror), so the golden is the actual shared picture.
 */
class ShareCardScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1200),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun shareCardGoNight() {
        snapshot(
            "shareCardGoNight",
            NightPlanCard(
                siteName = "Long Point",
                place = "42.6°N 80.4°W",
                dateLabel = "Sat 10 Aug",
                score = 91,
                scoreText = "91",
                verdictLabel = "Go · Excellent",
                bestWindow = "00:00–03:00 (90)",
                milkyWay = "Milky Way core up 22:14–03:40",
                target = "M8 Lagoon",
                saved = null,
            ),
        )
    }

    @Test
    fun shareCardNoScore() {
        snapshot(
            "shareCardNoScore",
            NightPlanCard(
                siteName = "Home",
                place = "43.7°N 79.4°W",
                dateLabel = "Sat 10 Aug",
                score = null,
                scoreText = "—",
                verdictLabel = "No score",
                bestWindow = null,
                milkyWay = "",
                target = null,
                saved = "Saved Aug 9, 08:00",
            ),
        )
    }

    @Test
    fun shareCardEstimated() {
        snapshot(
            "shareCardEstimated",
            NightPlanCard(
                siteName = "Long Point",
                place = "42.6°N 80.4°W",
                dateLabel = "Sat 10 Aug",
                score = 78,
                scoreText = "78",
                verdictLabel = "Go · Good",
                bestWindow = "01:00–03:00 (80)",
                milkyWay = "Milky Way core up 22:14–03:40",
                target = "M8 Lagoon",
                saved = null,
                estimated = true,
            ),
        )
    }

    private fun snapshot(name: String, card: NightPlanCard) {
        val bitmap = NightPlanImage.render(card)
        paparazzi.snapshot(name) {
            NightBriefTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
