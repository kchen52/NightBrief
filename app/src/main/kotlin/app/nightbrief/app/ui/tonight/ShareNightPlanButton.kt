package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.share.printNightPlan
import app.nightbrief.app.share.shareNightPlan
import app.nightbrief.score.NightReport
import kotlinx.coroutines.launch

@Composable
internal fun ShareNightPlanButton(report: NightReport) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val preparingLabel = stringResource(R.string.preparing_night_plan)

    androidx.compose.foundation.layout.Box {
        IconButton(
            onClick = { picking = true },
            enabled = !busy,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = preparingLabel
                    },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_night_plan))
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { if (!busy) picking = false },
            title = { Text(stringResource(R.string.share_or_print)) },
            text = if (busy) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.preparing_night_plan))
                    }
                }
            } else null,
            confirmButton = {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            val ok = shareNightPlan(context, report)
                            busy = false
                            picking = false
                            if (!ok) {
                                snackbarHostState.showSnackbar(context.getString(R.string.share_night_plan_failed))
                            }
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.share)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            val ok = printNightPlan(context, report)
                            busy = false
                            picking = false
                            if (!ok) {
                                snackbarHostState.showSnackbar(context.getString(R.string.print_night_plan_failed))
                            }
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.print)) }
            },
        )
    }
}