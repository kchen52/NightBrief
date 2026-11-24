package app.nightbrief.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.WearGlance
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WearGlanceLoaderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @Before
    fun setUp() {
        val graph = AppGraph.get(app)
        runBlocking {
            graph.glanceSnapshot.clear()
            graph.settings.update {
                AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
            }
        }
    }

    @After
    fun tearDown() {
        AppGraph.get(app).resetSourcesForTest()
    }

    @Test
    fun localFallbackUsesBriefLeanOnceAndReusesGlanceSnapshot() = runBlocking {
        val graph = AppGraph.get(app)
        var fullBriefCalls = 0
        var leanBriefCalls = 0
        val source = object : BriefingSource {
            override suspend fun brief(
                sites: List<Site>,
                kit: GearKit,
                outlookDays: Int,
                forceRefresh: Boolean,
            ): Briefing {
                fullBriefCalls++
                return buildBriefing(sites, kit, graph)
            }

            override suspend fun briefLean(
                sites: List<Site>,
                kit: GearKit,
                forceRefresh: Boolean,
            ): Briefing {
                leanBriefCalls++
                return buildBriefing(sites, kit, graph)
            }

            override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
                NightPlanner.plan(site, date, null, kit)
        }
        graph.replaceSourcesForTest(QuietForecasts, source)

        val first = WearGlanceLoader.local(app)
        val second = WearGlanceLoader.local(app)

        assertEquals("full brief should never be called by Wear local fallback", 0, fullBriefCalls)
        assertEquals("briefLean should run once on cold cache, then hit glanceSnapshot", 1, leanBriefCalls)
        assertTrue("first glance should be ready", first.ready)
        assertEquals(first, second)
        assertEquals("Home", first.siteName)
    }

    @Test
    fun localFallbackIsolatesBriefingFailures() = runBlocking {
        val graph = AppGraph.get(app)
        val failing = object : BriefingSource {
            override suspend fun brief(
                sites: List<Site>,
                kit: GearKit,
                outlookDays: Int,
                forceRefresh: Boolean,
            ): Briefing = error("boom")

            override suspend fun briefLean(
                sites: List<Site>,
                kit: GearKit,
                forceRefresh: Boolean,
            ): Briefing = error("boom")

            override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
                error("boom")
        }
        graph.replaceSourcesForTest(QuietForecasts, failing)

        val glance = WearGlanceLoader.local(app)
        assertFalse("failed fallback should return setup glance", glance.ready)
        assertEquals(WearGlance.SETUP, glance.verdictText)
    }

    private fun buildBriefing(sites: List<Site>, kit: GearKit, graph: AppGraph): Briefing {
        val now = Instant.now()
        val reports = sites.map { site ->
            val date = NightPlanner.tonight(now, site.zone)
            val start = now.epochSecond - 3600L
            val fc = Forecast(
                site.latitude,
                site.longitude,
                "best_match",
                now.epochSecond,
                (0 until 48).map { h ->
                    HourlyWeather(
                        epochSecond = start + h * 3600L,
                        cloudCover = 5,
                        humidity = 40,
                        windKmh = 6.0,
                        temperatureC = 12.0,
                        dewPointC = 4.0,
                        seeing = 2,
                        transparency = 2,
                    )
                },
            )
            NightPlanner.plan(
                site = site,
                date = date,
                forecast = fc,
                kit = kit,
                forecastStatus = ForecastStatus.FRESH,
                includeSuggestions = false,
            )
        }
        return Briefing(now, reports, emptyList())
    }

    private object QuietForecasts : ForecastSource {
        override suspend fun forecast(
            latitude: Double,
            longitude: Double,
            forceRefresh: Boolean,
        ): ForecastResult = ForecastResult(
            Forecast(latitude, longitude, "test", Instant.EPOCH.epochSecond, emptyList()),
            ForecastStatus.CACHED,
        )
    }
}
