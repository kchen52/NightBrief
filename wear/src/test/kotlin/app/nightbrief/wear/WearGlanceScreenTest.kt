package app.nightbrief.wear

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.nightbrief.score.WearGlance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w200dp-h200dp", application = Application::class)
class WearGlanceScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun goNightShowsTheSiteScoreAndVerdict() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 92)
        compose.setContent { WearGlanceScreen(glance) }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("92").assertIsDisplayed()
        compose.onNodeWithText("Go").assertIsDisplayed()
    }

    @Test
    fun noGoNightShowsTheVerdict() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Long Point", score = 15)
        compose.setContent { WearGlanceScreen(glance) }
        compose.onNodeWithText("Long Point").assertIsDisplayed()
        compose.onNodeWithText("15").assertIsDisplayed()
        compose.onNodeWithText("No-go").assertIsDisplayed()
    }

    @Test
    fun maybeNightShowsTheVerdict() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 55)
        compose.setContent { WearGlanceScreen(glance) }
        compose.onNodeWithText("55").assertIsDisplayed()
        compose.onNodeWithText("Maybe").assertIsDisplayed()
    }

    @Test
    fun setupHidesTheScore() {
        val glance = WearGlance.from(onboardingComplete = false, siteName = null, score = null)
        compose.setContent { WearGlanceScreen(glance) }
        compose.onNodeWithText(WearGlance.SETUP).assertIsDisplayed()
        compose.onNodeWithText("—").assertDoesNotExist()
    }
}
