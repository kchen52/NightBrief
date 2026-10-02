package app.nightbrief.sites

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHorizonTest {
    @Test
    fun sectorsStepClockwiseFromNorth() {
        assertEquals(0, LocalHorizon.sectorIndex(0.0))
        assertEquals(0, LocalHorizon.sectorIndex(359.0))
        assertEquals(0, LocalHorizon.sectorIndex(-10.0))
        assertEquals(1, LocalHorizon.sectorIndex(22.5))
        assertEquals(4, LocalHorizon.sectorIndex(180.0))
        assertEquals(5, LocalHorizon.sectorIndex(225.0))
    }

    @Test
    fun anUnsetSectorBlocksNothingAndASetOneAppliesOnlyThere() {
        val horizon = LocalHorizon(south = 25.0)
        assertTrue(!horizon.isUnset)
        assertEquals(25.0, horizon.obstructionDeg(180.0), 0.0)
        assertEquals(0.0, horizon.obstructionDeg(0.0), 0.0)
        assertEquals(0.0, LocalHorizon().obstructionDeg(180.0), 0.0)
        assertTrue(LocalHorizon().isUnset)
        assertTrue(!LocalHorizon.open().isUnset)
        assertEquals(0.0, LocalHorizon.open().obstructionDeg(180.0), 0.0)
    }

    @Test
    fun altitudeOutsideZeroToNinetyIsRejected() {
        assertRejected { LocalHorizon(south = 91.0) }
        assertRejected { LocalHorizon(north = -1.0) }
    }

    @Test
    fun olderSiteJsonKeepsAFlatHorizon() {
        val json = Json { ignoreUnknownKeys = true }
        val site = json.decodeFromString(
            Site.serializer(),
            """{"id":"home","name":"Home","latitude":43.65,"longitude":-79.38,"zoneId":"America/Toronto"}""",
        )
        assertTrue(site.horizon.isUnset)
        val saved = json.decodeFromString(Site.serializer(), json.encodeToString(Site.serializer(), site))
        assertEquals(site, saved)

        val withTrees = site.copy(horizon = LocalHorizon(south = 25.0))
        val roundTrip = json.decodeFromString(Site.serializer(), json.encodeToString(Site.serializer(), withTrees))
        assertEquals(25.0, roundTrip.horizon.south!!, 0.0)
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("horizon altitude"))
            return
        }
        throw AssertionError("expected a horizon altitude rejection")
    }
}
