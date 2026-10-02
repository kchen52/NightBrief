package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.EmphasizedText
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.score.CloudAgreement
import app.nightbrief.score.ModelAgreement
import app.nightbrief.score.ModelConfidence

/**
 * Second-model cloud spread. Pure rendering of [ModelAgreement.assess]; null means
 * the second fetch failed and the card is not shown at all.
 */
@Composable
fun ConfidenceCard(agreement: CloudAgreement, modifier: Modifier = Modifier) {
    val color = when (agreement.confidence) {
        ModelConfidence.AGREE -> NightColors.Excellent
        ModelConfidence.MIXED -> NightColors.Amber
        ModelConfidence.DISAGREE -> NightColors.Poor
    }
    SectionCard(
        title = stringResource(R.string.section_confidence),
        icon = Icons.Filled.Cloud,
        modifier = modifier,
    ) {
        EmphasizedText(
            ModelAgreement.line(agreement),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.confidence_detail, agreement.hoursCompared),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
