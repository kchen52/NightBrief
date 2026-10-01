package app.nightbrief.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
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
    val scroll = rememberScrollState()

    LaunchedEffect(s.gear) {
        if (vm.onboardingGear.value == null) vm.onboardingGear.value = s.gear
    }
    LaunchedEffect(step) { scroll.scrollTo(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(step) {
        if (step == STEP_NOTIFICATIONS && !askedNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotification = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                TextButton(onClick = { step -= 1 }, enabled = step > STEP_WELCOME) { Text("Back") }
                if (step < STEP_NOTIFICATIONS) {
                    Button(onClick = { step += 1 }, enabled = canAdvance) { Text("Next") }
                } else {
                    Button(onClick = { vm.completeOnboarding(digestTime) }) { Text("Finish") }
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    STEP_WELCOME -> WelcomeStep()
                    STEP_SITE -> {
                        Text("Your first site", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Where you shoot from. Darkness, moon, and the forecast are all scored for this place.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SiteForm(
                            draft = draft,
                            onChange = { vm.onboardingSite.value = it },
                            lookupBortle = vm::lookupBortle,
                            showPrimaryToggle = false,
                            showDigestOverride = false,
                            globalDigestTime = s.digestTime,
                        )
                        if (!draft.isValid) {
                            draft.errors.firstOrNull()?.let {
                                Text(it, color = NightColors.Marginal, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    STEP_GEAR -> {
                        Text("Your gear", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Exposure hints use your primary camera and lenses. A Canon R7 and Sigma 10–18mm f/2.8 are filled in so you can start tonight — change anything.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val kit = gear
                        if (kit == null) {
                            CircularProgressIndicator()
                        } else {
                            GearEditor(kit, onChange = { vm.onboardingGear.value = it })
                        }
                    }
                    else -> {
                        Text("Morning digest", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "NightBrief sends a go/no-go summary at the time you pick. You can change it later in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showTime = true }) { Text("Digest time: $digestTime") }
                        Text(
                            "Allow notifications so the digest can reach you. On Android 13 and later the system asks first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Allow notifications") }
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

@Composable
private fun WelcomeStep() {
    Text("NightBrief", style = MaterialTheme.typography.displaySmall)
    Text(
        "A morning go/no-go for astrophotography. NightBrief scores tonight's sky at your site — clouds, moon, darkness, and seeing — and tells you whether to head out.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Bullet("Save the places you shoot from.")
    Bullet("Add your camera and lenses for exposure hints.")
    Bullet("Get a digest before the day gets busy.")
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StepIndicator(step: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "Step ${step + 1} of $STEP_COUNT",
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
