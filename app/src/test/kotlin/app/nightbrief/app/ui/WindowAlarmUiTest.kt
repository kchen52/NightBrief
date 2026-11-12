package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.TonightScreen
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
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class WindowAlarmUiTest {
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
    }

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun tonightChipArmsAndCancelsTheWindowStartReminder() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, ClearBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Remind me at the window").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Remind me at the window").assertIsDisplayed()
        compose.onNodeWithTag("window-alarm").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Reminder set for", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(home.id, runBlocking { graph.settings.current().windowAlarm?.siteId })

        compose.onNodeWithTag("window-alarm").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Remind me at the window").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(runBlocking { graph.settings.current().windowAlarm })
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

    private object ClearBriefings : BriefingSource {
        private val date = LocalDate.of(2024, 8, 10)

        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing {
            val site = sites.first()
            val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
            val forecast = Forecast(
                site.latitude,
                site.longitude,
                "gem_seamless",
                start,
                (0 until 72).map {
                    HourlyWeather(
                        epochSecond = start + it * 3600L,
                        cloudCover = 0,
                        humidity = 45,
                        windKmh = 6.0,
                        gustKmh = 10.0,
                        jetStreamKmh = 40.0,
                        seeing = 4,
                        transparency = 4,
                    )
                },
            )
            val report = NightPlanner.plan(site, date, forecast, kit)
            return Briefing(Instant.EPOCH, listOf(report), emptyList())
        }

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }
}
