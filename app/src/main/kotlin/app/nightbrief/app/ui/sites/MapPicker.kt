package app.nightbrief.app.ui.sites

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.weather.PlaceMatch
import app.nightbrief.weather.PlaceSearch
import app.nightbrief.weather.PlaceSearchResult
import app.nightbrief.weather.distanceKmFrom
import app.nightbrief.weather.nearestFirst
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * The pin's coordinates survive a fold. [GeoPoint] is not a Bundle type, so the
 * pair of doubles is what gets saved; a null pin saves nothing.
 */
private val geoPointSaver: Saver<GeoPoint?, Any> = listSaver(
    save = { point -> point?.let { listOf(it.latitude, it.longitude) }.orEmpty() },
    restore = { if (it.size == 2) GeoPoint(it[0], it[1]) else null },
)

private fun findDialogWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

/**
 * Full-screen OpenStreetMap picker. Tap to drop a pin, or type a place name.
 * [searchPlaces] is the Open-Meteo fuzzy geocoder; a failure leaves the pin to a tap.
 *
 * The confirm control sits above the map. A dialog measured to the full display is cropped
 * at the bottom edge, which used to hide the button; the map takes that leftover height.
 * Map tiles draw outside the view, so the map slot is clipped and cannot cover the search field.
 *
 * [onPick]'s place name is set only when the pin came from a search hit. A later map
 * tap clears it, so a nudged pin does not rename the site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    onDismiss: () -> Unit,
    searchPlaces: suspend (String) -> PlaceSearchResult,
    onPick: (latitude: Double, longitude: Double, placeName: String?) -> Unit,
) {
    val view = LocalView.current
    var picked by rememberSaveable(stateSaver = geoPointSaver) {
        mutableStateOf(if (initialLat != null && initialLon != null) GeoPoint(initialLat, initialLon) else null)
    }
    // The chosen place's name rides with the pin; a fold must not turn a searched
    // location back into an unnamed coordinate.
    var chosenName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PlaceMatch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var searchedQuery by remember { mutableStateOf<String?>(null) }
    val keyboardDismissals = remember { mutableIntStateOf(0) }
    var tilesLoading by remember { mutableStateOf(true) }
    var bias by remember { mutableStateOf(picked) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = view.context.packageName
        MapView(view.context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (picked != null) 10.0 else 4.0)
            controller.setCenter(picked ?: GeoPoint(50.0, -85.0))
            overlayManager.tilesOverlay.setLoadingBackgroundColor(0xFF000000.toInt())
        }
    }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }

    fun placePin(point: GeoPoint, recenter: Boolean) {
        picked = point
        marker.position = point
        if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
        if (recenter) {
            mapView.controller.setZoom(11.0)
            mapView.controller.setCenter(point)
        }
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        picked?.let {
            marker.position = it
            mapView.overlays.add(marker)
        }
        mapView.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    chosenName = null
                    keyboardDismissals.intValue++
                    placePin(p, recenter = false)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = singleTapConfirmedHelper(p)
            }),
        )
        val tiles = object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                tilesLoading = false
                return false
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                tilesLoading = false
                return false
            }
        }
        mapView.addMapListener(tiles)
        mapView.onResume()
        onDispose {
            mapView.removeMapListener(tiles)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(keyboardDismissals.intValue) {
        if (keyboardDismissals.intValue == 0) return@LaunchedEffect
        focusManager.clearFocus()
        keyboard?.hide()
    }

    LaunchedEffect(Unit) {
        if (picked != null) return@LaunchedEffect
        val city = ZoneId.systemDefault().id.substringAfterLast('/').replace('_', ' ').trim()
        if (city.length < PlaceSearch.MIN_QUERY_LENGTH) return@LaunchedEffect
        val found = try {
            searchPlaces(city)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        val place = found.places.minByOrNull { if (it.name.equals(city, ignoreCase = true)) 0 else 1 }
            ?: return@LaunchedEffect
        if (picked != null || query.isNotBlank()) return@LaunchedEffect
        val point = GeoPoint(place.latitude, place.longitude)
        bias = point
        mapView.controller.setZoom(8.0)
        mapView.controller.setCenter(point)
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (settledQuery != null && trimmed != settledQuery) {
            chosenName = null
            settledQuery = null
        }
        if (trimmed.length < PlaceSearch.MIN_QUERY_LENGTH || trimmed == settledQuery) {
            searching = false
            searchFailed = false
            if (trimmed != settledQuery) results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        searchFailed = false
        results = emptyList()
        delay(SEARCH_DEBOUNCE_MS)
        val outcome = try {
            searchPlaces(trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlaceSearchResult(failed = true)
        }
        val pin = picked ?: bias
        results = if (pin != null) outcome.places.nearestFirst(pin.latitude, pin.longitude) else outcome.places
        searchFailed = outcome.failed
        searchedQuery = trimmed
        searching = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            // The dialog window is wrap-content unless told otherwise, so a two-line
            // confirm label is clipped at the bottom of the screen.
            findDialogWindow(dialogView)?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        Surface(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = { Text(stringResource(R.string.tap_to_place)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, stringResource(R.string.close))
                            }
                        },
                    )
                },
            ) { inner ->
                Column(Modifier.padding(inner).fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.search_place)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            when {
                                searching -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                query.isNotEmpty() -> IconButton(
                                    onClick = {
                                        query = ""
                                        settledQuery = null
                                        chosenName = null
                                        results = emptyList()
                                        searchFailed = false
                                        searchedQuery = null
                                    },
                                ) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.clear_place_search))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("place-search"),
                    )
                    PlaceResults(
                        query = query,
                        settledQuery = settledQuery,
                        searchedQuery = searchedQuery,
                        results = results,
                        searching = searching,
                        failed = searchFailed,
                        near = picked ?: bias,
                        modifier = Modifier.weight(1f, fill = false),
                        onPick = { place ->
                            keyboardDismissals.intValue++
                            settledQuery = place.name
                            chosenName = place.name
                            query = place.name
                            results = emptyList()
                            searchFailed = false
                            placePin(GeoPoint(place.latitude, place.longitude), recenter = true)
                        },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    ) {
                        Text(
                            stringResource(R.string.map_osm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.map_places),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val point = picked
                        Button(
                            onClick = { point?.let { onPick(it.latitude, it.longitude, chosenName) } },
                            enabled = point != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                point?.let {
                                    stringResource(R.string.use_coordinates, Format.coordinates(it.latitude, it.longitude))
                                } ?: stringResource(R.string.tap_map),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .testTag("site-map"),
                    ) {
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            update = { map -> (map.parent as? ViewGroup)?.clipChildren = true },
                        )
                        if (tilesLoading) {
                            Text(
                                stringResource(R.string.map_tiles_loading),
                                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceResults(
    query: String,
    settledQuery: String?,
    searchedQuery: String?,
    results: List<PlaceMatch>,
    searching: Boolean,
    failed: Boolean,
    near: GeoPoint?,
    onPick: (PlaceMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim()
    val outcomeReady = trimmed == searchedQuery && trimmed != settledQuery && !searching
    if (results.isEmpty() && !outcomeReady) return
    Column(modifier.padding(top = 8.dp)) {
        when {
            failed && outcomeReady -> Text(
                stringResource(R.string.place_search_failed),
                color = NightColors.Marginal,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            results.isEmpty() && outcomeReady -> Text(
                stringResource(R.string.place_search_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            results.isNotEmpty() -> Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 240.dp),
            ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                results.forEachIndexed { index, place ->
                    if (index > 0) HorizontalDivider()
                    val description = listOfNotNull(place.name, place.detail()).joinToString(", ")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = description }
                            .clickable(role = Role.Button) { onPick(place) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        val away = near?.let { center ->
                            stringResource(R.string.place_distance, place.distanceKmFrom(center.latitude, center.longitude).toInt())
                        }
                        Text(place.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(place.detail(), away).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }
}
