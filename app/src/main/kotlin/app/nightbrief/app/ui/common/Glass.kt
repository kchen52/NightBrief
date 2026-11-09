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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassMemberTransition
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

/**
 * Which retained tab the bars sample. [HazeSourceSelection.Behind] (the default) samples every
 * source, which is what a screen composed on its own wants. The host provides one selection for
 * the life of the composition. Its predicate reads the visible route, so a tab change retargets
 * capture without rebuilding the selection and recomposing every glass reader.
 */
val LocalHazeSelection = staticCompositionLocalOf<HazeSourceSelection> { HazeSourceSelection.Behind }

/**
 * Visibility of the retained page this glass sits on. The host provides one holder per page and
 * updates [PageVisibility.visible]. The local value itself does not change, so a tab switch does
 * not count as a new composition-local scope for the retained page.
 *
 * Null means the caller is not inside a retained page (the navigation bar, or a screen composed
 * on its own) and glass may sample.
 */
val LocalPageVisibility = staticCompositionLocalOf<PageVisibility?> { null }

class PageVisibility {
    var visible by mutableStateOf(true)
}

private val Pill = RoundedCornerShape(percent = 50)

fun Modifier.glassSource(state: HazeState?, key: Any? = null): Modifier =
    if (state == null) this else hazeSource(state, key = key)

/**
 * Which glass-backed scrollables are moving. While any is, the bars draw a flat fill and
 * release the source layer. A haze source is the whole viewport, so leaving that layer up
 * recaptures and re-blurs it on every frame of a fling.
 *
 * Each caller passes its own [key]. The gate stays up until every key has reported stopped.
 * The host provides one gate. The default is a fresh idle gate, so a screen composed alone
 * does not flip glass in another composition.
 */
class GlassScrollGate {
    private val active = HashSet<Any>()
    private var heldFor: Any? = null
    private var leftTabs = false

    var moving by mutableStateOf(false)
        private set

    /**
     * True for the frame a tab is becoming visible. Glass stays flat so the source layer is
     * attached on the following frame, not in the same turn as the tap.
     */
    var settling by mutableStateOf(false)
        private set

    fun report(key: Any, scrolling: Boolean) {
        val changed = if (scrolling) active.add(key) else active.remove(key)
        if (changed) moving = active.isNotEmpty()
    }

    /**
     * [route] null means a screen pushed over the tabs. The next tab to show settles.
     * The first route arms the gate without settling, so the cold start is not a flat frame.
     */
    fun holdFor(route: Any?) {
        if (route == null) {
            if (heldFor != null) leftTabs = true
            return
        }
        val switched = heldFor != null && (route != heldFor || leftTabs)
        heldFor = route
        leftTabs = false
        if (switched) settling = true
    }

    fun releaseFlat() {
        settling = false
    }
}

val LocalGlassScrollGate = staticCompositionLocalOf { GlassScrollGate() }

/**
 * Reports [scrolling] for this composition. Call it for every scrollable marked as a
 * [glassSource]. Leaving the composition counts as stopped.
 */
@Composable
fun ReportGlassScroll(scrolling: Boolean) {
    val gate = LocalGlassScrollGate.current
    val key = remember { Any() }
    gate.report(key, scrolling)
    DisposableEffect(gate, key) {
        onDispose { gate.report(key, false) }
    }
}

/** Sources clear when a tab leaves, so the bar never shows the last frame of another tab. */
private fun HazeState.input(selection: HazeSourceSelection) =
    HazeInput.Sources(this, selection = selection, retention = HazeSourceRetention.ClearWhenUnavailable)

/**
 * Night vision draws a flat fill instead: glass speculars and white point add green and blue,
 * which [NightPalette.NightVision] keeps at zero. A moving [LocalGlassScrollGate] does the same,
 * so the source layer is released for the gesture.
 */
@Composable
private fun Modifier.glassPill(state: HazeState?, tint: State<Color>): Modifier {
    val visibility = LocalPageVisibility.current
    val pageVisible = visibility?.visible != false
    val gate = LocalGlassScrollGate.current
    // Hidden pages stay flat and do not subscribe, so a tab change or a fling does not recompose them.
    val settling = if (pageVisible) gate.settling else false
    val moving = if (pageVisible) gate.moving else false
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
    if (state == null || !pageVisible || moving || settling || NightColors.palette == NightPalette.NightVision) {
        return background(NightColors.palette.surface.copy(alpha = 0.92f), Pill).background(tint.value, Pill)
    }
    // None: a fling detaches the glass and puts it back. A geometry morph on every stop would hitch.
    val selection = LocalHazeSelection.current
    return hazeGlass(state.input(selection), style = style, memberTransition = GlassMemberTransition.None)
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

/** Floating tab bar: one glass pill, with the selected tab in a flat capsule inside it. */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, modifier: Modifier = Modifier) {
    val state = LocalHazeState.current
    val tint = rememberUpdatedState(NightColors.palette.surfaceHigh.copy(alpha = 0.45f))
    Row(
        modifier
            .navigationBarsPadding()
            .padding(vertical = NavBarMargin)
            .height(NavBarHeight)
            .glassPill(state, tint)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        items.forEach { item -> GlassNavTab(item) }
    }
}

@Composable
private fun GlassNavTab(item: GlassNavItem) {
    val palette = NightColors.palette
    val capsule by animateColorAsState(
        if (item.selected) palette.primaryContainer.copy(alpha = 0.85f) else Color.Transparent,
        label = "tab-capsule",
    )
    val content = if (item.selected) palette.onPrimaryContainer else palette.textMuted
    Column(
        Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(capsule, Pill)
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
    val visibility = LocalPageVisibility.current
    val pageVisible = visibility?.visible != false
    val gate = LocalGlassScrollGate.current
    val settling = if (pageVisible) gate.settling else false
    val moving = if (pageVisible) gate.moving else false
    val background = MaterialTheme.colorScheme.background
    val style = remember(background) {
        HazeBlurStyle {
            blurRadius(32.dp)
            colorEffects(listOf(HazeColorEffect.tint(background.copy(alpha = 0.7f))))
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0.35f))
        }
    }
    // No source: an opaque bar, matching screens composed outside the tab host.
    // Scrolling: a flat scrim, so the blur is not resampled on every frame.
    if (state == null || !pageVisible || moving || settling) {
        Box(modifier.background(if (state == null) background else background.copy(alpha = 0.92f)))
        return
    }
    val selection = LocalHazeSelection.current
    Box(modifier.hazeBlur(state.input(selection), style = style))
}
