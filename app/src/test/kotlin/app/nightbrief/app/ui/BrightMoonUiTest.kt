package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.WidgetCopy
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrightMoonUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val kit = GearCatalog.exampleKit

    @Test
    fun aFullMoonNightShowsTheMoonAndAMoonlitLandscape() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 6, 21), forecast = null, kit)
        show(report)
        compose.onNodeWithText("Moon close-up").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Moonlit landscape").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tonightSaysWhenTheCoreClearsTheTreeline() {
        val date = LocalDate.of(2024, 5, 15)
        val flat = NightEphemeris.compute(date, home.zone, home.latitude, home.longitude)
        val peak = flat.milkyWay!!.peakAltitudeDeg
        val mid = (NightEphemeris.DEFAULT_MILKY_WAY_MIN_ALTITUDE + peak) / 2.0
        val site = home.copy(horizon = LocalHorizon(mid, mid, mid, mid, mid, mid, mid, mid))
        val report = NightPlanner.plan(site, date, forecast = null, kit)
        val clears = report.ephemeris.milkyWay?.clearsHorizonAt
        assertNotNull(clears)
        show(report)
        compose.onNodeWithText(WidgetCopy.clearsTreeline(clears!!, home.zone)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tonightSaysWhenTheTreelineHidesTheCore() {
        val site = home.copy(horizon = LocalHorizon(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0))
        val report = NightPlanner.plan(site, LocalDate.of(2024, 8, 10), forecast = null, kit)
        show(report)
        compose.onNodeWithText(WidgetCopy.BEHIND_TREELINE).performScrollTo().assertIsDisplayed()
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
}
