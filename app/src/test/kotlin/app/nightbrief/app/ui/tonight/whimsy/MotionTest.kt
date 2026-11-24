package app.nightbrief.app.ui.tonight.whimsy

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
class MotionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun localStillFrameFreezesMotionWithoutPaparazzi() {
        var still = false
        var allowed = true
        compose.setContent {
            CompositionLocalProvider(LocalStillFrame provides true) {
                still = stillFrame()
                allowed = motionAllowed()
            }
        }
        compose.waitForIdle()
        assertTrue("expected stillFrame=true under LocalStillFrame", still)
        assertFalse("expected motionAllowed=false under LocalStillFrame", allowed)
    }

    @Test
    fun zeroAnimatorDurationScaleDisablesMotion() {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var allowed = true
        val tick = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            tick.intValue
            allowed = motionAllowed()
        }
        compose.waitForIdle()
        assertFalse("expected motionAllowed=false when ANIMATOR_DURATION_SCALE is 0", allowed)

        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.runOnIdle { tick.intValue++ }
        compose.waitForIdle()
        assertEquals(true, allowed)
    }
}
