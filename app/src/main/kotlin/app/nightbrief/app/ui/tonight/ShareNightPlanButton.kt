package app.nightbrief.app.ui.tonight

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.app.share.shareNightPlan
import app.nightbrief.score.NightReport
import kotlinx.coroutines.launch

@Composable
internal fun ShareNightPlanButton(report: NightReport) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            sharing = true
            scope.launch {
                try {
                    shareNightPlan(context, report)
                } finally {
                    sharing = false
                }
            }
        },
        enabled = !sharing,
    ) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_night_plan))
    }
}
