package app.nightbrief.app.ui.tonight.whimsy

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView

/**
 * True in a screenshot or layout preview, where a moving clock or a running
 * animation would change the captured frame.
 *
 * Paparazzi 1.3.5 does not set [LocalInspectionMode], and the Compose view's
 * [android.view.View.isInEditMode] stays false. Its snapshot runs on a stack
 * that includes `app.cash.paparazzi`, which is the signal used here. A still
 * frame also pins "now" to the night window; reduce-motion does not.
 */
@Composable
internal fun stillFrame(): Boolean {
    val paparazzi = remember { paparazziOnStack() }
    return LocalInspectionMode.current || LocalView.current.isInEditMode || paparazzi
}

/**
 * False for a [stillFrame] and when either animator duration scale is 0
 * (reduce motion). Paparazzi zeros [ValueAnimator]'s scale rather than
 * [Settings.Global.ANIMATOR_DURATION_SCALE]. A failed settings read leaves
 * that scale at 1.
 */
@Composable
internal fun motionAllowed(): Boolean {
    if (stillFrame()) return false
    return settingsAnimatorScale() > 0f && valueAnimatorScale() > 0f
}

@Composable
private fun settingsAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: RuntimeException) {
        1f
    }
}

private fun valueAnimatorScale(): Float =
    try {
        val method = ValueAnimator::class.java.getDeclaredMethod("getDurationScale")
        method.invoke(null) as Float
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        1f
    }

private fun paparazziOnStack(): Boolean =
    Throwable().stackTrace.any { it.className.startsWith("app.cash.paparazzi.") }
