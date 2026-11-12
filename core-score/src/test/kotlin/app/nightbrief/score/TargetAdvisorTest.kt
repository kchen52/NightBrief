package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.RaDec
import app.nightbrief.astro.Target
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.astro.TargetKind
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.TargetInterest
import app.nightbrief.sites.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TargetAdvisorTest {
    private val torontoZone = ZoneId.of("America/Toronto")
    private val torontoEph = NightEphemeris.compute(LocalDate.of(2024, 8, 5), torontoZone, 43.65, -79.38)
    private val sydneyZone = ZoneId.of("Australia/Sydney")
    private val sydneyMayEph = NightEphemeris.compute(LocalDate.of(2024, 5, 8), sydneyZone, -33.87, 151.21)
    private val sydneyNovEph = NightEphemeris.compute(LocalDate.of(2024, 11, 1), sydneyZone, -33.87, 151.21)

    private val veil = Target(
        id = "custom-veil",
        name = "Eastern Veil Nebula (NGC 6992)",
        kind = TargetKind.EMISSION_NEBULA,
        position = RaDec(314.0, 31.7),
        maxBortle = 5,
        maxMoonIllumination = 0.4,
        minFocalMm = 85,
        idealFocalMm = 200,
        minAltitudeDeg = 25.0,
        tip = "Narrowband brings out the filamentary shock front.",
    )

    @Test
    fun southernCatalogTargetsAppearFromASouthernSite() {
        val mayIds = TargetAdvisor.suggest(
            eph = sydneyMayEph,
            bortle = 3,
            kit = GearCatalog.exampleKit,
            limit = 50,
        ).map { it.target.id }.toSet()
        assertTrue("expected Carina Nebula (ngc3372) in May from Sydney, got $mayIds", "ngc3372" in mayIds)
        assertTrue("expected Omega Centauri (ngc5139) in May from Sydney, got $mayIds", "ngc5139" in mayIds)

        val novIds = TargetAdvisor.suggest(
            eph = sydneyNovEph,
            bortle = 3,
            kit = GearCatalog.exampleKit,
            limit = 50,
        ).map { it.target.id }.toSet()
        assertTrue("expected Large Magellanic Cloud (lmc) in Nov from Sydney, got $novIds", "lmc" in novIds)
        assertTrue("expected Small Magellanic Cloud (smc) in Nov from Sydney, got $novIds", "smc" in novIds)
    }

    @Test
    fun customTargetRespectsTheInterestFilterAndBortleGate() {
        val catalog = TargetCatalog.withCustoms(listOf(veil))

        val underBortle4 = TargetAdvisor.suggest(
            eph = torontoEph,
            bortle = 4,
            kit = GearCatalog.exampleKit,
            catalog = catalog,
            limit = 50,
        ).map { it.target.id }
        assertTrue("expected custom Veil under Bortle 4, got $underBortle4", veil.id in underBortle4)

        val underBortle7 = TargetAdvisor.suggest(
            eph = torontoEph,
            bortle = 7,
            kit = GearCatalog.exampleKit,
            catalog = catalog,
            limit = 50,
        ).map { it.target.id }
        assertFalse("Bortle 7 exceeds Veil's maxBortle 5 without a filter, got $underBortle7", veil.id in underBortle7)

        val galaxiesOnly = TargetAdvisor.suggest(
            eph = torontoEph,
            bortle = 4,
            kit = GearCatalog.exampleKit.withInterests(setOf(TargetInterest.GALAXIES)),
            catalog = catalog,
            limit = 50,
        ).map { it.target.id }
        assertFalse("emission nebula custom target should be filtered out when only Galaxies is selected", veil.id in galaxiesOnly)

        val nebulaeOnly = TargetAdvisor.suggest(
            eph = torontoEph,
            bortle = 4,
            kit = GearCatalog.exampleKit.withInterests(setOf(TargetInterest.NEBULAE)),
            catalog = catalog,
            limit = 50,
        ).map { it.target.id }
        assertTrue("emission nebula custom target should survive when Nebulae is selected", veil.id in nebulaeOnly)
    }

    @Test
    fun withCustomsStripsMoonTrackingAndLunarKindsAndNightPlannerThreadsCustomsThrough() {
        val bogusMoon = veil.copy(id = "bogus-moon", tracksMoon = true)
        val bogusKind = veil.copy(id = "bogus-kind", kind = TargetKind.MOON)
        val merged = TargetCatalog.withCustoms(listOf(veil, bogusMoon, bogusKind)).map { it.id }
        assertTrue(veil.id in merged)
        assertFalse(bogusMoon.id in merged)
        assertFalse(bogusKind.id in merged)

        val site = Site("dark", "Dark", 43.65, -79.38, "America/Toronto", bortle = 3)
        val report = NightPlanner.plan(
            site = site,
            date = LocalDate.of(2024, 8, 5),
            forecast = null,
            kit = GearCatalog.exampleKit.withInterests(setOf(TargetInterest.NEBULAE)),
            customTargets = listOf(veil),
        )
        assertEquals(
            "expected custom Veil in NightPlanner suggestions",
            true,
            report.suggestions.any { it.target.id == veil.id },
        )
    }
}
