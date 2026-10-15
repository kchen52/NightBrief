package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.nightbrief.app.ui.theme.LocalNightPalette
import app.nightbrief.score.MascotMood
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NightOwl(
    mood: MascotMood,
    description: String,
    modifier: Modifier = Modifier,
) {
    val paint = owlPaint(LocalNightPalette.current)
    Canvas(
        modifier.semantics {
            contentDescription = description
            role = Role.Image
        },
    ) {
        scale(size.minDimension / 100f, size.minDimension / 100f, pivot = Offset.Zero) {
            drawNightOwl(mood, paint)
        }
    }
}

private fun DrawScope.drawNightOwl(mood: MascotMood, paint: OwlPaint) {
    if (mood == MascotMood.LOOKING_UP) drawAurora(paint)
    if (mood == MascotMood.UMBRELLA) drawUmbrella(paint)
    ears(paint)
    drawCircle(paint.body, 18f, Offset(46f, 46f))
    drawOval(paint.body, Offset(26f, 56f), Size(40f, 36f))
    drawOval(paint.belly, Offset(34f, 64f), Size(24f, 22f))
    val lookUp = if (mood == MascotMood.LOOKING_UP) 4.5f else 0f
    val open = when (mood) {
        MascotMood.ASLEEP -> 0.12f
        MascotMood.SQUINTING_AT_CLOUD -> 0.32f
        else -> 1f
    }
    eye(36f, 44f, open, lookUp, paint)
    eye(56f, 44f, open, lookUp, paint)
    val beak = Path().apply {
        moveTo(42f, 50f)
        lineTo(50f, 50f)
        lineTo(46f, 57f)
        close()
    }
    drawPath(beak, paint.beak)
    when (mood) {
        MascotMood.AWAKE_TELESCOPE -> telescope(paint)
        MascotMood.SQUINTING_AT_CLOUD -> cloud(paint)
        MascotMood.ASLEEP -> blanket(paint)
        MascotMood.SCARF -> scarf(paint)
        MascotMood.UMBRELLA, MascotMood.LOOKING_UP -> feet(paint)
    }
}

private fun DrawScope.ears(paint: OwlPaint) {
    val left = Path().apply {
        moveTo(30f, 40f)
        lineTo(36f, 16f)
        lineTo(46f, 36f)
        close()
    }
    val right = Path().apply {
        moveTo(46f, 36f)
        lineTo(56f, 14f)
        lineTo(64f, 40f)
        close()
    }
    drawPath(left, paint.body)
    drawPath(right, paint.body)
    drawPath(left, paint.ink, style = Stroke(1.2f))
    drawPath(right, paint.ink, style = Stroke(1.2f))
}

private fun DrawScope.eye(cx: Float, cy: Float, openness: Float, lookUp: Float, paint: OwlPaint) {
    if (openness < 0.2f) {
        drawArc(
            paint.ink,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - 7f, cy - 2f),
            size = Size(14f, 8f),
            style = Stroke(1.8f, cap = StrokeCap.Round),
        )
        return
    }
    val h = 8f * openness
    drawOval(paint.eye, Offset(cx - 7f, cy - h), Size(14f, h * 2f))
    drawCircle(paint.pupil, 3f, Offset(cx, cy + h * 0.1f - lookUp))
    drawCircle(paint.eye, 1.1f, Offset(cx - 1.4f, cy - lookUp * 0.25f - 1.2f))
}

private fun DrawScope.telescope(paint: OwlPaint) {
    drawRoundRect(paint.telescope, Offset(58f, 62f), Size(30f, 8f), CornerRadius(4f, 4f))
    drawCircle(paint.lens, 6f, Offset(90f, 66f))
    drawCircle(paint.ink, 6f, Offset(90f, 66f), style = Stroke(1.4f))
    feet(paint)
}

private fun DrawScope.cloud(paint: OwlPaint) {
    drawCircle(paint.cloud, 9f, Offset(78f, 36f))
    drawCircle(paint.cloud, 7f, Offset(70f, 42f))
    drawCircle(paint.cloud, 6f, Offset(86f, 42f))
    feet(paint)
}

private fun DrawScope.blanket(paint: OwlPaint) {
    drawOval(paint.blanket, Offset(22f, 70f), Size(52f, 26f))
    zed(Offset(72f, 18f), 12f, paint)
    zed(Offset(84f, 30f), 8f, paint)
}

private fun DrawScope.scarf(paint: OwlPaint) {
    drawLine(paint.scarf, Offset(30f, 58f), Offset(66f, 54f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(paint.scarf, Offset(62f, 54f), Offset(74f, 74f), strokeWidth = 4.5f, cap = StrokeCap.Round)
    feet(paint)
}

private fun DrawScope.drawUmbrella(paint: OwlPaint) {
    drawArc(
        paint.umbrella,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(22f, 2f),
        size = Size(48f, 26f),
    )
    drawLine(paint.ink, Offset(46f, 15f), Offset(46f, 30f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawAurora(paint: OwlPaint) {
    repeat(3) { i ->
        val path = Path()
        val y0 = 6f + i * 7f
        path.moveTo(8f, y0)
        val steps = 16
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val y = y0 + 3.5f * sin((t * 2.0 * PI) + i).toFloat()
            path.lineTo(8f + 84f * t, y)
        }
        drawPath(path, paint.aurora, style = Stroke(1.8f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.feet(paint: OwlPaint) {
    drawOval(paint.beak, Offset(34f, 88f), Size(10f, 6f))
    drawOval(paint.beak, Offset(48f, 88f), Size(10f, 6f))
}

private fun DrawScope.zed(origin: Offset, w: Float, paint: OwlPaint) {
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + w, origin.y)
        lineTo(origin.x, origin.y + w * 0.75f)
        lineTo(origin.x + w, origin.y + w * 0.75f)
    }
    drawPath(path, paint.zzz, style = Stroke(1.7f, cap = StrokeCap.Round))
}
