package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.nightbrief.app.ui.tonight.LoadingSky
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.sites.Site
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Tonight's loading sky reads the ephemeris off the frame, so the first composition stays cheap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LoadingSkyTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Test
    fun sunsetAppearsOnlyAfterTheOffFrameReadReturns() {
        val gate = CompletableDeferred<NightEphemeris>()
        compose.setContent {
            LoadingSky(home) { gate.await() }
        }

        compose.onNodeWithTag("loading-sky-progress").assertExists()

        gate.complete(
            NightEphemeris.compute(
                LocalDate.of(2026, 3, 20), home.zone, home.latitude, home.longitude,
            ),
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("loading-sky-progress").fetchSemanticsNodes().isEmpty()
        }
        // The explainer also starts with "Sunset", so the read is done when a second line joins it.
        val sunsetLines = compose.onAllNodesWithText("Sunset", substring = true).fetchSemanticsNodes()
        assertEquals("expected the explainer and the sunset line, got ${sunsetLines.size}", 2, sunsetLines.size)
        compose.onNodeWithText("Dark", substring = true).assertExists()
    }
}
