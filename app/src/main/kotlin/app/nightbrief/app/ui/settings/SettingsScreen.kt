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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.data.LibraryFormatException
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.data.LightPollutionAttribution
import app.nightbrief.app.ui.common.TimePickerDialog
import app.nightbrief.work.DigestNotifier
import app.nightbrief.work.DigestScheduler
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val context = LocalContext.current
    var showTime by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(DigestNotifier(context).canNotify()) }
    var canExact by remember { mutableStateOf(DigestScheduler.canScheduleExact(context)) }
    var digestNote by remember { mutableStateOf<String?>(null) }
    var libraryNote by remember { mutableStateOf<String?>(null) }
    val importLibrary = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull()
        libraryNote = if (text.isNullOrBlank()) {
            context.getString(R.string.library_import_failed)
        } else {
            try {
                vm.importLibrary(text)
                context.getString(R.string.library_imported)
            } catch (_: LibraryFormatException) {
                context.getString(R.string.library_import_failed)
            }
        }
    }
    var askedNotification by rememberSaveable { mutableStateOf(false) }
    var dragThreshold by remember { mutableStateOf<Int?>(null) }
    val shownThreshold = dragThreshold ?: s.alternativeThreshold
    val digestLabel = stringResource(R.string.daily_digest)
    val bigNightLabel = stringResource(R.string.big_night_alerts)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = DigestNotifier(context).canNotify()
    }
    LaunchedEffect(s.alternativeThreshold) {
        if (dragThreshold == s.alternativeThreshold) dragThreshold = null
    }

    LifecycleResumeEffect(Unit) {
        canNotify = DigestNotifier(context).canNotify()
        canExact = DigestScheduler.canScheduleExact(context)
        vm.rescheduleDigest()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                            stringResource(R.string.big_night_summary),
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
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alternative_threshold, shownThreshold),
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
                )
                Text(
                    stringResource(R.string.alternative_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(stringResource(R.string.section_notifications)) {
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

            SectionCard(stringResource(R.string.section_exact_alarms)) {
                Text(exactAlarmStatus(context, canExact), style = MaterialTheme.typography.bodyLarge)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.exact_alarms_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { context.openExactAlarmSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.allow_exact_alarms))
                    }
                }
            }

            Button(
                onClick = {
                    vm.sendDigestNow()
                    digestNote = context.getString(
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
                    onClick = {
                        val json = vm.exportLibrary() ?: return@OutlinedButton
                        shareLibrary(context, json)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_library)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLibrary.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.import_library)) }
                libraryNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(stringResource(R.string.section_about)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_open_meteo), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_7timer), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_swpc), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_iss), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_meteors), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_osm), style = MaterialTheme.typography.bodyMedium)
                Text(LightPollutionAttribution.TEXT, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_ephemeris), style = MaterialTheme.typography.bodyMedium)
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

private fun exactAlarmStatus(context: Context, canExact: Boolean): String =
    context.getString(if (canExact) R.string.exact_alarms_allowed else R.string.exact_alarms_denied)

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

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:$packageName"))
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
