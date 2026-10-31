package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.sites.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrackerTargetsTest {
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val august = LocalDate.of(2024, 8, 10)
    private val june = LocalDate.of(2024, 6, 21)

    private val gti = GearCatalog.trackers.first { it.id == "sw-star-adventurer-gti" }
    private val rotator = GearCatalog.trackers.first { it.id == "msm-rotator" }
    private val tele = GearCatalog.lenses.first { it.id == "rf-100-500" }

    @Test
    fun gtiPlansTightTargetsAWidefieldTrackerLeavesOut() {
        val kit = GearCatalog.exampleKit.addLens(tele)
        val eph = ephemeris(august)
        val onGti = TargetAdvisor.suggest(eph, toronto.effectiveBortle, kit.withTracker(gti), limit = 20)
        val onRotator = TargetAdvisor.suggest(eph, toronto.effectiveBortle, kit.withTracker(rotator), limit = 20)
        val bare = TargetAdvisor.suggest(eph, toronto.effectiveBortle, kit, limit = 20)

        val hercules = onGti.first { it.target.id == "m13" }
        assertTrue(hercules.reason, hercules.reason.contains("Star Adventurer GTi"))
        assertTrue(hercules.exposure!!.tracked)
        assertTrue(hercules.exposure!!.reachesTarget)
        val crop = GearCatalog.canonR7.cropFactor
        val capped = gti.planningFocalMm / crop
        assertTrue(
            "focal ${hercules.exposure!!.focalMm} should sit near $capped, not the 600 mm ideal",
            hercules.exposure!!.focalMm in (capped - 15.0)..(capped + 15.0),
        )

        assertTrue(onRotator.none { it.target.id == "m13" })
        assertTrue(onGti.none { it.target.untrackedOnly })
        assertTrue(onRotator.none { it.target.untrackedOnly })
        assertTrue(bare.any { it.target.untrackedOnly })
        assertTrue(onGti.any { it.target.id == "mw-core" })
        assertTrue(onRotator.any { it.target.id == "mw-core" })
    }

    @Test
    fun aTrackerDoesNotShortenTheMoonCloseUp() {
        val kit = GearKit(
            bodies = listOf(GearCatalog.canonR7),
            lenses = listOf(tele),
            primaryBodyId = GearCatalog.canonR7.id,
        ).withTracker(rotator)
        val closeup = TargetAdvisor.suggest(ephemeris(june), toronto.effectiveBortle, kit, limit = 20)
            .first { it.target.id == "moon-closeup" }
        assertTrue("focal was ${closeup.exposure!!.focalMm}", closeup.exposure!!.focalMm > 200.0)
        assertTrue(closeup.reason, !closeup.reason.contains("Rotator"))
    }

    private fun ephemeris(date: LocalDate): NightEphemeris =
        NightPlanner.plan(toronto, date, forecast = null, kit = GearCatalog.exampleKit, includeSuggestions = false)
            .ephemeris
}
