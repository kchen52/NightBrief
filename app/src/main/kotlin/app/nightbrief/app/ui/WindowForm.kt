package app.nightbrief.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * How the phone screens fit the window they are in.
 *
 * [MEDIUM_WIDTH_DP] and [MEDIUM_HEIGHT_DP] are the Material medium breakpoints.
 * An unfolded fold is portrait and still wider than [MEDIUM_WIDTH_DP], with a
 * height of at least [MEDIUM_HEIGHT_DP], so the rail and the two-column night
 * follow width and height. A landscape phone is wide and shorter than
 * [MEDIUM_HEIGHT_DP], so the hero and sky sit side by side and the rest stays
 * one column. A horizontal separating hinge is tabletop: the bottom bar stays.
 * A vertical hinge is kept so the columns can sit on either side of the crease.
 * When the hinge cannot be read, a wide window still uses the wide layout and
 * the columns are equal.
 */
internal data class WindowForm(
    val navigation: Navigation,
    val detail: Detail,
    val fold: FoldSpan?,
) {
    val weekColumns: Int get() = if (detail == Detail.Panes) 2 else 1

    enum class Navigation { Bar, Rail }

    enum class Detail {
        Stacked,
        Lead,
        Panes,
    }

    companion object {
        const val MEDIUM_WIDTH_DP = 600
        const val MEDIUM_HEIGHT_DP = 480

        fun of(widthDp: Int, heightDp: Int, landscape: Boolean, fold: FoldSpan?): WindowForm {
            val tabletop = fold != null && !fold.vertical && fold.separating
            val wide = !tabletop && (widthDp >= MEDIUM_WIDTH_DP || landscape)
            val tall = !tabletop && heightDp >= MEDIUM_HEIGHT_DP
            val detail = when {
                wide && tall -> Detail.Panes
                wide -> Detail.Lead
                else -> Detail.Stacked
            }
            return WindowForm(
                navigation = if (wide) Navigation.Rail else Navigation.Bar,
                detail = detail,
                fold = fold?.takeIf { it.vertical && detail == Detail.Panes },
            )
        }
    }
}

/**
 * A hinge in window pixels.
 *
 * [vertical] is a book fold. [separating] is a hinge the window treats as a
 * split, including tabletop. [occludes] means pixels under the hinge are not
 * shown, so the gutter has to be at least that wide.
 */
internal data class FoldSpan(
    val leftPx: Int,
    val rightPx: Int,
    val vertical: Boolean,
    val separating: Boolean,
    val occludes: Boolean,
)

/**
 * Column weights that put the gutter on a vertical hinge.
 *
 * Null when the hinge misses the content, or when either side would be under
 * a fifth of the width. [PaneWeights.gapPx] is the occluding hinge width, and
 * 0 when the crease does not hide pixels.
 */
internal data class PaneWeights(val left: Float, val right: Float, val gapPx: Int)

internal fun paneWeights(
    contentLeftPx: Int,
    contentWidthPx: Int,
    foldLeftPx: Int,
    foldRightPx: Int,
    occludes: Boolean,
): PaneWeights? {
    if (contentWidthPx <= 0) return null
    val hingeLeft = foldLeftPx - contentLeftPx
    val hingeRight = foldRightPx - contentLeftPx
    if (hingeRight <= 0 || hingeLeft >= contentWidthPx) return null
    val leftPx = hingeLeft.coerceAtLeast(0)
    val rightPx = (contentWidthPx - hingeRight).coerceAtLeast(0)
    val minSide = contentWidthPx * 0.2f
    if (leftPx < minSide || rightPx < minSide) return null
    val gapPx = if (occludes) (foldRightPx - foldLeftPx).coerceAtLeast(0) else 0
    return PaneWeights(leftPx.toFloat(), rightPx.toFloat(), gapPx)
}

@Composable
internal fun rememberWindowForm(): WindowForm {
    val configuration = LocalConfiguration.current
    val fold = rememberFoldSpan()
    return WindowForm.of(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        fold = fold,
    )
}

/**
 * Two columns. When [fold] crosses this row, the weights sit on either side
 * of it and an occluding hinge becomes the gutter. Otherwise the columns are equal.
 */
@Composable
internal fun FoldColumns(
    fold: FoldSpan?,
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    var weights by remember(fold) { mutableStateOf<PaneWeights?>(null) }
    val density = LocalDensity.current
    val gap = with(density) { (weights?.gapPx ?: 0).toDp() }
    Row(
        modifier
            .fillMaxWidth()
            .testTag("night-panes")
            .onGloballyPositioned { coords ->
                val span = fold ?: return@onGloballyPositioned
                val next = paneWeights(
                    contentLeftPx = coords.positionInWindow().x.roundToInt(),
                    contentWidthPx = coords.size.width,
                    foldLeftPx = span.leftPx,
                    foldRightPx = span.rightPx,
                    occludes = span.occludes,
                )
                if (next != weights) weights = next
            },
        horizontalArrangement = Arrangement.spacedBy(if (gap > 0.dp) 0.dp else 14.dp),
    ) {
        Column(
            Modifier.weight(weights?.left ?: 1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = left,
        )
        if (gap > 0.dp) Spacer(Modifier.width(gap))
        Column(
            Modifier.weight(weights?.right ?: 1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = right,
        )
    }
}

@Composable
private fun rememberFoldSpan(): FoldSpan? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val span = produceState<FoldSpan?>(initialValue = null, activity) {
        val host = activity ?: return@produceState
        try {
            WindowInfoTracker.getOrCreate(host)
                .windowLayoutInfo(host)
                .collect { info ->
                    value = info.displayFeatures.filterIsInstance<FoldingFeature>().primary()?.toFoldSpan()
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            value = null
        }
    }
    return span.value
}

private fun List<FoldingFeature>.primary(): FoldingFeature? =
    firstOrNull { it.isSeparating }
        ?: firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?: firstOrNull()

private fun FoldingFeature.toFoldSpan(): FoldSpan = FoldSpan(
    leftPx = bounds.left,
    rightPx = bounds.right,
    vertical = orientation == FoldingFeature.Orientation.VERTICAL,
    separating = isSeparating,
    occludes = occlusionType == FoldingFeature.OcclusionType.FULL,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
