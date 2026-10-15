package app.nightbrief.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
 * The frame is a postcard: a paper border, a perforated stamp in the corner, and a
 * circular postmark of [NightPlanCard.dateLabel] and [NightPlanCard.place] (already
 * rounded to 0.1°). Latitude and longitude are separate lines inside the ring and
 * are scaled down until the whole place fits, so the location is never ellipsized.
 * The date pill and the site name stay left of the stamp. The site pin is not drawn.
 *
 * Layout fills the 1080px frame: a header row pairs the "NightBrief" wordmark (left)
 * with a date pill (right), the score sits in a ring badge beside a surface card
 * with the night's details, and a centered footer signs the picture "NightBrief".
 * An estimated score adds a small proxy note under the verdict.
 */
object NightPlanImage {
    const val WIDTH = 1080
    private const val PAD = 80
    private const val FRAME_INSET = 28f
    private const val STAMP_WIDTH = 200f
    private const val STAMP_HEIGHT = 240f
    /** Gap between the stamp and the date pill / site-name column. */
    private const val STAMP_CLEARANCE = 40f
    private const val POSTMARK_DATE_START = 18f
    private const val POSTMARK_PLACE_START = 16f
    private const val POSTMARK_MIN = 11f

    /** Top-right stamp. Tests sample this rect; the postmark is drawn inside it. */
    fun stampRect(): RectF {
        val right = WIDTH - FRAME_INSET - 16f
        val top = FRAME_INSET + 16f
        return RectF(right - STAMP_WIDTH, top, right, top + STAMP_HEIGHT)
    }

    private const val SCORE_COL = 320
    private const val HERO_GAP = 48
    private const val BADGE_DIAMETER = 300f
    private const val CARD_PAD = 36
    private const val CARD_RADIUS = 36f

    fun render(card: NightPlanCard): Bitmap {
        val palette = NightPalette.Standard
        val textWidth = WIDTH - PAD * 2
        val stamp = stampRect()
        val besideStamp = (stamp.left - STAMP_CLEARANCE - PAD).toInt().coerceIn(280, textWidth)
        val scoreColor = palette.forScore(card.score).toArgb()
        val onBackground = palette.onBackground.toArgb()
        val muted = palette.textMuted.toArgb()
        val primary = palette.primary.toArgb()
        val surface = palette.surface.toArgb()
        val outline = palette.outlineVariant.toArgb()

        // Header: wordmark left, date pill right. The pill's right edge is the
        // site column's right edge, a fixed gap left of the stamp.
        val appPaint = paint(52f, primary, bold = true, tracking = 0.06f)
        val dateLabel = card.dateLabel.ifBlank { "—" }
        val maxPill = besideStamp / 2
        val dateBudget = (maxPill - 64).toFloat().coerceAtLeast(40f)
        val dateSize = sizeThatFits(listOf(dateLabel), dateBudget, bold = true, start = 32f, min = 12f)
        val datePaint = paint(dateSize, onBackground, bold = true)
        val dateTextWidth = datePaint.measureText(dateLabel)
        val pillWidth = (dateTextWidth + 64f).toInt().coerceIn(120, maxPill)
        val appWidth = (besideStamp - pillWidth - 24).coerceAtLeast(1)
        val appName = text("NightBrief", appPaint, appWidth, maxLines = 1)
        val dateLayout = text(
            dateLabel,
            datePaint,
            (pillWidth - 64).coerceAtLeast(1),
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1,
            ellipsize = false,
        )
        val pillHeight = dateLayout.height + 32
        val headerHeight = maxOf(appName.height, pillHeight)

        val site = text(card.siteName.ifBlank { "Untitled site" }, paint(66f, onBackground, bold = true), besideStamp, maxLines = 2)
        val meta = fittedPlace(card.place, muted, besideStamp)

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

        val beforeHero = PAD + headerHeight + 28 + site.height + 12 + meta.height + 40 + 3 + 40
        val stampGap = (stamp.bottom + 24f - beforeHero).toInt().coerceAtLeast(0)
        var height = PAD + headerHeight + 28 + site.height + 12 + meta.height + 40 + stampGap
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
        val pillLeft = (PAD + besideStamp - pillWidth).toFloat()
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

        y = drawDivider(canvas, y, outline) + 40
        if (y < stamp.bottom + 24f) y = stamp.bottom + 24f

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
        drawStamp(canvas, stamp, palette.surfaceHigh.toArgb(), palette.background.toArgb(), primary)
        drawPostmark(canvas, card, stamp, palette.amber.toArgb())
        drawPaperBorder(canvas, height.toFloat(), palette.outline.toArgb(), outline)
        return bitmap
    }

    private fun drawPaperBorder(canvas: Canvas, height: Float, ink: Int, hairline: Int) {
        val frame = RectF(FRAME_INSET, FRAME_INSET, WIDTH - FRAME_INSET, height - FRAME_INSET)
        canvas.drawRoundRect(frame, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.STROKE
            strokeWidth = 4f
        })
        val inner = RectF(frame.left + 8f, frame.top + 8f, frame.right - 8f, frame.bottom - 8f)
        canvas.drawRoundRect(inner, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hairline
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })
    }

    private fun drawStamp(canvas: Canvas, rect: RectF, fill: Int, hole: Int, ink: Int) {
        canvas.drawRoundRect(rect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        val inner = RectF(rect.left + 16f, rect.top + 16f, rect.right - 16f, rect.bottom - 16f)
        canvas.drawRoundRect(inner, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.STROKE
            strokeWidth = 3f
        })
        drawStar(canvas, rect.centerX(), rect.top + 58f, ink, 26f)
        val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hole }
        val step = 16f
        val radius = 3.4f
        var x = rect.left
        while (x <= rect.right + 0.5f) {
            canvas.drawCircle(x, rect.top, radius, punch)
            canvas.drawCircle(x, rect.bottom, radius, punch)
            x += step
        }
        var y = rect.top + step
        while (y < rect.bottom) {
            canvas.drawCircle(rect.left, y, radius, punch)
            canvas.drawCircle(rect.right, y, radius, punch)
            y += step
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, color: Int, outer: Float) {
        val path = Path()
        val inner = outer * 0.42f
        for (i in 0 until 8) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians(-90.0 + i * 45.0)
            val x = cx + (radius * Math.cos(angle)).toFloat()
            val y = cy + (radius * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawPostmark(canvas: Canvas, card: NightPlanCard, stamp: RectF, ink: Int) {
        val cx = stamp.centerX()
        val cy = postmarkCenterY(stamp)
        val radius = postmarkRadius(stamp)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawCircle(cx, cy, radius * 0.86f, Paint(ring).apply { strokeWidth = 1.5f })
        val maxWidth = postmarkInnerTextWidth(stamp)
        val dateLabel = card.dateLabel.trim().ifEmpty { "—" }
        val placeLines = postmarkPlaceLines(card.place)
        val dateSize = sizeThatFits(listOf(dateLabel), maxWidth, bold = true, start = POSTMARK_DATE_START, min = POSTMARK_MIN)
        val placeSize = sizeThatFits(placeLines, maxWidth, bold = false, start = POSTMARK_PLACE_START, min = POSTMARK_MIN)
        val datePaint = paint(dateSize, ink, bold = true).apply { textAlign = Paint.Align.CENTER }
        val placePaint = paint(placeSize, ink).apply { textAlign = Paint.Align.CENTER }
        val gap = 3f
        val dateHeight = lineHeight(datePaint)
        val placeHeight = lineHeight(placePaint)
        val total = dateHeight + placeLines.size * placeHeight + gap * placeLines.size
        var top = cy - total / 2f
        canvas.drawText(dateLabel, cx, top - datePaint.fontMetrics.ascent, datePaint)
        top += dateHeight + gap
        for (line in placeLines) {
            canvas.drawText(line, cx, top - placePaint.fontMetrics.ascent, placePaint)
            top += placeHeight + gap
        }
    }

    /**
     * Latitude and longitude on their own lines when [place] has a space.
     * A blank place is a single em dash. The lines join back to [place].
     */
    internal fun postmarkPlaceLines(place: String): List<String> {
        val value = place.trim().ifEmpty { return listOf("—") }
        val parts = value.split(Regex("\\s+"), limit = 2)
        return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts else listOf(value)
    }

    /** Every postmark line fits the inner ring at or above [POSTMARK_MIN], with no ellipsis. */
    internal fun postmarkShowsTheFullPlace(dateLabel: String, place: String): Boolean {
        val width = postmarkInnerTextWidth(stampRect())
        val date = dateLabel.trim().ifEmpty { "—" }
        val lines = postmarkPlaceLines(place)
        val dateSize = sizeThatFits(listOf(date), width, bold = true, start = POSTMARK_DATE_START, min = POSTMARK_MIN)
        val placeSize = sizeThatFits(lines, width, bold = false, start = POSTMARK_PLACE_START, min = POSTMARK_MIN)
        val dateFits = paint(dateSize, 0, bold = true).measureText(date) <= width + 0.5f
        val placeFits = lines.all { paint(placeSize, 0).measureText(it) <= width + 0.5f }
        val shown = if (lines.size == 1) lines[0] else lines.joinToString(" ")
        val expected = place.trim().ifEmpty { "—" }
        return dateFits && placeFits && shown == expected
    }

    /** The date pill and the site-name column stay [STAMP_CLEARANCE] left of the stamp. */
    internal fun datePillClearsTheStamp(dateLabel: String): Boolean {
        val stamp = stampRect()
        val beside = besideStampWidth()
        val label = dateLabel.ifBlank { "—" }
        val maxPill = beside / 2
        val budget = (maxPill - 64).toFloat().coerceAtLeast(40f)
        val size = sizeThatFits(listOf(label), budget, bold = true, start = 32f, min = 12f)
        val measured = paint(size, 0, bold = true).measureText(label)
        val pillWidth = (measured + 64f).toInt().coerceIn(120, maxPill)
        val pillRight = PAD + beside.toFloat()
        val drawnRight = (PAD + beside - pillWidth) + 32f + measured
        return stamp.left - pillRight >= STAMP_CLEARANCE - 0.5f &&
            stamp.left - (PAD + beside) >= STAMP_CLEARANCE - 0.5f &&
            drawnRight <= pillRight + 0.5f
    }

    private fun besideStampWidth(): Int {
        val textWidth = WIDTH - PAD * 2
        return (stampRect().left - STAMP_CLEARANCE - PAD).toInt().coerceIn(280, textWidth)
    }

    private fun postmarkRadius(stamp: RectF): Float = stamp.width() * 0.30f

    private fun postmarkCenterY(stamp: RectF): Float = stamp.top + stamp.height() * 0.65f

    private fun postmarkInnerTextWidth(stamp: RectF): Float {
        val inner = postmarkRadius(stamp) * 0.86f
        return inner * 2f * 0.90f
    }

    private fun lineHeight(paint: TextPaint): Float {
        val m = paint.fontMetrics
        return m.descent - m.ascent
    }

    /** Largest size in [min, start] at which every line is at most [maxWidth]. */
    private fun sizeThatFits(lines: List<String>, maxWidth: Float, bold: Boolean, start: Float, min: Float): Float {
        var size = start
        while (size > min && lines.any { paint(size, 0, bold).measureText(it) > maxWidth }) {
            size -= 0.5f
        }
        return size
    }

    /** One line when it fits; otherwise latitude and longitude, never ellipsized. */
    private fun fittedPlace(place: String, color: Int, width: Int): StaticLayout {
        val raw = place.trim().ifEmpty { "—" }
        var size = 32f
        while (size > 16f && paint(size, color).measureText(raw) > width) size -= 1f
        if (paint(size, color).measureText(raw) <= width) {
            return text(raw, paint(size, color), width, maxLines = 1, ellipsize = false)
        }
        val lines = postmarkPlaceLines(raw)
        size = 28f
        while (size > 14f && lines.any { paint(size, color).measureText(it) > width }) size -= 1f
        return text(lines.joinToString("\n"), paint(size, color), width, maxLines = lines.size, ellipsize = false)
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
        ellipsize: Boolean = true,
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) {
            builder.setMaxLines(maxLines)
            if (ellipsize) builder.setEllipsize(TextUtils.TruncateAt.END)
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
