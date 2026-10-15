package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import app.nightbrief.app.ui.passport.NewStampNotice
import app.nightbrief.app.ui.passport.PassportScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.score.ConstellationStreaks
import app.nightbrief.score.Passport
import app.nightbrief.score.SessionEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PassportUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun passportShowsAnEarnedStampAndALockedOne() {
        val sessions = listOf(
            SessionEntry("home", "2026-01-15", rating = 4, overnightLowC = -6.0),
        )
        compose.setContent {
            NightBriefTheme(nightVision = true) {
                PassportScreen(
                    stamps = Passport.evaluate(sessions),
                    progress = ConstellationStreaks.progress(sessions.size),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Frostbitten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Earned 2026-01-15").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Night owl").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log 10 sessions").performScrollTo().assertIsDisplayed()
        val progress = compose.onAllNodesWithText("Orion · 1 of 7")
        assertTrue("constellation progress should be on the passport", progress.fetchSemanticsNodes().isNotEmpty())
        progress[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log a night whose overnight low was below 0 °C").assertDoesNotExist()
    }

    @Test
    fun newStampCardShowsOnce() {
        val sessions = listOf(
            SessionEntry("home", "2026-01-15", rating = 4, overnightLowC = -6.0),
        )
        val stamps = Passport.evaluate(sessions)
        val seen = mutableStateOf(emptySet<String>())
        compose.setContent {
            NightBriefTheme(nightVision = true) {
                NewStampNotice(
                    stamps = stamps,
                    seenIds = seen.value,
                    onDismiss = { seen.value = seen.value + it },
                )
            }
        }
        compose.onNodeWithText("New stamp: Frostbitten").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("New stamp: Frostbitten").assertDoesNotExist()
    }
}
