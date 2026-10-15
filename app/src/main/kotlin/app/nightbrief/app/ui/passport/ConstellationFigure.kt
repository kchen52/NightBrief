package app.nightbrief.app.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.nightbrief.score.ConstellationState

/** Stick figure for one constellation. Lit stars use the theme primary; the rest are an outline. */
@Composable
fun ConstellationFigure(state: ConstellationState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val litColor = scheme.primary
    val dim = if (state.unlocked) scheme.outline else scheme.outlineVariant
    val lineColor = if (state.unlocked) scheme.primary else scheme.outline
    Canvas(modifier.size(160.dp)) {
        val width = size.width
        val height = size.height
        fun point(index: Int): Offset {
            val star = state.layout.stars[index]
            return Offset(star.x * width, star.y * height)
        }
        for ((start, end) in state.layout.lines) {
            drawLine(lineColor, point(start), point(end), strokeWidth = 2.dp.toPx())
        }
        state.layout.stars.forEachIndexed { index, _ ->
            val center = point(index)
            if (state.unlocked && index < state.lit) {
                drawCircle(litColor, radius = 6.dp.toPx(), center = center)
            } else {
                drawCircle(dim, radius = 5.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
