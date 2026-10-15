package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoonFactsTest {
    @Test
    fun theSameDateAlwaysPicksTheSameFact() {
        val date = LocalDate.of(2024, 8, 12)
        assertEquals(MoonFacts.forDate(date), MoonFacts.forDate(date))
    }

    @Test
    fun epochDaySelectsByModuloAndWraps() {
        val count = MoonFacts.facts.size
        assertTrue("need a list to index, size was $count", count >= 2)
        assertEquals(MoonFacts.facts[0], MoonFacts.forDate(LocalDate.ofEpochDay(0)))
        assertEquals(MoonFacts.facts[1], MoonFacts.forDate(LocalDate.ofEpochDay(1)))
        assertEquals(MoonFacts.facts[0], MoonFacts.forDate(LocalDate.ofEpochDay(count.toLong())))
        assertEquals(MoonFacts.facts[count - 1], MoonFacts.forDate(LocalDate.ofEpochDay(-1)))
    }

    @Test
    fun factsAreDistinctAndNonBlank() {
        assertEquals(MoonFacts.facts.size, MoonFacts.facts.toSet().size)
        assertTrue(MoonFacts.facts.all { it.isNotBlank() })
    }
}
