package app.nightbrief.app

import app.nightbrief.app.R
import app.nightbrief.sites.Site
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SiteDraftTest {
    @Test
    fun untouchedHomeTakesThePlaceNameAndATypedNameStays() {
        val home = SiteDraft(name = SiteDraft.UNTOUCHED_NAME)
        assertEquals("Squamish", home.nameAfterPlacePick(" Squamish "))
        assertEquals("Cabin", home.copy(name = "Cabin", nameEdited = true).nameAfterPlacePick("Squamish"))
        val saved = SiteDraft.from(
            Site("id", "Home", 49.7, -123.2, "America/Vancouver"),
            isPrimary = true,
        )
        assertEquals("Home", saved.nameAfterPlacePick("Squamish"))
    }

    @Test
    fun blankCoordinatesAskToSetALocationInsteadOfARange() {
        val errors = SiteDraft(name = "Home").errors
        assertEquals(R.string.site_error_location, errors.first())
        assertFalse(errors.contains(R.string.site_error_latitude))
        assertFalse(errors.contains(R.string.site_error_longitude))
        val outOfRange = SiteDraft(name = "Home", latitude = "99", longitude = "0")
        assertTrue(outOfRange.errors.contains(R.string.site_error_latitude))
        assertFalse(outOfRange.errors.contains(R.string.site_error_location))
    }
}
