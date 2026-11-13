package app.nightbrief.app.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.nightbrief.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.LocalHazeState
import app.nightbrief.app.ui.common.ReportGlassScroll
import app.nightbrief.app.ui.common.glassSource
import app.nightbrief.app.ui.rememberWindowForm
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.ScorePill
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.tonight.StaleForecastBanner
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.astro.Darkness
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.NightNames
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.weather.ForecastStatus
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    vm: AppViewModel,
    onOpenNight: (String, LocalDate) -> Unit,
    onOpenEvents: () -> Unit,
    contentPadding: PaddingValues,
    sourceKey: Any? = null,
) {
    val window = rememberWindowForm()
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.briefing.collectAsStateWithLifecycle()
    val selected by vm.selectedSiteId.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    val siteId = selected?.takeIf { book[it] != null } ?: book.primaryId ?: return
    val briefing = ui.briefing
    val outlook = briefing?.outlookFor(siteId)
    val report = briefing?.reportFor(siteId)
    val bigNightThreshold = state?.bigNightThreshold ?: BigNightAlerts.threshold

    val bottomInset = contentPadding.calculateBottomPadding()
    val stripSites = book.primaryFirst().map { site ->
        val best = briefing?.outlookFor(site.id)?.best
        SiteChip(site.id, site.name, best?.score, site.id == book.primaryId, bestNight = best?.date)
    }
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { WeekTitle(outlook) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        if (window.weekColumns > 1) {
            val gridState = rememberLazyGridState()
            ReportGlassScroll(gridState.isScrollInProgress)
            LazyVerticalGrid(
                columns = GridCells.Fixed(window.weekColumns),
                state = gridState,
                modifier = Modifier.padding(inner).fillMaxSize().glassSource(LocalHazeState.current, sourceKey).testTag("week-grid"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
            ) {
                if (ui.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (stripSites.size > 1) {
                        SiteStrip(
                            sites = stripSites,
                            selectedId = siteId,
                            onSelect = vm::selectSite,
                            summarizeBestNight = true,
                        )
                    }
                }
                val tonight = report
                if (tonight != null && tonight.forecastStatus == ForecastStatus.STALE) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StaleForecastBanner(tonight)
                    }
                }
                ui.error?.let { message ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        WeekErrorBanner(message, ui.loading) { vm.refresh(force = true) }
                    }
                }
                if (outlook == null && ui.error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        WeekUnavailable(ui.loading) { vm.refresh(force = true) }
                    }
                }
                if (outlook != null) {
                    val nightNames = NightNames.titles(outlook.nights, bigNightThreshold)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BestNightCard(outlook)
                    }
                    gridItems(outlook.nights, key = { it.date.toString() }) { night ->
                        NightRow(
                            night = night,
                            isBest = night == outlook.best,
                            modifier = Modifier,
                            onClick = { onOpenNight(siteId, night.date) },
                            nightName = nightNames[night.date],
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedButton(
                            onClick = onOpenEvents,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.sky_events_button)) }
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            ReportGlassScroll(listState.isScrollInProgress)
            LazyColumn(
                Modifier.padding(inner).fillMaxSize().glassSource(LocalHazeState.current, sourceKey),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp + bottomInset),
            ) {
                if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    if (stripSites.size > 1) {
                        SiteStrip(
                            sites = stripSites,
                            selectedId = siteId,
                            onSelect = vm::selectSite,
                            summarizeBestNight = true,
                        )
                    }
                }
                val tonight = report
                if (tonight != null && tonight.forecastStatus == ForecastStatus.STALE) {
                    item {
                        StaleForecastBanner(tonight, Modifier.padding(horizontal = 16.dp))
                    }
                }
                ui.error?.let { message ->
                    item {
                        WeekErrorBanner(message, ui.loading) { vm.refresh(force = true) }
                    }
                }
                if (outlook == null && ui.error == null) {
                    item {
                        WeekUnavailable(ui.loading) { vm.refresh(force = true) }
                    }
                }
                if (outlook != null) {
                    val nightNames = NightNames.titles(outlook.nights, bigNightThreshold)
                    item {
                        BestNightCard(outlook, Modifier.padding(horizontal = 16.dp))
                    }
                    items(outlook.nights, key = { it.date.toString() }) { night ->
                        NightRow(
                            night = night,
                            isBest = night == outlook.best,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = { onOpenNight(siteId, night.date) },
                            nightName = nightNames[night.date],
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = onOpenEvents,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) { Text(stringResource(R.string.sky_events_button)) }
                    }
                }
            }
        }
    }
}

/**
 * A failed refresh must not blank the page: the banner sits above any cached outlook and Retry
 * stays disabled while a forced refresh is already running.
 */
@Composable
private fun WeekErrorBanner(message: String, loading: Boolean, onRetry: () -> Unit) {
    Banner(
        text = message,
        color = NightColors.Poor,
        modifier = Modifier.padding(horizontal = 16.dp),
        action = {
            TextButton(onClick = onRetry, enabled = !loading) {
                Text(stringResource(R.string.retry))
            }
        },
    )
}

@Composable
private fun WeekUnavailable(loading: Boolean, onRetry: () -> Unit) {
    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(if (loading) R.string.week_loading else R.string.week_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRetry,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
internal fun WeekTitle(outlook: WeeklyOutlook?) {
    val nights = outlook?.nights.orEmpty()
    Column {
        Text(stringResource(R.string.week_title))
        if (nights.isNotEmpty()) {
            Text(
                Format.daySpan(nights.first().date, nights.last().date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Names the best night. The score sits in a pill, the same size as a row, so the page reads as a week. */
@Composable
internal fun BestNightCard(outlook: WeeklyOutlook, modifier: Modifier = Modifier) {
    val best = outlook.best
    val missing = outlook.nights.isNotEmpty() && outlook.nights.all { it.forecastMissing }
    SectionCard(modifier = modifier) {
        if (best == null || missing) {
            Text(outlook.headline(), style = MaterialTheme.typography.titleLarge)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.week_best_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Format.dayName(best.date),
                        style = MaterialTheme.typography.titleLarge,
                        color = NightColors.Amber,
                    )
                }
                ScorePill(best.score)
            }
        }
        Text(
            stringResource(R.string.week_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NightRow(
    night: OutlookNight,
    isBest: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    nightName: String? = null,
) {
    SectionCard(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("week-night"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(96.dp)) {
                Text(
                    Format.shortDayName(night.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) NightColors.Amber else MaterialTheme.colorScheme.onSurface,
                )
                Text(Format.dayMonth(night.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { (night.score ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .clearAndSetSemantics { },
                    color = NightColors.forScore(night.score),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                val moon = stringResource(
                    R.string.week_moon,
                    moonEmoji(night.moonIllumination),
                    Format.percent(night.moonIllumination),
                )
                val label = when {
                    night.darkness == Darkness.NONE -> stringResource(R.string.week_no_darkness)
                    night.forecastMissing -> stringResource(R.string.week_forecast_unavailable)
                    night.coverage == ForecastCoverage.NONE -> stringResource(R.string.week_beyond_forecast)
                    else -> night.band?.label ?: ""
                }
                val shownLabel = if (night.estimated) {
                    "$label · ${stringResource(R.string.week_estimated)}"
                } else {
                    label
                }
                Text(
                    stringResource(R.string.week_night_summary, shownLabel, moon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nightName != null) {
                    Text(
                        nightName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    nightNameReason(nightName)?.let { reason ->
                        Text(
                            reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            ScorePill(night.score)
        }
    }
}

@Composable
private fun nightNameReason(name: String): String? = when {
    name == "The Big One" -> stringResource(R.string.week_name_big_one)
    name.startsWith("Ghost Moon") -> stringResource(R.string.week_name_ghost)
    else -> null
}

private fun moonEmoji(illumination: Double): String = when {
    illumination < 0.05 -> "○"
    illumination < 0.45 -> "◔"
    illumination < 0.55 -> "◑"
    illumination < 0.95 -> "◕"
    else -> "●"
}
