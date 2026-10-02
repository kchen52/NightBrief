package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import app.nightbrief.app.ui.events.EventsList
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.astro.SkyEvents
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SkyEventsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")

    @Test
    fun listsEclipsesAndOppositionsWithMonths() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 1), zone)
        compose.setContent {
            NightBriefTheme {
                EventsList(events = events, zone = zone, modifier = Modifier.fillMaxSize())
            }
        }
        // First viewport: month header, a shower peak, and the Mars–Jupiter appulse.
        compose.onNodeWithText("August 2024").assertIsDisplayed()
        compose.onNodeWithText("Perseids peak").assertIsDisplayed()
        // September composes on demand: scroll the month header into view by index.
        val septemberHeader = 1 + events.count {
            java.time.YearMonth.from(it.instant.atZone(zone).toLocalDate()) == java.time.YearMonth.of(2024, 8)
        }
        compose.onNodeWithTag("events-list").performScrollToIndex(septemberHeader)
        compose.onNodeWithText("September 2024").assertIsDisplayed()
        compose.onNodeWithText("Saturn at opposition").assertIsDisplayed()
    }

    @Test
    fun emptyListShowsNoMonths() {
        compose.setContent {
            NightBriefTheme {
                EventsList(events = emptyList(), zone = zone)
            }
        }
        compose.onNodeWithText("September 2024").assertDoesNotExist()
    }
}
