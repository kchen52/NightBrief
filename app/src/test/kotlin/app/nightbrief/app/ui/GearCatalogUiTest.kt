package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import app.nightbrief.app.ui.gear.GearEditor
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.gear.GearCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GearCatalogUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hyphenSearchFindsTheSigma16to300() {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GearEditor(GearCatalog.exampleKit, onChange = {})
                }
            }
        }
        compose.onNodeWithText("Add lens").performScrollTo().performClick()
        compose.onNodeWithText("Search lenses").performTextInput("16-300")
        compose.onNodeWithText("Sigma 16–300mm f/3.5–6.7 DC OS Contemporary").assertIsDisplayed()
    }

    @Test
    fun nicknameKeepsTypedOrderWhenTheSaveDoesNotEcho() {
        var saved: String? = null
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // The parent never feeds the typed text back, the way a save that lags a keystroke does.
                    GearEditor(GearCatalog.exampleKit, onChange = { next -> saved = next.primaryBody?.nickname })
                }
            }
        }
        compose.onAllNodesWithText("Add a nickname")[0].performScrollTo().performClick()
        val field = compose.onNodeWithText("Nickname")
        "Fold Camera".forEach { field.performTextInput(it.toString()) }
        compose.waitForIdle()
        compose.onNodeWithText("Fold Camera").assertIsDisplayed()
        assertEquals("Fold Camera", saved)
    }

    @Test
    fun cameraSearchStaysOpenAcrossAConfigurationChange() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GearEditor(GearCatalog.exampleKit, onChange = {})
                }
            }
        }
        compose.onNodeWithText("Add body").performScrollTo().performClick()
        compose.onNodeWithText("Search cameras").performTextInput("Sony")
        compose.onNodeWithText("Sony α7 III").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("Sony").assertIsDisplayed()
        compose.onNodeWithText("Sony α7 III").assertIsDisplayed()
        compose.onNodeWithText("Search cameras").assertIsDisplayed()
    }

    @Test
    fun aTwoLinePrimeErrorLeavesAddLensWhereItWas() {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GearEditor(GearCatalog.exampleKit, onChange = {})
                }
            }
        }
        compose.onNodeWithText("Add lens").performScrollTo().performClick()
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithText("Name").performTextReplacement("QA mismatch prime")
        compose.onNodeWithText("Minimum focal length (mm)").performTextReplacement("35")
        compose.onNodeWithText("Max aperture (f-number)").performTextReplacement("2")
        compose.onNodeWithText("Aperture at long end").performTextReplacement("4")
        formAddLens().performScrollTo()
        val before = formAddLens().getUnclippedBoundsInRoot().top.value
        formAddLens().performClick()
        compose.onNodeWithText("A prime uses one aperture. Make both f-numbers match.").assertIsDisplayed()
        val after = formAddLens().getUnclippedBoundsInRoot().top.value
        assertEquals(before, after, 0.5f)
    }

    private fun formAddLens(): SemanticsNodeInteraction {
        val nodes = compose.onAllNodesWithText("Add lens")
        return nodes[nodes.fetchSemanticsNodes().lastIndex]
    }
}
