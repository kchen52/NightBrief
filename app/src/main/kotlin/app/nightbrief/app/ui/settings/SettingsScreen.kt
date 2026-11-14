package app.nightbrief.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.ui.icons.NightBriefIcons
import app.nightbrief.app.BuildConfig
import app.nightbrief.app.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.gear.InterestPicker
import app.nightbrief.astro.RaDec
import app.nightbrief.astro.Target
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.astro.TargetKind
import app.nightbrief.score.AuroraAlerts
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.UnitSystem
import app.nightbrief.data.LightPollutionAttribution
import app.nightbrief.app.ui.common.TimePickerDialog
import app.nightbrief.work.DigestNotifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPassport: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val context = LocalContext.current
    val resources = LocalResources.current
    var showTime by rememberSaveable { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(DigestNotifier(context).canNotify()) }
    var digestNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val libraryNote by vm.libraryNote.collectAsStateWithLifecycle()
    val libraryBusy by vm.libraryBusy.collectAsStateWithLifecycle()
    val importLibrary = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importLibrary(uri)
    }
    var askedNotification by rememberSaveable { mutableStateOf(false) }
    var dragThreshold by remember { mutableStateOf<Int?>(null) }
    var dragBigNight by remember { mutableStateOf<Int?>(null) }
    var dragAurora by remember { mutableStateOf<Int?>(null) }
    val shownThreshold = dragThreshold ?: s.alternativeThreshold
    val shownBigNight = (dragBigNight ?: s.bigNightThreshold)
        .coerceIn(BigNightAlerts.MIN_THRESHOLD, BigNightAlerts.MAX_THRESHOLD)
    val shownAurora = AuroraAlerts.clamp(dragAurora ?: s.auroraKpThreshold)
    val digestLabel = stringResource(R.string.daily_digest)
    val bigNightLabel = stringResource(R.string.big_night_alerts)
    val auroraLabel = stringResource(R.string.aurora_alerts)
    val nightVisionLabel = stringResource(R.string.night_vision)
    val issLabel = stringResource(R.string.iss_passes_setting)
    val resolvedUnits = s.resolvedUnits()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = DigestNotifier(context).canNotify()
    }
    LaunchedEffect(s.alternativeThreshold) {
        if (dragThreshold == s.alternativeThreshold) dragThreshold = null
    }
    LaunchedEffect(s.bigNightThreshold) {
        if (dragBigNight == s.bigNightThreshold) dragBigNight = null
    }
    LaunchedEffect(s.auroraKpThreshold) {
        if (dragAurora == s.auroraKpThreshold) dragAurora = null
    }
    LaunchedEffect(libraryNote) {
        val note = libraryNote ?: return@LaunchedEffect
        snackbar.showSnackbar(note)
        vm.consumeLibraryNote()
    }

    LifecycleResumeEffect(Unit) {
        canNotify = DigestNotifier(context).canNotify()
        vm.rescheduleDigest()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(NightBriefIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(stringResource(R.string.section_display)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nightVisionLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.night_vision_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.nightVision,
                        onCheckedChange = vm::setNightVision,
                        modifier = Modifier
                            .testTag("night-vision")
                            .semantics { contentDescription = nightVisionLabel },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.units_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        if (s.units == null) R.string.units_following_locale else R.string.units_chosen,
                        stringResource(
                            if (resolvedUnits == UnitSystem.METRIC) R.string.units_metric else R.string.units_imperial,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = resolvedUnits == UnitSystem.METRIC,
                        onClick = { vm.setUnits(UnitSystem.METRIC) },
                        label = { Text(stringResource(R.string.units_metric)) },
                        modifier = Modifier.testTag("units-metric"),
                    )
                    FilterChip(
                        selected = resolvedUnits == UnitSystem.IMPERIAL,
                        onClick = { vm.setUnits(UnitSystem.IMPERIAL) },
                        label = { Text(stringResource(R.string.units_imperial)) },
                        modifier = Modifier.testTag("units-imperial"),
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenPassport, modifier = Modifier.testTag("sky-passport")) {
                    Text(stringResource(R.string.sky_passport))
                }
            }

            SectionCard(stringResource(R.string.section_tonight_cards)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(issLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.iss_passes_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.issEnabled,
                        onCheckedChange = vm::setIssEnabled,
                        modifier = Modifier
                            .testTag("iss-enabled")
                            .semantics { contentDescription = issLabel },
                    )
                }
            }

            SectionCard(stringResource(R.string.section_what_i_shoot)) {
                Text(
                    stringResource(
                        if (s.gear.interests.isEmpty()) R.string.interests_summary_all else R.string.interests_summary_chosen,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                InterestPicker(s.gear.interests, onChange = { chosen -> vm.updateGear { it.withInterests(chosen) } })
                Spacer(Modifier.height(12.dp))
                CustomTargetsEditor(
                    targets = s.customTargets,
                    onAdd = vm::addCustomTarget,
                    onRemove = vm::removeCustomTarget,
                )
            }

            SectionCard(stringResource(R.string.section_morning_digest)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(digestLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.daily_digest_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.digestEnabled,
                        onCheckedChange = vm::setDigestEnabled,
                        modifier = Modifier
                            .testTag("digest-enabled")
                            .semantics { contentDescription = digestLabel },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bigNightLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.big_night_summary, shownBigNight),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.bigNightAlertsEnabled,
                        onCheckedChange = vm::setBigNightAlertsEnabled,
                        modifier = Modifier
                            .testTag("big-night-enabled")
                            .semantics { contentDescription = bigNightLabel },
                    )
                }
                TextButton(onClick = { showTime = true }) {
                    Text(stringResource(R.string.digest_time_button, clockLabel(s.digestTime)))
                }
                Text(
                    stringResource(R.string.digest_inexact),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val thresholdLabel = stringResource(R.string.alternative_threshold, shownThreshold)
                Text(
                    thresholdLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = shownThreshold.toFloat(),
                    onValueChange = { dragThreshold = it.roundToInt() },
                    onValueChangeFinished = {
                        val chosen = dragThreshold
                        if (chosen == null || chosen == s.alternativeThreshold) dragThreshold = null
                        else vm.setAlternativeThreshold(chosen)
                    },
                    valueRange = 5f..30f,
                    steps = 24,
                    modifier = Modifier.semantics {
                        contentDescription = thresholdLabel
                        stateDescription = shownThreshold.toString()
                    },
                )
                Text(
                    stringResource(R.string.alternative_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val bigNightSlider = stringResource(R.string.big_night_threshold, shownBigNight)
                Text(
                    bigNightSlider,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = shownBigNight.toFloat(),
                    onValueChange = { dragBigNight = it.roundToInt() },
                    onValueChangeFinished = {
                        val chosen = dragBigNight
                        if (chosen == null || chosen == s.bigNightThreshold) dragBigNight = null
                        else vm.setBigNightThreshold(chosen)
                    },
                    valueRange = BigNightAlerts.MIN_THRESHOLD.toFloat()..BigNightAlerts.MAX_THRESHOLD.toFloat(),
                    steps = BigNightAlerts.MAX_THRESHOLD - BigNightAlerts.MIN_THRESHOLD - 1,
                    modifier = Modifier
                        .testTag("big-night-threshold")
                        .semantics {
                            contentDescription = bigNightSlider
                            stateDescription = shownBigNight.toString()
                        },
                )
                Text(
                    stringResource(R.string.big_night_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(stringResource(R.string.section_notifications)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(auroraLabel, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.aurora_alerts_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.auroraAlertsEnabled,
                        onCheckedChange = vm::setAuroraAlertsEnabled,
                        modifier = Modifier
                            .testTag("aurora-enabled")
                            .semantics { contentDescription = auroraLabel },
                    )
                }
                if (s.auroraAlertsEnabled) {
                    Spacer(Modifier.height(8.dp))
                    val auroraSlider = stringResource(R.string.aurora_threshold, shownAurora)
                    Text(auroraSlider, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = shownAurora.toFloat(),
                        onValueChange = { dragAurora = it.roundToInt() },
                        onValueChangeFinished = {
                            val chosen = dragAurora
                            if (chosen == null || chosen == s.auroraKpThreshold) dragAurora = null
                            else vm.setAuroraKpThreshold(chosen)
                        },
                        valueRange = AuroraAlerts.MIN_THRESHOLD.toFloat()..AuroraAlerts.MAX_THRESHOLD.toFloat(),
                        steps = AuroraAlerts.MAX_THRESHOLD - AuroraAlerts.MIN_THRESHOLD - 1,
                        modifier = Modifier
                            .testTag("aurora-threshold")
                            .semantics {
                                contentDescription = auroraSlider
                                stateDescription = shownAurora.toString()
                            },
                    )
                    Text(
                        stringResource(R.string.aurora_threshold_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    stringResource(if (canNotify) R.string.notifications_allowed else R.string.notifications_off),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.notifications_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        requestNotifications(context, askedNotification, permissionLauncher) { askedNotification = true }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(notificationButtonLabel(context, canNotify))
                }
            }

            Button(
                onClick = {
                    vm.sendDigestNow()
                    digestNote = resources.getString(
                        if (canNotify) R.string.digest_queued else R.string.digest_queued_silent,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.send_digest_now)) }
            digestNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(stringResource(R.string.section_library)) {
                Text(
                    stringResource(R.string.library_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { if (!libraryBusy) scope.launch { vm.exportLibrary()?.let { shareLibrary(context, it) } } },
                    enabled = !libraryBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_library)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { if (!libraryBusy) importLibrary.launch("*/*") },
                    enabled = !libraryBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.import_library)) }
            }

            SectionCard(stringResource(R.string.section_about)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AboutVersion()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_open_meteo), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_air_quality), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_places), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_7timer), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_swpc), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_iss), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_comets), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_meteors), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_osm), style = MaterialTheme.typography.bodyMedium)
                Text(LightPollutionAttribution.TEXT, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_ephemeris), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_target_samples), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_icons), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showTime) {
        TimePickerDialog(
            initial = runCatching { LocalTime.parse(s.digestTime) }.getOrDefault(LocalTime.of(8, 0)),
            onDismiss = { showTime = false },
        ) { time ->
            showTime = false
            vm.setDigestTime("%02d:%02d".format(Locale.ROOT, time.hour, time.minute))
        }
    }
}

private const val PALE_BLUE_DOT_TAPS = 7

/** Seven taps on the version line reveal a short Pale Blue Dot excerpt. Fewer taps leave it hidden. */
@Composable
private fun AboutVersion() {
    var taps by remember { mutableIntStateOf(0) }
    Column {
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .testTag("about-version")
                .clickable { taps += 1 },
        )
        if (taps >= PALE_BLUE_DOT_TAPS) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pale_blue_dot), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.pale_blue_dot_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun clockLabel(hhmm: String): String = runCatching {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.parse(hhmm))
}.getOrDefault(hhmm)

private fun shareLibrary(context: Context, json: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.library_export_subject))
        putExtra(Intent.EXTRA_TEXT, json)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.export_library))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun notificationButtonLabel(context: Context, canNotify: Boolean): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
        return context.getString(R.string.allow_notifications)
    }
    return context.getString(if (canNotify) R.string.notification_settings else R.string.open_notification_settings)
}

private fun requestNotifications(
    context: Context,
    alreadyAsked: Boolean,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    markAsked: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission(context)) {
        context.openNotificationSettings()
        return
    }
    val activity = context.findActivity()
    val showRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    if (alreadyAsked && !showRationale) {
        context.openNotificationSettings()
    } else {
        markAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }.onFailure { openAppDetails() }
}

private fun Context.openAppDetails() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTargetsEditor(
    targets: List<Target>,
    onAdd: (Target) -> Unit,
    onRemove: (String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.custom_targets_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.custom_targets_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    targets.forEachIndexed { index, target ->
        if (index > 0) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        R.string.custom_target_spec,
                        target.kind.label,
                        "%.1f".format(Locale.ROOT, target.position.raDeg),
                        "%.1f".format(Locale.ROOT, target.position.decDeg),
                        target.maxBortle,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRemove(target.id) }) {
                Icon(
                    NightBriefIcons.Delete,
                    contentDescription = stringResource(R.string.remove_named, target.name),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (!adding) {
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().testTag("add-custom-target"),
        ) {
            Text(stringResource(R.string.add_custom_target))
        }
    } else {
        var name by rememberSaveable { mutableStateOf("") }
        var kindName by rememberSaveable { mutableStateOf(TargetKind.EMISSION_NEBULA.name) }
        var raText by rememberSaveable { mutableStateOf("") }
        var decText by rememberSaveable { mutableStateOf("") }
        var bortleText by rememberSaveable { mutableStateOf("6") }
        var focalText by rememberSaveable { mutableStateOf("135") }
        var tipText by rememberSaveable { mutableStateOf("") }
        val kind = TargetKind.customKinds.firstOrNull { it.name == kindName } ?: TargetKind.EMISSION_NEBULA
        val ra = raText.toDoubleOrNull()?.takeIf { it in 0.0..360.0 }
        val dec = decText.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val bortle = bortleText.toIntOrNull()?.takeIf { it in 1..9 }
        val focal = focalText.toIntOrNull()?.takeIf { it > 0 }
        val valid = name.isNotBlank() && ra != null && dec != null && bortle != null && focal != null

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.custom_target_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("custom-target-name"),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TargetKind.customKinds.forEach { option ->
                    FilterChip(
                        selected = option == kind,
                        onClick = { kindName = option.name },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("custom-target-kind-${option.name}"),
                    )
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints {
                val stacked = maxWidth < 320.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = raText,
                            onValueChange = { raText = it },
                            label = { Text(stringResource(R.string.custom_target_ra)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom-target-ra"),
                        )
                        OutlinedTextField(
                            value = decText,
                            onValueChange = { decText = it },
                            label = { Text(stringResource(R.string.custom_target_dec)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom-target-dec"),
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = raText,
                            onValueChange = { raText = it },
                            label = { Text(stringResource(R.string.custom_target_ra)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom-target-ra"),
                        )
                        OutlinedTextField(
                            value = decText,
                            onValueChange = { decText = it },
                            label = { Text(stringResource(R.string.custom_target_dec)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom-target-dec"),
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints {
                val stacked = maxWidth < 320.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bortleText,
                            onValueChange = { bortleText = it },
                            label = { Text(stringResource(R.string.custom_target_bortle)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom-target-bortle"),
                        )
                        OutlinedTextField(
                            value = focalText,
                            onValueChange = { focalText = it },
                            label = { Text(stringResource(R.string.custom_target_focal)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("custom-target-focal"),
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bortleText,
                            onValueChange = { bortleText = it },
                            label = { Text(stringResource(R.string.custom_target_bortle)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom-target-bortle"),
                        )
                        OutlinedTextField(
                            value = focalText,
                            onValueChange = { focalText = it },
                            label = { Text(stringResource(R.string.custom_target_focal)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom-target-focal"),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tipText,
                onValueChange = { tipText = it },
                label = { Text(stringResource(R.string.custom_target_tip)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("custom-target-tip"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { adding = false }) {
                    Text(stringResource(R.string.cancel))
                }
                val defaultTip = stringResource(
                    R.string.custom_target_default_tip,
                    kind.label.lowercase(LocalConfiguration.current.locales[0]),
                )
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        val custom = Target(
                            id = "custom-${UUID.randomUUID()}",
                            name = trimmed,
                            kind = kind,
                            position = RaDec(ra!!, dec!!),
                            maxBortle = bortle!!,
                            maxMoonIllumination = TargetCatalog.defaultMaxMoonIllumination(kind),
                            minFocalMm = (focal!! / 2).coerceAtLeast(10),
                            idealFocalMm = focal,
                            minAltitudeDeg = 20.0,
                            tip = tipText.trim().ifBlank { defaultTip },
                            tracksMoon = false,
                            requiresBrightMoon = false,
                        )
                        onAdd(custom)
                        adding = false
                    },
                    enabled = valid,
                    modifier = Modifier.testTag("save-custom-target"),
                ) {
                    Text(stringResource(R.string.save_custom_target))
                }
            }
        }
    }
}
