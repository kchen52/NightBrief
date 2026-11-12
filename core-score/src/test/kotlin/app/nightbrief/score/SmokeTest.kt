package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.AirQualitySample
import app.nightbrief.weather.AirQualitySeries
import app.nightbrief.weather.AirQualitySource
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SmokeTest {
    private val kit = GearCatalog.exampleKit
    private val date = LocalDate.of(2024, 8, 10)
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val zone = ZoneId.of("America/Toronto")
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    @Test
    fun smokeAboveTheBarIsFlaggedWithItsPeak() {
        val hour = Instant.parse("2024-08-11T03:00:00Z") // 23:00 EDT
        val smoke = assess(darkHours = listOf(hour), hour to 80.0)!!
        assertTrue("Pm2.5 80 is above the 35 bar", smoke.smoky)
        assertEquals(hour, smoke.from)
        assertEquals(80.0, smoke.peakPm25, 0.001)
        assertEquals("Smoke from 23:00 through the night", SmokeCopy.riskLine(smoke) { clock.format(it) })
        assertEquals("Peak PM2.5 80 µg/m³ (smoky above 35)", SmokeCopy.peakLine(smoke))
    }

    @Test
    fun theBarItselfIsNotSmoke() {
        val hour = Instant.parse("2024-08-11T03:00:00Z")
        val atBar = assess(darkHours = listOf(hour), hour to Smoke.PM25_BAR)!!
        assertFalse("exactly 35 µg/m³ is at the bar, not above it", atBar.smoky)
        assertNull(atBar.from)
        assertNull(SmokeCopy.riskLine(atBar) { clock.format(it) })
        assertEquals("Peak PM2.5 35 µg/m³ (smoky above 35)", SmokeCopy.peakLine(atBar))

        val justOver = assess(darkHours = listOf(hour), hour to Smoke.PM25_BAR + 0.1)!!
        assertTrue("35.1 µg/m³ is above the bar", justOver.smoky)
    }

    @Test
    fun thinningAfterThePeakIsNamed() {
        val smoky = Instant.parse("2024-08-11T03:00:00Z") // 23:00 EDT
        val clear = Instant.parse("2024-08-11T06:00:00Z") // 02:00 EDT
        val smoke = assess(darkHours = listOf(smoky, clear), smoky to 90.0, clear to 12.0)!!
        assertEquals(smoky, smoke.from)
        assertEquals(clear, smoke.thinningAt)
        assertEquals(
            "Smoke from 23:00, thinning after 02:00",
            SmokeCopy.riskLine(smoke) { clock.format(it) },
        )
        assertTrue(
            "the detail should point at the second half, got ${SmokeCopy.detail(smoke)}",
            SmokeCopy.detail(smoke).contains("second half"),
        )
    }

    @Test
    fun aThinningHourBeforeThePeakIsNotNamed() {
        val clear = Instant.parse("2024-08-11T02:00:00Z")
        val smoky = Instant.parse("2024-08-11T03:00:00Z")
        val smoke = assess(darkHours = listOf(clear, smoky), clear to 10.0, smoky to 70.0)!!
        assertEquals(smoky, smoke.from)
        assertNull("only a later hour lifts the smoke, got ${smoke.thinningAt}", smoke.thinningAt)
    }

    @Test
    fun smokeIsJoinedToTheNearestSampleInsideTheTolerance() {
        val hour = Instant.parse("2024-08-11T03:00:00Z")
        val series = AirQualitySeries(
            listOf(AirQualitySample(hour.plusSeconds(40 * 60).epochSecond, pm25 = 60.0)),
        )
        val smoke = Smoke.assess(listOf(hour), series)!!
        assertTrue("a sample 40 minutes away still describes the hour", smoke.smoky)

        val tooFar = AirQualitySeries(
            listOf(AirQualitySample(hour.plusSeconds(4 * 3600).epochSecond, pm25 = 60.0)),
        )
        assertNull("no sample near any dark hour must not invent smoke", Smoke.assess(listOf(hour), tooFar))
    }

    @Test
    fun aMissingSeriesLeavesTheReportWithoutSmoke() {
        assertNull("no air-quality source must leave the field null", Smoke.assess(listOf(Instant.parse("2024-08-11T03:00:00Z")), null))
        val report = NightPlanner.plan(toronto, date, null, kit)
        assertNull(report.smoke)
    }

    @Test
    fun briefingAttachesSmokeAndAFailedFetchLeavesTheScoreAndWarningsAlone() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T16:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(forecast(toronto))
        val plain = BriefingService(source, clock).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        val withAir = BriefingService(source, clock, airQuality = FixedAirQuality(covering(toronto)))
            .brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        val plainReport = plain.reportFor("home")!!
        val smoky = withAir.reportFor("home")!!
        assertEquals(plainReport.scoreValue, smoky.scoreValue)
        val smoke = smoky.smoke ?: throw AssertionError("a series covering the dark hours should attach smoke")
        assertTrue("the fixture is above the bar, got ${smoke.peakPm25}", smoke.smoky)
        assertNull("a report without an air-quality reading must not carry smoke", plainReport.smoke)

        val failed = BriefingService(source, clock, airQuality = object : AirQualitySource {
            override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries =
                throw WeatherApiException("air quality down")
        }).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        val failedReport = failed.reportFor("home")!!
        assertNull("a failed air-quality fetch must not invent smoke", failedReport.smoke)
        assertEquals(plainReport.scoreValue, failedReport.scoreValue)
        assertTrue("a failed side product must not add a warning, got ${failedReport.warnings}", failedReport.warnings.isEmpty())
    }

    @Test
    fun smokeLineFollowsDewInTheDigest() {
        val hour = Instant.parse("2024-08-11T03:00:00Z")
        val smoke = assess(darkHours = listOf(hour), hour to 80.0)!!
        val dew = DewOutlook(from = hour, frostFrom = null, overnightLowC = 4.0, spreadKnown = true)
        val report = NightPlanner.plan(toronto, date, null, kit).copy(smoke = smoke, dew = dew)
        val lines = DigestComposer.compose(report, emptyList()).lines
        val smokeLine = lines.indexOfFirst { it.startsWith("Smoke from") }
        val dewLine = lines.indexOfFirst { it.startsWith("Dew likely") }
        assertTrue("expected a dew line in the digest, got $lines", dewLine >= 0)
        assertTrue("expected a smoke line in the digest, got $lines", smokeLine >= 0)
        assertTrue(
            "expected the smoke line after dew, got $lines",
            smokeLine > dewLine,
        )
    }

    private fun assess(darkHours: List<Instant>, vararg readings: Pair<Instant, Double>): SmokeOutlook? {
        val series = AirQualitySeries(
            readings.map { (time, pm25) -> AirQualitySample(time.epochSecond, pm25 = pm25) },
        )
        return Smoke.assess(darkHours, series)
    }

    /** One sample at every hour of the fixture's dark window, so no hour falls outside the tolerance. */
    private fun covering(site: Site): AirQualitySeries {
        val eph = NightEphemeris.compute(date, site.zone, site.latitude, site.longitude)
        val dark = eph.darkWindow!!
        val hours = eph.hourly.map { it.time }.filter { it in dark }
        return AirQualitySeries(hours.map { AirQualitySample(it.epochSecond, pm25 = 85.0) })
    }

    private fun forecast(site: Site): Forecast {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            site.latitude, site.longitude, "best_match", start,
            (0 until 96).map {
                HourlyWeather(epochSecond = start + it * 3600L, cloudCover = 10, humidity = 50, windKmh = 8.0)
            },
        )
    }

    private class FakeSource(val forecast: Forecast) : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean) =
            ForecastResult(forecast, ForecastStatus.FRESH)
    }

    private class FixedAirQuality(val series: AirQualitySeries) : AirQualitySource {
        override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries = series
    }
}
