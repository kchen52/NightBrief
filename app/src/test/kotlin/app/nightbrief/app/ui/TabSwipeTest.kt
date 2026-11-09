package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
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
class TabSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
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
    fun longSidewaysDragStepsTowardTheSwipe() {
        assertEquals(1, swipeStep(dx = -100f, dy = 10f, velocityX = 0f, distance = 72f, fling = 500f))
        assertEquals(-1, swipeStep(dx = 100f, dy = -10f, velocityX = 0f, distance = 72f, fling = 500f))
    }

    @Test
    fun shortFlickCountsOnlyWhileMovingTheSameWay() {
        assertEquals(1, swipeStep(dx = -30f, dy = 0f, velocityX = -900f, distance = 72f, fling = 500f))
        assertEquals(0, swipeStep(dx = -30f, dy = 0f, velocityX = 900f, distance = 72f, fling = 500f))
        assertEquals(0, swipeStep(dx = -10f, dy = 0f, velocityX = -900f, distance = 72f, fling = 500f))
    }

    @Test
    fun mostlyVerticalDragIsNotATabSwipe() {
        assertEquals(0, swipeStep(dx = -100f, dy = 140f, velocityX = -900f, distance = 72f, fling = 500f))
        assertEquals(0, swipeStep(dx = -40f, dy = 0f, velocityX = 0f, distance = 72f, fling = 500f))
    }

    @Test
    fun swipeOverPlainContentSendsAStep() {
        val steps = mutableListOf<Int>()
        compose.setContent {
            Box(Modifier.fillMaxSize().tabSwipe(enabled = true) { steps += it })
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun horizontallyScrollingChildKeepsTheDrag() {
        val steps = mutableListOf<Int>()
        compose.setContent {
            Box(Modifier.fillMaxSize().tabSwipe(enabled = true) { steps += it }) {
                Row(Modifier.fillMaxSize().testTag("strip").horizontalScroll(rememberScrollState())) {
                    Spacer(Modifier.width(4000.dp))
                }
            }
        }
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("steps from a drag the strip scrolled: $steps", emptyList<Int>(), steps)
    }

    @Test
    fun swipingTheTabsMovesOneTabAndStopsAtTheEnds() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            NightBriefTheme { NightBriefNavHost(vm) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("nav-bar").fetchSemanticsNodes().isNotEmpty()
        }
        tab("Tonight").assertIsSelected()

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        tab("Tonight").assertIsSelected()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        tab("Week").assertIsSelected()
        tab("Tonight").assertIsNotSelected()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        tab("Sites").assertIsSelected()

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        tab("Week").assertIsSelected()
    }

    private fun tab(label: String) =
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("nav-bar")), useUnmergedTree = false)

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
