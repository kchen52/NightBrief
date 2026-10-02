package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
}
