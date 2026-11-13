package app.nightbrief.score

import app.nightbrief.astro.CometOrbit
import app.nightbrief.astro.Planets
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.AirQualitySeries
import app.nightbrief.weather.AirQualitySource
import app.nightbrief.weather.CometElementsFile
import app.nightbrief.weather.CometSource
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.IssTle
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.TleSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.atan2

/**
 * Aurora, ISS, smoke, and comets are Tonight-only cards, and the comet file is parsed once per
 * brief. Week's [OutlookNight] keeps none of those products.
 */
class BriefingEnrichmentTest {
    private val clock = Clock.fixed(Instant.parse("2024-10-12T22:00:00Z"), ZoneOffset.UTC)
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val near = Site("near", "Near", 43.70, -79.40, "America/Toronto", bortle = 5)
    private val far = Site("far", "Far", 44.10, -79.90, "America/Toronto", bortle = 3)

    @Test
    fun theCometFileIsParsedOnceForEverySiteAndItsCardLandsOnTonight() = runTest {
        var parses = 0
        val orbits = listOf(opposition())
        val service = BriefingService(
            FakeForecasts,
            clock,
            comets = FixedComets,
            cometParser = { parses++; orbits },
        )
        val brief = service.brief(listOf(home, near, far), kit, outlookDays = 7, forceRefresh = false)

        assertEquals("one parse per brief, got $parses", 1, parses)
        for (report in brief.tonight) {
            assertNotNull("${report.site.id} lost its comet card", report.comet)
            assertEquals("C/2099 A1 (Test)", report.comet!!.name)
        }
    }

    @Test
    fun aCometParseFailureKeepsTheScoreAndDropsOnlyTheCard() = runTest {
        val service = BriefingService(
            FakeForecasts,
            clock,
            comets = FixedComets,
            cometParser = { throw IllegalArgumentException("bad column layout") },
        )
        val brief = service.brief(listOf(home), kit, outlookDays = 1, forceRefresh = false)
        val report = brief.reportFor(home.id)!!
        assertNull(report.comet)
        assertNotNull(report.score)
        assertEquals(emptyList<String>(), report.warnings)
    }

    @Test
    fun sideProductsDoNotChangeWhatWeekReads() = runTest {
        val bare = BriefingService(FakeForecasts, clock)
            .brief(listOf(home, near), kit, outlookDays = 7, forceRefresh = false)
        val enriched = BriefingService(
            FakeForecasts,
            clock,
            comets = FixedComets,
            cometParser = { listOf(opposition()) },
        ).brief(listOf(home, near), kit, outlookDays = 7, forceRefresh = false)

        assertEquals(bare.outlooks, enriched.outlooks)
        assertEquals(
            bare.tonight.map { "${it.site.id}/${it.date}/${it.scoreValue}" },
            enriched.tonight.map { "${it.site.id}/${it.date}/${it.scoreValue}" },
        )
    }

    @Test
    fun theLeanPlanFetchesNoSideProductsAndSkipsSuggestions() = runTest {
        var cometFetches = 0
        val service = BriefingService(
            FakeForecasts,
            clock,
            // Any fetch here throws, so the lean plan fails loudly if it asks for a side product.
            kp = object : KpSource {
                override suspend fun fetch(): KpForecast = throw AssertionError("lean plan fetched Kp")
            },
            iss = object : TleSource {
                override suspend fun fetchIss(): IssTle = throw AssertionError("lean plan fetched the ISS")
            },
            airQuality = object : AirQualitySource {
                override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries =
                    throw AssertionError("lean plan fetched air quality")
            },
            comets = FixedComets,
            cometParser = { cometFetches++; listOf(opposition()) },
        )
        val date = LocalDate.of(2024, 10, 12)
        val report = service.planLean(home, date, kit)

        assertEquals("lean plan fetched and parsed comets, got $cometFetches", 0, cometFetches)
        assertNull(report.aurora)
        assertNull(report.comet)
        assertNull(report.smoke)
        assertTrue(report.issPasses.isEmpty())
        assertTrue("lean plan built suggestions", report.suggestions.isEmpty())

        // The published reading is the full plan's reading, not a second score model.
        val full = BriefingService(FakeForecasts, clock).plan(home, date, kit)
        assertEquals(full.scoreValue, report.scoreValue)
        assertEquals(full.coverage, report.coverage)
        assertTrue("full plan lost its suggestions", full.suggestions.isNotEmpty())
    }

    /** Perihelion at the middle of the dark window, opposite the Sun, so it culminates near midnight. */
    private fun opposition(): CometOrbit {
        val date = LocalDate.of(2024, 10, 12)
        val ephemeris = app.nightbrief.astro.NightEphemeris.compute(
            date, home.zone, home.latitude, home.longitude,
        )
        val dark = ephemeris.darkWindow!!
        val instant = dark.start.plusMillis(dark.duration.toMillis() / 2)
        val earth = Planets.earthBarycentre(instant)
        val longitude = Math.toDegrees(atan2(earth[1], earth[0])).let { if (it < 0) it + 360.0 else it }
        return CometOrbit(
            name = "C/2099 A1 (Test)",
            perihelion = instant,
            qAu = 1.5,
            eccentricity = 0.2,
            argPeriDeg = longitude,
            nodeDeg = 0.0,
            inclDeg = 0.0,
            absoluteMagnitude = 4.0,
            slope = 4.0,
        )
    }

    private object FixedComets : CometSource {
        override suspend fun fetch(): CometElementsFile =
            CometElementsFile("C/2099 A1\n", Instant.parse("2024-10-12T12:00:00Z"))
    }

    private object FakeForecasts : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
            val start = Instant.parse("2024-10-12T00:00:00Z").epochSecond
            val hours = (0 until 24 * 9).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 10, humidity = 40,
                    windKmh = 8.0, seeing = 2, transparency = 2,
                )
            }
            return ForecastResult(
                Forecast(latitude, longitude, "best_match", start, hours),
                ForecastStatus.FRESH,
            )
        }
    }
}
