package app.nightbrief.app.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.nightbrief.app.widget.NightBriefWidget
import app.nightbrief.app.widget.WidgetContent
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.BriefingSource
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.score.WidgetCopy
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WearPublisherTest {
    private val defaultNodeCheck = WearPublisher.hasConnectedNodes
    private val clock = Clock.fixed(Instant.parse("2026-08-12T18:00:00Z"), ZoneOffset.UTC)
    private val night = LocalDate.of(2026, 8, 12)
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)

    @After
    fun tearDown() {
        WearPublisher.hasConnectedNodes = defaultNodeCheck
        AppGraph.get(ApplicationProvider.getApplicationContext()).resetSourcesForTest()
    }

    @Test
    fun pushDoesNotThrowWhenPlayServicesAreMissing() = runBlocking {
        WearPublisher.push(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun pushSkipsScoringWhenNoWatchIsConnected() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        var briefCalls = 0
        graph.replaceSourcesForTest(NoopForecasts, countingBriefings(sampleReport(home)) { briefCalls++ })
        graph.settings.update {
            AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
        }
        WearPublisher.hasConnectedNodes = { false }

        WearPublisher.push(app, clock)
        assertEquals("expected 0 brief calls when no watch is paired, got $briefCalls", 0, briefCalls)
    }

    @Test
    fun widgetAndWearShareOneSnapshotAndInvalidateOnSiteChange() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val graph = AppGraph.get(app)
        var briefCalls = 0
        val report = sampleReport(home)
        graph.replaceSourcesForTest(NoopForecasts, countingBriefings(report) { briefCalls++ })
        graph.settings.update {
            AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true))
        }

        // Cold widget load computes fallback once and populates glanceSnapshot.
        val firstWidget = NightBriefWidget().load(app, clock)
        assertTrue(firstWidget is WidgetContent.Night)
        assertEquals(1, briefCalls)
        assertEquals(WidgetCopy.scoreLine(report.scoreValue), (firstWidget as WidgetContent.Night).scoreLine)

        // Immediate Wear load reuses the snapshot with zero additional brief calls.
        val glance = WearPublisher.load(app, clock)
        assertEquals(1, briefCalls)
        assertEquals(home.name, glance.siteName)
        assertEquals(report.flavorLine, glance.flavorText)

        // Renaming the primary site invalidates the snapshot so the old name is never served.
        val renamed = home.copy(name = "Observatory")
        graph.replaceSourcesForTest(NoopForecasts, countingBriefings(sampleReport(renamed)) { briefCalls++ })
        graph.settings.update {
            AppState(onboardingComplete = true, sites = SiteBook().add(renamed, makePrimary = true))
        }
        val secondWidget = NightBriefWidget().load(app, clock) as WidgetContent.Night
        assertEquals(2, briefCalls)
        assertEquals("Observatory", secondWidget.siteName)
    }

    private fun sampleReport(site: Site): NightReport {
        val start = Instant.parse("2026-08-12T00:00:00Z").epochSecond
        val forecast = Forecast(
            site.latitude, site.longitude, "test", start,
            (0 until 48).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 5, humidity = 40,
                    windKmh = 6.0, seeing = 2, transparency = 2,
                )
            },
        )
        return NightPlanner.plan(site, night, forecast, GearCatalog.exampleKit, ForecastStatus.FRESH)
    }

    private fun countingBriefings(report: NightReport, onCall: () -> Unit): BriefingSource =
        object : BriefingSource {
            override suspend fun brief(
                sites: List<Site>,
                kit: GearKit,
                outlookDays: Int,
                forceRefresh: Boolean,
            ): Briefing {
                onCall()
                return Briefing(clock.instant(), listOf(report), emptyList())
            }

            override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport = report
        }

    private object NoopForecasts : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult =
            ForecastResult(Forecast(latitude, longitude, "test", 0L, emptyList()), ForecastStatus.CACHED)
    }
}
