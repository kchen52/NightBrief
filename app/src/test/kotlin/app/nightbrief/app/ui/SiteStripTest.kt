package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.theme.NightBriefTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SiteStripTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLongPrimaryNameWrapsInsteadOfLookingLikeTheOldName() {
        compose.setContent {
            NightBriefTheme {
                SiteStrip(
                    sites = listOf(SiteChip("imported", "Toronto imported", 82, isPrimary = true)),
                    selectedId = "imported",
                    onSelect = {},
                )
            }
        }
        val name = compose.onNodeWithText("Toronto imported")
        name.assertIsDisplayed()
        val bounds = name.getBoundsInRoot()
        val height = bounds.bottom.value - bounds.top.value
        assertTrue("name stayed on one clipped line (${height}dp)", height > 30f)
    }

    @Test
    fun aCaptionNamesWhatTheScoreIs() {
        compose.setContent {
            NightBriefTheme {
                SiteStrip(
                    sites = listOf(SiteChip("home", "Home", 91, isPrimary = true)),
                    selectedId = "home",
                    onSelect = {},
                    scoreCaption = "Best this week",
                )
            }
        }
        compose.onNodeWithText("Best this week").assertIsDisplayed()
        compose.onNodeWithText("91").assertIsDisplayed()
    }
}
