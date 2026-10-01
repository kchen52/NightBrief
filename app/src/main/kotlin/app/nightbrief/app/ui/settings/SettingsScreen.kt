package app.nightbrief.app.ui.settings

import android.Manifest
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
    var askedNotification by rememberSaveable { mutableStateOf(false) }
    var dragThreshold by remember { mutableStateOf<Int?>(null) }
    val shownThreshold = dragThreshold ?: s.alternativeThreshold

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
            SectionCard("Morning digest") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily digest", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Go/no-go summary for your primary site",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.digestEnabled, onCheckedChange = vm::setDigestEnabled)
                }
                TextButton(onClick = { showTime = true }) { Text("Time: ${clockLabel(s.digestTime)}") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mention another site when it's at least $shownThreshold points better",
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
                    "Between 5 and 30. A higher bar means fewer site suggestions in the digest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Notifications") {
                Text(
                    if (canNotify) "Notifications are allowed." else "Notifications are off.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "The morning digest is posted as a notification. Without permission it can't be delivered.",
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

            SectionCard("Exact alarms") {
                Text(exactAlarmStatus(canExact), style = MaterialTheme.typography.bodyLarge)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The digest still arrives without exact alarms, just less precisely. Android may deliver it later while the phone is idle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { context.openExactAlarmSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow exact alarms")
                    }
                }
            }

            Button(
                onClick = {
                    vm.sendDigestNow()
                    digestNote = if (canNotify) {
                        "Digest queued. It will show up as a notification in a moment."
                    } else {
                        "Digest queued, but notifications are off so it may not appear."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Send digest now") }
            digestNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard("About") {
                Text("NightBrief", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Weather by Open-Meteo.com (CC BY 4.0).", style = MaterialTheme.typography.bodyMedium)
                Text("Seeing and transparency by 7Timer!.", style = MaterialTheme.typography.bodyMedium)
                Text("Planetary Kp by NOAA SWPC.", style = MaterialTheme.typography.bodyMedium)
                Text("Map data © OpenStreetMap contributors.", style = MaterialTheme.typography.bodyMedium)
                Text(LightPollutionAttribution.TEXT, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "The light-pollution data is licensed for non-commercial use only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Ephemeris uses Astronomical Almanac low-precision formulas.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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

private fun exactAlarmStatus(canExact: Boolean): String = when {
    canExact -> "Exact alarms are allowed, so the digest fires at the time you set."
    else -> "Exact alarms are not allowed."
}

private fun notificationButtonLabel(context: Context, canNotify: Boolean): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
        return "Allow notifications"
    }
    return if (canNotify) "Notification settings" else "Open notification settings"
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
