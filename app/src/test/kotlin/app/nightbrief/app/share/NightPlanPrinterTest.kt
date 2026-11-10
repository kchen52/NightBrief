package app.nightbrief.app.share

import android.app.Application
import android.print.PrintAttributes
import app.nightbrief.score.NightPlanCard
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NightPlanPrinterTest {
    private val card = NightPlanCard(
        siteName = "Long Point",
        place = "42.6°N 80.4°W",
        dateLabel = "Sat 10 Aug",
        score = 91,
        scoreText = "91",
        verdictLabel = "Go · Excellent",
        bestWindow = "00:00–03:00 (90)",
        milkyWay = "Milky Way core 22:10–03:40",
        target = "Cygnus",
        saved = null,
    )

    @Test
    fun thePrintedPdfCarriesTheWholeCardScaledToFitThePage() {
        val bitmap = NightPlanImage.render(card)
        // Letter with a quarter inch on every side.
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
            .setMinMargins(PrintAttributes.Margins(250, 250, 250, 250))
            .build()
        val pdf = pdfBytes(attributes, bitmap).toString(Charsets.ISO_8859_1)

        assertTrue("PDF must start with a header, was ${pdf.take(8)}", pdf.startsWith("%PDF-1.4"))
        assertTrue("PDF must end at %%EOF, was …${pdf.takeLast(16)}", pdf.endsWith("%%EOF"))
        assertTrue("Letter is 612x792 points, MediaBox was ${pdf.substringAfter("/MediaBox")}", pdf.contains("/MediaBox[0 0 612 792]"))
        assertTrue(
            "the document name ends the save dialog's suggested file name, was ${NightPlanPrinter.DOCUMENT_NAME}",
            NightPlanPrinter.DOCUMENT_NAME.endsWith(".pdf"),
        )
        assertTrue(
            "the image must ride at its own pixel size",
            pdf.contains("/Width ${bitmap.width}/Height ${bitmap.height}"),
        )

        // The card fits the printable area instead of filling and cropping the paper.
        val expected = cardRect(612f, 792f, 18f, bitmap.width, bitmap.height)
        assertTrue(
            "the fit must stay inside the 18pt margin: $expected",
            expected.left >= 18f - 0.01f && expected.top >= 18f - 0.01f &&
                expected.right <= 612f - 18f + 0.01f && expected.bottom <= 792f - 18f + 0.01f,
        )
        assertEquals(
            "the card's aspect must be kept",
            bitmap.width.toFloat() / bitmap.height.toFloat(),
            expected.width() / expected.height(),
            0.001f,
        )
        val cm = Regex("(\\d+\\.\\d+) 0 0 (\\d+\\.\\d+) (\\d+\\.\\d+) (\\d+\\.\\d+) cm").find(pdf)
        assertNotNull("the content stream must place the image, was ${pdf.substringAfter("stream")}", cm)
        cm!!
        assertEquals("scaled width", expected.width(), cm.groupValues[1].toFloat(), 0.05f)
        assertEquals("scaled height", expected.height(), cm.groupValues[2].toFloat(), 0.05f)
        assertEquals("left edge", expected.left, cm.groupValues[3].toFloat(), 0.05f)
        // PDF y grows up from the bottom edge, so the page flips the rectangle.
        assertEquals("bottom edge", 792f - expected.bottom, cm.groupValues[4].toFloat(), 0.05f)

        val image = Regex("/Filter/FlateDecode/Length (\\d+)>>stream\n").find(pdf)
        assertNotNull("the image stream must be Flate-compressed", image)
        val raw = pdf.substringAfter(">>stream\n").substringBefore("\nendstream").toByteArray(Charsets.ISO_8859_1)
        val inflated = Inflater().let { inflater ->
            inflater.setInput(raw)
            val out = ByteArray(bitmap.width * bitmap.height * 3)
            val read = inflater.inflate(out)
            inflater.end()
            assertTrue("the image must decompress in one pass, read $read", inflater.finished())
            out.take(read).toByteArray()
        }
        assertEquals(
            "DeviceRGB carries three bytes per pixel, so the card stays lossless",
            bitmap.width * bitmap.height * 3,
            inflated.size,
        )
        bitmap.recycle()
    }

    @Test
    fun cardRectFitsTheCardOnAnyPaperWithoutCropping() {
        // A 4x6 index card in landscape, the paper the share-sheet print path cropped on.
        val indexCard = cardRect(432f, 288f, 0f, 1080, 2916)
        assertTrue("width must fit: $indexCard", indexCard.width() <= 432f + 0.01f)
        assertTrue("height must fit: $indexCard", indexCard.height() <= 288f + 0.01f)
        assertEquals("aspect must be kept", 1080f / 2916f, indexCard.width() / indexCard.height(), 0.001f)

        val a4 = cardRect(595f, 842f, 72f, 1080, 956)
        assertTrue("margins must hold: $a4", a4.left >= 72f - 0.01f && a4.top >= 72f - 0.01f)
        assertTrue("margins must hold: $a4", a4.right <= 595f - 72f + 0.01f && a4.bottom <= 842f - 72f + 0.01f)
        // The card is narrower than the page is tall, so it centres instead of cropping.
        assertTrue("the whole height must show: $a4", a4.height() <= 698f + 0.01f)

        // A square page must shrink to the smaller side rather than overflow the other.
        val square = cardRect(600f, 600f, 0f, 1080, 956)
        assertTrue("square paper must fit both sides: $square", square.width() <= 600f + 0.01f && square.height() <= 600f + 0.01f)
    }
}
