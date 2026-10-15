package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import app.nightbrief.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.passport.NewStampNotice
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.score.ConstellationStreaks
import app.nightbrief.score.Passport
import app.nightbrief.score.SessionPrompt
import app.nightbrief.score.SiteComparison

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonightScreen(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpenPassport: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.briefing.collectAsStateWithLifecycle()
    val selected by vm.selectedSiteId.collectAsStateWithLifecycle()
    val current = state ?: return
    val book = current.sites
    val briefing = ui.briefing
    val siteId = selected?.takeIf { book[it] != null } ?: book.primaryId
    val units = current.resolvedUnits()
    val report = siteId?.let { briefing?.reportFor(it) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tonight)) },
                actions = {
                    IconButton(onClick = { vm.setNightVision(!current.nightVision) }) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (current.nightVision) R.string.night_vision_on else R.string.night_vision_off,
                            ),
                            tint = if (current.nightVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_forecast))
                    }
                    if (report != null) ShareNightPlanButton(report)
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (ui.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                RefreshStatusLine(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            SiteStrip(
                sites = book.primaryFirst().map {
                    SiteChip(it.id, it.name, briefing?.reportFor(it.id)?.scoreValue, it.id == book.primaryId)
                },
                selectedId = siteId,
                onSelect = vm::selectSite,
            )
            ui.error?.let { Banner(it, NightColors.Poor, Modifier.padding(horizontal = 16.dp)) }
            val stamps = remember(current.sessions) { Passport.evaluate(current.sessions) }
            NewStampNotice(
                stamps = stamps,
                seenIds = current.seenPassportStamps,
                onDismiss = vm::markPassportStampsSeen,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (briefing == null || report == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    if (ui.loading) CircularProgressIndicator() else Text(stringResource(R.string.tonight_empty))
                }
            } else {
                val others = briefing.tonight.filter { it.site.id != report.site.id }
                val alternative = SiteComparison.bestAlternative(
                    report, others, current.alternativeThreshold,
                )
                // Morning-after prompt: 06:00–12:00 local, yesterday's night, asked once.
                val zone = report.site.zone
                val today = java.time.LocalDate.now(zone)
                val promptDate = SessionPrompt.morningPromptDate(java.time.LocalTime.now(zone), today)
                if (promptDate != null && report.site.id.let { id ->
                        SessionPrompt.shouldAsk(id, promptDate, current.sessions, current.dismissedSessionPrompts)
                    }
                ) {
                    SessionPromptCard(
                        nightDate = promptDate,
                        score = null,
                        onSave = { rating, note ->
                            vm.logSession(report.site.id, promptDate, null, rating, note, report = report)
                        },
                        onDismiss = { vm.dismissSessionPrompt(report.site.id, promptDate) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                NightDetail(report, alternative, onOpenAlternative = vm::selectSite, units = units)
                val darkerSky by vm.darkerSky.collectAsStateWithLifecycle()
                DarkerSkyCard(
                    site = report.site,
                    state = darkerSky,
                    onSearch = vm::searchDarkerSky,
                    onSave = vm::saveDarkerSky,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onAccessFilter = vm::setDarkerSkyAccessFilter,
                )
                SessionHistoryCard(
                    sessions = current.sessions.filter { it.siteId == report.site.id },
                    siteName = report.site.name,
                    streakLine = current.sessions.takeIf { it.isNotEmpty() }?.let {
                        ConstellationStreaks.progress(it.size).summary
                    },
                    onOpenPassport = onOpenPassport,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Steps through [R.array.refresh_lines] from the top, one line every two seconds, while this stays composed. */
@Composable
internal fun RefreshStatusLine(modifier: Modifier = Modifier) {
    val lines = stringArrayResource(R.array.refresh_lines)
    if (lines.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size) {
        if (lines.size < 2) return@LaunchedEffect
        while (true) {
            delay(REFRESH_LINE_PERIOD_MS)
            index = (index + 1) % lines.size
        }
    }
    Text(
        lines[index.coerceIn(0, lines.lastIndex)],
        modifier = modifier.testTag("refresh-line"),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val REFRESH_LINE_PERIOD_MS = 2_000L
