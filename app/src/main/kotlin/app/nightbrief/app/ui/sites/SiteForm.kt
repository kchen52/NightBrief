package app.nightbrief.app.ui.sites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nightbrief.app.SiteDraft
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.common.TimePickerDialog
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.sites.BortleClass
import app.nightbrief.sites.BortleSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

private const val COORDINATE_LOOKUP_DEBOUNCE_MS = 400L

@Composable
fun SiteForm(
    draft: SiteDraft,
    onChange: (SiteDraft) -> Unit,
    lookupBortle: suspend (Double, Double) -> Int?,
    lookupTimeZone: suspend (Double, Double) -> String?,
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
    var editingZone by rememberSaveable(draft.id) { mutableStateOf(false) }
    var lookingUpDarkness by remember { mutableStateOf(false) }
    var noSkyCoverage by remember { mutableStateOf(false) }
    var zoneLookupFailed by remember { mutableStateOf(false) }
    val latestDraft by rememberUpdatedState(draft)
    val latestOnChange by rememberUpdatedState(onChange)

    fun setCoordinates(lat: Double, lon: Double) {
        latestOnChange(
            latestDraft.copy(
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
        val lat = draft.lat
        val lon = draft.lon
        if (lat == null || lon == null) {
            lookingUpDarkness = false
            noSkyCoverage = false
            zoneLookupFailed = false
            return@LaunchedEffect
        }
        delay(COORDINATE_LOOKUP_DEBOUNCE_MS)
        val before = latestDraft
        val lookupZone = !before.zoneEdited && before.coordinatesDifferFromSaved(lat, lon)
        lookingUpDarkness = true
        noSkyCoverage = false
        try {
            val (found, zone) = coroutineScope {
                val darkness = async { lookupBortle(lat, lon) }
                val zoneCall = if (lookupZone) async { lookupTimeZone(lat, lon) } else null
                darkness.await() to zoneCall?.await()
            }
            val current = latestDraft
            if (current.lat != lat || current.lon != lon) return@LaunchedEffect
            var next = current
            val manualBortle = current.bortleSource == BortleSource.USER && current.bortle != null
            if (found != null && !manualBortle) {
                next = next.copy(bortle = found, bortleSource = BortleSource.MAP)
            }
            noSkyCoverage = found == null
            if (!current.zoneEdited) {
                if (!current.coordinatesDifferFromSaved(lat, lon)) {
                    val savedZone = current.savedZoneId
                    if (savedZone != null && next.zoneId != savedZone) next = next.copy(zoneId = savedZone)
                    zoneLookupFailed = false
                } else if (lookupZone) {
                    if (zone != null) {
                        next = next.copy(zoneId = zone)
                        zoneLookupFailed = false
                    } else {
                        next = next.copy(zoneId = ZoneId.systemDefault().id)
                        zoneLookupFailed = true
                    }
                }
            }
            lookingUpDarkness = false
            if (next != current) latestOnChange(next)
        } finally {
            lookingUpDarkness = false
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
            if (editingZone) {
                OutlinedTextField(
                    value = draft.zoneId,
                    onValueChange = {
                        zoneLookupFailed = false
                        onChange(draft.copy(zoneId = it, zoneEdited = true))
                    },
                    label = { Text("Time zone") },
                    isError = !draft.zoneValid,
                    supportingText = { Text("IANA zone, e.g. America/Toronto") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            zoneLookupFailed = false
                            onChange(draft.copy(zoneId = ZoneId.systemDefault().id, zoneEdited = true))
                        }) { Text("Device") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val editColor = MaterialTheme.colorScheme.primary
                Text(
                    text = buildAnnotatedString {
                        append("Time zone: ${draft.zoneId} · ")
                        withStyle(SpanStyle(color = editColor)) { append("Edit") }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Edit time zone") {
                        editingZone = true
                    },
                )
            }
            if (zoneLookupFailed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Couldn't look up time zone — using device zone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            if (lookingUpDarkness) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Looking up sky darkness…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                when {
                    noSkyCoverage && draft.bortle == null ->
                        "No coverage in the light-pollution map. Scoring assumes Bortle 5 until you choose."
                    noSkyCoverage && draft.bortleSource == BortleSource.USER ->
                        "No coverage in the light-pollution map for these coordinates. Set by you."
                    noSkyCoverage ->
                        "No coverage in the light-pollution map for these coordinates."
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
