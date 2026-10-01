package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearGlanceTest {
    @Test
    fun setupUntilOnboardingHasANamedSite() {
        val incomplete = WearGlance.from(onboardingComplete = false, siteName = "Home", score = 90)
        assertFalse(incomplete.ready)
        assertEquals(listOf(WearGlance.SETUP), incomplete.lines())

        val unnamed = WearGlance.from(onboardingComplete = true, siteName = "  ", score = 90)
        assertFalse(unnamed.ready)
        assertEquals(WearGlance.SETUP, unnamed.verdictText)
    }

    @Test
    fun missingScoreKeepsTheSiteAndDoesNotInventAVerdict() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = null)
        assertTrue(glance.ready)
        assertEquals("Home", glance.siteName)
        assertEquals("—", glance.scoreText)
        assertEquals(WearGlance.NO_SCORE, glance.verdictText)
        assertEquals(listOf("Home", "—", WearGlance.NO_SCORE), glance.lines())
    }

    @Test
    fun verdictFollowsTheNightScoreBands() {
        assertEquals("Go", WearGlance.from(true, "Home", 70).verdictText)
        assertEquals("Go", WearGlance.from(true, "Home", 85).verdictText)
        assertEquals("92", WearGlance.from(true, "Home", 92).scoreText)
        assertEquals("Maybe", WearGlance.from(true, "Home", 69).verdictText)
        assertEquals("Maybe", WearGlance.from(true, "Home", 50).verdictText)
        assertEquals("No-go", WearGlance.from(true, "Home", 49).verdictText)
        assertEquals("No-go", WearGlance.from(true, "Home", 0).verdictText)
    }

    @Test
    fun linesAreSiteThenScoreThenVerdict() {
        val glance = WearGlance.from(true, "Long Point", 91)
        assertEquals(listOf("Long Point", "91", "Go"), glance.lines())
    }
}
