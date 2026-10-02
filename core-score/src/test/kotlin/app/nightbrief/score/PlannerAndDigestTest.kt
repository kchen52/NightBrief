package app.nightbrief.score

import app.nightbrief.astro.TargetKind
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlannerAndDigestTest {
    private val home = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 8)
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 3)
    private val kit = GearCatalog.exampleKit

    /** Ten days of identical hourly weather starting at [start]. */
    private fun forecast(
        site: Site,
        start: String = "2024-08-09T00:00:00Z",
        cloud: Int = 0,
        seeing: Int? = 3,
        transparency: Int? = 2,
        hours: Int = 240,
    ): Forecast {
        val s = Instant.parse(start).epochSecond
        return Forecast(
            site.latitude, site.longitude, "gem_seamless", s,
            (0 until hours).map {
                HourlyWeather(
                    epochSecond = s + it * 3600L, cloudCover = cloud, humidity = 60,
                    windKmh = 6.0, gustKmh = 10.0, jetStreamKmh = 80.0,
                    seeing = seeing, transparency = transparency,
                )
            },
        )
    }

    private val aug10 = LocalDate.of(2024, 8, 10)

    @Test
    fun clearNightAtDarkSiteScoresHighWithMilkyWaySuggestion() {
        val r = NightPlanner.plan(longPoint, aug10, forecast(longPoint), kit)
        assertEquals(ForecastCoverage.FULL, r.coverage)
        val score = r.score!!
        // A 36% waxing Moon is up for the first ~45 minutes of darkness, so not quite "clear & moonless".
        assertTrue("score was ${score.score}", score.score in 80..95)
        val first = r.suggestions.first()
        assertEquals(TargetKind.MILKY_WAY, first.target.kind)
        assertNotNull(first.exposure)
        assertEquals("sigma-10-18-f28-dcdn", first.exposure!!.lens.id)
        assertTrue(r.timeline.any { it.isDark } && r.timeline.any { !it.isDark })
    }

    @Test
    fun forecastBeyondHorizonHasNoScoreButKeepsEphemeris() {
        val saved = forecast(longPoint)
        val r = NightPlanner.plan(longPoint, aug10.plusDays(30), saved, kit)
        assertEquals(ForecastCoverage.NONE, r.coverage)
        assertNull(r.score)
        assertNotNull(r.ephemeris.darkWindow)
        assertEquals(saved.fetchedAt, r.forecastFetchedAt)
    }

    @Test
    fun aSavedForecastPlansLaterNightsAndRecomputesEphemerisPastItsHours() {
        val saved = forecast(longPoint, start = "2024-08-09T00:00:00Z", hours = 96)
        val inside = LocalDate.of(2024, 8, 11)
        val covered = NightPlanner.plan(
            longPoint, inside, saved, kit, forecastStatus = ForecastStatus.STALE,
        )
        assertEquals(ForecastStatus.STALE, covered.forecastStatus)
        assertEquals(saved.fetchedAt, covered.forecastFetchedAt)
        assertNotNull(covered.score)
        assertEquals(
            SavedForecast.detail(saved.fetchedAt, longPoint.zone),
            DigestComposer.compose(covered, emptyList()).lines.last(),
        )

        val past = LocalDate.of(2024, 8, 20)
        val uncovered = NightPlanner.plan(
            longPoint, past, saved, kit, forecastStatus = ForecastStatus.STALE,
        )
        assertEquals(ForecastCoverage.NONE, uncovered.coverage)
        assertNull(uncovered.score)
        assertNotNull(uncovered.ephemeris.darkWindow)
        assertEquals(saved.fetchedAt, uncovered.forecastFetchedAt)
        assertTrue(uncovered.ephemeris.sunset != covered.ephemeris.sunset)
    }

    @Test
    fun digestFlagsMuchBetterAlternativeSite() {
        val homeReport = NightPlanner.plan(home, aug10, forecast(home, cloud = 70), kit)
        val lpReport = NightPlanner.plan(longPoint, aug10, forecast(longPoint, cloud = 5), kit)
        val digest = DigestComposer.compose(homeReport, listOf(homeReport, lpReport))
        assertEquals("lp", digest.alternativeSiteId)
        assertTrue(digest.title, digest.title.startsWith("Tonight: ${homeReport.scoreValue} at Home, but ${lpReport.scoreValue} at Long Point"))
        val altLine = digest.lines.first { it.startsWith("Long Point +") }
        assertTrue(altLine, altLine.contains("clearer skies (5% vs 70% cloud)"))
        assertTrue(altLine, altLine.contains("Bortle 3 skies"))
        assertTrue(digest.lines.any { it.startsWith("Milky Way core") })
    }

    @Test
    fun digestOmitsAlternativeWhenNotMeaningfullyBetter() {
        val homeReport = NightPlanner.plan(home, aug10, forecast(home), kit)
        val lpReport = NightPlanner.plan(longPoint, aug10, forecast(longPoint), kit)
        val digest = DigestComposer.compose(homeReport, listOf(lpReport))
        assertNull(digest.alternativeSiteId)
        assertTrue(digest.title, digest.title.startsWith("Tonight: ${homeReport.scoreValue} at Home ·"))
    }

    @Test
    fun moonReasonComparesMoonriseRelativeToDarkness() {
        // Further south, darkness starts earlier relative to moonrise, leaving more moon-free time.
        val sudbury = Site("sud", "Sudbury", 46.49, -80.99, "America/Toronto", bortle = 4)
        val date = LocalDate.of(2024, 8, 24)
        val a = NightPlanner.plan(sudbury, date, forecast(sudbury), kit)
        val b = NightPlanner.plan(home, date, forecast(home), kit)
        val reason = SiteComparison.moonReason(a, b)
        assertTrue(reason, reason.matches(Regex("moon rises (19|2\\d) min later there")))
    }

    @Test
    fun moonLineDescribesFullMoon() {
        val r = NightPlanner.plan(home, LocalDate.of(2024, 6, 21), forecast(home, start = "2024-06-20T00:00:00Z"), kit)
        assertTrue(DigestComposer.moonLine(r.ephemeris) { it.toString() }.contains("up all night"))
    }

    @Test
    fun compassPoints() {
        assertEquals("N", DigestComposer.compass(359.0))
        assertEquals("S", DigestComposer.compass(181.0))
        assertEquals("SW", DigestComposer.compass(225.0))
    }

    @Test
    fun tonightRollsOverAtSixAm() {
        val zone = ZoneId.of("America/Toronto")
        assertEquals(LocalDate.of(2024, 8, 10), NightPlanner.tonight(Instant.parse("2024-08-10T12:00:00Z"), zone))
        assertEquals(LocalDate.of(2024, 8, 10), NightPlanner.tonight(Instant.parse("2024-08-11T06:00:00Z"), zone)) // 02:00 EDT
    }

    private class FakeSource(val forecasts: Map<Pair<Double, Double>, Forecast>) : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
            val f = forecasts[latitude to longitude] ?: throw WeatherApiException("offline")
            return ForecastResult(f, ForecastStatus.FRESH)
        }
    }

    private class CountingSource(val forecasts: Map<Pair<Double, Double>, Forecast>) : ForecastSource {
        var calls = 0
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
            calls++
            val f = forecasts[latitude to longitude] ?: throw WeatherApiException("offline")
            return ForecastResult(f, ForecastStatus.FRESH)
        }
    }

    @Test
    fun sitesInTheSameCacheCellShareOneFetch() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T12:00:00Z"), ZoneOffset.UTC)
        // ~50 m from home, inside the same ~1 km forecast cell.
        val nextDoor = Site("near", "Next door", 43.6535, -79.3835, "America/Toronto", bortle = 8)
        val source = CountingSource(mapOf((home.latitude to home.longitude) to forecast(home, cloud = 20)))
        val briefing = BriefingService(source, clock).brief(listOf(home, nextDoor), kit, outlookDays = 1)
        assertEquals("expected 1 fetch for 2 co-located sites, got ${source.calls}", 1, source.calls)
        assertNotNull(briefing.reportFor("home")!!.score)
        assertNotNull(briefing.reportFor("near")!!.score)
    }

    @Test
    fun planSurvivesKpAndForecastFailures() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T12:00:00Z"), ZoneOffset.UTC)
        val failingKp = object : KpSource {
            override suspend fun fetch(): KpForecast = throw WeatherApiException("swpc down")
        }
        val offline = object : ForecastSource {
            override suspend fun forecast(
                latitude: Double,
                longitude: Double,
                forceRefresh: Boolean,
            ): ForecastResult = throw WeatherApiException("offline")
        }
        val report = BriefingService(offline, clock, kp = failingKp).plan(home, aug10, kit)
        assertNull(report.aurora)
        assertNull("expected no score without a forecast, got ${report.scoreValue}", report.score)
        assertEquals(ForecastCoverage.NONE, report.coverage)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun briefingCoversAllSitesAndWeek() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T12:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(mapOf((home.latitude to home.longitude) to forecast(home, cloud = 20)))
        val briefing = BriefingService(source, clock).brief(listOf(home, longPoint), kit, outlookDays = 7, forceRefresh = false)
        assertEquals(2, briefing.tonight.size)
        assertNotNull(briefing.reportFor("home")!!.score)
        val lp = briefing.reportFor("lp")!!
        assertNull(lp.score)
        assertTrue(lp.warnings.single().contains("offline"))
        val outlook = briefing.outlookFor("home")!!
        assertEquals(7, outlook.nights.size)
        assertTrue(outlook.nights.none { it.estimated })
        assertTrue(outlook.headline(java.util.Locale.ENGLISH).startsWith("Best night this week: "))
    }

    @Test
    fun outlookMarksNightsWhoseSeeingWasEstimated() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T12:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(
            mapOf((home.latitude to home.longitude) to forecast(home, seeing = null, transparency = null)),
        )
        val outlook = BriefingService(source, clock).brief(listOf(home), kit, outlookDays = 7).outlookFor("home")!!
        assertTrue(outlook.nights.all { it.estimated && it.score != null })
    }
}
