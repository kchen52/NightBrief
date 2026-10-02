package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BrightMoonAndHorizonTest {
    private val kit = GearCatalog.exampleKit
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun fullMoonNightSuggestsACloseUpAndAMoonlitLandscape() {
        val report = NightPlanner.plan(toronto, LocalDate.of(2024, 6, 21), forecast = null, kit)
        assertTrue("illumination was ${report.ephemeris.moonIllumination}", report.ephemeris.moonIllumination > 0.5)
        val ids = report.suggestions.map { it.target.id }
        assertTrue(ids.toString(), "moon-closeup" in ids)
        assertTrue(ids.toString(), "moonlit-landscape" in ids)
        val closeup = report.suggestions.first { it.target.id == "moon-closeup" }
        assertTrue(closeup.reason, closeup.reason.contains("Full Moon"))
        assertTrue(closeup.reason, closeup.reason.contains("terminator at "))
        assertTrue(closeup.reason, closeup.reason.contains("°"))
        val landscape = report.suggestions.first { it.target.id == "moonlit-landscape" }
        assertTrue(landscape.reason, landscape.reason.contains("lights the foreground"))
        assertEquals("moonlit-landscape", report.suggestions.first().target.id)
    }

    @Test
    fun aThinMoonDoesNotOfferLunarTargets() {
        val report = NightPlanner.plan(toronto, LocalDate.of(2024, 8, 10), forecast = null, kit)
        assertTrue("illumination was ${report.ephemeris.moonIllumination}", report.ephemeris.moonIllumination <= 0.5)
        assertTrue(report.suggestions.none { it.target.requiresBrightMoon })
    }

    @Test
    fun compassSectorsMatchTheHorizonMask() {
        val points = LocalHorizon.ABBREVIATIONS
        listOf(0.0, 22.5, 90.0, 180.0, 225.0, 359.0).forEach { azimuth ->
            assertEquals(DigestComposer.compass(azimuth), points[LocalHorizon.sectorIndex(azimuth)])
        }
    }

    @Test
    fun northernTreesLeaveTheCoreAndSouthernOnesMoveIt() {
        val date = LocalDate.of(2024, 5, 15)
        val flat = NightEphemeris.compute(date, zone, toronto.latitude, toronto.longitude)
        val core = flat.milkyWay
        assertNotNull(core)
        core!!
        assertTrue("peak azimuth ${core.peakAzimuthDeg}", core.peakAzimuthDeg in 140.0..220.0)

        val north = LocalHorizon(north = 40.0, northEast = 40.0, northWest = 40.0)
        val withNorth = NightEphemeris.compute(
            date, zone, toronto.latitude, toronto.longitude,
            horizonObstructionDeg = north::obstructionDeg,
        )
        assertEquals(core.window, withNorth.milkyWay!!.window)
        assertNull(withNorth.milkyWay!!.clearsHorizonAt)

        val south = LocalHorizon(south = core.peakAltitudeDeg - 1, southEast = core.peakAltitudeDeg - 1, southWest = core.peakAltitudeDeg - 1)
        val withSouth = NightPlanner.plan(
            toronto.copy(horizon = south), date, forecast = null, kit,
        )
        val moved = withSouth.ephemeris.milkyWay
        if (moved == null) {
            assertTrue(withSouth.ephemeris.milkyWayBlockedByHorizon)
            assertTrue(DigestComposer.compose(withSouth, emptyList()).lines.contains(WidgetCopy.BEHIND_TREELINE))
        } else {
            assertNotNull(moved.clearsHorizonAt)
            assertEquals(moved.window.start, moved.clearsHorizonAt)
            assertTrue(moved.window.start.isAfter(core.window.start))
            val sentence = WidgetCopy.clearsTreeline(moved.clearsHorizonAt!!, zone)
            assertTrue(sentence.startsWith("The core clears the treeline at "))
            assertTrue(DigestComposer.compose(withSouth, emptyList()).lines.contains(sentence))
            val widget = WidgetCopy.milkyWayLine(moved.window, zone, moved.clearsHorizonAt)
            assertTrue(widget, widget.contains(sentence))
        }
        assertTrue(withSouth.suggestions.none { it.target.id == "mw-core" && it.window.start == core.window.start })
    }

    @Test
    fun aHorizonAboveTheCoreHidesItFromTheWindowTheDigestAndTheWidget() {
        val date = LocalDate.of(2024, 8, 10)
        val site = toronto.copy(
            horizon = LocalHorizon(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0),
        )
        val report = NightPlanner.plan(site, date, forecast = null, kit)
        assertNull(report.ephemeris.milkyWay)
        assertTrue(report.ephemeris.milkyWayBlockedByHorizon)
        assertTrue(report.suggestions.none { it.target.id == "mw-core" })
        assertTrue(DigestComposer.compose(report, emptyList()).lines.contains(WidgetCopy.BEHIND_TREELINE))
        assertEquals(WidgetCopy.BEHIND_TREELINE, WidgetCopy.milkyWayLine(null, zone, blockedByHorizon = true))
    }
}
