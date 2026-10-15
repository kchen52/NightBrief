package app.nightbrief.app.ui.tonight

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.score.NightReport

/** Logs the night on screen, so the report's score, Bortle, ISS passes, and overnight low are kept. */
@Composable
fun LogNightPrompt(
    report: NightReport,
    alreadyLogged: Boolean,
    onSave: (rating: Int, note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(report.site.id, report.date.toString()) { mutableStateOf(false) }
    if (alreadyLogged) return
    if (!open) {
        TextButton(onClick = { open = true }, modifier = modifier) {
            Text(stringResource(R.string.log_this_night))
        }
    } else {
        SessionPromptCard(
            nightDate = report.date,
            score = report.scoreValue,
            onSave = onSave,
            onDismiss = { open = false },
            modifier = modifier,
        )
    }
}
