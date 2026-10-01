package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.IssTle
import app.nightbrief.weather.TleSource
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MeteorAndIssBriefingTest {
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val kit = GearCatalog.exampleKit

    @Test
    fun perseidNightAddsADigestLineWithoutReplacingTheImagingTarget() {
        val date = LocalDate.of(2024, 8, 12)
        val report = NightPlanner.plan(toronto, date, forecast(toronto, "2024-08-12T00:00:00Z"), kit)
        val meteor = report.meteor
        assertNotNull(meteor)
        assertEquals("perseids", meteor!!.shower.id)
        assertEquals(0, meteor.daysFromPeak)
        assertTrue(meteor.worthWatching)
        assertTrue(report.suggestions.isNotEmpty())
        val digest = DigestComposer.compose(report, emptyList())
        assertTrue(digest.lines.any { it.contains("Perseids tonight") })
        assertTrue(digest.lines.any { it.startsWith("Try: ") })
    }

    @Test
    fun briefingAttachesIssPassesInTheDarkWindowAndIgnoresAFailedFetch() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-03-14T22:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(forecast(toronto, "2024-03-14T00:00:00Z"))
        val tle = IssTle(LINE1, LINE2, clock.instant())
        val withIss = BriefingService(source, clock, iss = FixedTle(tle))
            .brief(listOf(toronto), kit, outlookDays = 1)
        val passes = withIss.reportFor("home")!!.issPasses
        assertTrue("expected a dark-window pass, got ${passes.size}", passes.isNotEmpty())
        val digest = DigestComposer.compose(withIss.reportFor("home")!!, emptyList())
        assertTrue(digest.lines.any { it.startsWith("ISS ") })

        val failed = BriefingService(source, clock, iss = object : TleSource {
            override suspend fun fetchIss(): IssTle = throw WeatherApiException("celestrak down")
        }).brief(listOf(toronto), kit, outlookDays = 1)
        assertTrue(failed.reportFor("home")!!.issPasses.isEmpty())
        assertEquals(withIss.reportFor("home")!!.scoreValue, failed.reportFor("home")!!.scoreValue)
        assertTrue(failed.reportFor("home")!!.warnings.isEmpty())
    }

    private fun forecast(site: Site, start: String): Forecast {
        val s = Instant.parse(start).epochSecond
        return Forecast(
            site.latitude, site.longitude, "best_match", s,
            (0 until 96).map {
                HourlyWeather(
                    epochSecond = s + it * 3600L, cloudCover = 10, humidity = 50,
                    windKmh = 8.0, gustKmh = 12.0, jetStreamKmh = 90.0,
                    seeing = 2, transparency = 2,
                )
            },
        )
    }

    private class FakeSource(val forecast: Forecast) : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult =
            ForecastResult(forecast, ForecastStatus.FRESH)
    }

    private class FixedTle(val tle: IssTle) : TleSource {
        override suspend fun fetchIss(): IssTle = tle
    }

    companion object {
        private const val LINE1 = "1 25544U 98067A   24074.41191032  .00014407  00000+0  26068-3 0  9998"
        private const val LINE2 = "2 25544  51.6400  65.1219 0006168   5.0268 148.9614 15.49907257443850"
    }
}
