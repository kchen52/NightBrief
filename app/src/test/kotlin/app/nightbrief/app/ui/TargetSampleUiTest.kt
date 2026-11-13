package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.tonight.TargetSamples
import app.nightbrief.astro.TargetCatalog
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TargetSampleUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val kit = GearCatalog.exampleKit

    @Test
    fun everyCatalogTargetHasADecodableSamplePhoto() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TargetCatalog.all.forEach { target ->
            val sample = TargetSamples.of(target.id)
            assertNotNull("missing sample for ${target.id}", sample)
            assertTrue("blank credit for ${target.id}", sample!!.credit.isNotBlank())
            assertTrue(
                "source for ${target.id} was ${sample.sourceUrl}",
                sample.sourceUrl.startsWith("https://commons.wikimedia.org/wiki/File:"),
            )
            assertNotNull("drawable failed for ${target.id}", context.getDrawable(sample.drawable))
        }
    }

    @Test
    fun aCustomTargetHasNoSamplePhoto() {
        assertNull(TargetSamples.of("custom-veil"))
    }

    @Test
    fun suggestedTargetShowsASamplePhotoAndCreditsItWhenOpened() {
        val report = planned()
        val suggestion = report.suggestions.first()
        val sample = TargetSamples.of(suggestion.target.id)
        assertNotNull("catalog suggestion ${suggestion.target.id} should have a sample", sample)
        show(report)
        val photo = "Sample photo of ${suggestion.target.name}"
        compose.onNodeWithContentDescription(photo).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(sample!!.credit, substring = true).assertDoesNotExist()
        compose.onNodeWithText(suggestion.target.name, substring = true).performScrollTo().performClick()
        compose.onNodeWithText(sample.credit, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(suggestion.target.tip).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun samplePhotoStillShowsInNightVision() {
        val report = planned()
        val suggestion = report.suggestions.first()
        compose.setContent {
            NightBriefTheme(nightVision = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Sample photo of ${suggestion.target.name}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aCustomTargetKeepsTheKindIconAndHasNoSamplePhoto() {
        val report = planned()
        val custom = report.suggestions.first().let { suggestion ->
            suggestion.copy(target = suggestion.target.copy(id = "custom-veil", name = "My Veil"))
        }
        show(report.copy(suggestions = listOf(custom)))
        compose.onNodeWithText("My Veil").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Sample photo of My Veil").assertDoesNotExist()
        compose.onNodeWithText("My Veil").performClick()
        compose.onNodeWithText("Sample photo", substring = true).assertDoesNotExist()
        compose.onNodeWithText(custom.target.tip).performScrollTo().assertIsDisplayed()
    }

    private fun show(report: NightReport) {
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
    }

    private fun planned(): NightReport =
        NightPlanner.plan(home, date, forecast(), kit)

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600L,
                    cloudCover = 10,
                    humidity = 40,
                    windKmh = 6.0,
                    temperatureC = 10.0,
                    dewPointC = 0.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
