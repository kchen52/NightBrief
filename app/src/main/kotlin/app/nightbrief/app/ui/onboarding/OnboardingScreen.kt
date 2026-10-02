package app.nightbrief.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nightbrief.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.TimePickerDialog
import app.nightbrief.app.ui.gear.GearEditor
import app.nightbrief.app.ui.sites.SiteForm
import app.nightbrief.app.ui.theme.NightColors
import java.time.LocalTime
import java.util.Locale

private const val STEP_WELCOME = 0
private const val STEP_SITE = 1
private const val STEP_GEAR = 2
private const val STEP_NOTIFICATIONS = 3
private const val STEP_COUNT = 4

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val draft by vm.onboardingSite.collectAsStateWithLifecycle()
    val gear by vm.onboardingGear.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var digestTime by rememberSaveable { mutableStateOf(s.digestTime.ifBlank { "08:00" }) }
    var showTime by remember { mutableStateOf(false) }
    var askedNotification by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed(context)) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    LaunchedEffect(s.gear) {
        if (vm.onboardingGear.value == null) vm.updateOnboardingGear(s.gear)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = notificationsAllowed(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = notificationsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(step) {
        if (step == STEP_NOTIFICATIONS && !askedNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = notificationsAllowed(context)
            if (!notificationsGranted) {
                askedNotification = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val canAdvance = step != STEP_SITE || draft.isValid

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { step -= 1 }, enabled = step > STEP_WELCOME) { Text(stringResource(R.string.back)) }
                if (step < STEP_NOTIFICATIONS) {
                    Button(onClick = { step += 1 }, enabled = canAdvance) { Text(stringResource(R.string.next)) }
                } else {
                    Button(onClick = { vm.completeOnboarding(digestTime) }) { Text(stringResource(R.string.finish)) }
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            StepIndicator(step, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInHorizontally(tween(280)) { width ->
                        if (forward) width / 4 else -width / 4
                    } + fadeIn(tween(280))
                    val exit = slideOutHorizontally(tween(200)) { width ->
                        if (forward) -width / 4 else width / 4
                    } + fadeOut(tween(180))
                    enter.togetherWith(exit)
                },
                label = "onboarding-step",
            ) { current ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                when (current) {
                    STEP_WELCOME -> WelcomeStep()
                    STEP_SITE -> {
                        Text(stringResource(R.string.onboarding_site_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.onboarding_site_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SiteForm(
                            draft = draft,
                            onChange = vm::updateOnboardingSite,
                            lookupBortle = vm::lookupBortle,
                            lookupTimeZone = vm::lookupTimeZone,
                            showPrimaryToggle = false,
                            showDigestOverride = false,
                            globalDigestTime = s.digestTime,
                        )
                        if (!draft.isValid) {
                            draft.errors.firstOrNull()?.let {
                                Text(stringResource(it), color = NightColors.Marginal, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    STEP_GEAR -> {
                        Text(stringResource(R.string.onboarding_gear_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.onboarding_gear_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val kit = gear
                        if (kit == null) {
                            CircularProgressIndicator()
                        } else {
                            GearEditor(kit, onChange = vm::updateOnboardingGear)
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.onboarding_digest_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.onboarding_digest_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showTime = true }) {
                            Text(stringResource(R.string.onboarding_digest_time, digestTime))
                        }
                        Text(
                            stringResource(R.string.onboarding_notify_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                enabled = !notificationsGranted,
                                modifier = Modifier.fillMaxWidth().testTag("allow-notifications"),
                            ) { Text(stringResource(R.string.allow_notifications)) }
                        }
                    }
                }
                }
            }
            }
        }
    }

    if (showTime) {
        TimePickerDialog(
            initial = runCatching { LocalTime.parse(digestTime) }.getOrDefault(LocalTime.of(8, 0)),
            onDismiss = { showTime = false },
        ) { time ->
            showTime = false
            digestTime = "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)
        }
    }
}

private fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

@Composable
private fun WelcomeStep() {
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Bullet(stringResource(R.string.onboarding_bullet_sites))
    Bullet(stringResource(R.string.onboarding_bullet_gear))
    Bullet(stringResource(R.string.onboarding_bullet_digest))
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StepIndicator(step: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.step_of, step + 1, STEP_COUNT),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEP_COUNT) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
    }
}
