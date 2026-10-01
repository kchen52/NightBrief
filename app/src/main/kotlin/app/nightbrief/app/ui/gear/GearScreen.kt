package app.nightbrief.app.ui.gear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.gear.CameraBody
import app.nightbrief.gear.ExposureCalculator
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.gear.Lens
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearScreen(vm: AppViewModel, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val kit = state?.gear ?: return
    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Gear") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            GearEditor(kit, onChange = { next -> vm.updateGear { next } })
        }
    }
}

@Composable
fun GearEditor(kit: GearKit, onChange: (GearKit) -> Unit, modifier: Modifier = Modifier) {
    var addBody by remember { mutableStateOf(false) }
    var addLens by remember { mutableStateOf(false) }
    val primaryId = kit.primaryBodyId ?: kit.bodies.firstOrNull()?.id

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("Camera bodies") {
            if (kit.bodies.isEmpty()) {
                Text(
                    "No camera bodies yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Suggestions use the selected body.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Column(Modifier.selectableGroup()) {
                    kit.bodies.forEachIndexed { index, body ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BodyRow(
                            body = body,
                            selected = body.id == primaryId,
                            onSelect = { onChange(kit.copy(primaryBodyId = body.id)) },
                            onDelete = { onChange(kit.removeBody(body.id)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { addBody = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add body")
            }
        }

        SectionCard("Lenses") {
            val body = kit.primaryBody
            if (kit.lenses.isEmpty()) {
                Text(
                    "No lenses yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (body == null) {
                Text(
                    "Add a camera body to calculate exposure limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            kit.lenses.forEachIndexed { index, lens ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                LensRow(lens = lens, body = body, onDelete = { onChange(kit.removeLens(lens.id)) })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { addLens = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add lens")
            }
        }
    }

    if (addBody) {
        AddBodyDialog(
            ownedIds = kit.bodies.map { it.id }.toSet(),
            onDismiss = { addBody = false },
            onPick = { body ->
                onChange(kit.addBody(body))
                addBody = false
            },
        )
    }
    if (addLens) {
        AddLensDialog(
            ownedIds = kit.lenses.map { it.id }.toSet(),
            onDismiss = { addLens = false },
            onPick = { lens ->
                onChange(kit.addLens(lens))
                addLens = false
            },
        )
    }
}

@Composable
private fun BodyRow(body: CameraBody, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .semantics { role = Role.RadioButton }
                .padding(vertical = 8.dp),
        ) {
            Text(body.name, style = MaterialTheme.typography.titleMedium)
            Text(
                bodySpec(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${body.name}") }
    }
}

@Composable
private fun LensRow(lens: Lens, body: CameraBody?, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(lens.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${lens.focalLabel} · ${lens.apertureLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (body != null) {
                val npf = ExposureCalculator.npf(lens.minFocalMm, lens.maxAperture, body.pixelPitchUm)
                val rule = ExposureCalculator.rule500(lens.minFocalMm, body.cropFactor)
                Text(
                    "NPF at widest: ${ExposureCalculator.shutterLabel(npf)} · 500 rule: ${ExposureCalculator.shutterLabel(rule)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${lens.name}") }
    }
}

private fun bodySpec(body: CameraBody): String {
    val crop = "%.2f".format(Locale.ROOT, body.cropFactor)
    val pitch = "%.2f".format(Locale.ROOT, body.pixelPitchUm)
    return "${body.format.label} · ${crop}× crop · $pitch µm"
}

@Composable
private fun AddBodyDialog(ownedIds: Set<String>, onDismiss: () -> Unit, onPick: (CameraBody) -> Unit) {
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    CatalogDialog(
        title = if (custom) "Custom camera" else "Add body",
        query = query,
        onQuery = { query = it },
        searchLabel = "Search cameras",
        custom = custom,
        onCustom = { custom = true },
        onBackToList = { custom = false },
        onDismiss = onDismiss,
        list = {
            val matches = GearCatalog.bodies.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (matches.isEmpty()) {
                item { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(matches, key = { it.id }) { body ->
                val owned = body.id in ownedIds
                CatalogRow(
                    title = body.name,
                    detail = "${body.format.label} · ${body.megapixels.compact()} MP",
                    owned = owned,
                    onClick = { if (!owned) onPick(body) },
                )
            }
        },
        customForm = { CustomBodyForm(onAdd = onPick) },
    )
}

@Composable
private fun AddLensDialog(ownedIds: Set<String>, onDismiss: () -> Unit, onPick: (Lens) -> Unit) {
    var query by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    CatalogDialog(
        title = if (custom) "Custom lens" else "Add lens",
        query = query,
        onQuery = { query = it },
        searchLabel = "Search lenses",
        custom = custom,
        onCustom = { custom = true },
        onBackToList = { custom = false },
        onDismiss = onDismiss,
        list = {
            val matches = GearCatalog.lenses.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (matches.isEmpty()) {
                item { Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(matches, key = { it.id }) { lens ->
                val owned = lens.id in ownedIds
                CatalogRow(
                    title = lens.name,
                    detail = "${lens.focalLabel} · ${lens.apertureLabel}",
                    owned = owned,
                    onClick = { if (!owned) onPick(lens) },
                )
            }
        },
        customForm = { CustomLensForm(onAdd = onPick) },
    )
}

@Composable
private fun CatalogDialog(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    searchLabel: String,
    custom: Boolean,
    onCustom: () -> Unit,
    onBackToList: () -> Unit,
    onDismiss: () -> Unit,
    list: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    customForm: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                if (!custom) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        label = { Text(searchLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) { list() }
                    TextButton(onClick = onCustom, modifier = Modifier.align(Alignment.End)) { Text("Custom…") }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { customForm() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onBackToList) { Text("Back") }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(title: String, detail: String, owned: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !owned, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (owned) {
            Text("Added", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CustomBodyForm(onAdd: (CameraBody) -> Unit) {
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var megapixels by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val error = bodyError(name, width, height, megapixels)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            isError = submitted && name.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Sensor width (mm)", width, { width = it }, submitted && width.positiveOrNull() == null)
        NumberField("Sensor height (mm)", height, { height = it }, submitted && height.positiveOrNull() == null)
        NumberField("Megapixels", megapixels, { megapixels = it }, submitted && megapixels.positiveOrNull() == null)
        if (submitted && error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                submitted = true
                val message = bodyError(name, width, height, megapixels)
                if (message != null) return@Button
                onAdd(
                    CameraBody(
                        id = newId("custom-body"),
                        name = name.trim(),
                        sensorWidthMm = width.positiveOrNull()!!,
                        sensorHeightMm = height.positiveOrNull()!!,
                        megapixels = megapixels.positiveOrNull()!!,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add body") }
    }
}

@Composable
private fun CustomLensForm(onAdd: (Lens) -> Unit) {
    var name by remember { mutableStateOf("") }
    var minFocal by remember { mutableStateOf("") }
    var maxFocal by remember { mutableStateOf("") }
    var aperture by remember { mutableStateOf("") }
    var longEnd by remember { mutableStateOf("") }
    var longEndTouched by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val error = lensError(name, minFocal, maxFocal, aperture, longEnd)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            isError = submitted && name.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Minimum focal length (mm)", minFocal, { minFocal = it }, submitted && minFocal.positiveOrNull() == null)
        NumberField("Maximum focal length (mm)", maxFocal, { maxFocal = it }, submitted && maxFocal.positiveOrNull() == null)
        NumberField(
            label = "Max aperture (f-number)",
            value = aperture,
            onValue = {
                aperture = it
                if (!longEndTouched) longEnd = it
            },
            error = submitted && aperture.positiveOrNull() == null,
        )
        NumberField(
            label = "Aperture at long end",
            value = longEnd,
            onValue = {
                longEndTouched = true
                longEnd = it
            },
            error = submitted && longEnd.positiveOrNull() == null,
        )
        Text(
            "Enter f/2.8 as 2.8. For a prime, both apertures match. For a variable zoom, the long end is the same or slower.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (submitted && error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                submitted = true
                if (lensError(name, minFocal, maxFocal, aperture, longEnd) != null) return@Button
                onAdd(
                    Lens(
                        id = newId("custom-lens"),
                        name = name.trim(),
                        minFocalMm = minFocal.positiveOrNull()!!,
                        maxFocalMm = maxFocal.positiveOrNull()!!,
                        maxAperture = aperture.positiveOrNull()!!,
                        maxApertureAtLongEnd = longEnd.positiveOrNull()!!,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add lens") }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit, error: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun bodyError(name: String, width: String, height: String, megapixels: String): String? = when {
    name.isBlank() -> "Give the camera a name"
    width.positiveOrNull() == null -> "Sensor width must be a positive number of millimetres"
    height.positiveOrNull() == null -> "Sensor height must be a positive number of millimetres"
    megapixels.positiveOrNull() == null -> "Megapixels must be a positive number"
    else -> null
}

private fun lensError(name: String, minFocal: String, maxFocal: String, aperture: String, longEnd: String): String? {
    val minF = minFocal.positiveOrNull()
    val maxF = maxFocal.positiveOrNull()
    val wide = aperture.positiveOrNull()
    val tele = longEnd.positiveOrNull()
    return when {
        name.isBlank() -> "Give the lens a name"
        minF == null -> "Minimum focal length must be positive"
        maxF == null -> "Maximum focal length must be positive"
        maxF < minF -> "Maximum focal length can't be shorter than the minimum"
        wide == null -> "Maximum aperture must be a positive f-number"
        tele == null -> "Aperture at the long end must be a positive f-number"
        tele < wide -> "The long end can't be faster than the wide end"
        else -> null
    }
}

private fun String.positiveOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

private fun Double.compact(): String {
    val rounded = "%.1f".format(Locale.ROOT, this)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}

private fun newId(prefix: String) = "$prefix-${UUID.randomUUID()}"
