package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.week.WeekScreen
import app.nightbrief.astro.Darkness
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeekRetryTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val kit = GearCatalog.exampleKit

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        initWorkManager(app)
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun retryAfterAFailedFirstLoadShowsTheOutlook() {
        val briefings = ScriptedBriefings(
            listOf({ throw IllegalStateException("offline") }, { success(home, kit) }),
        )
        val vm = start(briefings)

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Retry")[0].performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, briefings.calls)
    }

    @Test
    fun missingOutlookOffersARetryThatLoadsTheWeek() {
        val briefings = ScriptedBriefings(
            listOf({ Briefing(Instant.EPOCH, emptyList(), emptyList()) }, { success(home, kit) }),
        )
        start(briefings)

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("No week outlook", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Retry")[0].performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, briefings.calls)
    }

    @Test
    fun failedRefreshKeepsTheCachedOutlookVisible() {
        val briefings = ScriptedBriefings(
            listOf({ success(home, kit) }, { throw IllegalStateException("offline") }),
        )
        val vm = start(briefings)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty()
        }

        compose.runOnIdle { vm.refresh(force = true) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("offline").fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(
            "cached week rows should survive a failed refresh",
            compose.onAllNodesWithTag("week-night").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun start(briefings: BriefingSource): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, briefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme {
                WeekScreen(
                    vm,
                    onOpenNight = { _, _ -> },
                    onOpenEvents = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        return vm
    }

    private fun success(site: Site, kit: GearKit): Briefing {
        val date = LocalDate.of(2024, 8, 12)
        val report = NightPlanner.plan(site, date, forecast(site), kit)
        val night = OutlookNight(
            date = date,
            score = 72,
            moonIllumination = 0.2,
            darkness = Darkness.ASTRONOMICAL,
            coverage = ForecastCoverage.FULL,
        )
        return Briefing(Instant.EPOCH, listOf(report), listOf(WeeklyOutlook(site, listOf(night))))
    }

    private fun forecast(site: Site): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            site.latitude,
            site.longitude,
            "gem_seamless",
            start,
            (0 until 72).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600L,
                    cloudCover = 10,
                    humidity = 45,
                    windKmh = 6.0,
                    seeing = 3,
                    transparency = 3,
                )
            },
        )
    }

    private class ScriptedBriefings(private val outcomes: List<() -> Briefing>) : BriefingSource {
        var calls = 0
            private set

        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing {
            val outcome = outcomes[minOf(calls, outcomes.lastIndex)]
            calls++
            return outcome()
        }

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
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
}
