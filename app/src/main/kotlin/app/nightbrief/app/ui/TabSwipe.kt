package app.nightbrief.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val SwipeDistance = 72.dp
private val SwipeFlingVelocity = 500.dp

/**
 * Turns a horizontal swipe across a tab into a step to the neighbouring tab: +1 for a swipe
 * to the left (next tab), -1 for a swipe to the right.
 *
 * The detector sees each event after the tab's content, so a child that already took the
 * drag (the hour strip, a chip row, a list that started scrolling) keeps it and no step is
 * sent. Once the swipe passes touch slop it consumes the drag, so a card under the finger
 * does not also register a click.
 */
@Composable
internal fun Modifier.tabSwipe(enabled: Boolean, onStep: (Int) -> Unit): Modifier {
    val step by rememberUpdatedState(onStep)
    if (!enabled) return this
    return pointerInput(Unit) {
        val distance = SwipeDistance.toPx()
        val fling = SwipeFlingVelocity.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
            } ?: return@awaitEachGesture
            tracker.addPosition(start.uptimeMillis, start.position)
            var last = start.position
            val lifted = horizontalDrag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                last = change.position
                change.consume()
            }
            if (!lifted) return@awaitEachGesture
            val delta = last - down.position
            val result = swipeStep(delta.x, delta.y, tracker.calculateVelocity().x, distance, fling)
            if (result != 0) step(result)
        }
    }
}

/**
 * +1, -1, or 0 for a finished drag. Distances are pixels and [velocityX] is pixels per second.
 * A drag counts when it is mostly sideways and either travels [distance], or travels a third
 * of it while still moving at [fling] in the same direction.
 */
internal fun swipeStep(dx: Float, dy: Float, velocityX: Float, distance: Float, fling: Float): Int {
    if (abs(dx) <= abs(dy)) return 0
    val far = abs(dx) >= distance
    val flung = abs(velocityX) >= fling && (velocityX < 0) == (dx < 0) && abs(dx) >= distance / 3
    if (!far && !flung) return 0
    return if (dx < 0) 1 else -1
}
