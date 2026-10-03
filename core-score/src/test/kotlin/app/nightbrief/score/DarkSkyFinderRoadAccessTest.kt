package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.RoadAccess
import app.nightbrief.weather.RoadAccessSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DarkSkyFinderRoadAccessTest {
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

    private inner class FakeBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: app.nightbrief.gear.GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = throw UnsupportedOperationException("unused")

        override suspend fun plan(site: Site, date: LocalDate, kit: app.nightbrief.gear.GearKit): NightReport =
            NightPlanner.plan(site, date, clearForecast(), kit)
    }

    @Test
    fun annotatesCandidatesWithRoadAccess() = runTest {
        val lookup = BortleLookup { _, _ -> 3 }
        val roads = RoadAccessSource { lat, _ ->
            if (lat > home.latitude) RoadAccess.DRIVE_UP else RoadAccess.HIKE_IN
        }
        val found = DarkSkyFinder.search(primary(), kit, lookup, FakeBriefings(), roads = roads)
        assertTrue("expected candidates, got none", found.isNotEmpty())
        assertTrue(
            "expected mixed access, got ${found.map { it.access }}",
            found.any { it.access == RoadAccess.DRIVE_UP } && found.any { it.access == RoadAccess.HIKE_IN },
        )
    }

    @Test
    fun nullRoadsLeavesUnknown() = runTest {
        val lookup = BortleLookup { _, _ -> 3 }
        val found = DarkSkyFinder.search(primary(), kit, lookup, FakeBriefings(), roads = null)
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { it.access == RoadAccess.UNKNOWN })
    }

    @Test
    fun roadFailureLeavesUnknownAndStillScores() = runTest {
        val lookup = BortleLookup { _, _ -> 3 }
        val roads = RoadAccessSource { _, _ -> throw IllegalStateException("overpass down") }
        val found = DarkSkyFinder.search(primary(), kit, lookup, FakeBriefings(), roads = roads)
        assertTrue("expected survivors, got none", found.isNotEmpty())
        assertTrue(found.all { it.access == RoadAccess.UNKNOWN })
        assertTrue(found.all { it.report.scoreValue != null })
    }

    @Test
    fun roadAccessForMapsFailureToUnknown() = runTest {
        val sample = DarkSkyFinder.samplePoints(home).first()
        val failing = RoadAccessSource { _, _ -> throw IllegalStateException("boom") }
        assertEquals(RoadAccess.UNKNOWN, DarkSkyFinder.roadAccessFor(failing, sample))
        assertEquals(RoadAccess.UNKNOWN, DarkSkyFinder.roadAccessFor(null, sample))
    }
}
