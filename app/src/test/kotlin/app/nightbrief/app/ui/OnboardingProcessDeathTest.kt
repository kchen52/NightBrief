package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.gear.GearCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingProcessDeathTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun siteDraftAndGearSurviveProcessDeath() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var vm = AppViewModel(app, SavedStateHandle())
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NightBriefTheme { OnboardingScreen(vm) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Step 1 of 4").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Latitude").performTextInput("43.65")
        compose.onNodeWithText("Longitude").performTextInput("-79.38")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your gear").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove ${GearCatalog.sigma10to18.name}")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("No lenses yet.").performScrollTo().assertIsDisplayed()

        val saved = vm.exportOnboardingState()
        vm = AppViewModel(app, SavedStateHandle(saved))
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Your gear").assertIsDisplayed()
        compose.onNodeWithText("Canon EOS R7").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No lenses yet.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("43.65").assertIsDisplayed()
        compose.onNodeWithText("-79.38").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }
}
