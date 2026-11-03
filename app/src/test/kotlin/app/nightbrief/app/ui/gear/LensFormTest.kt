package app.nightbrief.app.ui.gear

import app.nightbrief.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensFormTest {
    @Test
    fun aPrimeWithTwoAperturesIsRejected() {
        assertEquals(
            R.string.gear_error_prime_aperture,
            lensError("QA Prime", "24", "24", "2.8", "4"),
        )
    }

    @Test
    fun aPrimeWithMatchingAperturesIsAccepted() {
        assertNull(lensError("QA Prime", "24", "24", "2.8", "2.8"))
    }

    @Test
    fun aZoomMayBeSlowerAtTheLongEnd() {
        assertNull(lensError("Zoom", "16", "35", "2.8", "4"))
    }

    @Test
    fun aPrimeCanOmitTheLongFocalLength() {
        assertNull(lensError("QA Prime", "50", "", "1.8", "1.8"))
        assertEquals("50", effectiveMaxFocal("50", ""))
        assertEquals("35", effectiveMaxFocal("24", "35"))
    }

    @Test
    fun aBlankMaxFocalStillRejectsABadMinimum() {
        assertEquals(R.string.gear_error_min_focal, lensError("QA Prime", "", "", "1.8", "1.8"))
    }
}
