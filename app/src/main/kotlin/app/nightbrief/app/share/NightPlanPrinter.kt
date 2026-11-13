package app.nightbrief.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import app.nightbrief.app.R
import app.nightbrief.score.NightPlanCard
import app.nightbrief.score.NightReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prints one night's card through the system print dialog instead of the share sheet's
 * image target. The system one (BuiltInPrintService) names the file after the caption
 * (`downloadfile.png`), scales the image to fill the paper so the card's heading is
 * cropped, and cancels its own write when the dialog is resized, leaving a 0-byte PDF.
 *
 * A print here always names the document [DOCUMENT_NAME], fits the whole card on the
 * page whatever paper or orientation is picked, and re-writes on request, so resizing
 * the preview cannot produce an empty file.
 */
object NightPlanPrinter {
    /** The name the print dialog shows and "Save as PDF" proposes. Ends in `.pdf`. */
    const val DOCUMENT_NAME = "night-plan.pdf"

    /** One mil is a thousandth of an inch; a PDF point is a seventy-second of one. */
    const val POINTS_PER_MIL = 72f / 1000f
}

/**
 * Renders the card off the main thread, then opens the system print dialog.
 *
 * No-ops when the card cannot be rendered or this device has no print subsystem, and
 * swallows a failure to start the dialog, like [shareNightPlan] does.
 */
suspend fun printNightPlan(context: Context, report: NightReport): Boolean {
    val bitmap = withContext(Dispatchers.IO) {
        runCatching { NightPlanImage.render(NightPlanCard.from(report)) }.getOrNull()
    } ?: return false
    return withContext(Dispatchers.Main.immediate) {
        val manager = context.getSystemService(PrintManager::class.java)
        if (manager == null) {
            bitmap.recycle()
            return@withContext false
        }
        try {
            manager.print(
                context.getString(R.string.print_night_plan),
                NightPlanPrintAdapter(bitmap),
                PrintAttributes.Builder().setColorMode(PrintAttributes.COLOR_MODE_COLOR).build(),
            )
            true
        } catch (_: IllegalStateException) {
            bitmap.recycle()
            false
        }
    }
}

/**
 * The card as a one-page PDF document.
 *
 * [onLayout] and [onWrite] may each run again after a resize or a paper change, so
 * neither consumes the bitmap; [onFinish] recycles it. The write itself runs off the
 * main thread — the print framework calls these on it.
 */
private class NightPlanPrintAdapter(
    private val bitmap: Bitmap,
) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder(NightPlanPrinter.DOCUMENT_NAME)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val attrs = attributes
        if (attrs == null) {
            callback.onWriteFailed(null)
            return
        }
        scope.launch {
            try {
                val bytes = pdfBytes(attrs, bitmap)
                if (cancellationSignal.isCanceled) {
                    withContext(Dispatchers.Main.immediate) { callback.onWriteCancelled() }
                    return@launch
                }
                FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
                if (cancellationSignal.isCanceled) {
                    withContext(Dispatchers.Main.immediate) { callback.onWriteCancelled() }
                } else {
                    withContext(Dispatchers.Main.immediate) { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main.immediate) { callback.onWriteFailed(e.message) }
            }
        }
    }

    override fun onFinish() {
        scope.cancel()
        bitmap.recycle()
    }
}

/**
 * Where the card sits on the page, in points from the page's top-left corner: scaled to
 * fit [marginPt] inside each edge and centred, so no paper size or orientation crops it
 * and a printer that cannot print to the edge still gets the whole card.
 */
internal fun cardRect(
    pageWidthPt: Float,
    pageHeightPt: Float,
    marginPt: Float,
    imageWidth: Int,
    imageHeight: Int,
): RectF {
    val usableWidth = (pageWidthPt - 2 * marginPt).coerceAtLeast(1f)
    val usableHeight = (pageHeightPt - 2 * marginPt).coerceAtLeast(1f)
    val scale = min(usableWidth / imageWidth, usableHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (pageWidthPt - width) / 2f
    val top = (pageHeightPt - height) / 2f
    return RectF(left, top, left + width, top + height)
}

/**
 * The bytes of a one-page PDF carrying [bitmap] inside [rect], written with the page's
 * origin at its bottom-left corner as the format requires.
 *
 * The image rides as Flate-compressed DeviceRGB samples, so the card is not re-encoded
 * and stays lossless. Everything is plain JVM output, so the bytes can be asserted in a
 * unit test.
 */
internal fun pdfBytes(pageWidthPt: Int, pageHeightPt: Int, bitmap: Bitmap, rect: RectF): ByteArray {
    val image = flatedRgb(bitmap)
    // PDF y grows up from the bottom edge, so the rectangle's top-left flips.
    val y = pageHeightPt - rect.bottom
    val content = buildString {
        append("q\n")
        append(number(rect.width())).append(" 0 0 ").append(number(rect.height())).append(' ')
        append(number(rect.left)).append(' ').append(number(y)).append(" cm\n")
        append("/Im0 Do\nQ\n")
    }.toByteArray()
    val out = ByteArrayOutputStream()
    val offsets = IntArray(5)
    out.write("%PDF-1.4\n".toByteArray())
    fun obj(n: Int, body: (ByteArrayOutputStream) -> Unit) {
        offsets[n - 1] = out.size()
        out.write("$n 0 obj".toByteArray())
        body(out)
        out.write("\nendobj\n".toByteArray())
    }
    obj(1) { it.write("<</Type/Catalog/Pages 2 0 R>>".toByteArray()) }
    obj(2) { it.write("<</Type/Pages/Kids[3 0 R]/Count 1>>".toByteArray()) }
    obj(3) {
        it.write(
            ("<</Type/Page/Parent 2 0 R/MediaBox[0 0 $pageWidthPt $pageHeightPt]" +
                "/Resources<</XObject<</Im0 4 0 R>>>>/Contents 5 0 R>>").toByteArray(),
        )
    }
    obj(4) {
        it.write(
            ("<</Type/XObject/Subtype/Image/Width ${bitmap.width}/Height ${bitmap.height}" +
                "/ColorSpace/DeviceRGB/BitsPerComponent 8/Filter/FlateDecode/Length ${image.size}>>stream\n").toByteArray(),
        )
        it.write(image)
        it.write("\nendstream".toByteArray())
    }
    obj(5) {
        it.write("<</Length ${content.size}>>stream\n".toByteArray())
        it.write(content)
        it.write("\nendstream".toByteArray())
    }
    val xrefAt = out.size()
    out.write("xref\n0 6\n0000000000 65535 f \n".toByteArray())
    offsets.forEach { offset ->
        out.write("%010d 00000 n \n".format(Locale.ROOT, offset).toByteArray())
    }
    out.write("trailer\n<</Size 6/Root 1 0 R>>\nstartxref\n$xrefAt\n%%EOF".toByteArray())
    return out.toByteArray()
}

/** The page and card rectangle for [attributes], in points. */
internal fun pdfBytes(attributes: PrintAttributes, bitmap: Bitmap): ByteArray {
    val media = attributes.mediaSize ?: PrintAttributes.MediaSize.UNKNOWN_PORTRAIT
    val pageWidth = (media.widthMils * NightPlanPrinter.POINTS_PER_MIL).roundToInt()
    val pageHeight = (media.heightMils * NightPlanPrinter.POINTS_PER_MIL).roundToInt()
    val margins = attributes.minMargins ?: PrintAttributes.Margins.NO_MARGINS
    val marginPt = max(
        max(margins.leftMils, margins.rightMils),
        max(margins.topMils, margins.bottomMils),
    ) * NightPlanPrinter.POINTS_PER_MIL
    val rect = cardRect(pageWidth.toFloat(), pageHeight.toFloat(), marginPt, bitmap.width, bitmap.height)
    return pdfBytes(pageWidth, pageHeight, bitmap, rect)
}

/** The card's pixels as three Flate-compressed bytes per pixel, the PDF image payload. */
private fun flatedRgb(bitmap: Bitmap): ByteArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val rgb = ByteArray(pixels.size * 3)
    var at = 0
    for (pixel in pixels) {
        rgb[at++] = ((pixel shr 16) and 0xFF).toByte()
        rgb[at++] = ((pixel shr 8) and 0xFF).toByte()
        rgb[at++] = (pixel and 0xFF).toByte()
    }
    val deflater = Deflater()
    deflater.setInput(rgb)
    deflater.finish()
    val compressed = ByteArrayOutputStream(rgb.size / 2)
    val chunk = ByteArray(64 * 1024)
    while (!deflater.finished()) {
        compressed.write(chunk, 0, deflater.deflate(chunk))
    }
    deflater.end()
    return compressed.toByteArray()
}

/** A PDF number without a locale-dependent separator or exponent. */
private fun number(value: Float): String = String.format(Locale.ROOT, "%.2f", value)
