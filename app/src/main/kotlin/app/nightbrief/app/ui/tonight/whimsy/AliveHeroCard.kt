package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import app.nightbrief.score.MoonFacts
import app.nightbrief.score.NightReport
import java.time.Clock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tonight hero chrome: twilight gradient, Moon, Milky Way, and the one-shot celebrations.
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
            Column(
                Modifier.padding(start = 16.dp, top = 16.dp, end = HeroGutter, bottom = 16.dp),
            ) {
                content()
            }
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
                    .padding(top = 12.dp, end = 12.dp)
                    .size(36.dp),
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
        anim.animateTo(1f, tween(720, easing = LinearEasing))
    }
    Canvas(Modifier.matchParentSize()) {
        val streak = shootingStarStreak(size.width, size.height, anim.value) ?: return@Canvas
        val fade = sin(anim.value * PI).toFloat().coerceIn(0f, 1f)
        drawLine(
            Brush.linearGradient(
                listOf(color.copy(alpha = 0f), color.copy(alpha = 0.9f * fade)),
                start = streak.tail,
                end = streak.head,
            ),
            streak.tail,
            streak.head,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color.copy(alpha = fade), 2.4.dp.toPx(), streak.head)
    }
}

/**
 * One frame of the hero meteor. The head travels in a straight line about 24° down.
 * A short card shortens the run so the angle stays, instead of the streak skimming the top.
 * The tail lies on that same line, behind the head. [t] is 0 at the start and 1 at the end.
 */
internal fun shootingStarStreak(width: Float, height: Float, t: Float): StarStreak? {
    if (width <= 1f || height <= 1f || t <= 0.02f || t >= 0.98f) return null
    // ~24° down. On a short card the run shortens so the angle stays, instead of skimming the top edge.
    val start = Offset(-0.04f * width, 0.04f * height)
    val slope = 0.45f
    val maxDrop = height * 0.42f
    val travelX = (0.92f * width).coerceAtMost(maxDrop / slope)
    val end = Offset(start.x + travelX, start.y + travelX * slope)
    val head = Offset(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val tailLen = (width * 0.18f).coerceAtMost(len * 0.28f)
    return StarStreak(head, Offset(head.x - dx / len * tailLen, head.y - dy / len * tailLen))
}

internal data class StarStreak(val head: Offset, val tail: Offset)

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

/** Keeps the score copy clear of the Moon in the top-end corner. */
private val HeroGutter = 64.dp

private fun DrawScope.drawStars(color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val left = 10.dp.toPx()
    val right = size.width - (HeroGutter + 8.dp).toPx()
    val span = (right - left).coerceAtLeast(1f)
    val alongTop = listOf(0.06f, 0.24f, 0.42f, 0.63f, 0.84f)
    alongTop.forEachIndexed { i, f ->
        drawCircle(
            color.copy(alpha = alpha * if (i % 2 == 0) 0.55f else 0.32f),
            radius = if (i % 2 == 0) 1.15.dp.toPx() else 0.8.dp.toPx(),
            center = Offset(left + span * f, 7.dp.toPx() + if (i % 2 == 0) 0f else 2.5.dp.toPx()),
        )
    }
    drawCircle(color.copy(alpha = alpha * 0.4f), 0.9.dp.toPx(), Offset(6.dp.toPx(), size.height * 0.42f))
    drawCircle(color.copy(alpha = alpha * 0.28f), 0.8.dp.toPx(), Offset(6.dp.toPx(), size.height * 0.68f))
}

/**
 * A wash along the bottom of the card, edge to edge.
 * Peak alpha is 0.10 at a score of 100 and falls to 0 across the band.
 */
private fun DrawScope.drawMilkyWay(color: Color, strength: Float) {
    if (strength <= 0.01f) return
    val peak = (0.10f * strength).coerceIn(0f, 0.12f)
    val right = size.width.coerceAtLeast(1f)
    val mid = size.height - 7.dp.toPx()
    val half = 8.dp.toPx()
    val rise = 3.dp.toPx()
    fun centerY(x: Float): Float {
        val t = if (right <= 1f) 0f else (x / right).coerceIn(0f, 1f)
        val u = 2f * t - 1f
        return mid - rise * (1f - u * u)
    }
    val steps = 24
    val path = Path().apply {
        moveTo(0f, centerY(0f) - half)
        for (s in 1..steps) {
            val x = right * s / steps
            lineTo(x, centerY(x) - half)
        }
        for (s in steps downTo 0) {
            val x = right * s / steps
            lineTo(x, centerY(x) + half)
        }
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = peak),
            1f to Color.Transparent,
            startY = mid - rise - half,
            endY = mid + half,
        ),
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
