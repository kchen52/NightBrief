package app.nightbrief.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun stripsLeadingV() {
        assertEquals("1.2.3", SemVer.normalise("v1.2.3"))
        assertEquals("1.2.3", SemVer.normalise("V1.2.3"))
    }

    @Test
    fun numericSegmentsBeatStringOrder() {
        assertTrue(SemVer.isNewer("1.0.10", "1.0.9"))
        assertFalse(SemVer.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun releaseBeatsItsOwnPreRelease() {
        assertTrue(SemVer.isNewer("0.2.0", "0.2.0-beta.1"))
        assertFalse(SemVer.isNewer("0.2.0-beta.1", "0.2.0"))
    }

    @Test
    fun preReleasesSortNumerically() {
        assertTrue(SemVer.isNewer("0.2.0-beta.10", "0.2.0-beta.9"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(SemVer.isNewer("0.1.1", "0.1.1"))
        assertFalse(SemVer.isNewer("v0.1.1", "0.1.1"))
    }
}
