package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.ConfidenceCard
import app.nightbrief.score.CloudAgreement
import app.nightbrief.score.ModelConfidence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForecastConfidenceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun agreeCardShowsNoGapNumber() {
        show(CloudAgreement(ModelConfidence.AGREE, meanAbsDiff = 4, hoursCompared = 8))
        compose.onNodeWithText("Models agree on clouds").assertIsDisplayed()
        compose.onNodeWithText("Primary model against a second opinion across 8 dark hours.").assertIsDisplayed()
    }

    @Test
    fun disagreeCardNamesTheGap() {
        show(CloudAgreement(ModelConfidence.DISAGREE, meanAbsDiff = 38, hoursCompared = 6))
        compose.onNodeWithText("Models disagree on clouds (avg 38 pts apart)").assertIsDisplayed()
    }

    private fun show(agreement: CloudAgreement) {
        compose.setContent {
            NightBriefTheme {
                ConfidenceCard(agreement)
            }
        }
    }
}
