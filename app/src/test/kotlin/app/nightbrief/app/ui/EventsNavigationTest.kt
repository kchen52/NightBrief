package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.events.EventsScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class EventsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val other = Site("other", "Other", 44.0, -79.5, "America/Toronto", bortle = 5)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        initWorkManager(app)
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true).add(other),
                )
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun eventOpensThePrimarySiteThatProducedTheList() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        // Week has a different site selected. Events always lists the primary site's events, so the
        // tap must open the primary site's planner rather than the global selection.
        vm.selectSite(other.id)
        var openedSite: String? = null
        var openedDate: LocalDate? = null
        compose.setContent {
            NightBriefTheme {
                EventsScreen(
                    vm,
                    onBack = {},
                    onOpenNight = { siteId, date ->
                        openedSite = siteId
                        openedDate = date
                    },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("event-row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("event-row")[0].performClick()

        assertEquals(home.id, openedSite)
        assertNotNull(openedDate)
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
