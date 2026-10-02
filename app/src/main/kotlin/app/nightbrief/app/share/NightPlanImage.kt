package app.nightbrief.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.score.NightPlanCard
import java.util.Random
import kotlin.math.roundToInt

/**
 * Renders a [NightPlanCard] to a bitmap for sharing.
 * Always the blue night palette, so a night-vision screen does not paint the picture red.
 *
 * Layout fills the 1080px frame: a header row pairs the "NightBrief" wordmark (left)
 * with a date pill (right), the score sits in a ring badge beside a surface card
 * with the night's details, and a centered footer signs the picture "NightBrief".
 * An estimated score adds a small proxy note under the verdict.
 */
object NightPlanImage {
    const val WIDTH = 1080
    private const val PAD = 80

    private const val SCORE_COL = 320
    private const val HERO_GAP = 48
    private const val BADGE_DIAMETER = 300f
    private const val CARD_PAD = 36
    private const val CARD_RADIUS = 36f

    fun render(card: NightPlanCard): Bitmap {
        val palette = NightPalette.Standard
        val textWidth = WIDTH - PAD * 2
        val scoreColor = palette.forScore(card.score).toArgb()
        val onBackground = palette.onBackground.toArgb()
        val muted = palette.textMuted.toArgb()
        val primary = palette.primary.toArgb()
        val surface = palette.surface.toArgb()
        val outline = palette.outlineVariant.toArgb()

        // Header: wordmark left, date pill right on one row. Columns never overlap:
        // the pill is capped, single-line, and the wordmark takes the remainder.
        val appPaint = paint(52f, primary, bold = true, tracking = 0.06f)
        val datePaint = paint(32f, onBackground, bold = true)
        val dateTextWidth = datePaint.measureText(card.dateLabel)
        val pillWidth = (dateTextWidth + 64f).toInt().coerceIn(120, textWidth / 2)
        val appWidth = (textWidth - pillWidth - 24).coerceAtLeast(1)
        val appName = text("NightBrief", appPaint, appWidth, maxLines = 1)
        val dateLayout = text(
            card.dateLabel.ifBlank { "—" },
            datePaint,
            (pillWidth - 64).coerceAtLeast(1),
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1,
        )
        val pillHeight = dateLayout.height + 32
        val headerHeight = maxOf(appName.height, pillHeight)

        val site = text(card.siteName.ifBlank { "Untitled site" }, paint(66f, onBackground, bold = true), textWidth, maxLines = 2)
        val meta = text(card.place, paint(32f, muted), textWidth, maxLines = 1)

        // Hero left: score badge + verdict (+ estimated note).
        val scoreColWidth = if (hasDetails(card)) SCORE_COL else textWidth
        val score = text(card.scoreText, paint(160f, scoreColor, bold = true), scoreColWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        val verdict = text(card.verdictLabel, paint(38f, scoreColor, bold = true), scoreColWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 2)
        val estimatedNote = if (card.estimated) {
            text("Estimated", paint(28f, muted), scoreColWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        } else {
            null
        }
        val scoreBlockHeight = BADGE_DIAMETER + 20f + verdict.height +
            if (estimatedNote != null) 12f + estimatedNote.height else 0f

        // Hero right: details ride in a surface card so the wide frame is filled.
        // Skipped entirely when every row is absent, so no empty sliver renders.
        val cardInnerWidth = textWidth - SCORE_COL - HERO_GAP - CARD_PAD * 2
        val detailRows = listOfNotNull(
            card.bestWindow?.let { "•  Best $it" to true },
            card.milkyWay.takeIf { it.isNotBlank() }?.let { "•  $it" to false },
            card.target?.let { "•  Try $it" to false },
        ).map { (value, bold) ->
            text(value, paint(34f, onBackground, bold = bold), cardInnerWidth, maxLines = 2)
        }
        val rowGap = 22
        val cardHeight = if (detailRows.isEmpty()) {
            0
        } else {
            CARD_PAD * 2 + detailRows.sumOf { it.height } + rowGap * (detailRows.size - 1)
        }
        val heroHeight = maxOf(scoreBlockHeight.toInt(), cardHeight)

        val saved = card.saved?.let { text("•  $it", paint(32f, palette.amber.toArgb()), textWidth, maxLines = 2) }
        val footerTitle = text("Shared with NightBrief", paint(34f, onBackground, bold = true), textWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 1)
        val footerSub = text("Is tonight worth imaging?", paint(32f, muted), textWidth, Layout.Alignment.ALIGN_CENTER, maxLines = 1)

        var height = PAD + headerHeight + 28 + site.height + 12 + meta.height + 40
        height += heroHeight + 40
        if (saved != null) height += saved.height + 32
        height += 3 + 36 + footerTitle.height + 10 + footerSub.height + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(palette.background.toArgb())
        val canvas = Canvas(bitmap)

        drawStars(canvas, WIDTH, height)

        var y = PAD.toFloat()
        // Header row.
        canvas.save()
        canvas.translate(PAD.toFloat(), y + (headerHeight - appName.height) / 2f)
        appName.draw(canvas)
        canvas.restore()
        val pillLeft = (PAD + textWidth - pillWidth).toFloat()
        val pillTop = y + (headerHeight - pillHeight) / 2f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surfaceHigh.toArgb()
        })
        canvas.save()
        canvas.translate(pillLeft + 32f, pillTop + 16f)
        dateLayout.draw(canvas)
        canvas.restore()
        y += headerHeight + 28

        y = drawAt(canvas, site, PAD.toFloat(), y) + 12
        y = drawAt(canvas, meta, PAD.toFloat(), y) + 40

        // Divider under the site block.
        y = drawDivider(canvas, y, outline) + 40

        // Hero: badge (+ verdict) and details card share the row, both centered
        // in the row so a tall card does not strand the badge at the top.
        val heroTop = y
        val scoreBlockTop = heroTop + (heroHeight.toFloat() - scoreBlockHeight) / 2f
        val badgeCenterX = if (hasDetails(card)) {
            PAD.toFloat() + SCORE_COL.toFloat() / 2f
        } else {
            PAD.toFloat() + textWidth.toFloat() / 2f
        }
        val badgeCenterY = scoreBlockTop + BADGE_DIAMETER / 2f
        val fillAlpha = if (card.score == null) 0 else 36
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scoreColor
            alpha = fillAlpha
            style = Paint.Style.FILL
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scoreColor
            alpha = if (card.score == null) 140 else 200
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val badgeLeft = PAD.toFloat()
        canvas.drawCircle(badgeCenterX, badgeCenterY, BADGE_DIAMETER / 2f, fill)
        canvas.drawCircle(badgeCenterX, badgeCenterY, BADGE_DIAMETER / 2f, ring)
        // Tight StaticLayout box (includePad=false) already centers the glyphs.
        drawAt(canvas, score, badgeLeft, badgeCenterY - score.height / 2f)
        val verdictBottom = drawAt(canvas, verdict, badgeLeft, scoreBlockTop + BADGE_DIAMETER + 20)
        if (estimatedNote != null) {
            drawAt(canvas, estimatedNote, badgeLeft, verdictBottom + 12)
        }

        if (detailRows.isNotEmpty()) {
            val cardLeft = (PAD + SCORE_COL + HERO_GAP).toFloat()
            val cardTop = (heroTop + (heroHeight - cardHeight) / 2f).roundToInt().toFloat()
            val cardRect = RectF(cardLeft, cardTop, cardLeft + textWidth - SCORE_COL - HERO_GAP, cardTop + cardHeight)
            canvas.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = surface
            })
            var rowY = cardTop + CARD_PAD
            detailRows.forEachIndexed { index, layout ->
                drawAt(canvas, layout, cardLeft + CARD_PAD, rowY)
                rowY += layout.height + if (index == detailRows.lastIndex) 0 else rowGap
            }
        }
        y = heroTop + heroHeight + 40

        if (saved != null) {
            y = drawAt(canvas, saved, PAD.toFloat(), y) + 32
        }

        y = drawDivider(canvas, y, outline) + 36
        y = drawAt(canvas, footerTitle, PAD.toFloat(), y) + 10
        drawAt(canvas, footerSub, PAD.toFloat(), y)
        return bitmap
    }

    private fun hasDetails(card: NightPlanCard): Boolean =
        card.bestWindow != null || card.milkyWay.isNotBlank() || card.target != null

    private fun drawAt(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun drawDivider(canvas: Canvas, y: Float, color: Int): Float {
        val lineY = y.roundToInt().toFloat()
        canvas.drawLine(
            PAD.toFloat(), lineY, (WIDTH - PAD).toFloat(), lineY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = 3f
            },
        )
        return lineY + 3
    }

    /**
     * A faint deterministic starfield kept to the frame margins, so it never
     * sits under body copy and never touches the (0, 0) corner pixel.
     */
    private fun drawStars(canvas: Canvas, width: Int, height: Int) {
        val random = Random(0x2E1A4L)
        val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        var drawn = 0
        var guard = 0
        while (drawn < 40 && guard++ < 400) {
            val edge = random.nextInt(4)
            val x = when (edge) {
                0 -> 24f + random.nextFloat() * (width.toFloat() - 48f) // top margin
                1 -> 24f + random.nextFloat() * (width.toFloat() - 48f) // bottom margin
                2 -> 24f + random.nextFloat() * (PAD.toFloat() - 48f) // left gutter
                else -> (width.toFloat() - PAD.toFloat() + 24f) + random.nextFloat() * (PAD.toFloat() - 48f) // right gutter
            }
            val y = when (edge) {
                0 -> 24f + random.nextFloat() * (PAD.toFloat() / 2f - 24f).coerceAtLeast(1f)
                1 -> (height.toFloat() - PAD.toFloat() / 2f) + random.nextFloat() * (PAD.toFloat() / 2f - 24f).coerceAtLeast(1f)
                else -> 24f + random.nextFloat() * (height.toFloat() - 48f)
            }
            if (x < 24f || y < 24f) continue
            star.alpha = 24 + random.nextInt(37)
            canvas.drawCircle(x, y, 1.5f + random.nextFloat() * 1.5f, star)
            drawn++
        }
    }

    private fun text(
        value: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) {
            builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false, tracking: Float = 0f): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = tracking
        }
}
