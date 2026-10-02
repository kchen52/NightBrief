package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.UnitSystem
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
class DisplayUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

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
    fun nightVisionIsRedOnBlackAndTheDefaultThemeStaysBlue() {
        var background = Color.Unspecified
        var onBackground = Color.Unspecified
        var excellent = Color.Unspecified
        var standardPrimary = Color.Unspecified
        compose.setContent {
            Column {
                NightBriefTheme(nightVision = true) {
                    background = MaterialTheme.colorScheme.background
                    onBackground = MaterialTheme.colorScheme.onBackground
                    excellent = NightColors.forScore(90)
                }
                NightBriefTheme {
                    standardPrimary = NightColors.Primary
                }
            }
        }
        assertEquals(Color.Black, background)
        assertTrue(onBackground.red > onBackground.green && onBackground.red > onBackground.blue)
        assertTrue(excellent.red > excellent.green && excellent.red > excellent.blue)
        assertTrue(excellent.green < 0.9f)
        assertEquals(Color(0xFF8AB4FF), standardPrimary)
    }

    @Test
    fun settingsTogglesNightVisionAndStoresTheUnitChoice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                    units = UnitSystem.METRIC,
                )
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SettingsScreen(vm, onBack = {}) }
        }
        compose.onNodeWithTag("night-vision").assertIsOff().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("night-vision") and isOn()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("units-imperial").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("units-imperial") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        val saved = runBlocking { graph.settings.current() }
        assertTrue(saved.nightVision)
        assertEquals(UnitSystem.IMPERIAL, saved.units)
    }

    @Test
    fun tonightAppBarTogglesNightVision() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.onNodeWithContentDescription("Night vision off").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasContentDescription("Night vision on")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { graph.settings.current().nightVision }
        }
    }

    @Test
    fun timelineFootnoteUsesMilesWhenImperial() {
        val date = LocalDate.of(2024, 8, 12)
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        val forecast = Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 48).map { i ->
                HourlyWeather(
                    epochSecond = start + i * 3600L,
                    cloudCover = 10,
                    humidity = 40,
                    windKmh = 6.0,
                    temperatureC = -4.0,
                    dewPointC = -6.0,
                )
            },
        )
        val report = NightPlanner.plan(home, date, forecast, GearCatalog.exampleKit)
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {}, units = UnitSystem.IMPERIAL)
                }
            }
        }
        compose.onNodeWithText("wind in mph", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dress for 25 °F").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("km/h", substring = true).assertDoesNotExist()
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
