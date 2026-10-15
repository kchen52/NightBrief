package app.nightbrief.score

import app.nightbrief.astro.Ephemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HeroSkyTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val zone = ZoneId.of("America/Toronto")
    private val report = NightPlanner.plan(home, date, forecast(), GearCatalog.exampleKit)

    @Test
    fun sunAltitudeBandsFollowTheTwilightThresholds() {
        assertEquals(SkyTwilight.DAY, SkyTwilight.fromSunAltitude(10.0))
        assertEquals(SkyTwilight.DAY, SkyTwilight.fromSunAltitude(Ephemeris.HORIZON_ALTITUDE + 0.01))
        assertEquals(SkyTwilight.CIVIL, SkyTwilight.fromSunAltitude(Ephemeris.HORIZON_ALTITUDE))
        assertEquals(SkyTwilight.CIVIL, SkyTwilight.fromSunAltitude(-3.0))
        assertEquals(SkyTwilight.CIVIL, SkyTwilight.fromSunAltitude(Ephemeris.CIVIL_TWILIGHT + 0.01))
        assertEquals(SkyTwilight.NAUTICAL, SkyTwilight.fromSunAltitude(Ephemeris.CIVIL_TWILIGHT))
        assertEquals(SkyTwilight.NAUTICAL, SkyTwilight.fromSunAltitude(-9.0))
        assertEquals(SkyTwilight.NAUTICAL, SkyTwilight.fromSunAltitude(Ephemeris.NAUTICAL_TWILIGHT + 0.01))
        assertEquals(SkyTwilight.ASTRO, SkyTwilight.fromSunAltitude(Ephemeris.NAUTICAL_TWILIGHT))
        assertEquals(SkyTwilight.ASTRO, SkyTwilight.fromSunAltitude(-40.0))
    }

    @Test
    fun hourlySamplesAndTheAfternoonMatchThoseBands() {
        val afternoon = date.atTime(15, 0).atZone(zone).toInstant()
        assertEquals(SkyTwilight.DAY, HeroSky.of(report, afternoon).twilight)
        val sunrise = report.ephemeris.sunrise
        assertNotNull(sunrise)
        assertEquals(SkyTwilight.DAY, HeroSky.of(report, sunrise!!.plus(Duration.ofHours(2))).twilight)
        report.ephemeris.hourly.forEach { sample ->
            assertEquals(
                SkyTwilight.fromSunAltitude(sample.sunAltitudeDeg),
                HeroSky.of(report, sample.time).twilight,
            )
        }
        assertTrue(
            "August night in Toronto should reach astro dark",
            report.ephemeris.hourly.any { SkyTwilight.fromSunAltitude(it.sunAltitudeDeg) == SkyTwilight.ASTRO },
        )
        val sunset = report.ephemeris.sunset
        assertNotNull(sunset)
        assertEquals(SkyTwilight.CIVIL, HeroSky.of(report, sunset!!.plus(Duration.ofMinutes(12))).twilight)
    }

    @Test
    fun moonUsesTonightIlluminationAndTheCloseupTerminator() {
        val mid = HeroSky.midpoint(report.ephemeris)
        val sky = HeroSky.of(report, mid)
        assertEquals(report.ephemeris.moonIllumination, sky.moonIllumination, 0.0)
        assertEquals(Ephemeris.moonIllumination(mid), report.ephemeris.moonIllumination, 1e-9)
        assertEquals(Ephemeris.moonTerminatorAngleDeg(mid), sky.moonTerminatorDeg, 1e-6)
        assertTrue(
            "terminator ${sky.moonTerminatorDeg}",
            sky.moonTerminatorDeg >= 0.0 && sky.moonTerminatorDeg < 360.0,
        )
    }

    @Test
    fun milkyWayFollowsTheScoreAndTheShootingStarStartsAtExcellent() {
        val now = HeroSky.midpoint(report.ephemeris)
        val excellent = HeroSky.of(withScore(85), now)
        val below = HeroSky.of(withScore(84), now)
        val half = HeroSky.of(withScore(50), now)
        val none = HeroSky.of(report.copy(score = null), now)
        assertTrue(excellent.shootingStar)
        assertFalse(below.shootingStar)
        assertFalse(none.shootingStar)
        assertEquals(0.85, excellent.milkyWayAlpha, 0.0)
        assertEquals(0.5, half.milkyWayAlpha, 0.0)
        assertEquals(0.0, none.milkyWayAlpha, 0.0)
        assertEquals(Band.EXCELLENT.min, 85)
    }

    @Test
    fun aStillFrameUsesTheMiddleOfTheDarkWindow() {
        val dark = report.ephemeris.darkWindow
        assertNotNull(dark)
        assertEquals(dark!!.start.plus(dark.duration.dividedBy(2)), HeroSky.stillInstant(report))
        assertEquals(HeroSky.stillInstant(report), HeroSky.stillInstant(report))
    }

    private fun withScore(score: Int): NightReport =
        report.copy(score = report.score!!.copy(score = score))

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude,
            home.longitude,
            "best_match",
            start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
