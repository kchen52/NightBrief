package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingValidationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcomeHidesBackAndTheSiteStepWaitsForNextBeforeComplaining() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app, SavedStateHandle())
        compose.setContent { NightBriefTheme { OnboardingScreen(vm) } }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Step 1 of 4").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithText("Back").fetchSemanticsNodes().size)
        compose.onNodeWithText("Next").performClick()
        assertEquals(0, compose.onAllNodesWithText("Set a location").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Latitude must be between -90 and 90").fetchSemanticsNodes().size)
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Set a location").performScrollTo().assertIsDisplayed()
    }
}
