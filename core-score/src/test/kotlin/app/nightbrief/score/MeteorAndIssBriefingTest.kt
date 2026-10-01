package app.nightbrief.score

import app.nightbrief.astro.IssPass
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            .brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        val report = withIss.reportFor("home")!!
        val passes = report.issPasses
        assertTrue("expected a dark-window pass, got ${passes.size}", passes.isNotEmpty())
        val dark = report.ephemeris.darkWindow!!
        assertTrue(passes.all { !it.peak.isBefore(dark.start) && !it.peak.isAfter(dark.end) })
        val digest = DigestComposer.compose(report, emptyList())
        assertTrue(digest.lines.any { it.startsWith("ISS ") })

        val planned = BriefingService(source, clock, iss = FixedTle(tle))
            .plan(toronto, report.date, kit)
        assertEquals(passes.map { it.peak }, planned.issPasses.map { it.peak })

        val failed = BriefingService(source, clock, iss = object : TleSource {
            override suspend fun fetchIss(): IssTle = throw WeatherApiException("celestrak down")
        }).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        assertTrue(failed.reportFor("home")!!.issPasses.isEmpty())
        assertEquals(withIss.reportFor("home")!!.scoreValue, failed.reportFor("home")!!.scoreValue)
        assertTrue(failed.reportFor("home")!!.warnings.isEmpty())

        val malformed = BriefingService(
            source,
            clock,
            iss = FixedTle(IssTle("not-a-tle", "also-not", clock.instant())),
        ).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        assertTrue(malformed.reportFor("home")!!.issPasses.isEmpty())
        assertEquals(withIss.reportFor("home")!!.scoreValue, malformed.reportFor("home")!!.scoreValue)
    }

    @Test
    fun aShowerBelowTheWatchBarIsOmittedFromTheDigest() {
        val date = LocalDate.of(2024, 8, 12)
        val report = NightPlanner.plan(toronto, date, forecast(toronto, "2024-08-12T00:00:00Z"), kit)
        val weak = report.copy(meteor = report.meteor!!.copy(peakRadiantAltitudeDeg = 5.0))
        assertFalse(weak.meteor!!.worthWatching)
        val lines = DigestComposer.compose(weak, emptyList()).lines
        assertTrue(lines.none { it.contains("Perseids") })
    }

    @Test
    fun aNightWithNoShowerOmitsTheMeteorLine() {
        val date = LocalDate.of(2024, 3, 2)
        val report = NightPlanner.plan(toronto, date, forecast(toronto, "2024-03-01T00:00:00Z"), kit)
        assertNull(report.meteor)
        assertTrue(DigestComposer.compose(report, emptyList()).lines.none { it.contains("ZHR") })
    }

    @Test
    fun issLineUsesTheHighestPassAndDescribesAPartialSpan() {
        assertNull(DigestComposer.issLine(emptyList()) { it.toString() })
        val low = pass(peakAlt = 20.0, rise = "2024-03-15T01:00:00Z", set = "2024-03-15T01:04:00Z")
        val high = pass(peakAlt = 62.4, rise = "2024-03-15T03:10:00Z", set = "2024-03-15T03:16:00Z", azimuth = 225.0)
        val line = DigestComposer.issLine(listOf(low, high)) { it.toString().substring(11, 16) }
        assertEquals("ISS 03:10–03:16, peak 62° SW · 2 passes", line)

        val alreadyUp = high.copy(rise = null)
        assertTrue(DigestComposer.issLine(listOf(alreadyUp)) { it.toString() }!!.contains("until "))
        val stillUp = high.copy(set = null)
        assertTrue(DigestComposer.issLine(listOf(stillUp)) { it.toString() }!!.contains("from "))
        val peakOnly = high.copy(rise = null, set = null)
        assertTrue(DigestComposer.issLine(listOf(peakOnly)) { it.toString() }!!.contains("at "))
    }

    private fun pass(peakAlt: Double, rise: String?, set: String?, azimuth: Double = 10.0) = IssPass(
        rise = rise?.let(Instant::parse),
        set = set?.let(Instant::parse),
        peak = Instant.parse("2024-03-15T03:13:00Z"),
        peakAltitudeDeg = peakAlt,
        peakAzimuthDeg = azimuth,
    )

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
