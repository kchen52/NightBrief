package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.score.SessionEntry
import java.time.LocalDate

@Composable
fun SessionPromptCard(
    nightDate: LocalDate,
    score: Int?,
    onSave: (rating: Int, note: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rating by rememberSaveable(nightDate.toString()) { mutableIntStateOf(0) }
    var note by rememberSaveable(nightDate.toString()) { mutableStateOf("") }
    SectionCard(stringResource(R.string.session_prompt_title), modifier = modifier) {
        Text(
            stringResource(R.string.session_prompt_body, nightDate.toString()) +
                (score?.let { " (scored $it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (star in 1..5) {
                IconButton(
                    onClick = { rating = star },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.session_rate, star),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.session_note)) },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (rating in 1..5) onSave(rating, note) }, enabled = rating in 1..5) {
                Text(stringResource(R.string.session_save))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.session_dismiss)) }
        }
    }
}

@Composable
fun SessionHistoryCard(sessions: List<SessionEntry>, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.session_history), modifier = modifier) {
        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.session_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sessions.sortedByDescending { it.nightDate }.take(5).forEach { entry ->
                Column {
                    Text(
                        "${entry.nightDate} · ${"★".repeat(entry.rating)}${"☆".repeat(5 - entry.rating)}" +
                            (entry.score?.let { " · scored $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.note.isNotBlank()) {
                        Text(
                            entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
