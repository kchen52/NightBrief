package app.nightbrief.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.score.WearGlance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class NightBriefTileTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun tileLayoutCarriesTheScoreAndVerdict() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 92)
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("Home"))
        assertTrue(dump, dump.contains("92"))
        assertTrue(dump, dump.contains("Go"))
    }

    @Test
    fun aSavedForecastIsOnTheTile() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 70, savedText = "Saved Thu 18:40")
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("Saved Thu 18:40"))
        assertTrue(dump, dump.contains("Go"))
    }

    @Test
    fun setupTileDoesNotInventAScore() {
        val glance = WearGlance.from(onboardingComplete = false, siteName = "Home", score = 92)
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains(WearGlance.SETUP))
        assertFalse(dump, dump.contains("92"))
    }
}
