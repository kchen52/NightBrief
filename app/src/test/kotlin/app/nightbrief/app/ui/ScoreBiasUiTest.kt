package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.SessionHistoryCard
import app.nightbrief.score.SessionEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScoreBiasUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sessionHistoryShowsTheBiasSentenceForScoredNights() {
        // An 88 expects 5 stars; rating 4 on five nights reads a star optimistic.
        val sessions = (1..5).map { i ->
            SessionEntry("home", "2026-08-%02d".format(i), score = 88, rating = 4)
        }
        show(sessions)
        compose.onNodeWithText("Home scores about a star optimistic over your last 5 scored nights.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun sessionHistoryStaysQuietWithoutEnoughScoredNights() {
        val sessions = (1..3).map { i ->
            SessionEntry("home", "2026-08-%02d".format(i), score = 88, rating = 4)
        }
        show(sessions)
        compose.onNodeWithText("SHOOTING HISTORY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("scores about", substring = true).assertDoesNotExist()
    }

    private fun show(sessions: List<SessionEntry>) {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SessionHistoryCard(
                        sessions = sessions,
                        siteName = "Home",
                        streakLine = null,
                        onOpenPassport = {},
                    )
                }
            }
        }
    }
}
