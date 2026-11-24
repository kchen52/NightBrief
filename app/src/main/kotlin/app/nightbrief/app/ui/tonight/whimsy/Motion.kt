package app.nightbrief.app.ui.tonight.whimsy

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView

/** Explicit override for tests or previews that require a frozen frame. */
internal val LocalStillFrame = staticCompositionLocalOf { false }

/**
 * True in a screenshot or layout preview, where a moving clock or a running
 * animation would change the captured frame.
 *
 * Paparazzi does not set [LocalInspectionMode], and the Compose view's
 * [android.view.View.isInEditMode] stays false. Production builds do not have
 * Paparazzi on the classpath, so [hasPaparazziOnClasspath] is false and no stack
 * trace is ever allocated on user devices. A still frame also pins "now" to the
 * night window; reduce-motion does not.
 */
@Composable
internal fun stillFrame(): Boolean {
    if (LocalStillFrame.current || LocalInspectionMode.current || LocalView.current.isInEditMode) {
        return true
    }
    if (!hasPaparazziOnClasspath) return false
    return remember { paparazziOnStack() }
}

/**
 * False for a [stillFrame] and when animations are disabled via system settings
 * or [ValueAnimator.areAnimatorsEnabled] (API 26+, no hidden reflection).
 * Paparazzi zeros [ValueAnimator]'s duration scale rather than
 * [Settings.Global.ANIMATOR_DURATION_SCALE]. A failed settings read leaves
 * that scale at 1.
 */
@Composable
internal fun motionAllowed(): Boolean {
    if (stillFrame()) return false
    return settingsAnimatorScale() > 0f && ValueAnimator.areAnimatorsEnabled()
}

@Composable
private fun settingsAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: RuntimeException) {
        1f
    }
}

internal val hasPaparazziOnClasspath: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
    try {
        Class.forName("app.cash.paparazzi.Paparazzi", false, MotionClassLoaderHolder::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

private object MotionClassLoaderHolder

private fun paparazziOnStack(): Boolean =
    Throwable().stackTrace.any { it.className.startsWith("app.cash.paparazzi.") }
