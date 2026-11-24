package app.nightbrief.app.ui.tonight.whimsy

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuroraRippleRecompositionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun advancingTheRippleAnimationDoesNotRecompose() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(200.dp, 80.dp)) {
                AuroraRipple(Color.Green, onCompose = { compositions++ })
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("initial compositions", 1, compositions)

        repeat(8) {
            compose.mainClock.advanceTimeBy(250L)
            compose.mainClock.advanceTimeByFrame()
        }
        assertEquals("AuroraRipple must read anim.value in draw phase, not composition", 1, compositions)
    }
}
