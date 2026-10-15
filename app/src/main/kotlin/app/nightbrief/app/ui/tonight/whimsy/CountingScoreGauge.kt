package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nightbrief.app.R
import app.nightbrief.app.ui.theme.NightColors
import kotlin.math.roundToInt

/**
 * Hero score arc. The number counts from 0 to [score] over 800 ms when motion is allowed.
 * Semantics always expose [score], so a test that does not advance the clock still finds it.
 * A still frame and a duration scale of 0 draw [score] immediately.
 */
@Composable
fun CountingScoreGauge(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
) {
    val motion = motionAllowed() && score != null
    val target = score ?: 0
    val anim = remember(score, motion) { Animatable(if (motion) 0f else target.toFloat()) }
    LaunchedEffect(score, motion) {
        if (!motion) {
            anim.snapTo(target.toFloat())
        } else {
            anim.snapTo(0f)
            anim.animateTo(target.toFloat(), tween(durationMillis = 800))
        }
    }
    val shown = when {
        score == null -> null
        !motion -> score
        else -> anim.value.roundToInt()
    }
    val color = NightColors.forScore(score)
    val track = MaterialTheme.colorScheme.outlineVariant
    val announced = score?.toString() ?: "—"
    val visual = shown?.toString() ?: "—"
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                track,
                135f,
                270f,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val arcScore = shown
            if (arcScore != null && arcScore > 0) {
                drawArc(
                    color,
                    135f,
                    270f * arcScore / 100f,
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                visual,
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(announced) },
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                stringResource(R.string.score_out_of),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
