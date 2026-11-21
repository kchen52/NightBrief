package app.nightbrief.app.ui.common

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReportGlassScrollRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scrollStartAndStopDoNotRecomposeTheOwner() {
        val gate = GlassScrollGate()
        val scrolling = mutableStateOf(false)
        var compositions = 0
        compose.setContent {
            CompositionLocalProvider(LocalGlassScrollGate provides gate) {
                compositions++
                ReportGlassScroll { scrolling.value }
            }
        }
        compose.waitForIdle()
        assertEquals("initial compositions", 1, compositions)

        compose.runOnUiThread { scrolling.value = true }
        compose.waitForIdle()
        assertTrue("gate should be up while scrolling", gate.moving)

        compose.runOnUiThread { scrolling.value = false }
        compose.waitForIdle()
        assertFalse("gate should drop when scrolling stops", gate.moving)
        assertEquals("owner recomposed on scroll transitions", 1, compositions)
    }

    @Test
    fun disposingWhileScrollingClearsTheGate() {
        val gate = GlassScrollGate()
        val scrolling = mutableStateOf(true)
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalGlassScrollGate provides gate) {
                if (visible.value) ReportGlassScroll { scrolling.value }
            }
        }
        compose.waitForIdle()
        assertTrue("gate should be up", gate.moving)

        compose.runOnUiThread { visible.value = false }
        compose.waitForIdle()
        assertFalse("leaving composition must count as stopped", gate.moving)
    }
}
