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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.ScorePill
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.tonight.StaleForecastBanner
import app.nightbrief.app.ui.common.SiteChip
import app.nightbrief.app.ui.common.SiteStrip
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.astro.Darkness
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.OutlookNight
import app.nightbrief.weather.ForecastStatus
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    vm: AppViewModel,
    onOpenNight: (String, LocalDate) -> Unit,
    onOpenEvents: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.briefing.collectAsStateWithLifecycle()
    val selected by vm.selectedSiteId.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    val siteId = selected?.takeIf { book[it] != null } ?: book.primaryId ?: return
    val briefing = ui.briefing
    val outlook = briefing?.outlookFor(siteId)
    val report = briefing?.reportFor(siteId)

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.week_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.padding(inner).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                SiteStrip(
                    sites = book.primaryFirst().map {
                        SiteChip(it.id, it.name, briefing?.outlookFor(it.id)?.best?.score, it.id == book.primaryId)
                    },
                    selectedId = siteId,
                    onSelect = vm::selectSite,
                )
            }
            val tonight = report
            if (tonight != null && tonight.forecastStatus == ForecastStatus.STALE) {
                item {
                    StaleForecastBanner(tonight, Modifier.padding(horizontal = 16.dp))
                }
            }
            if (outlook != null) {
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(outlook.headline(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.week_footnote),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(outlook.nights, key = { it.date.toString() }) { night ->
                    NightRow(
                        night = night,
                        isBest = night == outlook.best,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = { onOpenNight(siteId, night.date) },
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

@Composable
internal fun NightRow(night: OutlookNight, isBest: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
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
            }
            Spacer(Modifier.width(12.dp))
            ScorePill(night.score)
        }
    }
}

private fun moonEmoji(illumination: Double): String = when {
    illumination < 0.05 -> "○"
    illumination < 0.45 -> "◔"
    illumination < 0.55 -> "◑"
    illumination < 0.95 -> "◕"
    else -> "●"
}
