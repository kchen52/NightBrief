package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nightbrief.app.DarkerSkyUiState
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.ScorePill
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.score.DarkSkyCandidate
import app.nightbrief.score.DarkSkyFinder
import app.nightbrief.score.DigestComposer
import app.nightbrief.sites.Site
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * "Darker sky within 60 km" for a site with only saved-site alternatives otherwise.
 * Pure rendering; the search itself runs in [app.nightbrief.app.AppViewModel.searchDarkerSky].
 */
@Composable
fun DarkerSkyCard(
    site: Site,
    state: DarkerSkyUiState,
    onSearch: (String) -> Unit,
    onSave: (DarkSkyCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMap by remember { mutableStateOf(false) }
    SectionCard(
        title = stringResource(R.string.darker_sky_title),
        icon = Icons.Filled.Explore,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.darker_sky_body, site.name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        when {
            state.searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 10.dp))
                Text(stringResource(R.string.darker_sky_searching), style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null -> {
                Banner(state.error, NightColors.Poor)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onSearch(site.id) }) { Text(stringResource(R.string.darker_sky_search)) }
            }
            state.candidates == null || state.searchedSiteId != site.id -> {
                Button(onClick = { onSearch(site.id) }) { Text(stringResource(R.string.darker_sky_search)) }
            }
            state.candidates.isEmpty() -> {
                Text(
                    stringResource(R.string.darker_sky_empty, site.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onSearch(site.id) }) { Text(stringResource(R.string.darker_sky_search)) }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.candidates.forEach { candidate ->
                        DarkerSkyRow(
                            primary = site,
                            candidate = candidate,
                            saved = candidate.site.id in state.savedIds,
                            onSave = { onSave(candidate) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { onSearch(site.id) }) { Text(stringResource(R.string.darker_sky_search)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showMap = true }) { Text(stringResource(R.string.darker_sky_show_map)) }
                }
            }
        }
    }
    if (showMap && state.candidates != null) {
        DarkerSkyMapDialog(
            primary = site,
            candidates = state.candidates,
            onDismiss = { showMap = false },
            onSave = onSave,
            savedIds = state.savedIds,
        )
    }
}

@Composable
internal fun DarkerSkyRow(
    primary: Site,
    candidate: DarkSkyCandidate,
    saved: Boolean,
    onSave: (DarkSkyCandidate) -> Unit,
) {
    val bearing = DarkSkyFinder.bearingDeg(
        primary.latitude, primary.longitude,
        candidate.site.latitude, candidate.site.longitude,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ScorePill(candidate.report.scoreValue)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    R.string.darker_sky_bortle_distance,
                    candidate.bortle,
                    candidate.distanceKm.toInt(),
                    DigestComposer.compass(bearing),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            candidate.delta?.let { delta ->
                Text(
                    stringResource(R.string.darker_sky_delta, delta),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta >= 0) NightColors.Excellent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (saved) {
            Text(
                stringResource(R.string.darker_sky_saved),
                style = MaterialTheme.typography.labelLarge,
                color = NightColors.Excellent,
            )
        } else {
            TextButton(onClick = { onSave(candidate) }) { Text(stringResource(R.string.darker_sky_save)) }
        }
    }
}

/** Read-only osmdroid map of the candidates with a per-row Save as site button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DarkerSkyMapDialog(
    primary: Site,
    candidates: List<DarkSkyCandidate>,
    onDismiss: () -> Unit,
    onSave: (DarkSkyCandidate) -> Unit,
    savedIds: Set<String>,
) {
    val context = LocalContext.current
    val center = remember(primary, candidates) {
        val lats = listOf(primary.latitude) + candidates.map { it.site.latitude }
        val lons = listOf(primary.longitude) + candidates.map { it.site.longitude }
        GeoPoint(
            (lats.minOrNull()!! + lats.maxOrNull()!!) / 2,
            (lons.minOrNull()!! + lons.maxOrNull()!!) / 2,
        )
    }
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(9.0)
            controller.setCenter(center)
        }
    }
    DisposableEffect(primary, candidates) {
        mapView.overlays.clear()
        Marker(mapView).apply {
            position = GeoPoint(primary.latitude, primary.longitude)
            title = primary.name
            mapView.overlays.add(this)
        }
        for (candidate in candidates) {
            Marker(mapView).apply {
                position = GeoPoint(candidate.site.latitude, candidate.site.longitude)
                title = "${candidate.site.name} · ${candidate.report.scoreValue ?: "—"}"
                mapView.overlays.add(this)
            }
        }
        mapView.invalidate()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.darker_sky_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                )
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().weight(1f))
                Text(
                    stringResource(R.string.map_osm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(candidates, key = { it.site.id }) { candidate ->
                        DarkerSkyRow(
                            primary = primary,
                            candidate = candidate,
                            saved = candidate.site.id in savedIds,
                            onSave = onSave,
                        )
                    }
                }
            }
        }
    }
}
