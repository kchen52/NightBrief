package app.nightbrief.gear

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerFilterTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun olderKitWithoutTrackerStillDecodes() {
        val raw = """{"bodies":[],"lenses":[]}"""
        val kit = json.decodeFromString(GearKit.serializer(), raw)
        assertEquals(null, kit.tracker)
        assertTrue(kit.filters.isEmpty())
        assertFalse(kit.isTracked)
    }

    @Test
    fun trackerLiftsTheThirtySecondCap() {
        val kit = GearCatalog.exampleKit
        val untracked = ExposureCalculator.bestFor(kit, 24.0, 12.0, bortle = 3, brightMoon = false)!!
        assertFalse(untracked.tracked)
        assertTrue("untracked ${untracked.shutterSeconds}", untracked.shutterSeconds <= 30.0)

        val trackedKit = kit.withTracker(GearCatalog.trackers.first())
        val tracked = ExposureCalculator.bestFor(trackedKit, 24.0, 12.0, bortle = 3, brightMoon = false)!!
        assertTrue(tracked.tracked)
        assertTrue("tracked ${tracked.shutterSeconds} vs ${untracked.shutterSeconds}",
            tracked.shutterSeconds >= untracked.shutterSeconds)
        assertTrue(tracked.stackCount != null && tracked.stackCount!! >= 1)
        assertTrue(tracked.integrationMinutes == 30 || tracked.integrationMinutes == 60)
        assertTrue(tracked.summary.contains("×"))
    }

    @Test
    fun trackedTelephotoSuggestsAnHourStack() {
        val tele = GearCatalog.lenses.first { it.id == "samyang-135-f2" }
        val kit = GearCatalog.exampleKit.addLens(tele).withTracker(GearCatalog.trackers.first())
        val s = ExposureCalculator.bestFor(kit, 600.0, 200.0, bortle = 4, brightMoon = false)!!
        assertTrue(s.tracked)
        assertEquals(60, s.integrationMinutes)
        assertTrue(s.shutterSeconds in 30.0..120.0)
    }

    @Test
    fun filterHelpers() {
        val dual = GearFilter("test-dual", "Test Dual", FilterKind.DUAL_BAND)
        val kit = GearCatalog.exampleKit.addFilter(dual)
        assertTrue(kit.hasDualBandFilter)
        assertFalse(kit.hasLightPollutionFilter)
        assertTrue(kit.removeFilter("test-dual").filters.isEmpty())
    }

    @Test
    fun catalogIdsStayUnique() {
        val ids = GearCatalog.bodies.map { it.id } +
            GearCatalog.lenses.map { it.id } +
            GearCatalog.trackers.map { it.id } +
            GearCatalog.filters.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
