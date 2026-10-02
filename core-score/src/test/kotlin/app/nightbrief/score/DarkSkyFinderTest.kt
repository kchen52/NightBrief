package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DarkSkyFinderTest {
    private val home = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 8)
    private val kit = GearCatalog.exampleKit
    private val date = LocalDate.of(2024, 8, 10)

    private fun clearForecast(): Forecast {
        val s = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "gem_seamless", s,
            (0 until 240).map {
                HourlyWeather(
                    epochSecond = s + it * 3600L, cloudCover = 0, humidity = 60,
                    windKmh = 6.0, gustKmh = 10.0, jetStreamKmh = 80.0,
                    seeing = 3, transparency = 2,
                )
            },
        )
    }

    private fun primary(): NightReport = NightPlanner.plan(home, date, clearForecast(), kit)

    private inner class FakeBriefings(
        private val failAt: (Site) -> Boolean = { false },
    ) : BriefingSource {
        var plans = 0
        override suspend fun brief(sites: List<Site>, kit: app.nightbrief.gear.GearKit, outlookDays: Int, forceRefresh: Boolean): Briefing =
            throw UnsupportedOperationException("unused")
        override suspend fun plan(site: Site, date: LocalDate, kit: app.nightbrief.gear.GearKit): NightReport {
            plans++
            if (failAt(site)) throw IllegalStateException("offline at ${site.latitude},${site.longitude}")
            return NightPlanner.plan(site, date, clearForecast(), kit)
        }
    }

    @Test
    fun samplesRingsAroundPrimary() {
        val samples = DarkSkyFinder.samplePoints(home)
        assertEquals("samples were ${samples.size}", 32, samples.size)
        val rings = samples.map { it.distanceKm }.distinct().sorted()
        assertEquals(listOf(15.0, 30.0, 45.0, 60.0), rings)
        for (sample in samples) {
            val back = DarkSkyFinder.distanceKm(home.latitude, home.longitude, sample.latitude, sample.longitude)
            assertTrue("drifted to $back km for ring ${sample.distanceKm}", kotlin.math.abs(back - sample.distanceKm) < 0.5)
        }
    }

    @Test
    fun destinationMovesNorthForZeroBearing() {
        val (lat, lon) = DarkSkyFinder.destination(home.latitude, home.longitude, 60.0, 0.0)
        assertTrue("latitude was $lat", lat > home.latitude)
        assertTrue("longitude drifted to $lon", kotlin.math.abs(lon - home.longitude) < 0.5)
    }

    @Test
    fun findsDarkerSamplesAndScoresBestFirst() = runTest {
        val lookup = BortleLookup { lat, lon ->
            // East of home is dark, west is city-bright.
            if (lon > home.longitude) 3 else 9
        }
        val briefings = FakeBriefings()
        val found = DarkSkyFinder.search(primary(), kit, lookup, briefings)
        assertTrue("expected candidates, got none", found.isNotEmpty())
        assertTrue("expected at most ${DarkSkyFinder.MAX_CANDIDATES_TO_SCORE}, got ${found.size}", found.size <= DarkSkyFinder.MAX_CANDIDATES_TO_SCORE)
        for (candidate in found) {
            assertTrue("bortle was ${candidate.bortle}", candidate.bortle < home.effectiveBortle)
            assertTrue("distance was ${candidate.distanceKm}", candidate.distanceKm <= DarkSkyFinder.SEARCH_RADIUS_KM + 0.5)
            assertEquals(home.zoneId, candidate.site.zoneId)
        }
        val scores = found.map { it.report.scoreValue ?: -1 }
        assertEquals("scores were $scores", scores.sortedDescending(), scores)
    }

    @Test
    fun returnsEmptyWhenNothingDarker() = runTest {
        val lookup = BortleLookup { _, _ -> 9 }
        val briefings = FakeBriefings()
        assertTrue(DarkSkyFinder.search(primary(), kit, lookup, briefings).isEmpty())
    }

    @Test
    fun skipsLookupFailures() = runTest {
        val lookup = BortleLookup { _, lon ->
            if (lon > home.longitude) throw IllegalStateException("grid gap") else 3
        }
        val briefings = FakeBriefings()
        val found = DarkSkyFinder.search(primary(), kit, lookup, briefings)
        assertTrue("expected survivors, got none", found.isNotEmpty())
    }

    @Test
    fun skipsForecastFailures() = runTest {
        val lookup = BortleLookup { _, _ -> 2 }
        val briefings = FakeBriefings(failAt = { it.latitude > home.latitude })
        val found = DarkSkyFinder.search(primary(), kit, lookup, briefings, maxToScore = 5)
        assertTrue("expected survivors south of home, got none", found.isNotEmpty())
        for (candidate in found) {
            assertTrue("northern failure leaked: ${candidate.site.latitude}", candidate.site.latitude <= home.latitude)
        }
    }

    @Test
    fun limitsForecastCallsToDarkest() = runTest {
        val lookup = BortleLookup { _, _ -> 2 }
        val briefings = FakeBriefings()
        DarkSkyFinder.search(primary(), kit, lookup, briefings, maxToScore = 2)
        assertEquals(2, briefings.plans)
    }
}
