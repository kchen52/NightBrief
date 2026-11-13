package app.nightbrief.app.ui.tonight

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.app.update.AppUpdateUiState

/**
 * Tonight banner when a newer GitHub APK exists.
 *
 * One tap downloads and opens Android's installer. A failed check never appears.
 * Returning to the foreground re-checks under the ViewModel's interval gate.
 */
@Composable
fun UpdateBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    val update by vm.appUpdate.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.checkForUpdate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    UpdateBannerContent(
        state = update,
        onInstall = vm::installUpdate,
        onSkip = vm::skipUpdate,
        onOpenPermission = vm::openInstallPermissionSettings,
        modifier = modifier,
    )
}

@Composable
internal fun UpdateBannerContent(
    state: AppUpdateUiState,
    onInstall: (app.nightbrief.app.update.AvailableUpdate) -> Unit,
    onSkip: (app.nightbrief.app.update.AvailableUpdate) -> Unit,
    onOpenPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = state.available ?: return
    val context = LocalContext.current
    Column(modifier.padding(horizontal = 16.dp)) {
        val text = when {
            state.needsInstallPermission -> stringResource(R.string.update_needs_permission)
            state.downloadFailed -> stringResource(R.string.update_download_failed, available.label)
            state.progress != null -> stringResource(R.string.update_downloading, available.label)
            else -> stringResource(R.string.update_available, available.label)
        }
        Banner(
            text = text,
            color = NightColors.Good,
            action = {
                when {
                    state.needsInstallPermission -> {
                        TextButton(onClick = onOpenPermission) {
                            Text(stringResource(R.string.update_open_settings))
                        }
                    }
                    state.progress != null -> { /* downloading */ }
                    else -> {
                        TextButton(onClick = { onInstall(available) }) {
                            Text(stringResource(R.string.update_install))
                        }
                        TextButton(onClick = { onSkip(available) }) {
                            Text(stringResource(R.string.update_skip))
                        }
                    }
                }
            },
        )
        val progress = state.progress
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            if (progress <= 0f) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.downloadFailed) {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(available.htmlUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) {
                Text(
                    stringResource(R.string.update_open_release),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
