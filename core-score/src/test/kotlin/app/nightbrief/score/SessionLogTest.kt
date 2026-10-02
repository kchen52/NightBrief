package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SessionLogTest {
    @Test
    fun morningWindowIsSixToNoon() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(LocalDate.of(2026, 10, 2), SessionPrompt.morningPromptDate(LocalTime.of(8, 0), today))
        assertEquals(null, SessionPrompt.morningPromptDate(LocalTime.of(5, 59), today))
        assertEquals(null, SessionPrompt.morningPromptDate(LocalTime.of(12, 0), today))
        assertEquals(null, SessionPrompt.morningPromptDate(LocalTime.of(20, 0), today))
    }

    @Test
    fun askOncePerSiteNight() {
        val date = LocalDate.of(2026, 10, 2)
        assertTrue(SessionPrompt.shouldAsk("s1", date, emptyList(), emptySet()))
        val logged = listOf(SessionEntry("s1", "2026-10-02", score = 82, rating = 5))
        assertFalse(SessionPrompt.shouldAsk("s1", date, logged, emptySet()))
        assertFalse(SessionPrompt.shouldAsk("s1", date, emptyList(), setOf("s1/2026-10-02")))
        // Another site is still asked.
        assertTrue(SessionPrompt.shouldAsk("s2", date, logged, emptySet()))
    }

    @Test
    fun ratingBounds() {
        runCatching { SessionEntry("s1", "2026-10-02", rating = 0) }
            .onSuccess { throw AssertionError("rating 0 should fail") }
        runCatching { SessionEntry("s1", "2026-10-02", rating = 6) }
            .onSuccess { throw AssertionError("rating 6 should fail") }
        assertEquals(5, SessionEntry("s1", "2026-10-02", rating = 5).rating)
    }
}
