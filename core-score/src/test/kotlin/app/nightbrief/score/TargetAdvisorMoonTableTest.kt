package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The Moon table is shared by every candidate; these fix its output across moon phases. */
class TargetAdvisorMoonTableTest {
    @Test
    fun sharedMoonTableProducesTheSameSuggestionsAcrossPhases() {
        val zone = ZoneId.of("America/Toronto")
        val expected = mapOf(
            LocalDate.of(2024, 4, 8) to listOf(
                "cygnus@2024-04-09T08:55:23.437Z",
                "big-dipper@2024-04-09T03:55:23.437Z",
                "leo@2024-04-09T03:05:23.437Z",
                "mw-core@2024-04-09T08:55:23.437Z",
                "polaris-trails@2024-04-09T01:35:23.437Z",
            ),
            LocalDate.of(2024, 4, 15) to listOf(
                "cygnus@2024-04-16T08:46:38.437Z",
                "moonlit-landscape@2024-04-16T01:46:38.437Z",
                "moon-closeup@2024-04-16T01:46:38.437Z",
                "big-dipper@2024-04-16T03:26:38.437Z",
                "leo@2024-04-16T02:36:38.437Z",
            ),
            LocalDate.of(2024, 6, 22) to listOf(
                "moonlit-landscape@2024-06-23T06:35:42.187Z",
                "polaris-trails@2024-06-23T07:05:42.187Z",
                "big-dipper@2024-06-23T03:25:42.187Z",
                "moon-closeup@2024-06-23T06:35:42.187Z",
                "scorpius@2024-06-23T03:35:42.187Z",
            ),
        )
        for ((date, ids) in expected) {
            val eph = NightEphemeris.compute(date, zone, 43.6532, -79.3832)
            val actual = TargetAdvisor.suggest(eph, bortle = 4, kit = GearCatalog.exampleKit, limit = 5)
                .map { "${it.target.id}@${it.bestTime}" }
            assertEquals("$date suggestions", ids, actual)
        }
    }
}
