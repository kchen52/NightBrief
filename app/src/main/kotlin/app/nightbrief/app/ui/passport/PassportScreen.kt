package app.nightbrief.app.ui.passport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.score.ConstellationProgress
import app.nightbrief.score.ConstellationStreaks
import app.nightbrief.score.Passport
import app.nightbrief.score.StampStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportRoute(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val stamps = remember(current.sessions) { Passport.evaluate(current.sessions) }
    val progress = remember(current.sessions.size) { ConstellationStreaks.progress(current.sessions.size) }
    val earnedIds = stamps.filter { it.earned }.map { it.id }
    LaunchedEffect(earnedIds) {
        vm.markPassportStampsSeen(earnedIds.toSet())
    }
    PassportScreen(stamps, progress, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(
    stamps: List<StampStatus>,
    progress: ConstellationProgress,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sky_passport)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(stringResource(R.string.passport_constellations)) {
                Text(
                    progress.summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                progress.figures.forEach { figure ->
                    Text(
                        figure.name + " · " + if (figure.unlocked) {
                            "${figure.lit} of ${figure.total}"
                        } else {
                            figure.unlockHint.orEmpty()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (figure.unlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    ConstellationFigure(figure)
                    Spacer(Modifier.height(8.dp))
                }
            }
            SectionCard(stringResource(R.string.passport_stamps)) {
                stamps.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { stamp ->
                            StampBadge(stamp, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
