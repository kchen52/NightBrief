package app.nightbrief.gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GearCatalogTest {
    @Test
    fun idsAreUnique() {
        val bodyIds = GearCatalog.bodies.map { it.id }
        val lensIds = GearCatalog.lenses.map { it.id }
        assertEquals(bodyIds.size, bodyIds.toSet().size)
        assertEquals(lensIds.size, lensIds.toSet().size)
        assertTrue(bodyIds.toSet().intersect(lensIds.toSet()).isEmpty())
    }

    @Test
    fun includesTheSigma16to300() {
        val lens = GearCatalog.lenses.first { it.id == "sigma-16-300-f35-dcos" }
        assertEquals("Sigma 16–300mm f/3.5–6.7 DC OS Contemporary", lens.name)
        assertEquals(16.0, lens.minFocalMm, 0.0)
        assertEquals(300.0, lens.maxFocalMm, 0.0)
        assertEquals(3.5, lens.maxAperture, 0.0)
        assertEquals(6.7, lens.maxApertureAtLongEnd, 0.0)
        assertTrue(lens.covers(135.0))
    }

    @Test
    fun catalogCoversCommonBodiesAndLenses() {
        assertTrue("bodies ${GearCatalog.bodies.size}", GearCatalog.bodies.size >= 45)
        assertTrue("lenses ${GearCatalog.lenses.size}", GearCatalog.lenses.size >= 60)
        assertTrue(GearCatalog.bodies.any { it.name == "Sony α6700" })
        assertTrue(GearCatalog.bodyMatches(GearCatalog.bodies.first { it.id == "sony-a7iv" }, "a7 IV"))
        assertTrue(GearCatalog.bodyMatches(GearCatalog.bodies.first { it.id == "sony-a7iii" }, "Sony a7 III"))
        assertTrue(GearCatalog.bodyMatches(GearCatalog.bodies.first { it.id == "sony-a7iv" }, "ILCE-7M4"))
        assertTrue("16-300".let { query ->
            GearCatalog.lenses.any { it.name.matchesGearQuery(query) && it.id == "sigma-16-300-f35-dcos" }
        })
        assertFalse(GearCatalog.bodyMatches(GearCatalog.bodies.first { it.id == "sony-a6700" }, "ILCE-7M4"))
        assertTrue(GearCatalog.lenses.any { it.name.startsWith("Tamron 35–150") })
    }
}
