package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.events.EventsScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEventKind
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** The year-ahead scan is asynchronous: a loading state, then a failure retry, and no late publish after exit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EventsLoadingTest {
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
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                )
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun theScanDoesNotBlockTheFirstFrameAndLeavingCancelsIt() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        val gate = CompletableDeferred<List<SkyEvent>>()
        var cancelled = false
        var shown by mutableStateOf(true)
        compose.setContent {
            NightBriefTheme {
                if (shown) {
                    EventsScreen(
                        vm,
                        onBack = {},
                        onOpenNight = { _, _ -> },
                        loadEvents = {
                            try {
                                gate.await()
                            } catch (e: CancellationException) {
                                cancelled = true
                                throw e
                            }
                        },
                    )
                }
            }
        }

        // The frame rendered without the year-ahead scan, so the loading state is what the user sees.
        compose.onNodeWithTag("events-loading").assertExists()
        val rows = compose.onAllNodesWithTag("event-row").fetchSemanticsNodes()
        assertEquals("event rows before the scan returned: $rows", 0, rows.size)

        compose.runOnIdle { shown = false }
        compose.waitForIdle()

        assertTrue("the year-ahead load kept running after the screen left", cancelled)
    }

    @Test
    fun aFailedScanOffersRetryAndTheRetryReplacesTheError() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        var attempts = 0
        compose.setContent {
            NightBriefTheme {
                EventsScreen(
                    vm,
                    onBack = {},
                    onOpenNight = { _, _ -> },
                    loadEvents = { zone ->
                        attempts++
                        if (attempts == 1) throw IllegalStateException("scan failed")
                        listOf(
                            SkyEvent(
                                instant = Instant.parse("2026-03-20T12:00:00Z"),
                                kind = SkyEventKind.SEASON,
                                title = "Equinox",
                                detail = "The Sun crosses the celestial equator.",
                            ),
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag("events-error").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("event-row").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, attempts)
        compose.onNodeWithText("Equinox").assertExists()
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
            kit: app.nightbrief.gear.GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(Instant.EPOCH, emptyList(), emptyList())

        override suspend fun plan(site: Site, date: LocalDate, kit: app.nightbrief.gear.GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
