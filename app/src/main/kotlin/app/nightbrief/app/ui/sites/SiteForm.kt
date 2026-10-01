package app.nightbrief.app.ui.sites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.nightbrief.app.SiteDraft
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.common.TimePickerDialog
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.sites.BortleClass
import app.nightbrief.sites.BortleSource
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SiteForm(
    draft: SiteDraft,
    onChange: (SiteDraft) -> Unit,
    lookupBortle: (Double, Double) -> Int?,
    showPrimaryToggle: Boolean,
    showDigestOverride: Boolean,
    globalDigestTime: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    fun setCoordinates(lat: Double, lon: Double) {
        onChange(
            draft.copy(
                latitude = String.format(Locale.ROOT, "%.5f", lat),
                longitude = String.format(Locale.ROOT, "%.5f", lon),
            ),
        )
    }

    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                locating = true
                val loc = LocationHelper.currentLocation(context)
                locating = false
                if (loc != null) setCoordinates(loc.latitude, loc.longitude) else locationError = "Couldn't get a location fix"
            }
        } else {
            locationError = "Location permission denied — enter coordinates or use the map"
        }
    }

    LaunchedEffect(draft.lat, draft.lon) {
        val lat = draft.lat ?: return@LaunchedEffect
        val lon = draft.lon ?: return@LaunchedEffect
        val found = lookupBortle(lat, lon)
        if (found != null && (draft.bortle == null || draft.bortleSource == BortleSource.MAP)) {
            onChange(draft.copy(bortle = found, bortleSource = BortleSource.MAP))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = { Text("Site name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionCard("Location") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        locationError = null
                        requestLocation.launch(LocationHelper.PERMISSIONS)
                    },
                    enabled = !locating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use GPS")
                }
                OutlinedButton(onClick = { showMap = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick on map")
                }
            }
            locationError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = NightColors.Marginal, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.latitude,
                    onValueChange = { onChange(draft.copy(latitude = it)) },
                    label = { Text("Latitude") },
                    isError = draft.latitude.isNotBlank() && draft.lat == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.longitude,
                    onValueChange = { onChange(draft.copy(longitude = it)) },
                    label = { Text("Longitude") },
                    isError = draft.longitude.isNotBlank() && draft.lon == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.zoneId,
                onValueChange = { onChange(draft.copy(zoneId = it)) },
                label = { Text("Time zone") },
                isError = !draft.zoneValid,
                supportingText = { Text("IANA zone, e.g. America/Toronto") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { onChange(draft.copy(zoneId = ZoneId.systemDefault().id)) }) { Text("Device") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("Sky darkness") {
            val bortle = draft.bortle ?: 5
            val cls = BortleClass.of(bortle)
            Text("Bortle $bortle — ${cls.label}", style = MaterialTheme.typography.titleMedium)
            Text(cls.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = bortle.toFloat(),
                onValueChange = { onChange(draft.copy(bortle = it.roundToInt(), bortleSource = BortleSource.USER)) },
                valueRange = 1f..9f,
                steps = 7,
            )
            Text(
                when {
                    draft.bortle == null -> "Not set — scoring assumes Bortle 5 until you choose."
                    draft.bortleSource == BortleSource.MAP -> "From the light-pollution map. Drag to override."
                    else -> "Set by you. Check lightpollutionmap.info if unsure."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showDigestOverride || showPrimaryToggle) {
            SectionCard("Digest") {
                if (showPrimaryToggle) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = draft.makePrimary, onCheckedChange = { onChange(draft.copy(makePrimary = it)) })
                        Text("Primary site (leads the morning digest)")
                    }
                }
                if (showDigestOverride) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Own digest time", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                draft.digestTimeOverride?.let { "Sends a digest for this site at $it" }
                                    ?: if (draft.makePrimary) "Uses the global time ($globalDigestTime)" else "No separate digest",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.digestTimeOverride != null,
                            onCheckedChange = { on -> if (on) showTime = true else onChange(draft.copy(digestTimeOverride = null)) },
                        )
                    }
                    if (draft.digestTimeOverride != null) {
                        TextButton(onClick = { showTime = true }) { Text("Change time") }
                    }
                }
            }
        }
    }

    if (showMap) {
        MapPickerDialog(draft.lat, draft.lon, onDismiss = { showMap = false }) { lat, lon ->
            showMap = false
            setCoordinates(lat, lon)
        }
    }
    if (showTime) {
        TimePickerDialog(
            initial = draft.digestTimeOverride?.let(LocalTime::parse) ?: LocalTime.parse(globalDigestTime),
            onDismiss = { showTime = false },
        ) {
            showTime = false
            onChange(draft.copy(digestTimeOverride = it.toString()))
        }
    }
}
