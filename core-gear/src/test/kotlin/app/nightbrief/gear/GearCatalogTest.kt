package app.nightbrief.gear

import org.junit.Assert.assertEquals
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
        assertTrue(GearCatalog.lenses.any { it.name.startsWith("Tamron 35–150") })
    }
}
