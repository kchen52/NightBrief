package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreBiasTest {

    private fun entry(score: Int?, rating: Int, night: Int) =
        SessionEntry(siteId = "home", nightDate = "2026-08-%02d".format(night), score = score, rating = rating)

    @Test
    fun meanBiasNeedsFiveScoredNights() {
        assertNull("four scored nights are too few", ScoreBias.meanBias((1..4).map { entry(50, 3, it) }))
        assertEquals("five scored nights are enough", 0.0, ScoreBias.meanBias((1..5).map { entry(50, 3, it) })!!, 0.0)
    }

    @Test
    fun meanBiasAveragesRatingMinusExpectedStars() {
        // A 50 expects 3 stars; four ratings of 4 and one of 2 average +0.6.
        val sessions = listOf(4, 4, 4, 4, 2).mapIndexed { i, rating -> entry(50, rating, i + 1) }
        assertEquals(0.6, ScoreBias.meanBias(sessions)!!, 0.0)
    }

    @Test
    fun meanBiasIgnoresSessionsWithoutAScore() {
        // Seven nights but only four carry a score: too few to say anything.
        val four = (1..4).map { entry(50, 4, it) } + (5..7).map { entry(null, 4, it) }
        assertNull("unscored nights should not count toward the minimum", ScoreBias.meanBias(four))
        // Eight nights, five scored: the mean covers the scored five alone.
        val five = (1..5).map { entry(50, 4, it) } + (6..8).map { entry(null, 4, it) }
        assertEquals(1.0, ScoreBias.meanBias(five)!!, 0.0)
    }

    @Test
    fun lineSaysOptimisticWhenRatingsTrailTheScore() {
        // An 88 expects 5 stars; rating 4 every night means the score reads a star high.
        val sessions = (1..5).map { entry(88, 4, it) }
        assertEquals("Home scores about a star optimistic over your last 5 scored nights.", ScoreBias.line("Home", sessions))
    }

    @Test
    fun lineSaysPessimisticWhenRatingsLeadTheScore() {
        // A 12 expects 1 star; rating 3 every night means the score reads two stars low.
        val sessions = (1..5).map { entry(12, 3, it) }
        assertEquals("Home scores about two stars pessimistic over your last 5 scored nights.", ScoreBias.line("Home", sessions))
    }

    @Test
    fun lineSaysHalfAStarForAMeanUnderThreeQuarters() {
        // Deltas +1, 0, +1, 0, +1 average +0.6: about half a star.
        val sessions = listOf(4, 3, 4, 3, 4).mapIndexed { i, rating -> entry(50, rating, i + 1) }
        assertEquals("Home scores about half a star pessimistic over your last 5 scored nights.", ScoreBias.line("Home", sessions))
    }

    @Test
    fun lineStaysQuietWhenTheMeanIsUnderAQuarterStar() {
        val sessions = listOf(4, 3, 3, 3, 3).mapIndexed { i, rating -> entry(50, rating, i + 1) }
        assertNull("a 0.2-star mean says nothing", ScoreBias.line("Home", sessions))
    }

    @Test
    fun lineStaysQuietWithNoScoredSessions() {
        val sessions = (1..6).map { entry(null, 4, it) }
        assertNull(ScoreBias.meanBias(sessions))
        assertNull(ScoreBias.line("Home", sessions))
    }
}
