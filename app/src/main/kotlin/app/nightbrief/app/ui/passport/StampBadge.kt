package app.nightbrief.app.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.score.StampStatus
import kotlin.math.cos
import kotlin.math.sin

/** Circular postmark. Earned stamps use the theme primary; locked ones stay on the outline. */
@Composable
fun StampBadge(stamp: StampStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val earned = stamp.earned
    val ink = if (earned) scheme.primary else scheme.outline
    Column(
        modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(112.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            if (earned) {
                drawCircle(scheme.primaryContainer, radius = radius * 0.84f, center = center)
            }
            val ticks = 28
            for (index in 0 until ticks) {
                val angle = index * (Math.PI * 2.0 / ticks)
                val inner = radius * 0.90f
                val outer = radius * 0.99f
                drawLine(
                    ink,
                    center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                    center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                    strokeWidth = 2.5f,
                )
            }
            drawCircle(ink, radius = radius * 0.84f, center = center, style = Stroke(width = 2.5f))
            drawCircle(
                ink,
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
            )
        }
        Text(
            stamp.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (earned) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (stamp.earnedOn != null) {
                stringResource(R.string.passport_earned_on, stamp.earnedOn.toString())
            } else {
                stamp.hint
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (earned) scheme.primary else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
