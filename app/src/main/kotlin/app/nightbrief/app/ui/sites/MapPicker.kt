package app.nightbrief.app.ui.sites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.nightbrief.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nightbrief.app.ui.common.Format
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** Full-screen OpenStreetMap picker: tap to drop a pin, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLon: Double?,
    onDismiss: () -> Unit,
    onPick: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(if (initialLat != null && initialLon != null) GeoPoint(initialLat, initialLon) else null) }
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (picked != null) 10.0 else 4.0)
            controller.setCenter(picked ?: GeoPoint(50.0, -85.0))
        }
    }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }

    DisposableEffect(Unit) {
        picked?.let {
            marker.position = it
            mapView.overlays.add(marker)
        }
        mapView.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    picked = p
                    marker.position = p
                    if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
                    mapView.invalidate()
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = singleTapConfirmedHelper(p)
            }),
        )
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
                    title = { Text(stringResource(R.string.tap_to_place)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } },
                )
            },
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.map_osm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val p = picked
                    Button(
                        onClick = { p?.let { onPick(it.latitude, it.longitude) } },
                        enabled = p != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            p?.let { stringResource(R.string.use_coordinates, Format.coordinates(it.latitude, it.longitude)) }
                                ?: stringResource(R.string.tap_map),
                        )
                    }
                }
            }
        }
    }
}
