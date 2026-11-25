package app.nightbrief.app.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.astro.Darkness
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeekPlanCard
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.weather.ForecastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeekPlanSharerTest {
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 3)
    private val aug12 = LocalDate.of(2024, 8, 12)

    private fun sampleOutlook(): WeeklyOutlook = WeeklyOutlook(
        site = longPoint,
        nights = listOf(
            OutlookNight(aug12, 91, 0.25, Darkness.ASTRONOMICAL, ForecastCoverage.FULL),
            OutlookNight(aug12.plusDays(1), 76, 0.35, Darkness.ASTRONOMICAL, ForecastCoverage.FULL),
            OutlookNight(aug12.plusDays(2), 54, 0.45, Darkness.ASTRONOMICAL, ForecastCoverage.FULL),
            OutlookNight(aug12.plusDays(3), 38, 0.56, Darkness.ASTRONOMICAL, ForecastCoverage.FULL, estimated = true),
            OutlookNight(aug12.plusDays(4), 19, 0.67, Darkness.ASTRONOMICAL, ForecastCoverage.FULL, estimated = true),
            OutlookNight(aug12.plusDays(5), 65, 0.78, Darkness.ASTRONOMICAL, ForecastCoverage.FULL, estimated = true),
            OutlookNight(aug12.plusDays(6), null, 0.88, Darkness.ASTRONOMICAL, ForecastCoverage.NONE),
        ),
    )

    @Test
    fun thePictureIsAPngOfTheBlueWeekCardAndTheIntentDoesNotCarryThePin() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val outlook = sampleOutlook()
        val card = WeekPlanCard.from(outlook)

        val bitmap = WeekPlanImage.render(card)
        val background = NightPalette.Standard.background.toArgb()
        assertEquals(WeekPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(background, bitmap.getPixel(0, 0))
        bitmap.recycle()

        val send = WeekPlanSharer.sendIntent(context, outlook)
        assertNotNull(send)
        send!!
        assertEquals("image/png", send.type)
        assertEquals(card.caption, send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertNotNull(send.clipData)
        assertEquals("text/uri-list", send.clipData!!.description.getMimeType(0))
        assertFalse(card.caption, card.caption.contains(longPoint.latitude.toString()))
        assertFalse(card.caption, card.caption.contains(longPoint.longitude.toString()))

        val file = context.cacheDir.resolve("share/week-plan.png")
        assertTrue(file.isFile)
        val bytes = file.readBytes()
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(decoded)
        assertEquals(WeekPlanImage.WIDTH, decoded.width)

        val chooser = WeekPlanSharer.chooserIntent(context, outlook)
        assertNotNull(chooser)
        chooser!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(context.getString(app.nightbrief.app.R.string.share_week), chooser.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val inner = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        assertEquals("image/png", inner?.type)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun weekPostcardKeepsTheBlueGroundAndDrawsTheStamp() {
        val card = WeekPlanCard.from(
            sampleOutlook(),
            forecastStatus = ForecastStatus.STALE,
            forecastFetchedAt = Instant.parse("2024-08-11T18:40:00Z"),
        )
        val bitmap = WeekPlanImage.render(card)
        val background = NightPalette.Standard.background.toArgb()
        assertEquals(WeekPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(background, bitmap.getPixel(0, 0))
        assertEquals(background, bitmap.getPixel(bitmap.width - 1, 0))
        val stamp = NightPlanImage.stampRect()
        val stampFill = NightPalette.Standard.surfaceHigh.toArgb()
        val sampleX = (stamp.left + stamp.width() / 2f).toInt()
        val sampleY = (stamp.top + 28f).toInt()
        assertEquals("stamp fill at $sampleX,$sampleY", stampFill, bitmap.getPixel(sampleX, sampleY))
        bitmap.recycle()
    }

    @Test
    fun emptyOrNoForecastWeekStillRendersABoundedPicture() {
        val empty = WeekPlanCard.from(WeeklyOutlook(longPoint, emptyList()))
        val bitmap = WeekPlanImage.render(empty)
        assertEquals(WeekPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(NightPalette.Standard.background.toArgb(), bitmap.getPixel(0, 0))
        bitmap.recycle()
    }
}
