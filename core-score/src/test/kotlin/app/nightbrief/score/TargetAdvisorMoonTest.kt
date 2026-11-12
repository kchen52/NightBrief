package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.TimeWindow
import app.nightbrief.gear.GearCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TargetAdvisorMoonTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun moonSetBeforeTheBestInstantSaysWhenToStart() {
        val line = TargetAdvisor.moonCaveat(
            moonUpDuringWindow = true,
            moonUpAtBest = false,
            percent = 28,
            moonset = Instant.parse("2024-08-11T02:00:00Z"),
            zone = utc,
        )
        assertEquals("Moon sets 02:00 — shoot after that", line)
    }

    @Test
    fun moonStillUpAtTheBestInstantWarns() {
        val line = TargetAdvisor.moonCaveat(
            moonUpDuringWindow = true,
            moonUpAtBest = true,
            percent = 29,
            moonset = Instant.parse("2024-08-11T06:00:00Z"),
            zone = utc,
        )
        assertEquals("a 29% Moon will brighten the sky", line)
    }

    @Test
    fun moonOutsideTheWindowStaysQuiet() {
        assertNull(TargetAdvisor.moonCaveat(false, false, 28, null, utc))
    }

    @Test
    fun contiguousWindowsSplitsAcrossAMoonBlockedGapAndSuggestCarriesMoonFreeSubWindows() {
        val t0 = Instant.parse("2024-08-11T01:00:00Z")
        val step = Duration.ofMinutes(10)
        val samples = listOf(
            t0,
            t0.plus(step),
            t0.plus(step.multipliedBy(2)),
            // Two-hour moon-blocked gap.
            t0.plus(Duration.ofHours(3)),
            t0.plus(Duration.ofHours(3)).plus(step),
        )
        val subWindows = TargetAdvisor.contiguousWindows(samples, step)
        assertEquals(
            listOf(
                TimeWindow(t0, t0.plus(step.multipliedBy(2))),
                TimeWindow(t0.plus(Duration.ofHours(3)), t0.plus(Duration.ofHours(3)).plus(step)),
            ),
            subWindows,
        )

        // On 2024-08-12 in Toronto, the waxing crescent Moon sets after astronomical dusk,
        // so targets that start at dusk have a moonlit lead-in followed by a moon-free sub-window.
        val zone = ZoneId.of("America/Toronto")
        val eph = NightEphemeris.compute(LocalDate.of(2024, 8, 12), zone, 43.65, -79.38)
        val suggestions = TargetAdvisor.suggest(eph, bortle = 4, kit = GearCatalog.exampleKit, limit = 10)
        val withMoonUp = suggestions.first { it.moonUpDuringWindow }
        assertTrue(
            "expected a distinct moon-free sub-window inside ${withMoonUp.window}, got ${withMoonUp.moonFreeWindows}",
            withMoonUp.hasDistinctMoonFreeWindows,
        )
        assertTrue(withMoonUp.moonFreeWindows.single().start.isAfter(withMoonUp.window.start))
    }
}
