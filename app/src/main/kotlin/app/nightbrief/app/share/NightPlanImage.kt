package app.nightbrief.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.score.NightPlanCard

/**
 * Renders a [NightPlanCard] to a bitmap for sharing.
 * Always the blue night palette, so a night-vision screen does not paint the picture red.
 */
object NightPlanImage {
    const val WIDTH = 1080
    private const val PAD = 80

    fun render(card: NightPlanCard): Bitmap {
        val palette = NightPalette.Standard
        val textWidth = WIDTH - PAD * 2
        val scoreColor = palette.forScore(card.score).toArgb()
        val onBackground = palette.onBackground.toArgb()
        val muted = palette.textMuted.toArgb()

        val wordmark = text("NIGHTBRIEF", paint(28f, palette.primary.toArgb(), bold = true, tracking = 0.18f), textWidth)
        val site = text(card.siteName, paint(64f, onBackground, bold = true), textWidth)
        val meta = text("${card.place} · ${card.dateLabel}", paint(32f, muted), textWidth)
        val score = text(card.scoreText, paint(156f, scoreColor, bold = true), textWidth)
        val verdict = text(card.verdictLabel, paint(40f, scoreColor, bold = true), textWidth)
        val details = listOfNotNull(
            card.bestWindow?.let { "Best $it" },
            card.milkyWay,
            card.target?.let { "Try: $it" },
        ).map { text(it, paint(38f, onBackground), textWidth) }
        val saved = card.saved?.let { text(it, paint(32f, palette.amber.toArgb()), textWidth) }

        val detailGap = 18
        val detailsHeight = if (details.isEmpty()) {
            0
        } else {
            details.sumOf { it.height } + detailGap * (details.size - 1)
        }
        var height = PAD + wordmark.height + 40 + site.height + 10 + meta.height + 48
        height += score.height + 6 + verdict.height + 40 + detailsHeight
        if (saved != null) height += 28 + saved.height
        height += PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(palette.background.toArgb())
        val canvas = Canvas(bitmap)
        var y = PAD.toFloat()
        fun draw(layout: StaticLayout, gapAfter: Int) {
            canvas.save()
            canvas.translate(PAD.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gapAfter
        }
        draw(wordmark, 40)
        draw(site, 10)
        draw(meta, 48)
        draw(score, 6)
        draw(verdict, 40)
        details.forEachIndexed { index, layout ->
            draw(layout, if (index == details.lastIndex) 0 else detailGap)
        }
        if (saved != null) {
            y += 28f
            draw(saved, 0)
        }
        return bitmap
    }

    private fun text(value: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    private fun paint(size: Float, color: Int, bold: Boolean = false, tracking: Float = 0f): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = tracking
        }
}
