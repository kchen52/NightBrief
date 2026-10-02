package app.nightbrief.app.ui

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingNotificationsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allowNotificationsStartsGreyedOutWhenPermissionIsAlreadyGranted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showOnboarding(app)
        openNotificationStep()
        compose.onNodeWithTag("allow-notifications").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun allowNotificationsGreysOutAfterPermissionIsGranted() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showOnboarding(app)
        openNotificationStep()
        compose.onNodeWithTag("allow-notifications").performScrollTo().assertIsEnabled()

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.onNodeWithTag("allow-notifications").assertIsNotEnabled()
    }

    private fun showOnboarding(app: Application) {
        val vm = AppViewModel(app, SavedStateHandle())
        compose.setContent {
            NightBriefTheme { OnboardingScreen(vm) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Step 1 of 4").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openNotificationStep() {
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Latitude").performTextInput("43.65")
        compose.onNodeWithText("Longitude").performTextInput("-79.38")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your gear").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Morning digest").assertIsDisplayed()
    }
}
