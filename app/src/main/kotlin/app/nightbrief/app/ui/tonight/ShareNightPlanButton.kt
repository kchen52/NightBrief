package app.nightbrief.app.ui.tonight

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    IconButton(
        onClick = { picking = true },
        enabled = !busy,
    ) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_night_plan))
    }
    if (picking) {
        // The share sheet's own Print target mangles the card, so printing is offered here
        // first and runs NightBrief's print path, which keeps the name, the bytes, and the fit.
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.share_or_print)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        picking = false
                        busy = true
                        scope.launch {
                            try {
                                shareNightPlan(context, report)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.share)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        picking = false
                        busy = true
                        scope.launch {
                            try {
                                printNightPlan(context, report)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.print)) }
            },
        )
    }
}
