package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.GridOpenWater
import app.nightbrief.sites.OpenWater
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream

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

    @Test
    fun skipsOpenWaterAndScoresTheNextDarkestLand() = runTest {
        // East of home is the darker sky, and it is also the lake.
        val lookup = BortleLookup { _, lon -> if (lon > home.longitude) 2 else 4 }
        val water = OpenWater { _, lon -> lon > home.longitude }
        val briefings = FakeBriefings()
        val found = DarkSkyFinder.search(primary(), kit, lookup, briefings, water = water)
        assertTrue("expected land candidates, got none", found.isNotEmpty())
        assertTrue(
            "scored longitudes ${found.map { it.site.longitude }}",
            found.all { it.site.longitude <= home.longitude },
        )
        assertTrue("bortles were ${found.map { it.bortle }}", found.all { it.bortle == 4 })
        assertEquals(found.size, briefings.plans)
    }

    @Test
    fun waterLookupFailureSkipsThatSampleOnly() = runTest {
        val lookup = BortleLookup { _, _ -> 3 }
        val water = OpenWater { lat, _ ->
            if (lat > home.latitude) throw IllegalStateException("mask")
            false
        }
        val briefings = FakeBriefings()
        val found = DarkSkyFinder.search(primary(), kit, lookup, briefings, water = water)
        assertTrue("expected survivors south of home, got none", found.isNotEmpty())
        for (candidate in found) {
            assertTrue(
                "northern failure leaked: ${candidate.site.latitude}",
                candidate.site.latitude <= home.latitude,
            )
        }
    }

    @Test
    fun torontoSearchDropsLakeOntario() = runTest {
        val water = GridOpenWater {
            GZIPInputStream(asset("water_mask.wmsk.gzip").inputStream().buffered())
        }
        val offshore = listOf(
            30.0 to 90.0,
            30.0 to 135.0,
            30.0 to 180.0,
            45.0 to 90.0,
            45.0 to 135.0,
        )
        val samples = DarkSkyFinder.samplePoints(home)
        for ((distanceKm, bearingDeg) in offshore) {
            val sample = samples.first { it.distanceKm == distanceKm && it.bearingDeg == bearingDeg }
            assertTrue(
                "expected open water at $distanceKm km bearing $bearingDeg (${sample.latitude}, ${sample.longitude})",
                water.isOpenWater(sample.latitude, sample.longitude),
            )
        }
        val lookup = BortleLookup { _, _ -> 3 }
        val found = DarkSkyFinder.search(primary(), kit, lookup, FakeBriefings(), water = water)
        assertTrue("expected land candidates, got none", found.isNotEmpty())
        for (candidate in found) {
            assertTrue(
                "recommended ${candidate.site.latitude}, ${candidate.site.longitude}",
                !water.isOpenWater(candidate.site.latitude, candidate.site.longitude),
            )
        }
        val offshorePoints = offshore.map { (distanceKm, bearingDeg) ->
            samples.first { it.distanceKm == distanceKm && it.bearingDeg == bearingDeg }
        }
        for (candidate in found) {
            val hit = offshorePoints.any {
                kotlin.math.abs(it.latitude - candidate.site.latitude) < 1e-6 &&
                    kotlin.math.abs(it.longitude - candidate.site.longitude) < 1e-6
            }
            assertTrue("Lake Ontario sample was scored: ${candidate.site.name}", !hit)
        }
    }

    private fun asset(name: String): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .map { File(it, "data/src/main/assets/$name") }
            .firstOrNull { it.isFile }
            ?: error("missing data/src/main/assets/$name (started at ${start.absolutePath})")
    }
}
