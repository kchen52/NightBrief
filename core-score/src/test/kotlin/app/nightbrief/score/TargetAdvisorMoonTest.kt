package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
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
}
