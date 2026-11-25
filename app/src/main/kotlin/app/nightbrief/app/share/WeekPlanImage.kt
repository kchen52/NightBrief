package app.nightbrief.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import androidx.compose.ui.graphics.toArgb
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.score.WeekPlanCard
import app.nightbrief.score.WeekPlanNight

/**
 * Renders a [WeekPlanCard] to a bitmap for sharing, using the same blue postcard
 * frame, perforated stamp, and postmark as [NightPlanImage].
 */
object WeekPlanImage {
    const val WIDTH = NightPlanImage.WIDTH
    private const val PAD = NightPlanImage.PAD
    private const val CARD_RADIUS = 32f
    private const val CARD_PAD_H = 32
    private const val CARD_PAD_V = 26
    private const val BEST_BADGE_DIAMETER = 132f
    private const val ROW_RADIUS = 24f
    private const val ROW_PAD_H = 28
    private const val ROW_PAD_V = 20
    private const val ROW_GAP = 14
    private const val DAY_COL = 160
    private const val PILL_WIDTH = 104
    private const val PILL_HEIGHT = 60
    private const val BAR_HEIGHT = 14f

    private data class MeasuredRow(
        val night: WeekPlanNight,
        val dayLayout: StaticLayout,
        val dateLayout: StaticLayout,
        val summaryLayout: StaticLayout,
        val scoreLayout: StaticLayout,
        val height: Int,
    )

    fun render(card: WeekPlanCard): Bitmap {
        val palette = NightPalette.Standard
        val textWidth = WIDTH - PAD * 2
        val stamp = NightPlanImage.stampRect()
        val besideStamp = NightPlanImage.besideStampWidth()
        val onBackground = palette.onBackground.toArgb()
        val muted = palette.textMuted.toArgb()
        val primary = palette.primary.toArgb()
        val amber = palette.amber.toArgb()
        val surface = palette.surface.toArgb()
        val outline = palette.outlineVariant.toArgb()

        // Header: wordmark left, date range pill right, both left of the stamp.
        val appPaint = NightPlanImage.paint(52f, primary, bold = true, tracking = 0.06f)
        val rangeLabel = card.dateRangeLabel.ifBlank { "—" }
        val maxPill = besideStamp / 2
        val rangeBudget = (maxPill - 64).toFloat().coerceAtLeast(40f)
        val rangeSize = NightPlanImage.sizeThatFits(listOf(rangeLabel), rangeBudget, bold = true, start = 32f, min = 12f)
        val rangePaint = NightPlanImage.paint(rangeSize, onBackground, bold = true)
        val rangeTextWidth = rangePaint.measureText(rangeLabel)
        val pillWidth = (rangeTextWidth + 64f).toInt().coerceIn(120, maxPill)
        val appWidth = (besideStamp - pillWidth - 24).coerceAtLeast(1)
        val appName = NightPlanImage.text("NightBrief", appPaint, appWidth, maxLines = 1)
        val rangeLayout = NightPlanImage.text(
            rangeLabel,
            rangePaint,
            (pillWidth - 64).coerceAtLeast(1),
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1,
            ellipsize = false,
        )
        val pillHeight = rangeLayout.height + 32
        val headerHeight = maxOf(appName.height, pillHeight)

        val site = NightPlanImage.text(
            card.siteName.ifBlank { "Untitled site" },
            NightPlanImage.paint(66f, onBackground, bold = true),
            besideStamp,
            maxLines = 2,
        )
        val meta = NightPlanImage.fittedPlace(card.place, muted, besideStamp)

        // Best-night summary card.
        val hasBest = card.bestDayLabel != null && card.bestScore != null
        val bestScoreColor = palette.forScore(card.bestScore).toArgb()
        val bestTextWidth = if (hasBest) {
            (textWidth - CARD_PAD_H * 2 - BEST_BADGE_DIAMETER.toInt() - 28).coerceAtLeast(120)
        } else {
            textWidth - CARD_PAD_H * 2
        }
        val bestEyebrow = if (hasBest) {
            NightPlanImage.text("BEST NIGHT", NightPlanImage.paint(24f, muted, bold = true, tracking = 0.08f), bestTextWidth, maxLines = 1)
        } else {
            null
        }
        val bestDay = if (hasBest) {
            NightPlanImage.text(card.bestDayLabel!!, NightPlanImage.paint(44f, amber, bold = true), bestTextWidth, maxLines = 1)
        } else {
            null
        }
        val bestVerdict = NightPlanImage.text(
            card.bestVerdictLabel,
            NightPlanImage.paint(if (hasBest) 30f else 34f, if (hasBest) bestScoreColor else onBackground, bold = true),
            bestTextWidth,
            maxLines = 2,
        )
        val bestScoreLayout = if (hasBest) {
            NightPlanImage.text(
                card.bestScoreText,
                NightPlanImage.paint(58f, bestScoreColor, bold = true),
                BEST_BADGE_DIAMETER.toInt(),
                Layout.Alignment.ALIGN_CENTER,
                maxLines = 1,
            )
        } else {
            null
        }
        val bestLeftHeight = if (hasBest) {
            bestEyebrow!!.height + 6 + bestDay!!.height + 6 + bestVerdict.height
        } else {
            bestVerdict.height
        }
        val bestCardHeight = CARD_PAD_V * 2 + maxOf(bestLeftHeight, if (hasBest) BEST_BADGE_DIAMETER.toInt() else 0)

        // Night rows.
        val midWidth = (textWidth - ROW_PAD_H * 2 - DAY_COL - PILL_WIDTH - 48).coerceAtLeast(120)
        val measuredRows = card.nights.map { night ->
            val scoreColor = palette.forScore(night.score).toArgb()
            val dayLayout = NightPlanImage.text(
                night.dayLabel,
                NightPlanImage.paint(34f, if (night.isBest) amber else onBackground, bold = night.isBest),
                DAY_COL,
                maxLines = 1,
            )
            val dateLayout = NightPlanImage.text(
                night.dateLabel,
                NightPlanImage.paint(24f, muted),
                DAY_COL,
                maxLines = 1,
            )
            val summaryText = listOf(night.summary, night.moonText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val summaryLayout = NightPlanImage.text(
                summaryText,
                NightPlanImage.paint(26f, muted),
                midWidth,
                maxLines = 1,
            )
            val scoreLayout = NightPlanImage.text(
                night.scoreText,
                NightPlanImage.paint(32f, scoreColor, bold = true),
                PILL_WIDTH,
                Layout.Alignment.ALIGN_CENTER,
                maxLines = 1,
            )
            val leftH = dayLayout.height + 4 + dateLayout.height
            val midH = BAR_HEIGHT.toInt() + 12 + summaryLayout.height
            val rowH = ROW_PAD_V * 2 + maxOf(leftH, midH, PILL_HEIGHT)
            MeasuredRow(night, dayLayout, dateLayout, summaryLayout, scoreLayout, rowH)
        }
        val rowsBlockHeight = if (measuredRows.isEmpty()) {
            0
        } else {
            measuredRows.sumOf { it.height } + ROW_GAP * (measuredRows.size - 1)
        }

        val saved = card.saved?.let {
            NightPlanImage.text("•  $it", NightPlanImage.paint(32f, amber), textWidth, maxLines = 2)
        }
        val footerTitle = NightPlanImage.text(
            "Shared with NightBrief",
            NightPlanImage.paint(34f, onBackground, bold = true),
            textWidth,
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1,
        )
        val footerSub = NightPlanImage.text(
            "Is tonight worth imaging?",
            NightPlanImage.paint(32f, muted),
            textWidth,
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1,
        )

        val beforeBody = PAD + headerHeight + 28 + site.height + 12 + meta.height + 40 + 3 + 36
        val stampGap = (stamp.bottom + 24f - beforeBody).toInt().coerceAtLeast(0)
        var height = PAD + headerHeight + 28 + site.height + 12 + meta.height + 40 + 3 + 36 + stampGap
        height += bestCardHeight
        if (rowsBlockHeight > 0) height += 20 + rowsBlockHeight
        height += 36
        if (saved != null) height += saved.height + 32
        height += 3 + 36 + footerTitle.height + 10 + footerSub.height + PAD

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(palette.background.toArgb())
        val canvas = Canvas(bitmap)

        NightPlanImage.drawStars(canvas, WIDTH, height)

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
        rangeLayout.draw(canvas)
        canvas.restore()
        y += headerHeight + 28

        y = NightPlanImage.drawAt(canvas, site, PAD.toFloat(), y) + 12
        y = NightPlanImage.drawAt(canvas, meta, PAD.toFloat(), y) + 40

        y = NightPlanImage.drawDivider(canvas, y, outline) + 36
        if (y < stamp.bottom + 24f) y = stamp.bottom + 24f

        // Best-night card.
        val bestRect = RectF(PAD.toFloat(), y, (PAD + textWidth).toFloat(), y + bestCardHeight)
        canvas.drawRoundRect(bestRect, CARD_RADIUS, CARD_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
        })
        val bestInnerTop = y + (bestCardHeight - bestLeftHeight) / 2f
        if (hasBest) {
            var leftY = NightPlanImage.drawAt(canvas, bestEyebrow!!, (PAD + CARD_PAD_H).toFloat(), bestInnerTop) + 6
            leftY = NightPlanImage.drawAt(canvas, bestDay!!, (PAD + CARD_PAD_H).toFloat(), leftY) + 6
            NightPlanImage.drawAt(canvas, bestVerdict, (PAD + CARD_PAD_H).toFloat(), leftY)

            val cx = bestRect.right - CARD_PAD_H - BEST_BADGE_DIAMETER / 2f
            val cy = bestRect.centerY()
            val radius = BEST_BADGE_DIAMETER / 2f
            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bestScoreColor
                alpha = 36
                style = Paint.Style.FILL
            })
            canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bestScoreColor
                alpha = 200
                style = Paint.Style.STROKE
                strokeWidth = 4f
            })
            NightPlanImage.drawAt(canvas, bestScoreLayout!!, cx - radius, cy - bestScoreLayout.height / 2f)
        } else {
            NightPlanImage.drawAt(canvas, bestVerdict, (PAD + CARD_PAD_H).toFloat(), bestInnerTop)
        }
        y += bestCardHeight

        // Night rows.
        if (measuredRows.isNotEmpty()) {
            y += 20
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
            val bestBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amber
                alpha = 160
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline }
            measuredRows.forEachIndexed { index, row ->
                val rect = RectF(PAD.toFloat(), y, (PAD + textWidth).toFloat(), y + row.height)
                canvas.drawRoundRect(rect, ROW_RADIUS, ROW_RADIUS, cardPaint)
                if (row.night.isBest) {
                    canvas.drawRoundRect(rect, ROW_RADIUS, ROW_RADIUS, bestBorderPaint)
                }

                val leftX = PAD.toFloat() + ROW_PAD_H
                val leftH = row.dayLayout.height + 4 + row.dateLayout.height
                val leftTop = y + (row.height - leftH) / 2f
                val dayBottom = NightPlanImage.drawAt(canvas, row.dayLayout, leftX, leftTop)
                NightPlanImage.drawAt(canvas, row.dateLayout, leftX, dayBottom + 4)

                val midX = leftX + DAY_COL + 20f
                val midH = BAR_HEIGHT + 12f + row.summaryLayout.height
                val midTop = y + (row.height - midH) / 2f
                val scoreColor = palette.forScore(row.night.score).toArgb()
                val trackRect = RectF(midX, midTop, midX + midWidth, midTop + BAR_HEIGHT)
                canvas.drawRoundRect(trackRect, BAR_HEIGHT / 2f, BAR_HEIGHT / 2f, trackPaint)
                val fillFraction = ((row.night.score ?: 0).coerceIn(0, 100)) / 100f
                if (fillFraction > 0f) {
                    val fillWidth = (midWidth * fillFraction).coerceAtLeast(BAR_HEIGHT)
                    val fillRect = RectF(midX, midTop, midX + fillWidth, midTop + BAR_HEIGHT)
                    canvas.drawRoundRect(fillRect, BAR_HEIGHT / 2f, BAR_HEIGHT / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = scoreColor
                    })
                }
                NightPlanImage.drawAt(canvas, row.summaryLayout, midX, midTop + BAR_HEIGHT + 12f)

                val scorePillRight = rect.right - ROW_PAD_H
                val scorePillLeft = scorePillRight - PILL_WIDTH
                val scorePillTop = y + (row.height - PILL_HEIGHT) / 2f
                val scorePillRect = RectF(scorePillLeft, scorePillTop, scorePillRight, scorePillTop + PILL_HEIGHT)
                canvas.drawRoundRect(scorePillRect, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = scoreColor
                    alpha = if (row.night.score == null) 0 else 36
                    style = Paint.Style.FILL
                })
                canvas.drawRoundRect(scorePillRect, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = scoreColor
                    alpha = if (row.night.score == null) 140 else 200
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                })
                NightPlanImage.drawAt(
                    canvas,
                    row.scoreLayout,
                    scorePillLeft,
                    scorePillRect.centerY() - row.scoreLayout.height / 2f,
                )

                y += row.height + if (index == measuredRows.lastIndex) 0 else ROW_GAP
            }
        }
        y += 36

        if (saved != null) {
            y = NightPlanImage.drawAt(canvas, saved, PAD.toFloat(), y) + 32
        }

        y = NightPlanImage.drawDivider(canvas, y, outline) + 36
        y = NightPlanImage.drawAt(canvas, footerTitle, PAD.toFloat(), y) + 10
        NightPlanImage.drawAt(canvas, footerSub, PAD.toFloat(), y)
        NightPlanImage.drawStamp(canvas, stamp, palette.surfaceHigh.toArgb(), palette.background.toArgb(), primary)
        NightPlanImage.drawPostmark(canvas, card.dateRangeLabel, card.place, stamp, amber)
        NightPlanImage.drawPaperBorder(canvas, height.toFloat(), palette.outline.toArgb(), outline)
        return bitmap
    }
}
