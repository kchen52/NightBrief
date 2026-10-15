package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.MascotMood
import app.nightbrief.score.MoonFacts
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
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
class AliveHeroTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Test
    fun mascotDescriptionMatchesTheVerdict() {
        val base = clearNight()
        val cases = listOf(
            90 to "Owl awake with a telescope: a go night",
            55 to "Owl squinting at a cloud: a maybe night",
            20 to "Owl asleep under a blanket: a no-go night",
        )
        val step = mutableIntStateOf(0)
        compose.setContent {
            val (value, _) = cases[step.intValue]
            val report = base.copy(
                score = base.score!!.copy(score = value),
                dew = null,
                aurora = null,
            )
            NightBriefTheme {
                NightDetail(report, alternative = null, onOpenAlternative = {})
            }
        }
        cases.forEachIndexed { index, (value, description) ->
            step.intValue = index
            val report = base.copy(
                score = base.score!!.copy(score = value),
                dew = null,
                aurora = null,
            )
            assertTrue(MascotMood.of(report).contentDescription(report) == description)
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText(value.toString()).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun longPressOnTheMoonShowsAFact() {
        val report = clearNight()
        val fact = MoonFacts.forDate(report.date)
        compose.setContent {
            NightBriefTheme {
                NightDetail(report, alternative = null, onOpenAlternative = {})
            }
        }
        compose.onNodeWithContentDescription("Moon", substring = true)
            .assertIsDisplayed()
            .performTouchInput { longClick() }
        compose.onNodeWithText(fact).assertIsDisplayed()
    }

    private fun clearNight(): NightReport {
        val date = LocalDate.of(2024, 8, 12)
        return NightPlanner.plan(home, date, forecast(), GearCatalog.exampleKit)
    }

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude,
            home.longitude,
            "best_match",
            start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
