package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MeteorShowersTest {
    private val quadrantids = MeteorShowers.annual.first { it.id == "quadrantids" }
    private val perseids = MeteorShowers.annual.first { it.id == "perseids" }
    private val geminids = MeteorShowers.annual.first { it.id == "geminids" }

    @Test
    fun quadrantidsSpanTheYearBoundaryAndSkipMidJanuary() {
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2025, 12, 30)).contains(quadrantids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 1, 4)).contains(quadrantids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2025, 12, 28)).contains(quadrantids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 1, 12)).contains(quadrantids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 1, 20)).none { it.id == quadrantids.id })
    }

    @Test
    fun daysFromPeakChangesSignAcrossTheQuadrantidPeak() {
        // Peak is 3 Jan. Dec 30 is still before that peak, so the offset is negative.
        assertEquals(-4, MeteorShowers.daysFromPeak(quadrantids, LocalDate.of(2025, 12, 30)))
        assertEquals(0, MeteorShowers.daysFromPeak(quadrantids, LocalDate.of(2026, 1, 3)))
        assertEquals(1, MeteorShowers.daysFromPeak(quadrantids, LocalDate.of(2026, 1, 4)))
        assertTrue(MeteorShowers.daysFromPeak(quadrantids, LocalDate.of(2025, 12, 30)) < 0)
        assertTrue(MeteorShowers.daysFromPeak(quadrantids, LocalDate.of(2026, 1, 4)) > 0)
    }

    @Test
    fun perseidsAreActiveOnTheirPeakDate() {
        val active = MeteorShowers.activeOn(LocalDate.of(2026, 8, 12))
        assertTrue(active.contains(perseids))
    }

    @Test
    fun geminidWindowIncludesBothEndpoints() {
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 12, 4)).contains(geminids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 12, 17)).contains(geminids))
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 12, 3)).none { it.id == geminids.id })
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 12, 18)).none { it.id == geminids.id })
    }

    @Test
    fun aDateOutsideEveryWindowIsNotActive() {
        assertTrue(MeteorShowers.activeOn(LocalDate.of(2026, 3, 15)).isEmpty())
    }

    @Test
    fun peaksAreListedInCalendarOrder() {
        val peaks = MeteorShowers.annual.map { it.peakMonth * 100 + it.peakDay }
        assertEquals(peaks.sorted(), peaks)
    }
}
