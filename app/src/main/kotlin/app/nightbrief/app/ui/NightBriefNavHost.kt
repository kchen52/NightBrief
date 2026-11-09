package app.nightbrief.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import app.nightbrief.app.ui.common.LocalHazeState
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
    val showBar = route == null || route in topDestinations.map { it.route }
    val rail = showBar && form.navigation == WindowForm.Navigation.Rail

    LaunchedEffect(openTonightSignal) {
        if (openTonightSignal > 0) {
            // NavHost installs the graph after this effect is first scheduled.
            navController.currentBackStackEntryFlow.first()
            navController.showTonight()
        }
    }

    val hazeState = rememberHazeState()
    val bottomBar = showBar && !rail
    val innerPadding = if (bottomBar) glassNavBarPadding() else PaddingValues()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
        if (rail) {
            NavigationRail(Modifier.testTag("nav-rail")) {
                topDestinations.forEach { dest ->
                    NavigationRailItem(
                        selected = route == dest.route || (route == null && dest.route == Routes.Tonight),
                        onClick = { navController.goTop(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        }
        NavHost(
            navController,
            startDestination = Routes.Tonight,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .tabSwipe(enabled = showBar) { step ->
                    topDestinations.getOrNull(tabIndex(route) + step)?.let { navController.goTop(it.route) }
                },
            enterTransition = {
                when (val direction = tabDirection(initialState, targetState)) {
                    0 -> fadeIn(tween(700))
                    else -> slideInHorizontally(tween(TabSlideMillis)) { direction * it / 4 } +
                        fadeIn(tween(TabSlideMillis))
                }
            },
            exitTransition = {
                when (val direction = tabDirection(initialState, targetState)) {
                    0 -> fadeOut(tween(700))
                    else -> slideOutHorizontally(tween(TabSlideMillis)) { -direction * it / 4 } +
                        fadeOut(tween(TabSlideMillis))
                }
            },
        ) {
            composable(Routes.Tonight) {
                TonightScreen(
                    vm,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    contentPadding = innerPadding,
                    onOpenPassport = { navController.navigate(Routes.Passport) },
                )
            }
            composable(Routes.Week) {
                WeekScreen(
                    vm,
                    onOpenNight = { siteId, date -> navController.navigate(Routes.night(siteId, date)) },
                    onOpenEvents = { navController.navigate(Routes.Events) },
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.Sites) {
                SitesScreen(
                    vm,
                    onEdit = { id -> navController.navigate(Routes.siteEdit(id)) },
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.Gear) {
                GearScreen(vm, contentPadding = innerPadding)
            }
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
        }
        if (bottomBar) {
            GlassNavBar(
                items = topDestinations.map { dest ->
                    GlassNavItem(
                        label = stringResource(dest.label),
                        icon = dest.icon,
                        selected = route == dest.route || (route == null && dest.route == Routes.Tonight),
                        onClick = { navController.goTop(dest.route) },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).testTag("nav-bar"),
            )
        }
    }
    }
}

private const val TabSlideMillis = 300

/** Position in [topDestinations]; null is the start destination, and -1 is a pushed screen. */
private fun tabIndex(route: String?): Int =
    if (route == null) 0 else topDestinations.indexOfFirst { it.route == route }

/** 1 when moving to a tab on the right, -1 to the left, 0 when either end is not a tab. */
private fun tabDirection(from: NavBackStackEntry, to: NavBackStackEntry): Int {
    val start = topDestinations.indexOfFirst { it.route == from.destination.route }
    val end = topDestinations.indexOfFirst { it.route == to.destination.route }
    if (start < 0 || end < 0 || start == end) return 0
    return if (end > start) 1 else -1
}

private fun NavHostController.goTop(route: String) {
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
private fun NavHostController.showTonight() {
    navigate(Routes.Tonight) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}
