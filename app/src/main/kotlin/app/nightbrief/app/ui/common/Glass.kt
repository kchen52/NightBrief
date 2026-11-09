package app.nightbrief.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import app.nightbrief.app.ui.theme.NightPalette
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nightbrief.app.ui.theme.NightColors
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.hazeSource

/**
 * Content that glass bars sample. Null outside the tab host: glass then draws as a flat
 * translucent fill, so screens still render in tests that compose them alone.
 *
 * An effect cannot sample an ancestor source, so a screen marks its scrolling content with
 * [glassSource] instead of the host marking the whole NavHost.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private val Pill = RoundedCornerShape(percent = 50)

fun Modifier.glassSource(state: HazeState?): Modifier = if (state == null) this else hazeSource(state)

/** Sources clear when a tab leaves, so the bar never shows the last frame of another tab. */
private fun HazeState.input() = HazeInput.Sources(this, retention = HazeSourceRetention.ClearWhenUnavailable)

/**
 * Night vision draws a flat fill instead: glass speculars and white point add green and blue,
 * which [NightPalette.NightVision] keeps at zero.
 */
@Composable
private fun Modifier.glassPill(state: HazeState?, tint: State<Color>): Modifier {
    if (state == null || NightColors.palette == NightPalette.NightVision) {
        return background(NightColors.palette.surface.copy(alpha = 0.92f), Pill).background(tint.value, Pill)
    }
    val style = remember(tint) {
        // The app is always dark, but Regular picks its light response on a light-mode device.
        GlassStyle.regular.then {
            shape(Pill)
            specularIntensity(0.38f)
            edgeShadow(Color.Black.copy(alpha = 0.32f))
            ambientResponse(0.08f)
            contrast(0.08f)
            whitePoint(-0.22f)
            chromaMultiplier(1.1f)
            tint(tint.value)
        }
    }
    return hazeGlass(state.input(), style = style)
}

/** Fixed height of [GlassNavBar], without the system navigation bar under it. */
private val NavBarHeight = 64.dp
private val NavBarMargin = 12.dp

/** Bottom padding a tab needs so its last item can scroll clear of [GlassNavBar]. */
@Composable
fun glassNavBarPadding(): PaddingValues = PaddingValues(
    bottom = NavBarHeight + NavBarMargin * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
)

data class GlassNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private val NavTabWidth = 80.dp

/**
 * Floating tab bar: one glass pill, with the selected tab in a flat capsule inside it. When the
 * selection changes the capsule flows to the new tab like a droplet (see [TabDroplet]).
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, modifier: Modifier = Modifier) {
    val state = LocalHazeState.current
    val tint = rememberUpdatedState(NightColors.palette.surfaceHigh.copy(alpha = 0.45f))
    val selected = items.indexOfFirst { it.selected }
    val droplet = remember { TabDroplet(selected.coerceAtLeast(0)) }
    LaunchedEffect(selected) {
        if (selected >= 0) droplet.flowTo(selected)
    }
    val dropletColor by animateColorAsState(
        if (selected >= 0) NightColors.palette.primaryContainer.copy(alpha = 0.85f) else Color.Transparent,
        label = "tab-droplet",
    )
    Row(
        modifier
            .navigationBarsPadding()
            .padding(vertical = NavBarMargin)
            .height(NavBarHeight)
            .glassPill(state, tint)
            .padding(4.dp)
            .drawBehind { drawTabDroplet(droplet, NavTabWidth.toPx(), dropletColor) }
            .selectableGroup(),
    ) {
        items.forEach { item -> GlassNavTab(item) }
    }
}

/**
 * The selected-tab capsule, in tab widths: tab `i` spans `i..i + 1`. When it moves, the edge
 * on the side it travels toward springs ahead and the far edge follows on a softer spring, so
 * the capsule stretches out, thins in the middle, and snaps back with a small wobble.
 */
@Stable
private class TabDroplet(index: Int) {
    val start = Animatable(index.toFloat())
    val end = Animatable(index + 1f)
    var forward by mutableStateOf(true)
        private set

    suspend fun flowTo(index: Int) = coroutineScope {
        val target = index.toFloat()
        if (start.targetValue == target && end.targetValue == target + 1f) return@coroutineScope
        forward = target > start.value
        val (lead, trail) = if (forward) end to start else start to end
        val leadTarget = if (forward) target + 1f else target
        val trailTarget = if (forward) target else target + 1f
        launch { lead.animateTo(leadTarget, DropletLead) }
        launch { trail.animateTo(trailTarget, DropletTrail) }
    }
}

private val DropletLead = spring<Float>(dampingRatio = 0.55f, stiffness = 520f)
private val DropletTrail = spring<Float>(dampingRatio = 0.72f, stiffness = 170f)

/**
 * A resting droplet is a plain capsule. A stretched one is a full-size head at the leading
 * edge, a smaller tail at the trailing edge, and a bridge whose sides pinch in between them.
 * The three are unioned so the translucent colour is not drawn twice where they overlap.
 */
private fun DrawScope.drawTabDroplet(droplet: TabDroplet, tabWidth: Float, color: Color) {
    if (color.alpha == 0f) return
    val a = droplet.start.value * tabWidth
    val b = droplet.end.value * tabWidth
    val left = min(a, b)
    val right = max(a, b)
    val h = size.height
    val stretch = ((right - left) / tabWidth - 1f).coerceIn(0f, 1f)
    if (stretch < 0.02f) {
        drawRoundRect(color, Offset(left, 0f), Size(right - left, h), CornerRadius(h / 2))
        return
    }
    val headLeft = if (droplet.forward) right - tabWidth else left
    val tailH = h * (1f - 0.3f * stretch)
    val tailW = tabWidth * (1f - 0.25f * stretch)
    val tailLeft = if (droplet.forward) left else right - tailW
    val tailTop = (h - tailH) / 2
    val head = Path().apply { addRoundRect(RoundRect(headLeft, 0f, headLeft + tabWidth, h, CornerRadius(h / 2))) }
    val tail = Path().apply {
        addRoundRect(RoundRect(tailLeft, tailTop, tailLeft + tailW, tailTop + tailH, CornerRadius(tailH / 2)))
    }
    val headX = headLeft + tabWidth / 2
    val tailX = tailLeft + tailW / 2
    val midX = (headX + tailX) / 2
    val pinch = h * 0.55f * stretch
    val bridge = Path().apply {
        moveTo(tailX, tailTop)
        quadraticTo(midX, pinch, headX, 0f)
        lineTo(headX, h)
        quadraticTo(midX, h - pinch, tailX, tailTop + tailH)
        close()
    }
    val blob = Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, head, tail), bridge)
    drawPath(blob, color)
}

@Composable
private fun GlassNavTab(item: GlassNavItem) {
    val palette = NightColors.palette
    val content by animateColorAsState(
        if (item.selected) palette.onPrimaryContainer else palette.textMuted,
        label = "tab-content",
    )
    Column(
        Modifier
            .fillMaxHeight()
            .width(NavTabWidth)
            .selectable(selected = item.selected, onClick = item.onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(item.label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/**
 * Toolbar actions as glass circles. They sit inside the merge distance, so they render as one
 * capsule. [highlighted] tints one action, for example night vision while it is on.
 */
@Composable
fun GlassActionCluster(
    modifier: Modifier = Modifier,
    content: @Composable GlassActionScope.() -> Unit,
) {
    val state = LocalHazeState.current
    Row(
        modifier.padding(end = 8.dp).hazeGlassContainer(mergeDistance = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassActionScope(this, state).content()
    }
}

class GlassActionScope internal constructor(rowScope: RowScope, private val state: HazeState?) : RowScope by rowScope {
    @Composable
    fun Action(highlighted: Boolean = false, content: @Composable () -> Unit) {
        val palette = NightColors.palette
        val tint = animateColorAsState(
            if (highlighted) palette.primaryContainer.copy(alpha = 0.8f) else palette.surfaceHigh.copy(alpha = 0.4f),
            label = "action-tint",
        )
        Box(Modifier.size(44.dp).glassPill(state, tint), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Frosted backdrop for a top bar that content scrolls under. Draw it as a sibling behind the bar,
 * not on the bar: glass actions inside the bar would otherwise nest inside this effect.
 */
@Composable
fun GlassTopBarBackdrop(modifier: Modifier = Modifier) {
    val state = LocalHazeState.current
    val background = MaterialTheme.colorScheme.background
    if (state == null) {
        Box(modifier.background(background))
        return
    }
    val style = remember(background) {
        HazeBlurStyle {
            blurRadius(32.dp)
            colorEffects(listOf(HazeColorEffect.tint(background.copy(alpha = 0.7f))))
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0.35f))
        }
    }
    Box(modifier.hazeBlur(state.input(), style = style))
}
