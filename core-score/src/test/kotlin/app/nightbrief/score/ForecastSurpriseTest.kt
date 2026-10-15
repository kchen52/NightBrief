package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastSurpriseTest {
    @Test
    fun expectedStarsRoundOnePlusScoreOver25() {
        assertEquals(1, ForecastSurprise.expectedStars(0))
        assertEquals(1, ForecastSurprise.expectedStars(12))
        assertEquals(2, ForecastSurprise.expectedStars(13))
        assertEquals(2, ForecastSurprise.expectedStars(37))
        assertEquals(3, ForecastSurprise.expectedStars(38))
        assertEquals(3, ForecastSurprise.expectedStars(50))
        assertEquals(3, ForecastSurprise.expectedStars(58))
        assertEquals(3, ForecastSurprise.expectedStars(62))
        assertEquals(4, ForecastSurprise.expectedStars(63))
        assertEquals(4, ForecastSurprise.expectedStars(87))
        assertEquals(5, ForecastSurprise.expectedStars(88))
        assertEquals(5, ForecastSurprise.expectedStars(100))
    }

    @Test
    fun beatWhenTheRatingIsAtLeastOneStarAboveTheScore() {
        assertTrue(ForecastSurprise.beat(4, 58))
        assertFalse(ForecastSurprise.beat(3, 58))
        assertTrue(ForecastSurprise.beat(2, 0))
        assertFalse(ForecastSurprise.beat(1, 0))
        assertFalse("a perfect score already expects 5 stars", ForecastSurprise.beat(5, 100))
        assertTrue(ForecastSurprise.beat(5, 87))
        assertFalse(ForecastSurprise.beat(4, 87))
        assertFalse(ForecastSurprise.beat(5, null))
    }

    @Test
    fun surpriseLineNamesTheSiteRatingAndScore() {
        val home = SessionEntry("home", "2026-01-02", score = 58, rating = 4)
        assertEquals(
            "The sky surprised you at Home: 4★ on a 58.",
            ForecastSurprise.line("Home", home),
        )
        assertNull(ForecastSurprise.line("Home", home.copy(rating = 3)))
        assertNull(ForecastSurprise.line("Home", home.copy(score = null)))
    }
}
