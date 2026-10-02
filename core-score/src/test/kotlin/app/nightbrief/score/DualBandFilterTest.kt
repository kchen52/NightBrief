package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.gear.FilterKind
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearFilter
import app.nightbrief.sites.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DualBandFilterTest {
    private val zone = ZoneId.of("America/Toronto")
    private val site = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 6)
    private val date = LocalDate.of(2024, 8, 10)

    @Test
    fun dualBandKeepsEmissionNebulaInBrightSkies() {
        val plain = GearCatalog.exampleKit
        // NGC 7000 allows Bortle 4; at Bortle 6 it is filtered without a dual-band filter.
        assertEquals(4, TargetCatalog.all.first { it.id == "ngc7000" }.maxBortle)
        assertEquals(4, TargetAdvisor.effectiveMaxBortle(TargetCatalog.all.first { it.id == "ngc7000" }, plain))

        val dual = plain.addFilter(GearFilter("test-dual", "Test Dual", FilterKind.DUAL_BAND))
        assertEquals(6, TargetAdvisor.effectiveMaxBortle(TargetCatalog.all.first { it.id == "ngc7000" }, dual))
    }

    @Test
    fun dualBandRelaxesMoonForEmissionNebulaeOnly() {
        val target = TargetCatalog.all.first { it.id == "ngc7000" }
        val plain = GearCatalog.exampleKit
        val dual = plain.addFilter(GearFilter("test-dual", "Test Dual", FilterKind.DUAL_BAND))
        assertTrue(TargetAdvisor.effectiveMaxMoon(target, dual) > target.maxMoonIllumination)

        val galaxy = TargetCatalog.all.first { it.id == "m31" }
        assertEquals(galaxy.maxMoonIllumination, TargetAdvisor.effectiveMaxMoon(galaxy, dual), 0.0)
        assertEquals(galaxy.maxBortle, TargetAdvisor.effectiveMaxBortle(galaxy, dual))
    }

    @Test
    fun emissionReasonNamesTheFilterWhenItHelped() {
        val eph = NightEphemeris.compute(date, zone, site.latitude, site.longitude)
        val dual = GearCatalog.exampleKit.addFilter(
            GearFilter("optolong-l-enhance", "Optolong L-eNhance (dual-band)", FilterKind.DUAL_BAND),
        )
        val suggestions = TargetAdvisor.suggest(eph, bortle = 6, dual)
        val emission = suggestions.filter { it.target.kind.name == "EMISSION_NEBULA" }
        // At least one emission target kept past its plain Bortle cap should credit the filter.
        val helped = emission.filter { it.target.maxBortle < 6 }
        if (helped.isNotEmpty()) {
            assertTrue(helped.first().reason, helped.first().reason.contains("filter"))
        }
    }
}
