package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class SkyEventsTest {
    private val utc = ZoneId.of("UTC")

    private fun dateOf(event: SkyEvent): LocalDate = event.instant.atZone(utc).toLocalDate()

    private fun within(events: List<SkyEvent>, date: LocalDate, days: Long): List<SkyEvent> =
        events.filter { kotlin.math.abs(ChronoUnit.DAYS.between(dateOf(it), date)) <= days }

    @Test
    fun findsTheMarch2025TotalLunarEclipse() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 10), utc)
        val lunar = within(events, LocalDate.of(2025, 3, 14), 2)
            .filter { it.kind == SkyEventKind.LUNAR_ECLIPSE }
        assertEquals("expected the 2025-03-14 eclipse, got $lunar", 1, lunar.size)
        assertEquals("Total lunar eclipse", lunar.single().title)
    }

    @Test
    fun findsTheApril2024TotalSolarEclipse() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 1, 1), utc)
        val solar = within(events, LocalDate.of(2024, 4, 8), 2)
            .filter { it.kind == SkyEventKind.SOLAR_ECLIPSE }
        assertEquals("expected the 2024-04-08 eclipse, got $solar", 1, solar.size)
        assertEquals("Total solar eclipse", solar.single().title)
    }

    @Test
    fun findsTheAugust2026TotalSolarEclipse() {
        val events = SkyEvents.yearAhead(LocalDate.of(2026, 1, 1), utc)
        val solar = within(events, LocalDate.of(2026, 8, 12), 2)
            .filter { it.kind == SkyEventKind.SOLAR_ECLIPSE }
        assertTrue("expected an eclipse near 2026-08-12, got $solar", solar.isNotEmpty())
    }

    @Test
    fun september2025PartialSolarEclipseWithoutTheAugustFalsePositive() {
        val events = SkyEvents.yearAhead(LocalDate.of(2025, 1, 1), utc)
        val solar = events.filter { it.kind == SkyEventKind.SOLAR_ECLIPSE }
        val august = solar.filter { dateOf(it).monthValue == 8 }
        assertTrue("expected no August 2025 eclipse, got $august", august.isEmpty())
        val september = within(solar, LocalDate.of(2025, 9, 21), 2)
        assertEquals("expected the 2025-09-21 partial, got $september", 1, september.size)
        assertEquals("Partial solar eclipse", september.single().title)
    }

    @Test
    fun findsTheMarsJupiterConjunctionOfAugust2024() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 1), utc)
        val found = within(events, LocalDate.of(2024, 8, 14), 2)
            .filter { it.kind == SkyEventKind.CONJUNCTION && "Mars" in it.title && "Jupiter" in it.title }
        assertEquals("expected the Mars–Jupiter appulse, got $found", 1, found.size)
    }

    @Test
    fun findsSaturnOpposition2024() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 1), utc)
        val found = within(events, LocalDate.of(2024, 9, 8), 3)
            .filter { it.kind == SkyEventKind.OPPOSITION && "Saturn" in it.title }
        assertEquals("expected Saturn opposition, got $found", 1, found.size)
    }

    @Test
    fun listsShowerPeaksAndSeasons() {
        val events = SkyEvents.yearAhead(LocalDate.of(2024, 8, 1), utc)
        val perseids = events.filter { it.kind == SkyEventKind.SHOWER_PEAK && it.title == "Perseids peak" }
        assertEquals(perseids.toString(), 1, perseids.size)
        assertEquals(LocalDate.of(2024, 8, 12), dateOf(perseids.single()))
        val equinox = events.filter { it.kind == SkyEventKind.SEASON && it.title == "September equinox" }
        assertEquals(1, equinox.size)
        assertTrue(dateOf(equinox.single()).dayOfMonth in 22..23)
    }

    @Test
    fun eventsAreSortedAndInRange() {
        val from = LocalDate.of(2024, 8, 1)
        val events = SkyEvents.yearAhead(from, utc)
        assertTrue("expected a full year of events, got ${events.size}", events.size >= 40)
        assertEquals(events.sortedBy { it.instant }, events)
        for (event in events) {
            val date = dateOf(event)
            assertTrue("$event out of range", !date.isBefore(from) && !date.isAfter(from.plusDays(366)))
        }
    }
}
