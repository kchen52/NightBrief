package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationStreaksTest {
    @Test
    fun figuresFollowOrionThenTheRest() {
        assertEquals(
            listOf("Orion", "Cassiopeia", "Ursa Major", "Cygnus", "Scorpius"),
            ConstellationStreaks.catalog.map { it.name },
        )
        assertEquals(listOf(7, 5, 7, 5, 7), ConstellationStreaks.catalog.map { it.stars.size })
    }

    @Test
    fun starLayoutsStayInsideTheUnitSquare() {
        for (layout in ConstellationStreaks.catalog) {
            for (star in layout.stars) {
                assertTrue("${layout.name} star x ${star.x}", star.x in 0f..1f)
                assertTrue("${layout.name} star y ${star.y}", star.y in 0f..1f)
            }
            for ((a, b) in layout.lines) {
                assertTrue("${layout.name} line $a", a in layout.stars.indices)
                assertTrue("${layout.name} line $b", b in layout.stars.indices)
                assertTrue("${layout.name} line joins two stars", a != b)
            }
        }
    }

    @Test
    fun completingAFigureUnlocksTheNext() {
        val six = ConstellationStreaks.progress(6)
        assertEquals(6, six.figure("Orion").lit)
        assertFalse("Cassiopeia stays locked until Orion is full", six.figure("Cassiopeia").unlocked)
        assertEquals("Finish Orion to light Cassiopeia", six.figure("Cassiopeia").unlockHint)
        assertEquals("Orion", six.current.name)
        assertEquals("Orion · 6 of 7", six.summary)

        val seven = ConstellationStreaks.progress(7)
        assertEquals(7, seven.figure("Orion").lit)
        assertTrue(seven.figure("Cassiopeia").unlocked)
        assertEquals(0, seven.figure("Cassiopeia").lit)
        assertEquals("Cassiopeia · 0 of 5", seven.summary)

        val eight = ConstellationStreaks.progress(8)
        assertEquals(1, eight.figure("Cassiopeia").lit)
        assertEquals("Cassiopeia", eight.current.name)
        assertFalse("Ursa Major waits for Cassiopeia", eight.figure("Ursa Major").unlocked)

        assertTrue(ConstellationStreaks.progress(12).figure("Ursa Major").unlocked)
        assertFalse(ConstellationStreaks.progress(11).figure("Ursa Major").unlocked)
        assertFalse(ConstellationStreaks.progress(23).figure("Scorpius").unlocked)
        val scorpius = ConstellationStreaks.progress(24)
        assertTrue(scorpius.figure("Scorpius").unlocked)
        assertEquals(0, scorpius.figure("Scorpius").lit)
        assertEquals("Scorpius · 0 of 7", scorpius.summary)
    }

    @Test
    fun missedNightsDoNotResetTheFigure() {
        val progress = ConstellationStreaks.progress(2)
        assertEquals("Orion", progress.current.name)
        assertEquals(2, progress.current.lit)
        assertEquals("Orion · 2 of 7", progress.summary)
        assertFalse(progress.figure("Cassiopeia").unlocked)
    }

    @Test
    fun sessionsPastTheLastStarLeaveEveryFigureFull() {
        val done = ConstellationStreaks.progress(31)
        assertTrue(done.figures.all { it.unlocked && it.lit == it.total })
        assertEquals("Scorpius · 7 of 7", done.summary)
        val extra = ConstellationStreaks.progress(40)
        assertEquals(done.figures.map { it.lit }, extra.figures.map { it.lit })
        assertEquals("Scorpius · 7 of 7", extra.summary)
    }

    @Test
    fun anEmptyLogStartsOrionWithNoStarsLit() {
        val progress = ConstellationStreaks.progress(0)
        assertEquals("Orion · 0 of 7", progress.summary)
        assertTrue(progress.figure("Orion").unlocked)
        assertTrue("only Orion is unlocked", progress.figures.drop(1).none { it.unlocked })
    }

    private fun ConstellationProgress.figure(name: String) = figures.first { it.name == name }
}
