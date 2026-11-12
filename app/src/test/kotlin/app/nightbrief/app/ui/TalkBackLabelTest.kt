package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.gear.GearScreen
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.sites.SiteEditorScreen
import app.nightbrief.app.ui.sites.SitesScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.PlannedNightScreen
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TalkBackLabelTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        initWorkManager(app)
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun settingsSwitchesAreLabeledForTalkBack() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SettingsScreen(vm, onBack = {}) }
        }
        compose.onNodeWithContentDescription("Daily digest").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Daily digest") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Big Night alerts").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Big Night alerts") and isOff()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Aurora alerts").performScrollTo().assertIsOff().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Aurora alerts") and isOn()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun plannerArrowsAreLabeledForTalkBack() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        val tonight = NightPlanner.tonight(Instant.now(), home.zone)
        compose.setContent {
            NightBriefTheme {
                PlannedNightScreen(vm, home.id, tonight, onBack = {}, onSelectDate = {})
            }
        }
        compose.onNodeWithContentDescription("Previous night").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next night").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Share night plan")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Share night plan").assertIsDisplayed()
    }

    @Test
    fun sitesControlsIncludeTheSiteName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SitesScreen(vm, onEdit = {}, contentPadding = PaddingValues()) }
        }
        compose.onNodeWithContentDescription("Add site").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move Home up").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move Home down").assertIsDisplayed()
    }

    @Test
    fun gearBodyRadioUsesTheBodyName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { GearScreen(vm, PaddingValues()) }
        }
        compose.onNodeWithContentDescription("Canon EOS R7").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Canon EOS R7").assertIsDisplayed()
    }

    @Test
    fun siteFormTogglesAreNamed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SiteEditorScreen(vm, siteId = null, onDone = {}) }
        }
        compose.onNodeWithContentDescription("Primary site (leads the morning digest)")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Own digest time").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsSlidersExposeTheirValue() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SettingsScreen(vm, onBack = {}) }
        }
        compose.onNode(
            hasContentDescription("Mention another site when it's at least 15 points better") and
                hasStateDescription("15"),
        ).performScrollTo().assertIsDisplayed()
        compose.onNode(
            hasContentDescription("Big Night at 85 or above") and hasStateDescription("85"),
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Aurora alerts").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Alert at Kp 5 or above")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(
            hasContentDescription("Alert at Kp 5 or above") and hasStateDescription("5"),
        ).performScrollTo().assertIsDisplayed()
    }

    private fun initWorkManager(app: Application) {
        try {
            WorkManager.getInstance(app)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app,
                Configuration.Builder()
                    .setExecutor(SynchronousExecutor())
                    .setTaskExecutor(SynchronousExecutor())
                    .build(),
            )
        }
    }

    private object QuietForecasts : ForecastSource {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            forceRefresh: Boolean,
        ): ForecastResult = ForecastResult(
            Forecast(latitude, longitude, "test", 0L, emptyList()),
            ForecastStatus.CACHED,
        )
    }

    private object QuietBriefings : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(Instant.EPOCH, emptyList(), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
