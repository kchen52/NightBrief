package app.nightbrief.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.score.WearGlance
import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(dump, dump.contains(NightBriefTile.GO_SPARKLE))
    }

    @Test
    fun maybeNightDoesNotTwinkle() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 55)
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("Maybe"))
        assertFalse(dump, dump.contains(NightBriefTile.GO_SPARKLE))
    }

    @Test
    fun noGoNightDoesNotTwinkle() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Long Point", score = 15)
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("No-go"))
        assertFalse(dump, dump.contains(NightBriefTile.GO_SPARKLE))
    }

    @Test
    fun aSavedForecastIsOnTheTile() {
        val glance = WearGlance.from(onboardingComplete = true, siteName = "Home", score = 70, savedText = "Saved Thu 18:40")
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("Saved Thu 18:40"))
        assertTrue(dump, dump.contains("Go"))
    }

    @Test
    fun tileCarriesTheFlavourLine() {
        val glance = WearGlance.from(
            onboardingComplete = true,
            siteName = "Home",
            score = 12,
            flavorText = "Overcast. Even the owls are staying in.",
        )
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains("Overcast. Even the owls are staying in."))
    }

    @Test
    fun aPayloadWithoutTheFlavourKeyStillDecodes() {
        val map = DataMap()
        map.putBoolean(WearGlance.KEY_READY, true)
        map.putString(WearGlance.KEY_SITE, "Home")
        map.putString(WearGlance.KEY_SCORE, "90")
        map.putString(WearGlance.KEY_VERDICT, "Go")
        val glance = WearData.glance(map)
        assertNull(glance.flavorText)
        assertEqualsLines(listOf("Home", "90", "Go"), glance.lines())

        map.putString(WearGlance.KEY_FLAVOR, "The Moon is hogging the stage tonight.")
        val withFlavor = WearData.glance(map)
        assertEqualsLines(
            listOf("Home", "90", "Go", "The Moon is hogging the stage tonight."),
            withFlavor.lines(),
        )
    }

    private fun assertEqualsLines(expected: List<String>, actual: List<String>) {
        assertTrue("expected $expected but was $actual", expected == actual)
    }

    @Test
    fun setupTileDoesNotInventAScore() {
        val glance = WearGlance.from(onboardingComplete = false, siteName = "Home", score = 92)
        val dump = NightBriefTile.build(context, glance).toString()
        assertTrue(dump, dump.contains(WearGlance.SETUP))
        assertFalse(dump, dump.contains("92"))
    }
}
