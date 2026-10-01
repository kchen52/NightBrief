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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Format
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.weather.OpenMeteoClient
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
    var report by remember(siteId, date) { mutableStateOf<NightReport?>(null) }
    var loaded by remember(siteId, date) { mutableStateOf(false) }
    LaunchedEffect(siteId, date) {
        report = vm.planNight(siteId, date)
        loaded = true
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val site = state?.sites?.get(siteId)
    val tonight = site?.let { NightPlanner.tonight(Instant.now(), it.zone) }
    val lastDate = tonight?.plusDays(OpenMeteoClient.DEFAULT_FORECAST_DAYS - 1L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(report?.let { "${it.site.name} · ${Format.dayName(date)}" } ?: Format.dayName(date)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(
                        onClick = { onSelectDate(date.plusDays(-1)) },
                        enabled = tonight != null && date.isAfter(tonight),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous night")
                    }
                    IconButton(
                        onClick = { onSelectDate(date.plusDays(1)) },
                        enabled = lastDate != null && date.isBefore(lastDate),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next night")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        val r = report
        when {
            !loaded -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r == null -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("That night isn't available for this site.")
            }
            else -> Box(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
                NightDetail(r, alternative = null, onOpenAlternative = {})
            }
        }
    }
}
