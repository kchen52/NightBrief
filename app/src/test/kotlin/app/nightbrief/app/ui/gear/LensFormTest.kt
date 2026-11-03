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
}
