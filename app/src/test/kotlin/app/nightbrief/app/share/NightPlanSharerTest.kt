package app.nightbrief.app.share

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanCard
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NightPlanSharerTest {
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 3)

    @Test
    fun thePictureIsAPngOfTheBlueCardAndTheIntentDoesNotCarryThePin() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val report = NightPlanner.plan(longPoint, LocalDate.of(2024, 8, 10), forecast(), GearCatalog.exampleKit)
        val card = NightPlanCard.from(report)

        val bitmap = NightPlanImage.render(card)
        val background = NightPalette.Standard.background.toArgb()
        assertEquals(NightPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(background, bitmap.getPixel(0, 0))
        bitmap.recycle()

        val send = NightPlanSharer.sendIntent(context, report)
        assertNotNull(send)
        send!!
        assertEquals("image/png", send.type)
        assertEquals(card.caption, send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertNotNull(send.clipData)
        assertFalse(card.caption, card.caption.contains(longPoint.latitude.toString()))
        assertFalse(card.caption, card.caption.contains(longPoint.longitude.toString()))

        val file = context.cacheDir.resolve("share/night-plan.png")
        assertTrue(file.isFile)
        val bytes = file.readBytes()
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(decoded)
        assertEquals(NightPlanImage.WIDTH, decoded.width)

        val chooser = NightPlanSharer.chooserIntent(context, report)
        assertNotNull(chooser)
        chooser!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(context.getString(app.nightbrief.app.R.string.share_night_plan), chooser.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun aNullScoreWithoutDetailsStillRendersABoundedPicture() {
        val card = NightPlanCard(
            siteName = "Home",
            place = "43.7°N 79.4°W",
            dateLabel = "Sat 10 Aug",
            score = null,
            scoreText = "—",
            verdictLabel = "No score",
            bestWindow = null,
            milkyWay = "",
            target = null,
            saved = null,
        )

        val bitmap = NightPlanImage.render(card)
        val background = NightPalette.Standard.background.toArgb()
        assertEquals(NightPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(background, bitmap.getPixel(0, 0))
        // The details card is skipped, so no surface pixels should remain
        // (the date pill uses surfaceHigh, a different swatch).
        val surface = NightPalette.Standard.surface.toArgb()
        var surfacePixels = 0
        for (x in 0 until bitmap.width step 8) {
            for (y in 0 until bitmap.height step 8) {
                if (bitmap.getPixel(x, y) == surface) surfacePixels++
            }
        }
        assertEquals("surface sliver rendered for empty details", 0, surfacePixels)
        bitmap.recycle()
    }

    @Test
    fun aVeryLongSiteNameStaysBounded() {
        val card = NightPlanCard(
            siteName = "A very long site name that keeps going and going and going and going and going",
            place = "43.7°N 79.4°W",
            dateLabel = "Sat 10 Aug",
            score = 91,
            scoreText = "91",
            verdictLabel = "Go · Excellent",
            bestWindow = "00:00–03:00 (90)",
            milkyWay = "Milky Way core up most of the night with a very long description that wraps",
            target = "A target with a very long name that would wrap many lines in a narrow card",
            saved = null,
        )

        val bitmap = NightPlanImage.render(card)
        val background = NightPalette.Standard.background.toArgb()
        assertEquals(NightPlanImage.WIDTH, bitmap.width)
        assertTrue("height was ${bitmap.height}", bitmap.height in 400..4000)
        assertEquals(background, bitmap.getPixel(0, 0))
        bitmap.recycle()
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            longPoint.latitude,
            longPoint.longitude,
            "gem_seamless",
            start,
            (0 until 240).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 0,
                    humidity = 60,
                    windKmh = 6.0,
                    seeing = 3,
                    transparency = 2,
                )
            },
        )
    }
}
