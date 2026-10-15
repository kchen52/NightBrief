package app.nightbrief.score

import app.nightbrief.astro.MeteorShowers
import app.nightbrief.astro.MoonPhase
import app.nightbrief.astro.SkyEventKind
import app.nightbrief.astro.SkyEvents
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NightNamesTest {
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val zone = ZoneId.of("America/Toronto")
    private val thursday = LocalDate.of(2024, 8, 15)

    @Test
    fun anOrdinaryNightHasNoName() {
        assertNull(
            NightNames.title(
                date = LocalDate.of(2024, 3, 15),
                score = 42,
                fullMoon = false,
                clearSkies = false,
                showerLabel = null,
                skyEventTitle = null,
                bigNightThreshold = 85,
            ),
        )
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 45)
        val title = NightNames.title(report, 85)
        assertTrue("title was $title", title == null)
    }

    @Test
    fun aShowerNearPeakIsNamedForThatDay() {
        assertEquals("Perseid", NightNames.adjective("Perseids"))
        assertEquals("Eta Aquariid", NightNames.adjective("Eta Aquariids"))
        assertEquals(
            "Perseid Thursday",
            NightNames.title(
                date = thursday,
                score = 40,
                fullMoon = false,
                clearSkies = false,
                showerLabel = "Perseid",
                skyEventTitle = null,
                bigNightThreshold = 85,
            ),
        )
        val perseids = MeteorShowers.annual.first { it.id == "perseids" }
        assertEquals("Perseid", NightNames.showerLabel(watching(perseids, daysFromPeak = 0, altitude = 40.0, zhr = 40)))
        assertEquals("Perseid", NightNames.showerLabel(watching(perseids, daysFromPeak = -1, altitude = 40.0, zhr = 40)))
        assertNull(NightNames.showerLabel(watching(perseids, daysFromPeak = 2, altitude = 40.0, zhr = 40)))
        assertNull(NightNames.showerLabel(watching(perseids, daysFromPeak = 0, altitude = 10.0, zhr = 40)))
        assertNull(NightNames.showerLabel(watching(perseids, daysFromPeak = 0, altitude = 40.0, zhr = 5)))
    }

    @Test
    fun aClearFullMoonIsAGhostMoonAndBeatsTheBigOne() {
        val date = LocalDate.of(2024, 6, 21)
        val report = plan(home, date, cloud = 0)
        assertEquals(MoonPhase.FULL, report.ephemeris.moonPhaseName)
        assertTrue(NightNames.clearSkies(report))
        assertEquals("Ghost Moon Friday", NightNames.title(report, 85))
        assertEquals(
            "Ghost Moon Friday",
            NightNames.title(
                date = date,
                score = 95,
                fullMoon = true,
                clearSkies = true,
                showerLabel = null,
                skyEventTitle = null,
                bigNightThreshold = 85,
            ),
        )
    }

    @Test
    fun anEstimatedFullMoonIsNotCalledClear() {
        val report = plan(home, LocalDate.of(2024, 6, 21), cloud = 0, dropHalfTheCloud = true)
        assertEquals(MoonPhase.FULL, report.ephemeris.moonPhaseName)
        assertTrue(report.score!!.factor(Factor.CLOUD).estimated)
        assertFalse(NightNames.clearSkies(report))
        val title = NightNames.title(report, 85)
        assertTrue("title was $title", title == null || !title.startsWith("Ghost Moon"))
    }

    @Test
    fun theBigOneUsesTheCallersThreshold() {
        val date = LocalDate.of(2024, 3, 15)
        assertEquals(
            "The Big One",
            NightNames.title(date, 85, false, false, null, null, 85),
        )
        assertNull(NightNames.title(date, 84, false, false, null, null, 85))
        assertEquals(
            "The Big One",
            NightNames.title(date, 70, false, false, null, null, 70),
        )
        assertNull(NightNames.title(date, 69, false, false, null, null, 70))
    }

    @Test
    fun anEclipseOrOppositionNameWins() {
        assertEquals(
            "Total lunar eclipse",
            NightNames.title(thursday, 95, true, true, "Perseid", "Total lunar eclipse", 85),
        )
        assertEquals(
            "Saturn at opposition",
            NightNames.title(thursday, 95, false, false, "Perseid", "Saturn at opposition", 85),
        )
        val eclipse = SkyEvents.between(LocalDate.of(2025, 3, 13), LocalDate.of(2025, 3, 15), zone)
            .first { it.kind == SkyEventKind.LUNAR_ECLIPSE }
        val eclipseDate = eclipse.instant.atZone(zone).toLocalDate()
        val eclipseNight = plan(home, eclipseDate, cloud = 0)
        assertEquals(eclipse.title, NightNames.title(eclipseNight, 85))

        val opposition = SkyEvents.between(LocalDate.of(2024, 9, 6), LocalDate.of(2024, 9, 10), zone)
            .first { it.kind == SkyEventKind.OPPOSITION && "Saturn" in it.title }
        val oppositionDate = opposition.instant.atZone(zone).toLocalDate()
        val oppositionNight = plan(home, oppositionDate, cloud = 40)
        assertEquals(opposition.title, NightNames.title(oppositionNight, 95))
    }

    @Test
    fun aShowerNameBeatsAGhostMoonAndTheBigOne() {
        assertEquals(
            "Perseid Thursday",
            NightNames.title(thursday, 96, true, true, "Perseid", null, 85),
        )
    }

    private fun plan(
        site: Site,
        date: LocalDate,
        cloud: Int,
        dropHalfTheCloud: Boolean = false,
    ): NightReport {
        val start = date.minusDays(1).atStartOfDay(zone).toInstant()
        val forecast = Forecast(
            site.latitude,
            site.longitude,
            "test",
            start.epochSecond,
            (0 until 96).map { offset ->
                val missing = dropHalfTheCloud && offset % 2 == 1
                HourlyWeather(
                    epochSecond = start.epochSecond + offset * 3600L,
                    cloudCover = if (missing) null else cloud,
                    humidity = 50,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 80.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
        return NightPlanner.plan(site, date, forecast, kit)
    }

    private fun watching(
        shower: app.nightbrief.astro.MeteorShower,
        daysFromPeak: Int,
        altitude: Double,
        zhr: Int,
    ) = MeteorOutlook(
        shower = shower,
        peakDate = LocalDate.of(2024, 8, 12),
        daysFromPeak = daysFromPeak,
        expectedZhr = zhr,
        peakRadiantAltitudeDeg = altitude,
        peakRadiantTime = Instant.parse("2024-08-13T06:00:00Z"),
        peakRadiantAzimuthDeg = 40.0,
        moonIllumination = 0.1,
        moonUpAtRadiantPeak = false,
        moonRadiantSeparationDeg = 80.0,
        interference = MeteorInterference.NONE,
    )
}
