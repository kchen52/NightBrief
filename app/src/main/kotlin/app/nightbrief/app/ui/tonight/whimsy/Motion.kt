package app.nightbrief.app.ui.tonight.whimsy

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView

/**
 * True in screenshot and layout preview, where a moving clock or a running
 * animation would change the captured frame. Layoutlib (Paparazzi) reports
 * [android.view.View.isInEditMode] and does not set [LocalInspectionMode].
 */
@Composable
internal fun stillFrame(): Boolean = LocalInspectionMode.current || LocalView.current.isInEditMode

/**
 * False for a [stillFrame] and when the system animator duration scale is 0
 * (reduce motion). A failed settings read leaves motion on.
 */
@Composable
internal fun motionAllowed(): Boolean {
    if (stillFrame()) return false
    return animatorDurationScale() > 0f
}

@Composable
private fun animatorDurationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: RuntimeException) {
        1f
    }
}
