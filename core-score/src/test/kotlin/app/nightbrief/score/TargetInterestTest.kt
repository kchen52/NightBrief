package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.astro.TargetKind
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.TargetInterest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TargetInterestTest {
    private val zone = ZoneId.of("America/Toronto")
    private val eph = NightEphemeris.compute(LocalDate.of(2024, 8, 10), zone, 43.65, -79.38)

    @Test
    fun everyCatalogKindMapsToAnInterest() {
        TargetKind.entries.forEach { TargetAdvisor.interestOf(it) }
        assertEquals(TargetInterest.NEBULAE, TargetAdvisor.interestOf(TargetKind.EMISSION_NEBULA))
        assertEquals(TargetInterest.NEBULAE, TargetAdvisor.interestOf(TargetKind.REFLECTION_NEBULA))
    }

    @Test
    fun chosenInterestsAreTheOnlyKindsSuggested() {
        val kit = GearCatalog.exampleKit.withInterests(setOf(TargetInterest.NEBULAE))
        val suggestions = TargetAdvisor.suggest(eph, bortle = 3, kit, limit = 50)
        assertTrue("expected some nebulae under Bortle 3", suggestions.isNotEmpty())
        val kinds = suggestions.map { TargetAdvisor.interestOf(it.target.kind) }.toSet()
        assertEquals(setOf(TargetInterest.NEBULAE), kinds)
    }

    @Test
    fun noInterestsKeepsEveryKind() {
        val all = TargetAdvisor.suggest(eph, bortle = 3, GearCatalog.exampleKit, limit = 50)
        val nebulaeOnly = TargetAdvisor.suggest(
            eph, bortle = 3, GearCatalog.exampleKit.withInterests(setOf(TargetInterest.NEBULAE)), limit = 50,
        )
        assertTrue("all=${all.size} nebulaeOnly=${nebulaeOnly.size}", all.size > nebulaeOnly.size)
    }

    @Test
    fun olderKitJsonWithoutInterestsDecodesAsNoPreference() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val kit = json.decodeFromString(app.nightbrief.gear.GearKit.serializer(), """{"bodies":[],"lenses":[]}""")
        assertTrue(kit.interests.isEmpty())
    }
}
