package app.nightbrief.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.gear.GearScreen
import app.nightbrief.app.ui.onboarding.OnboardingScreen
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.sites.SiteEditorScreen
import app.nightbrief.app.ui.sites.SitesScreen
import app.nightbrief.app.ui.tonight.PlannedNightScreen
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.app.ui.week.WeekScreen
import java.time.LocalDate

private object Routes {
    const val Tonight = "tonight"
    const val Week = "week"
    const val Sites = "sites"
    const val Gear = "gear"
    const val Settings = "settings"
    const val SiteEdit = "site/edit?id={id}"
    const val Night = "night/{siteId}/{date}"

    fun siteEdit(id: String?) = if (id == null) "site/edit" else "site/edit?id=$id"
    fun night(siteId: String, date: LocalDate) = "night/$siteId/$date"
}

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.Tonight, "Tonight", Icons.Filled.NightsStay),
    TopDest(Routes.Week, "Week", Icons.Filled.DateRange),
    TopDest(Routes.Sites, "Sites", Icons.Filled.Place),
    TopDest(Routes.Gear, "Gear", Icons.Filled.PhotoCamera),
)

@Composable
fun NightBriefNavHost(vm: AppViewModel, openTonightSignal: Int = 0) {
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

    LaunchedEffect(openTonightSignal) {
        if (openTonightSignal > 0) navController.goTop(Routes.Tonight)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = route == dest.route || (route == null && dest.route == Routes.Tonight),
                            onClick = { navController.goTop(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = Routes.Tonight, modifier = Modifier.fillMaxSize()) {
            composable(Routes.Tonight) {
                TonightScreen(
                    vm,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    contentPadding = innerPadding,
                )
            }
            composable(Routes.Week) {
                WeekScreen(
                    vm,
                    onOpenNight = { siteId, date -> navController.navigate(Routes.night(siteId, date)) },
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
                SettingsScreen(vm, onBack = { navController.popBackStack() })
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
}

private fun NavHostController.goTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
