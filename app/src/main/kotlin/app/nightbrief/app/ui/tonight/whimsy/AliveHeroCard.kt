package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.theme.LocalNightPalette
import app.nightbrief.score.AuroraChance
import app.nightbrief.score.HeroSky
import app.nightbrief.score.MascotMood
import app.nightbrief.score.MoonFacts
import app.nightbrief.score.NightReport
import java.time.Clock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tonight hero chrome: twilight gradient, Moon, Milky Way, owl, and the one-shot celebrations.
 * [clock] supplies "now" for the twilight band. A still frame uses [HeroSky.stillInstant]
 * so a screenshot does not follow the wall clock. Ephemeris is not recomputed here.
 */
@Composable
fun AliveHeroCard(
    report: NightReport,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC(),
    content: @Composable () -> Unit,
) {
    val palette = LocalNightPalette.current
    val still = stillFrame()
    val now = if (still) HeroSky.stillInstant(report) else clock.instant()
    val sky = HeroSky.of(report, now)
    val paint = skyPaint(palette, sky.twilight)
    val mood = MascotMood.of(report)
    var showFact by remember { mutableStateOf(false) }
    val percent = (sky.moonIllumination * 100).roundToInt()
    val moonDescription = "Moon, ${report.ephemeris.moonPhaseName.label}, $percent percent lit"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.verticalGradient(listOf(paint.top, paint.bottom)))
                drawStars(paint.star, starAlpha(sky.twilight))
                drawMilkyWay(paint.milkyWay, sky.milkyWayAlpha.toFloat())
            }
            if (report.aurora?.chance == AuroraChance.LIKELY) {
                AuroraRipple(palette.excellent)
            }
            if (sky.shootingStar && motionAllowed()) {
                ShootingStar(palette.amber)
            }
            Column(Modifier.padding(16.dp)) {
                Spacer(Modifier.height(68.dp))
                content()
            }
            NightOwl(
                mood,
                mood.contentDescription(report),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 10.dp)
                    .size(64.dp),
            )
            MoonTarget(
                illumination = sky.moonIllumination.toFloat(),
                terminatorDeg = sky.moonTerminatorDeg.toFloat(),
                light = paint.moonLight,
                shadow = paint.moonShadow,
                description = moonDescription,
                longClickLabel = "Show a Moon fact",
                onLongClick = { showFact = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(48.dp),
            )
        }
    }
    if (showFact) {
        AlertDialog(
            onDismissRequest = { showFact = false },
            title = { Text(report.ephemeris.moonPhaseName.label) },
            text = { Text(MoonFacts.forDate(report.date)) },
            confirmButton = {
                TextButton(onClick = { showFact = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun BoxScope.AuroraRipple(color: Color) {
    val motion = motionAllowed()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        anim.snapTo(0f)
        if (motion) anim.animateTo(1f, tween(1600, easing = LinearEasing))
    }
    val phase = if (motion) anim.value else 0f
    Canvas(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(36.dp),
    ) {
        drawRipples(phase, color)
    }
}

@Composable
private fun BoxScope.ShootingStar(color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(900, easing = LinearEasing))
    }
    Canvas(Modifier.matchParentSize()) {
        val t = anim.value
        if (t <= 0.02f || t >= 0.98f) return@Canvas
        val head = Offset(size.width * (-0.15f + 1.25f * t), size.height * (0.12f + 0.22f * t))
        val tail = Offset(head.x - size.width * 0.14f, head.y - size.height * 0.05f)
        drawLine(color.copy(alpha = 0.85f), tail, head, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color, 3.dp.toPx(), head)
    }
}

@Composable
private fun MoonTarget(
    illumination: Float,
    terminatorDeg: Float,
    light: Color,
    shadow: Color,
    description: String,
    longClickLabel: String,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val click by rememberUpdatedState(onLongClick)
    Box(
        modifier
            .semantics {
                contentDescription = description
                role = Role.Button
                onLongClick(label = longClickLabel) {
                    click()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { click() })
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawMoonDisc(illumination, terminatorDeg, light, shadow)
        }
    }
}

private fun DrawScope.drawStars(color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val spots = listOf(
        0.18f to 0.22f,
        0.32f to 0.62f,
        0.46f to 0.18f,
        0.62f to 0.48f,
        0.74f to 0.16f,
        0.22f to 0.78f,
        0.55f to 0.72f,
    )
    spots.forEachIndexed { i, (x, y) ->
        drawCircle(
            color.copy(alpha = alpha * if (i % 2 == 0) 0.9f else 0.55f),
            radius = if (i % 3 == 0) 1.8.dp.toPx() else 1.1.dp.toPx(),
            center = Offset(size.width * x, size.height * y),
        )
    }
}

private fun DrawScope.drawMilkyWay(color: Color, strength: Float) {
    if (strength <= 0.01f) return
    val path = Path().apply {
        moveTo(0f, size.height * 0.42f)
        quadraticBezierTo(size.width * 0.45f, size.height * 0.08f, size.width, size.height * 0.36f)
    }
    drawPath(
        path,
        color.copy(alpha = 0.42f * strength),
        style = Stroke(width = size.minDimension * 0.16f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawRipples(phase: Float, color: Color) {
    repeat(3) { i ->
        val path = Path()
        val y0 = size.height * (0.28f + i * 0.22f)
        val amp = size.height * 0.14f
        path.moveTo(0f, y0)
        val steps = 28
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val y = y0 + amp * sin(t * 2f * PI.toFloat() + phase * 2f * PI.toFloat() + i).toFloat()
            path.lineTo(size.width * t, y)
        }
        drawPath(
            path,
            color.copy(alpha = 0.4f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Lit portion of the disc. [terminatorDeg] is the position angle of the terminator,
 * degrees east of celestial north (0 faces north, clockwise when east is to the right).
 * The bright limb is opposite that angle. [illumination] is 0..1.
 */
internal fun DrawScope.drawMoonDisc(illumination: Float, terminatorDeg: Float, light: Color, shadow: Color) {
    val radius = size.minDimension * 0.42f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(shadow, radius, center)
    drawCircle(light.copy(alpha = 0.55f), radius, center, style = Stroke(1.2.dp.toPx()))
    val k = illumination.coerceIn(0f, 1f)
    if (k < 0.015f) return
    val bright = ((terminatorDeg % 360f) + 360f) % 360f
    val brightLimb = (bright + 180f) % 360f
    rotate(brightLimb - 90f, center) {
        val cosI = (2f * k - 1f).coerceIn(-1f, 1f)
        val disc = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        val path = Path().apply {
            arcTo(disc, -90f, 180f, false)
            if (abs(cosI) < 0.02f) {
                close()
            } else {
                val rx = radius * abs(cosI)
                val ellipse = Rect(center.x - rx, center.y - radius, center.x + rx, center.y + radius)
                arcTo(ellipse, 90f, if (cosI >= 0f) 180f else -180f, false)
                close()
            }
        }
        clipPath(Path().apply { addOval(disc) }) {
            drawPath(path, light)
        }
    }
}
