package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Format
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.UnitSystem
import java.time.Instant
import java.time.LocalDate

/** Planning mode: the full Tonight breakdown for any saved site and date within the forecast horizon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedNightScreen(
    vm: AppViewModel,
    siteId: String,
    date: LocalDate,
    onBack: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val site = state?.sites?.get(siteId)
    val tonight = site?.let { NightPlanner.tonight(Instant.now(), it.zone) }
    var report by remember(siteId, date) { mutableStateOf<NightReport?>(null) }
    var loaded by remember(siteId, date) { mutableStateOf(false) }
    // Settings start empty. Planning before they arrive would stick on "unavailable"
    // and the share action would never show.
    LaunchedEffect(siteId, date, state != null, site) {
        if (state == null) return@LaunchedEffect
        if (site == null) {
            report = null
            loaded = true
            return@LaunchedEffect
        }
        report = vm.planNight(siteId, date)
        loaded = true
    }
    // The app bar is a scaffold slot. Reading the night here recomposes that slot once planning finishes.
    val night = report

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        night?.let { stringResource(R.string.planner_title, it.site.name, Format.dayName(date)) }
                            ?: Format.dayName(date),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(
                        onClick = { onSelectDate(date.plusDays(-1)) },
                        enabled = tonight != null && NightPlanner.canStepToPreviousNight(date, tonight),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_night))
                    }
                    IconButton(
                        onClick = { onSelectDate(date.plusDays(1)) },
                        enabled = tonight != null && NightPlanner.canStepToNextNight(date, tonight),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_night))
                    }
                    if (night != null) ShareNightPlanButton(night)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        val r = report
        when {
            !loaded -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r == null -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.planner_unavailable))
            }
            else -> Box(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
                NightDetail(
                    r,
                    alternative = null,
                    onOpenAlternative = {},
                    units = state?.resolvedUnits() ?: UnitSystem.METRIC,
                )
            }
        }
    }
}
