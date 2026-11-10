package app.nightbrief.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.GlassNavBar
import app.nightbrief.app.ui.common.GlassNavItem
import app.nightbrief.app.ui.common.GlassScrollGate
import app.nightbrief.app.ui.common.LocalGlassScrollGate
import app.nightbrief.app.ui.common.LocalHazeSelection
import app.nightbrief.app.ui.common.LocalPageVisibility
import app.nightbrief.app.ui.common.PageVisibility
import app.nightbrief.app.ui.common.LocalHazeState
import dev.chrisbanes.haze.HazeSourceSelection
import app.nightbrief.app.ui.common.glassNavBarPadding
import dev.chrisbanes.haze.rememberHazeState
import app.nightbrief.app.ui.gear.GearScreen
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.passport.PassportRoute
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.sites.SiteEditorScreen
import app.nightbrief.app.ui.sites.SitesScreen
import app.nightbrief.app.ui.tonight.PlannedNightScreen
import app.nightbrief.app.ui.events.EventsScreen
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.app.ui.week.WeekScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private object Routes {
    const val Tonight = "tonight"
    const val Week = "week"
    const val Sites = "sites"
    const val Gear = "gear"
    const val Settings = "settings"
    const val Passport = "passport"
    const val Events = "events"
    const val SiteEdit = "site/edit?id={id}"
    const val Night = "night/{siteId}/{date}"

    fun siteEdit(id: String?) = if (id == null) "site/edit" else "site/edit?id=$id"
    fun night(siteId: String, date: LocalDate) = "night/$siteId/$date"
}

private data class TopDest(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.Tonight, R.string.nav_tonight, Icons.Filled.NightsStay),
    TopDest(Routes.Week, R.string.nav_week, Icons.Filled.DateRange),
    TopDest(Routes.Sites, R.string.nav_sites, Icons.Filled.Place),
    TopDest(Routes.Gear, R.string.nav_gear, Icons.Filled.PhotoCamera),
)

private val tabRoutes = topDestinations.map { it.route }.toSet()

/**
 * Tab changes are instant. The host default fades for 700 ms and keeps both
 * destinations composed, so leaving Tonight crossfades that whole column while
 * it is still the glass source. Pushed screens keep a short fade.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.switchingTabs(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in tabRoutes && to in tabRoutes
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (switchingTabs()) EnterTransition.None else fadeIn(tween(STACK_FADE_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (switchingTabs()) ExitTransition.None else fadeOut(tween(STACK_FADE_MS))

private const val STACK_FADE_MS = 180

@Composable
fun NightBriefNavHost(vm: AppViewModel, openTonightSignal: Int = 0) {
    val form = rememberWindowForm()
    val state by vm.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    if (!current.onboardingComplete) {
        OnboardingScreen(vm)
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == null || route in tabRoutes
    val rail = showBar && form.navigation == WindowForm.Navigation.Rail

    val hazeState = rememberHazeState()
    val glassScroll = remember { GlassScrollGate() }
    val activeTab = if (showBar) route ?: Routes.Tonight else null
    val visibleRoute = remember { VisibleRoute() }
    // After this composition, so the write is not a snapshot change mid-pass. Haze reads it
    // when it next resolves sources. Glass is flat for that same frame.
    SideEffect { visibleRoute.route = activeTab }
    // One predicate for the life of the host. It reads [visibleRoute], which haze observes,
    // so a tab change retargets capture without a new selection object.
    val hazeSelection = remember {
        HazeSourceSelection.Behind.where { meta ->
            val route = visibleRoute.route
            route == null || meta.key == route
        }
    }
    glassScroll.holdFor(activeTab)

    LaunchedEffect(openTonightSignal) {
        if (openTonightSignal > 0) {
            // NavHost installs the graph after this effect is first scheduled.
            navController.currentBackStackEntryFlow.first()
            navController.showTonight(glassScroll)
        }
    }
    val bottomBar = showBar && !rail
    val innerPadding = if (bottomBar) glassNavBarPadding() else PaddingValues()
    val bottomPad = innerPadding.calculateBottomPadding()
    val openSettings = remember(navController) { { navController.navigate(Routes.Settings) } }
    val openPassport = remember(navController) { { navController.navigate(Routes.Passport) } }
    val openNight = remember(navController) {
        { siteId: String, date: LocalDate -> navController.navigate(Routes.night(siteId, date)) }
    }
    val openEvents = remember(navController) { { navController.navigate(Routes.Events) } }
    val editSite = remember(navController) { { id: String? -> navController.navigate(Routes.siteEdit(id)) } }
    // The tab body stays composed after the first visit. Navigation still owns the back stack,
    // but switching back does not build Tonight again.
    val tonight = remember(vm, openSettings, openPassport) {
        TabContent(
            movableContentOf { bottom: Dp ->
                TonightScreen(
                    vm,
                    openSettings,
                    PaddingValues(bottom = bottom),
                    openPassport,
                    sourceKey = Routes.Tonight,
                )
            },
        )
    }
    val week = remember(vm, openNight, openEvents) {
        TabContent(
            movableContentOf { bottom: Dp ->
                WeekScreen(vm, openNight, openEvents, PaddingValues(bottom = bottom), sourceKey = Routes.Week)
            },
        )
    }
    val sites = remember(vm, editSite) {
        TabContent(movableContentOf { bottom: Dp -> SitesScreen(vm, editSite, PaddingValues(bottom = bottom)) })
    }
    val gear = remember(vm) {
        TabContent(movableContentOf { bottom: Dp -> GearScreen(vm, PaddingValues(bottom = bottom)) })
    }
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalGlassScrollGate provides glassScroll,
        LocalHazeSelection provides hazeSelection,
    ) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
        if (rail) {
            NavigationRail(Modifier.testTag("nav-rail")) {
                topDestinations.forEach { dest ->
                    NavigationRailItem(
                        selected = route == dest.route || (route == null && dest.route == Routes.Tonight),
                        onClick = { navController.goTop(dest.route, glassScroll) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .tabSwipe(enabled = activeTab != null) { step ->
                    val next = topDestinations.getOrNull(topDestinations.indexOfFirst { it.route == activeTab } + step)
                    if (next != null) navController.goTop(next.route, glassScroll)
                },
        ) {
        NavHost(
            navController,
            startDestination = Routes.Tonight,
            // Tab bodies live in RetainedTabs. A full-size host here is measured on every
            // switch for an empty destination, which is where the warm-switch time went.
            modifier = if (activeTab != null) Modifier.size(0.dp) else Modifier.fillMaxSize(),
            enterTransition = { enter() },
            exitTransition = { exit() },
            popEnterTransition = { enter() },
            popExitTransition = { exit() },
        ) {
            // Tab screens live in [RetainedTabs]. These entries only keep the back stack.
            composable(Routes.Tonight) {}
            composable(Routes.Week) {}
            composable(Routes.Sites) {}
            composable(Routes.Gear) {}
            composable(Routes.Settings) {
                SettingsScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onOpenPassport = { navController.navigate(Routes.Passport) },
                )
            }
            composable(Routes.Passport) {
                PassportRoute(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.Events) {
                EventsScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onOpenNight = { date ->
                        val siteId = vm.selectedSiteId.value ?: vm.state.value?.sites?.primaryId
                        if (siteId != null) navController.navigate(Routes.night(siteId, date))
                    },
                )
            }
            composable(
                route = Routes.SiteEdit,
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                SiteEditorScreen(
                    vm,
                    siteId = entry.arguments?.getString("id"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Night,
                arguments = listOf(
                    navArgument("siteId") { type = NavType.StringType },
                    navArgument("date") { type = NavType.StringType },
                ),
            ) { entry ->
                val siteId = entry.arguments?.getString("siteId").orEmpty()
                val date = runCatching { LocalDate.parse(entry.arguments?.getString("date")) }.getOrNull()
                if (date == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    PlannedNightScreen(
                        vm,
                        siteId,
                        date,
                        onBack = { navController.popBackStack() },
                        onSelectDate = { newDate ->
                            navController.navigate(Routes.night(siteId, newDate)) {
                                popUpTo(Routes.Night) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
        RetainedTabs(activeTab, bottomPad, glassScroll, tonight, week, sites, gear)
        }
        }
        if (bottomBar) {
            GlassNavBar(
                items = topDestinations.map { dest ->
                    GlassNavItem(
                        label = stringResource(dest.label),
                        icon = dest.icon,
                        selected = route == dest.route || (route == null && dest.route == Routes.Tonight),
                        onClick = { navController.goTop(dest.route, glassScroll) },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).testTag("nav-bar"),
            )
        }
    }
    }
}

/**
 * Tabs the user has opened stay measured. The visible one is placed on screen; the others
 * stay offscreen with their semantics cleared, so a return trip does not rebuild the tree.
 * [active] null covers a pushed screen: the pages stay warm and the layout reports no size,
 * so the screen on the back stack receives touches.
 *
 * Hidden pages park a full width *and* height up and left. Hit testing follows placed
 * bounds without clipping to this layout, and a rail narrows the content area by its own
 * width, so a page parked at `(-width, 0)` still reaches across the rail and steals its
 * taps. No touch has a negative window y, so `(-width, -height)` is out of reach.
 */
@Composable
private fun RetainedTabs(
    active: String?,
    bottom: Dp,
    glass: GlassScrollGate,
    tonight: TabContent,
    week: TabContent,
    sites: TabContent,
    gear: TabContent,
) {
    val seen = remember { mutableStateListOf(Routes.Tonight) }
    val order = listOf(Routes.Tonight, Routes.Week, Routes.Sites, Routes.Gear)
    val shown = order.filter { it == active || it in seen }
    SideEffect {
        if (active != null && active !in seen) seen.add(active)
    }
    LaunchedEffect(active) {
        if (active != null) {
            withFrameNanos { }
            glass.releaseFlat()
        }
    }
    Layout(
        content = {
            shown.forEach { route ->
                RetainedPage(visible = route == active) {
                    when (route) {
                        Routes.Tonight -> RetainedTab(bottom, tonight)
                        Routes.Week -> RetainedTab(bottom, week)
                        Routes.Sites -> RetainedTab(bottom, sites)
                        Routes.Gear -> RetainedTab(bottom, gear)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val childConstraints = if (width == Constraints.Infinity || height == Constraints.Infinity) {
            constraints
        } else {
            Constraints.fixed(width, height)
        }
        val placeables = measurables.map { it.measure(childConstraints) }
        if (active == null || width == Constraints.Infinity || height == Constraints.Infinity) {
            layout(0, 0) {
                placeables.forEach { it.place(-width.coerceAtLeast(0), -height.coerceAtLeast(0)) }
            }
        } else {
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    if (shown[index] == active) {
                        placeable.place(0, 0)
                    } else {
                        placeable.place(-width, -height)
                    }
                }
            }
        }
    }
}

@Composable
private fun RetainedPage(visible: Boolean, content: @Composable () -> Unit) {
    val visibility = remember { PageVisibility() }
    visibility.visible = visible
    CompositionLocalProvider(LocalPageVisibility provides visibility) {
        Box(
            Modifier
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .drawWithContent { if (visible) drawContent() },
        ) {
            content()
        }
    }
}

/**
 * Skips when [bottom] and [content] are unchanged, so a tab switch does not recompose a page
 * that is already retained. [content] is [Stable], which a composable lambda parameter is not.
 */
@Composable
private fun RetainedTab(bottom: Dp, content: TabContent) {
    content.render(bottom)
}

@Stable
private class TabContent(val render: @Composable (Dp) -> Unit)

private class VisibleRoute {
    var route by mutableStateOf<String?>(null)
}

private fun NavHostController.goTop(route: String, glass: GlassScrollGate) {
    glass.holdFor(route)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens Tonight and drops whatever was pushed on top of it.
 *
 * [goTop] saves that nested stack and restores it, so a digest tap while Settings
 * is showing would put Settings straight back. `saveState` and `restoreState` stay
 * off here so the notification lands on Tonight itself.
 */
private fun NavHostController.showTonight(glass: GlassScrollGate) {
    glass.holdFor(Routes.Tonight)
    navigate(Routes.Tonight) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
