package app.nightbrief.score

import app.nightbrief.astro.CometOrbit
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.Planets
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.CometElementsFile
import app.nightbrief.weather.CometSource
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
import kotlin.math.atan2

class CometAdvisorTest {
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val kit = GearCatalog.exampleKit
    private val date = LocalDate.of(2024, 10, 12)

    @Test
    fun theBrighterCometAboveTheHorizonWinsAndAFaintOrLowOneDoesNot() {
        val zone = ZoneId.of("America/Toronto")
        val ephemeris = NightEphemeris.compute(date, zone, toronto.latitude, toronto.longitude)
        val dark = ephemeris.darkWindow!!
        val mid = dark.start.plusMillis(dark.duration.toMillis() / 2)
        val bright = opposition(mid, "C/2099 A1 (Bright)", absoluteMagnitude = 4.0)
        val dimmer = opposition(mid, "C/2099 A2 (Dimmer)", absoluteMagnitude = 6.0)
        val faint = opposition(mid, "C/2099 A3 (Faint)", absoluteMagnitude = 20.0)
        val below = southEclipticPole(mid, "C/2099 Z9 (South)")

        assertNull(CometAdvisor.forNight(ephemeris, emptyList()))
        assertNull(CometAdvisor.forNight(ephemeris, listOf(faint)))
        assertNull(CometAdvisor.forNight(ephemeris, listOf(below)))

        val outlook = CometAdvisor.forNight(ephemeris, listOf(dimmer, faint, below, bright))
        assertEquals("C/2099 A1 (Bright)", outlook!!.name)
        assertTrue("altitude ${outlook.peakAltitudeDeg}", outlook.peakAltitudeDeg >= CometAdvisor.MIN_ALTITUDE_DEG)
        assertTrue("magnitude ${outlook.magnitude}", outlook.magnitude <= CometAdvisor.MAGNITUDE_BAR)
        assertTrue(outlook.worthShowing)
        assertTrue(dark.contains(outlook.peakTime))
    }

    @Test
    fun theDigestKeepsTheImagingTargetAndDropsACometPastTheBar() {
        val showing = CometOutlook(
            name = "C/2023 A3 (Tsuchinshan-ATLAS)",
            magnitude = 2.4,
            peakAltitudeDeg = 38.0,
            peakTime = Instant.parse("2024-10-13T02:00:00Z"),
            peakAzimuthDeg = 250.0,
        )
        assertEquals("C/2023 A3 (Tsuchinshan-ATLAS) · mag 2.4 · 38°", CometAdvisor.digestLine(showing))
        assertTrue(CometAdvisor.detail(showing).contains("non-gravitational"))

        val report = NightPlanner.plan(toronto, date, forecast(), kit).copy(comet = showing)
        val digest = DigestComposer.compose(report, emptyList())
        assertTrue(digest.lines.any { it == CometAdvisor.digestLine(showing) })
        assertTrue(digest.lines.any { it.startsWith("Try: ") })
        assertTrue(report.suggestions.none { it.target.name.contains("Tsuchinshan") })

        val faint = showing.copy(magnitude = 8.1)
        assertFalse(faint.worthShowing)
        assertTrue(DigestComposer.compose(report.copy(comet = faint), emptyList()).lines.none { it.contains("mag") && it.contains("Tsuchinshan") })
        val low = showing.copy(peakAltitudeDeg = 9.0)
        assertFalse(low.worthShowing)
    }

    @Test
    fun aFailedElementFetchLeavesTheScoreAndOmitsTheCard() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-10-12T22:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(forecast())
        val plain = BriefingService(source, clock).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        val failed = BriefingService(source, clock, comets = object : CometSource {
            override suspend fun fetch(): CometElementsFile = throw WeatherApiException("mpc down")
        }).brief(listOf(toronto), kit, outlookDays = 1, forceRefresh = false)
        assertNull(failed.reportFor("home")!!.comet)
        assertEquals(plain.reportFor("home")!!.scoreValue, failed.reportFor("home")!!.scoreValue)
        assertTrue(failed.reportFor("home")!!.warnings.isEmpty())

        val garbage = BriefingService(source, clock, comets = object : CometSource {
            override suspend fun fetch(): CometElementsFile =
                CometElementsFile("<html>nope</html>", clock.instant())
        }).plan(toronto, date, kit)
        assertNull(garbage.comet)
        assertEquals(plain.reportFor("home")!!.scoreValue, garbage.scoreValue)
    }

    /** Perihelion at [instant], on the ecliptic, opposite the Sun, so it culminates near midnight. */
    private fun opposition(instant: Instant, name: String, absoluteMagnitude: Double): CometOrbit {
        val earth = Planets.earthBarycentre(instant)
        val longitude = Math.toDegrees(atan2(earth[1], earth[0])).let { if (it < 0) it + 360.0 else it }
        return CometOrbit(
            name = name,
            perihelion = instant,
            qAu = 1.5,
            eccentricity = 0.2,
            argPeriDeg = longitude,
            nodeDeg = 0.0,
            inclDeg = 0.0,
            absoluteMagnitude = absoluteMagnitude,
            slope = 4.0,
        )
    }

    /** At the south ecliptic pole, which does not rise from Toronto. */
    private fun southEclipticPole(instant: Instant, name: String) = CometOrbit(
        name = name,
        perihelion = instant,
        qAu = 1.0,
        eccentricity = 0.2,
        argPeriDeg = 270.0,
        nodeDeg = 0.0,
        inclDeg = 90.0,
        absoluteMagnitude = 0.0,
        slope = 4.0,
    )

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-10-12T00:00:00Z").epochSecond
        return Forecast(
            toronto.latitude, toronto.longitude, "best_match", start,
            (0 until 96).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 10, humidity = 40,
                    windKmh = 8.0, seeing = 2, transparency = 2,
                )
            },
        )
    }

    private class FakeSource(val forecast: Forecast) : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult =
            ForecastResult(forecast, ForecastStatus.FRESH)
    }
}
