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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * TalkBack names and a large font, on a device or emulator.
 * `./gradlew :app:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class ConnectedAccessibilityTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreFontScale() {
        shell("settings put system font_scale 1.0")
    }

    @Test
    fun plannerAndSettingsKeepTalkBackLabelsAtLargeFont() {
        shell("settings put system font_scale 1.5")
        compose.activityRule.scenario.recreate()
        assertEquals(1.5f, compose.activity.resources.configuration.fontScale, 0.05f)

        finishOnboarding()
        compose.onNodeWithText("Week").performClick()
        compose.waitUntil(timeoutMillis = 45_000) {
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("week-night")[0].performClick()
        compose.onNodeWithContentDescription("Previous night").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next night").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Daily digest").performScrollTo().assertIsOn()
        compose.onNodeWithContentDescription("Big Night alerts").assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription("Daily digest").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Daily digest") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Big Night alerts").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Big Night alerts") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
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

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        descriptor.use { FileInputStream(it.fileDescriptor).readBytes() }
    }
}
