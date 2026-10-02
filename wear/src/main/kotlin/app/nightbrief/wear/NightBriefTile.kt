package app.nightbrief.wear

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.TileBuilders
import app.nightbrief.score.Verdict
import app.nightbrief.score.WearGlance

object NightBriefTile {
    const val RESOURCES_VERSION = "1"

    fun build(context: Context, glance: WearGlance): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(Timeline.fromLayoutElement(layout(context, glance)))
            .setFreshnessIntervalMillis(60 * 60 * 1000L)
            .build()

    fun resources(): Resources = Resources.Builder().setVersion(RESOURCES_VERSION).build()

    fun layout(context: Context, glance: WearGlance): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (!glance.ready) {
            column.addContent(line(context, glance.verdictText, Typography.TYPOGRAPHY_TITLE3, TEXT_MUTED))
        } else {
            column.addContent(line(context, glance.siteName, Typography.TYPOGRAPHY_CAPTION1, TEXT_MUTED))
            column.addContent(line(context, glance.scoreText, Typography.TYPOGRAPHY_DISPLAY1, TEXT_PRIMARY))
            column.addContent(line(context, glance.verdictText, Typography.TYPOGRAPHY_TITLE2, verdictArgb(glance.verdictText)))
            glance.savedText?.let { saved ->
                column.addContent(line(context, saved, Typography.TYPOGRAPHY_CAPTION2, TEXT_MUTED))
            }
        }
        return column.build()
    }

    private fun line(context: Context, value: String, typography: Int, color: Int): LayoutElementBuilders.LayoutElement =
        Text.Builder(context, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(2)
            .build()

    private fun verdictArgb(verdict: String): Int = when (verdict) {
        Verdict.GO.label -> 0xFF4ADE80.toInt()
        Verdict.MAYBE.label -> 0xFFFBBF24.toInt()
        Verdict.NO_GO.label -> 0xFFF87171.toInt()
        else -> TEXT_MUTED
    }

    private const val TEXT_PRIMARY = 0xFFF4F6FB.toInt()
    private const val TEXT_MUTED = 0xFFB7BDD0.toInt()
}
