package app.nightbrief.app.ui.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.gear.TargetInterest

/**
 * Chips for [TargetInterest]. An empty selection means no preference, so the clear button only
 * appears once something is chosen. Shared by Settings and the onboarding Gear step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestPicker(
    selected: Set<TargetInterest>,
    onChange: (Set<TargetInterest>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TargetInterest.entries.forEach { interest ->
            val on = interest in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - interest else selected + interest) },
                label = { Text(interest.label) },
                modifier = Modifier.testTag("interest-${interest.name.lowercase()}"),
            )
        }
    }
    if (selected.isNotEmpty()) {
        TextButton(onClick = { onChange(emptySet()) }, modifier = Modifier.testTag("interest-clear")) {
            Text(stringResource(R.string.interests_clear))
        }
    }
}
