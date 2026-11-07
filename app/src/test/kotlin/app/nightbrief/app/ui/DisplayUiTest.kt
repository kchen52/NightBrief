package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import app.nightbrief.app.ui.common.CardCollapse
import app.nightbrief.app.ui.common.CardIds
import app.nightbrief.app.ui.common.LocalCardCollapse
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.settings.SettingsScreen
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.astro.IssPass
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
        assertEquals(0f, onBackground.green, 0.001f)
        assertEquals(0f, onBackground.blue, 0.001f)
        assertEquals(0f, excellent.green, 0.001f)
        assertEquals(0f, excellent.blue, 0.001f)
        assertTrue(onBackground.red > 0.5f)
        assertTrue(excellent.red > 0.5f)
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
        compose.onNodeWithText("Dress for 25 °F, the overnight low", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("km/h", substring = true).assertDoesNotExist()
    }

    @Test
    fun issCardStaysOutOfNightDetailUntilOptedIn() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 8, 12), null, GearCatalog.exampleKit).copy(
            issPasses = listOf(
                IssPass(
                    rise = Instant.parse("2024-08-13T02:10:00Z"),
                    set = Instant.parse("2024-08-13T02:16:00Z"),
                    peak = Instant.parse("2024-08-13T02:13:00Z"),
                    peakAltitudeDeg = 62.0,
                    peakAzimuthDeg = 140.0,
                ),
            ),
        )
        var showIss by mutableStateOf(false)
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {}, showIss = showIss)
                }
            }
        }
        compose.onNodeWithText("ISS").assertDoesNotExist()
        compose.runOnIdle { showIss = true }
        compose.onNodeWithText("ISS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sectionCardCollapsesAndExpandsWhenTheHostStoresIt() {
        var collapsed by mutableStateOf(emptySet<String>())
        compose.setContent {
            NightBriefTheme {
                CompositionLocalProvider(
                    LocalCardCollapse provides CardCollapse(collapsed) { id ->
                        collapsed = if (id in collapsed) collapsed - id else collapsed + id
                    },
                ) {
                    Column {
                        SectionCard("Sky", collapseId = CardIds.SKY) { Text("sky body") }
                        SectionCard("Plain") { Text("plain body") }
                    }
                }
            }
        }
        compose.onNodeWithText("sky body").assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse Sky card").performClick()
        compose.onNodeWithText("sky body").assertDoesNotExist()
        compose.onNodeWithText("SKY").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand Sky card").performClick()
        compose.onNodeWithText("sky body").assertIsDisplayed()
        // A card with no id never offers the control.
        compose.onNodeWithContentDescription("Collapse Plain card").assertDoesNotExist()
        compose.onNodeWithText("plain body").assertIsDisplayed()
    }

    @Test
    fun sectionCardIsStaticWithoutAHost() {
        compose.setContent {
            NightBriefTheme { SectionCard("Sky", collapseId = CardIds.SKY) { Text("sky body") } }
        }
        compose.onNodeWithContentDescription("Collapse Sky card").assertDoesNotExist()
        compose.onNodeWithText("sky body").assertIsDisplayed()
    }

    @Test
    fun settingsSwitchStoresTheIssOptIn() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, QuietBriefings)
        runBlocking {
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
        assertEquals(false, runBlocking { graph.settings.current().issEnabled })
        val vm = AppViewModel(app)
        compose.setContent {
            NightBriefTheme { SettingsScreen(vm, onBack = {}) }
        }
        compose.onNodeWithTag("iss-enabled").performScrollTo().assertIsOff().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("iss-enabled") and isOn()).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { graph.settings.current().issEnabled })
    }

    @Test
    fun collapsedCardsAreStoredAndFlippedBack() {
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
            val state by vm.state.collectAsState()
            Text(state?.collapsedCards?.sorted()?.joinToString(",").orEmpty(), Modifier.testTag("cards"))
        }
        vm.toggleCardCollapsed(CardIds.TIMELINE)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("cards") and hasText(CardIds.TIMELINE)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(setOf(CardIds.TIMELINE), runBlocking { graph.settings.current().collapsedCards })
        vm.toggleCardCollapsed(CardIds.TIMELINE)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("cards") and hasText("")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptySet<String>(), runBlocking { graph.settings.current().collapsedCards })
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
