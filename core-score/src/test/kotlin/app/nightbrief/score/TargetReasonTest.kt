package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TargetReasonTest {
    private val kit = GearCatalog.exampleKit
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun cygnusDoesNotPromiseACoreThatStaysBelowTheHorizon() {
        val date = LocalDate.of(2026, 11, 3)
        val eph = NightEphemeris.compute(date, zone, toronto.latitude, toronto.longitude)
        assertNull("galactic core was ${eph.milkyWay}", eph.milkyWay)
        val cygnus = TargetCatalog.all.first { it.id == "cygnus" }
        val suggestion = TargetAdvisor.suggest(
            eph,
            bortle = 4,
            kit = kit,
            catalog = listOf(cygnus),
        ).singleOrNull()
        assertNotNull("Cygnus should still be a target on $date", suggestion)
        val reason = suggestion!!.reason
        assertTrue(reason, "core" !in reason.lowercase())
        assertTrue(reason, reason.contains("Northern Milky Way"))
    }

    @Test
    fun theCoreStillNamesItselfWhenItClearsTheHorizon() {
        val date = LocalDate.of(2024, 5, 15)
        val eph = NightEphemeris.compute(date, zone, toronto.latitude, toronto.longitude)
        assertNotNull(eph.milkyWay)
        val suggestion = TargetAdvisor.suggest(
            eph,
            bortle = 4,
            kit = kit,
            catalog = listOf(TargetCatalog.milkyWayCore),
        ).singleOrNull()
        assertNotNull(suggestion)
        val reason = suggestion!!.reason
        assertTrue(reason, reason.contains("core", ignoreCase = true))
    }
}
