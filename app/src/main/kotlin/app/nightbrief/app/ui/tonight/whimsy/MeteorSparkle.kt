package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.nightbrief.app.ui.theme.LocalNightPalette
import kotlin.math.PI
import kotlin.math.sin

/**
 * A few dots behind the Meteors card. They twinkle once when motion is allowed
 * and sit still for a screenshot. The dots are not a click target.
 */
@Composable
fun MeteorSparkle(content: @Composable () -> Unit) {
    val palette = LocalNightPalette.current
    val motion = motionAllowed()
    val anim = remember { Animatable(1f) }
    LaunchedEffect(motion) {
        if (!motion) {
            anim.snapTo(1f)
        } else {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(1200))
        }
    }
    val alpha = if (!motion) {
        0.75f
    } else {
        val wave = sin(anim.value * PI.toFloat() * 2f)
        0.2f + 0.7f * if (wave < 0f) 0f else wave
    }
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawSparkles(palette.amber, alpha) },
    ) {
        content()
    }
}

private fun DrawScope.drawSparkles(color: Color, alpha: Float) {
    val spots = listOf(
        0.08f to 0.18f,
        0.22f to 0.08f,
        0.78f to 0.12f,
        0.9f to 0.22f,
        0.7f to 0.55f,
    )
    spots.forEachIndexed { i, (x, y) ->
        drawCircle(
            color.copy(alpha = alpha * if (i % 2 == 0) 1f else 0.65f),
            radius = if (i == 0) 2.4.dp.toPx() else 1.6.dp.toPx(),
            center = Offset(size.width * x, size.height * y),
        )
    }
}
