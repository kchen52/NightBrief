package app.nightbrief.app.ui.common

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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

@Composable
private fun Modifier.glassPill(state: HazeState?, tint: State<Color>): Modifier {
    if (state == null) return background(tint.value, Pill)
    val style = remember(tint) {
        GlassStyle.regular.then {
            shape(Pill)
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

/**
 * Floating tab bar. Each tab is its own glass member; they sit closer than the container's
 * merge distance, so they render as one pill and the selected tab keeps its own tint.
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, modifier: Modifier = Modifier) {
    val state = LocalHazeState.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(vertical = NavBarMargin)
            .hazeGlassContainer(mergeDistance = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item -> GlassNavTab(item, state) }
    }
}

@Composable
private fun GlassNavTab(item: GlassNavItem, state: HazeState?) {
    val palette = NightColors.palette
    val tint = animateColorAsState(
        if (item.selected) palette.primaryContainer.copy(alpha = 0.78f) else palette.surfaceHigh.copy(alpha = 0.42f),
        label = "tab-tint",
    )
    val content = if (item.selected) palette.onPrimaryContainer else palette.textMuted
    Column(
        Modifier
            .height(NavBarHeight)
            .width(80.dp)
            .glassPill(state, tint)
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
            blurRadius(24.dp)
            colorEffects(listOf(HazeColorEffect.tint(background.copy(alpha = 0.55f))))
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0.35f))
        }
    }
    Box(modifier.hazeBlur(state.input(), style = style))
}
