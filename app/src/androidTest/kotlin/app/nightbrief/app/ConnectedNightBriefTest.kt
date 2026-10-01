package app.nightbrief.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`.
 * Compiles without a device: `./gradlew :app:assembleDebugAndroidTest`.
 * Orchestrator clears app data between tests. Forecast fetches may be slow offline;
 * the week test waits for the on-device ephemeris rows, which do not need a network.
 */
@RunWith(AndroidJUnit4::class)
class ConnectedNightBriefTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingMapDialogShowsTheOpenStreetMapCredit() {
        compose.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your first site").assertIsDisplayed()
        compose.onNodeWithText("Pick on map").performScrollTo().performClick()
        compose.onNodeWithText("Tap to place the site").assertIsDisplayed()
        compose.onNodeWithText("Map data © OpenStreetMap contributors").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Your first site").assertIsDisplayed()
    }

    @Test
    fun finishingOnboardingReachesTonightAndTheOtherTabs() {
        finishOnboarding()
        compose.onNodeWithText("Tonight").assertIsDisplayed()
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithText("Sites").performClick()
        compose.onNodeWithText("Sites").assertIsDisplayed()
        compose.onNodeWithText("Gear").performClick()
        compose.onNodeWithText("Gear").assertIsDisplayed()
    }

    @Test
    fun settingsCanTurnOffTheDigestAndBigNightAlerts() {
        finishOnboarding()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("ISS orbits by Celestrak.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Planetary Kp by NOAA SWPC.").assertIsDisplayed()
        compose.onNodeWithTag("digest-enabled").assertIsOn().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("digest-enabled") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("big-night-enabled").assertIsOn().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("big-night-enabled") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("digest-enabled").assertIsOff()
        compose.onNodeWithTag("big-night-enabled").assertIsOff()
    }

    @Test
    fun weekOpensThePlannerAndStepsToTheNextNight() {
        finishOnboarding()
        compose.onNodeWithText("Week").performClick()
        compose.waitUntil(timeoutMillis = 45_000) {
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("week-night")[0].performClick()
        compose.onNodeWithContentDescription("Previous night").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next night").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Previous night").assertIsEnabled()
        compose.onNodeWithContentDescription("Next night").assertIsEnabled()
    }

    private fun finishOnboarding() {
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Latitude").performTextInput("43.65")
        compose.onNodeWithText("Longitude").performTextInput("-79.38")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your gear").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Tonight").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
