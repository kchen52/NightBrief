package app.nightbrief.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEventKind
import app.nightbrief.astro.SkyEvents
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Year-ahead sky events beside the 8-day forecast: oppositions, elongations,
 * conjunctions, eclipses, seasons, and shower peaks, computed on device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    val site = book.primary ?: return
    val zone = site.zone
    val today = remember(zone) { LocalDate.now(zone) }
    val events = remember(site.id, today) { SkyEvents.yearAhead(today, zone) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.events_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { inner ->
        EventsList(events = events, zone = zone, modifier = Modifier.padding(inner))
    }
}

@Composable
fun EventsList(events: List<SkyEvent>, zone: ZoneId, modifier: Modifier = Modifier) {
    val months = remember(events, zone) {
        events.groupBy { YearMonth.from(it.instant.atZone(zone).toLocalDate()) }.toSortedMap()
    }
    LazyColumn(
        modifier.fillMaxSize().testTag("events-list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        months.forEach { (month, monthEvents) ->
            item(key = "month-${month}") {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(monthEvents, key = { it.instant.toString() + it.title }) { event ->
                EventRow(event, zone)
            }
        }
    }
}

@Composable
internal fun EventRow(event: SkyEvent, zone: ZoneId, modifier: Modifier = Modifier) {
    val date = event.instant.atZone(zone).toLocalDate()
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(
                    Format.shortDayName(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Format.dayMonth(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    eventKindLabel(event.kind) + " · " + event.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun eventKindLabel(kind: SkyEventKind): String = stringResource(
    when (kind) {
        SkyEventKind.OPPOSITION -> R.string.event_opposition
        SkyEventKind.ELONGATION -> R.string.event_elongation
        SkyEventKind.CONJUNCTION -> R.string.event_conjunction
        SkyEventKind.APPULSE -> R.string.event_appulse
        SkyEventKind.LUNAR_ECLIPSE -> R.string.event_lunar_eclipse
        SkyEventKind.SOLAR_ECLIPSE -> R.string.event_solar_eclipse
        SkyEventKind.SEASON -> R.string.event_season
        SkyEventKind.SHOWER_PEAK -> R.string.event_shower
    },
)
