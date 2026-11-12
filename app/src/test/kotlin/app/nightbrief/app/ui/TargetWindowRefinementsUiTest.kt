package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.TargetInterest
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TargetWindowRefinementsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expandedTargetRowShowsClearsTreelineAndMoonFreeSubWindows() {
        val site = Site(
            id = "home",
            name = "Home",
            latitude = 43.65,
            longitude = -79.38,
            zoneId = "America/Toronto",
            bortle = 4,
            horizon = LocalHorizon(northEast = 42.0, east = 42.0),
        )
        val delayedM31 = NightPlanner.plan(
            site = site,
            date = LocalDate.of(2024, 8, 5),
            forecast = null,
            kit = GearCatalog.exampleKit.withInterests(setOf(TargetInterest.GALAXIES)),
        ).suggestions.first { it.target.id == "m31" }
        assertNotNull(delayedM31.clearsHorizonAt)

        val moonSplit = NightPlanner.plan(
            site = site.copy(horizon = LocalHorizon()),
            date = LocalDate.of(2024, 8, 12),
            forecast = null,
            kit = GearCatalog.exampleKit,
        )
        val withMoon = moonSplit.suggestions.first { it.hasDistinctMoonFreeWindows }
        val report = moonSplit.copy(suggestions = listOf(delayedM31, withMoon))

        compose.setContent {
            NightBriefTheme {
                NightDetail(
                    report = report,
                    alternative = null,
                    onOpenAlternative = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        compose.onNodeWithText(delayedM31.target.name).performScrollTo().performClick()
        compose.onNodeWithText("Clears your treeline at", substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(withMoon.target.name).performScrollTo().performClick()
        compose.onNodeWithText("Moon-free:", substring = true).performScrollTo().assertIsDisplayed()
    }
}
