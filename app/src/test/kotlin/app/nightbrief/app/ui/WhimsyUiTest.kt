package app.nightbrief.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.theme.NightBriefTheme
import app.nightbrief.app.ui.tonight.NightDetail
import app.nightbrief.app.ui.tonight.TonightScreen
import app.nightbrief.app.ui.week.WeekScreen
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.MeteorAdvisor
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.OutlookNight
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.astro.Darkness
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WhimsyUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val cabin = Site("cabin", "Cabin", 44.0, -80.0, "America/Toronto", bortle = 3)
    private val scoredNight = LocalDate.of(2024, 8, 12)

    @After
    fun tearDown() {
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun tonightHeroShowsTheFlavourLine() {
        val date = LocalDate.of(2024, 3, 15)
        val start = date.minusDays(1).atStartOfDay(java.time.ZoneId.of("America/Toronto")).toInstant()
        val forecast = Forecast(
            home.latitude,
            home.longitude,
            "test",
            start.epochSecond,
            (0 until 72).map { offset ->
                HourlyWeather(
                    epochSecond = start.epochSecond + offset * 3600L,
                    cloudCover = 100,
                    humidity = 50,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 80.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
        val report = NightPlanner.plan(home, date, forecast, GearCatalog.exampleKit)
        val line = report.flavorLine
        assertNotNull(line)
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
        compose.onNodeWithText(line!!).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weekShowsANightName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        val thursday = LocalDate.of(2024, 8, 15)
        graph.replaceSourcesForTest(
            QuietForecasts,
            object : BriefingSource {
                override suspend fun brief(
                    sites: List<Site>,
                    kit: GearKit,
                    outlookDays: Int,
                    forceRefresh: Boolean,
                ): Briefing {
                    val night = OutlookNight(
                        date = thursday,
                        score = 40,
                        moonIllumination = 0.2,
                        darkness = Darkness.ASTRONOMICAL,
                        coverage = ForecastCoverage.FULL,
                        showerLabel = "Perseid",
                    )
                    return Briefing(
                        Instant.EPOCH,
                        emptyList(),
                        listOf(WeeklyOutlook(home, listOf(night))),
                    )
                }

                override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
                    NightPlanner.plan(site, date, null, kit)
            },
        )
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                )
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
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Perseid Thursday").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Perseid Thursday").assertIsDisplayed()
    }

    @Test
    fun weekStripNamesEachSitesBestNightRatherThanTonight() {
        val homeTonight = clearNight(home).scoreValue
        val cabinTonight = clearNight(cabin).scoreValue
        assertTrue("fixture should score tonight at home", homeTonight != null)
        assertTrue("fixture should score tonight at the cabin", cabinTonight != null)
        assertTrue(
            "fixture tonight ($homeTonight, $cabinTonight) must differ from the week's best (40, 55)",
            homeTonight != 40 && cabinTonight != 55 && homeTonight != 55 && cabinTonight != 40,
        )
        val vm = viewModelWith(bestNightBriefing())
        compose.setContent {
            NightBriefTheme {
                WeekScreen(vm, onOpenNight = { _, _ -> }, onOpenEvents = {}, contentPadding = PaddingValues())
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("40").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("55").fetchSemanticsNodes().isNotEmpty()
        }
        val captions = compose.onAllNodesWithText("Best this week").fetchSemanticsNodes().size
        assertTrue("each week chip should name the score, found $captions", captions == 2)
        assertTrue(
            "week should show the best nights, not tonight's $homeTonight",
            compose.onAllNodesWithText(homeTonight.toString()).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "the footnote should no longer explain the strip",
            compose.onAllNodesWithText("Strip scores", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun tonightStripLeavesTheScoreUnlabeled() {
        val homeTonight = clearNight(home).scoreValue
        assertTrue("fixture should score tonight", homeTonight != null)
        val vm = viewModelWith(bestNightBriefing())
        compose.setContent {
            NightBriefTheme { TonightScreen(vm, onOpenSettings = {}, contentPadding = PaddingValues()) }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(homeTonight.toString()).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "tonight's chips should not say the score is the week's best",
            compose.onAllNodesWithText("Best this week").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "tonight should show tonight's score, not the week's best of 40",
            compose.onAllNodesWithText("40").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun meteorCardRowsStackWithoutOverlapping() {
        // November 15 carries the Leonids, so the card renders without a forecast.
        val report = NightPlanner.plan(home, LocalDate.of(2026, 11, 15), null, GearCatalog.exampleKit)
        val meteor = report.meteor
        assertNotNull("November 15 should carry a shower", meteor)
        assertTrue("the shower must be worth watching to show its card", meteor!!.worthWatching)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val summary = MeteorAdvisor.digestLine(meteor)
        val radiant = context.getString(
            app.nightbrief.app.R.string.meteor_radiant,
            Format.azimuth(meteor.peakRadiantAzimuthDeg),
            Format.time(meteor.peakRadiantTime, report.site.zone),
        )
        compose.setContent {
            NightBriefTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NightDetail(report, alternative = null, onOpenAlternative = {})
                }
            }
        }
        // Scroll the bottom-most row into view: this Compose version zeroes the bounds of
        // nodes clipped below a scrollable's viewport, so every row must be on screen.
        compose.onNodeWithContentDescription(
            context.getString(
                app.nightbrief.app.R.string.show_section_details,
                context.getString(app.nightbrief.app.R.string.section_meteors),
            ),
        ).performScrollTo()
        val summaryBounds = compose.onNodeWithText(summary).fetchSemanticsNode().boundsInRoot
        val radiantBounds = compose.onNodeWithText(radiant).fetchSemanticsNode().boundsInRoot
        val whyBounds = compose
            .onNodeWithContentDescription(
                context.getString(
                    app.nightbrief.app.R.string.show_section_details,
                    context.getString(app.nightbrief.app.R.string.section_meteors),
                ),
            )
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("the summary row must be laid out on screen, was $summaryBounds", summaryBounds.height > 0f)
        assertTrue("the radiant row must be laid out on screen, was $radiantBounds", radiantBounds.height > 0f)
        assertTrue("the Why control must be laid out on screen, was $whyBounds", whyBounds.height > 0f)
        assertTrue(
            "summary must sit above the radiant row: $summaryBounds vs $radiantBounds",
            summaryBounds.bottom <= radiantBounds.top,
        )
        assertTrue(
            "radiant row must sit above the Why control: $radiantBounds vs $whyBounds",
            radiantBounds.bottom <= whyBounds.top,
        )
    }

    /** Tonight scores high; each site's best night is a different number, so the two tabs cannot share a chip. */
    private fun bestNightBriefing(): BriefingSource = object : BriefingSource {
        override suspend fun brief(
            sites: List<Site>,
            kit: GearKit,
            outlookDays: Int,
            forceRefresh: Boolean,
        ): Briefing = Briefing(
            Instant.EPOCH,
            listOf(clearNight(home), clearNight(cabin)),
            listOf(
                WeeklyOutlook(home, listOf(outlookNight(40))),
                WeeklyOutlook(cabin, listOf(outlookNight(55))),
            ),
        )

        override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
            NightPlanner.plan(site, date, null, kit)
    }

    private fun viewModelWith(briefings: BriefingSource): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        graph.replaceSourcesForTest(QuietForecasts, briefings)
        runBlocking {
            graph.settings.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true).add(cabin),
                )
            }
        }
        return AppViewModel(app)
    }

    private fun clearNight(site: Site): NightReport =
        NightPlanner.plan(site, scoredNight, clearForecast(), GearCatalog.exampleKit)

    private fun outlookNight(score: Int) = OutlookNight(
        date = scoredNight,
        score = score,
        moonIllumination = 0.2,
        darkness = Darkness.ASTRONOMICAL,
        coverage = ForecastCoverage.FULL,
    )

    private fun clearForecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude,
            home.longitude,
            "best_match",
            start,
            (0 until 24 * 8).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
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
