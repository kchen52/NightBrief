package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Format
import app.nightbrief.score.NightReport
import java.time.LocalDate

/** Planning mode: the full Tonight breakdown for any saved site and date within the forecast horizon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedNightScreen(vm: AppViewModel, siteId: String, date: LocalDate, onBack: () -> Unit) {
    var report by remember { mutableStateOf<NightReport?>(null) }
    LaunchedEffect(siteId, date) { report = vm.planNight(siteId, date) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(report?.let { "${it.site.name} · ${Format.dayName(date)}" } ?: Format.dayName(date)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        val r = report
        if (r == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Box(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
                NightDetail(r, alternative = null, onOpenAlternative = {})
            }
        }
    }
}
