package app.nightbrief.app.ui.passport

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.score.StampStatus

/**
 * One unseen stamp. [onDismiss] receives that stamp's id so Tonight can remember it.
 * A stamp already in [seenIds] is not shown again.
 */
@Composable
fun NewStampNotice(
    stamps: List<StampStatus>,
    seenIds: Set<String>,
    onDismiss: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fresh = stamps.filter { it.earned && it.id !in seenIds }
    if (fresh.isEmpty()) return
    val stamp = fresh.first()
    SectionCard(modifier = modifier) {
        Text(
            stringResource(R.string.passport_new_stamp, stamp.name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = { onDismiss(setOf(stamp.id)) }) {
            Text(stringResource(R.string.session_dismiss))
        }
    }
}
