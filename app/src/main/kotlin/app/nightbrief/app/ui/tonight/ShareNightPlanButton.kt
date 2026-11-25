package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.share.shareNightPlan
import app.nightbrief.app.share.shareWeekPlan
import app.nightbrief.app.ui.icons.NightBriefIcons
import app.nightbrief.score.NightReport
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ShareNightPlanButton(report: NightReport) {
    val context = LocalContext.current
    val resources = LocalResources.current
    ShareActionButton(
        contentDescription = stringResource(R.string.share_night_plan),
        preparingLabel = stringResource(R.string.preparing_night_plan),
        failedMessage = { resources.getString(R.string.share_night_plan_failed) },
        onShare = { shareNightPlan(context, report) },
    )
}

@Composable
internal fun ShareWeekButton(
    outlook: WeeklyOutlook,
    forecastStatus: ForecastStatus?,
    forecastFetchedAt: Instant?,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    ShareActionButton(
        contentDescription = stringResource(R.string.share_week),
        preparingLabel = stringResource(R.string.preparing_week_plan),
        failedMessage = { resources.getString(R.string.share_week_failed) },
        onShare = { shareWeekPlan(context, outlook, forecastStatus, forecastFetchedAt) },
    )
}

@Composable
private fun ShareActionButton(
    contentDescription: String,
    preparingLabel: String,
    failedMessage: () -> String,
    onShare: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box {
        IconButton(
            onClick = {
                if (busy) return@IconButton
                busy = true
                scope.launch {
                    val ok = onShare()
                    busy = false
                    if (!ok) {
                        snackbarHostState.showSnackbar(failedMessage())
                    }
                }
            },
            enabled = !busy,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        this.contentDescription = preparingLabel
                    },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(NightBriefIcons.Share, contentDescription = contentDescription)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
