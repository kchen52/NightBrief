package app.nightbrief.app.ui.events

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.ui.icons.NightBriefIcons
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEventKind
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Year-ahead sky events beside the 8-day forecast: oppositions, elongations,
 * conjunctions, eclipses, seasons, and shower peaks, computed on device.
 *
 * The year-ahead scan runs through [AppViewModel.skyEventsYearAhead] on `Dispatchers.Default`,
 * never during composition, so opening the screen shows a loading state instead of a freeze.
 * [loadEvents] is a test seam for holding that load open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenNight: (siteId: String, date: LocalDate) -> Unit,
    loadEvents: suspend (ZoneId) -> List<SkyEvent> = vm::skyEventsYearAhead,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    val site = book.primary ?: return
    val zone = site.zone
    // Bumped by Retry. It restarts the load without changing the site.
    var attempt by remember(site.id) { mutableIntStateOf(0) }
    val loaded by produceState<Result<List<SkyEvent>>?>(initialValue = null, site.id, attempt) {
        value = try {
            Result.success(loadEvents(zone))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.events_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(NightBriefIcons.Back, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { inner ->
        val result = loaded
        val events = result?.getOrNull()
        when {
            result == null -> EventsStatus(
                text = stringResource(R.string.events_loading),
                progress = true,
                modifier = Modifier.padding(inner).testTag("events-loading"),
            )
            events == null -> EventsStatus(
                text = stringResource(R.string.events_error),
                progress = false,
                onRetry = { attempt++ },
                modifier = Modifier.padding(inner).testTag("events-error"),
            )
            events.isEmpty() -> EventsStatus(
                text = stringResource(R.string.events_empty),
                progress = false,
                modifier = Modifier.padding(inner).testTag("events-empty"),
            )
            else -> EventsList(
                events = events,
                zone = zone,
                // Open the planner for the site that produced these events, not the global selection.
                onOpenNight = { date -> onOpenNight(site.id, date) },
                modifier = Modifier.padding(inner),
            )
        }
    }
}

/** Loading, failure, or empty state for the year-ahead scan. The retry only exists after a failure. */
@Composable
private fun EventsStatus(
    text: String,
    progress: Boolean,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun EventsList(
    events: List<SkyEvent>,
    zone: ZoneId,
    onOpenNight: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                    month.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]) + " ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(monthEvents, key = { it.instant.toString() + it.title }) { event ->
                EventRow(event, zone, onOpenNight = onOpenNight)
            }
        }
    }
}

@Composable
internal fun EventRow(
    event: SkyEvent,
    zone: ZoneId,
    onOpenNight: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val date = event.instant.atZone(zone).toLocalDate()
    SectionCard(modifier = modifier.clickable { onOpenNight(date) }.testTag("event-row")) {
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
