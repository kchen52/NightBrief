package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NightPlanShareTest {
    private val home = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 8)
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 3)
    private val kit = GearCatalog.exampleKit
    private val aug10 = LocalDate.of(2024, 8, 10)

    @Test
    fun coarsePlaceRoundsToATenthAndDropsThePin() {
        assertEquals("43.7°N 79.4°W", NightPlanCard.place(43.6532, -79.3832))
        assertEquals("33.9°S 151.2°E", NightPlanCard.place(-33.8688, 151.2093))
        assertEquals("0.0°N 0.0°E", NightPlanCard.place(0.04, -0.04))
        assertEquals("90.0°N 180.0°E", NightPlanCard.place(90.06, 179.96))
        assertEquals("90.0°S 180.0°W", NightPlanCard.place(-90.06, -179.96))
    }

    @Test
    fun clearNightCardCarriesScoreWindowMilkyWayAndTargetWithoutThePin() {
        val report = NightPlanner.plan(longPoint, aug10, forecast(longPoint), kit)
        val card = NightPlanCard.from(report)
        val score = report.score!!
        assertTrue("score was ${score.score}", score.verdict != Verdict.NO_GO)
        assertEquals("Long Point", card.siteName)
        assertEquals("42.6°N 80.4°W", card.place)
        assertEquals("Sat 10 Aug", card.dateLabel)
        assertEquals(score.score, card.score)
        assertEquals(score.score.toString(), card.scoreText)
        assertEquals("${score.verdict.label} · ${score.band.label}", card.verdictLabel)
        val window = card.bestWindow
        assertNotNull(window)
        assertTrue(window, window!!.matches(Regex("""\d{2}:\d{2}–\d{2}:\d{2} \(\d+\)""")))
        assertTrue(window, window.endsWith("(${score.bestWindowScore})"))
        assertTrue(card.milkyWay, card.milkyWay.startsWith("Milky Way "))
        assertEquals(report.suggestions.first().target.name, card.target)
        assertNull(card.saved)
        assertTrue(card.caption, card.caption.contains("Long Point"))
        assertTrue(card.caption, card.caption.contains("Try: ${card.target}"))
        assertTrue(card.caption, card.caption.endsWith("NightBrief"))
        assertFalse(card.caption, card.caption.contains(longPoint.latitude.toString()))
        assertFalse(card.caption, card.caption.contains(longPoint.longitude.toString()))
        assertFalse(card.place, card.place.contains(longPoint.latitude.toString()))
    }

    @Test
    fun noGoNightHidesAWeakWindowAndStillShowsOneThatScoresFifty() {
        val report = NightPlanner.plan(longPoint, aug10, forecast(longPoint), kit)
        val hidden = report.copy(score = report.score!!.copy(score = 20, bestWindowScore = 40))
        assertNotNull(hidden.score!!.bestWindow)
        assertFalse(NightPlanCard.showsBestWindow(hidden.score!!))
        assertNull(NightPlanCard.from(hidden).bestWindow)

        val kept = report.copy(score = report.score!!.copy(score = 20, bestWindowScore = 55))
        assertTrue(NightPlanCard.showsBestWindow(kept.score!!))
        assertNotNull(NightPlanCard.from(kept).bestWindow)
    }

    @Test
    fun aNightPastTheForecastKeepsTheSkyAndSaysThereIsNoScore() {
        val saved = forecast(longPoint)
        val report = NightPlanner.plan(
            longPoint,
            aug10.plusDays(30),
            saved,
            kit,
            forecastStatus = ForecastStatus.STALE,
        )
        assertNull(report.score)
        val card = NightPlanCard.from(report)
        assertNull(card.score)
        assertEquals("—", card.scoreText)
        assertEquals("No score", card.verdictLabel)
        assertNull(card.bestWindow)
        assertEquals(SavedForecast.shortLabel(ForecastStatus.STALE, saved.fetchedAt, longPoint.zone), card.saved)
        assertTrue(card.caption, card.caption.contains(card.saved!!))
        assertFalse(card.milkyWay.isBlank())
    }

    @Test
    fun aFreshForecastIsNotLabeledSaved() {
        val report = NightPlanner.plan(
            home,
            aug10,
            forecast(home),
            kit,
            forecastStatus = ForecastStatus.CACHED,
        )
        assertNull(NightPlanCard.from(report).saved)
    }

    @Test
    fun aTreelineThatHidesTheCoreSaysSoOnTheCard() {
        val site = home.copy(horizon = LocalHorizon(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0))
        val report = NightPlanner.plan(site, aug10, forecast = null, kit)
        assertTrue(report.ephemeris.milkyWayBlockedByHorizon)
        assertEquals(WidgetCopy.BEHIND_TREELINE, NightPlanCard.from(report).milkyWay)
    }

    @Test
    fun theCardNamesTheFirstSuggestion() {
        val report = NightPlanner.plan(home, LocalDate.of(2024, 6, 21), forecast = null, kit)
        val card = NightPlanCard.from(report)
        assertEquals("moonlit-landscape", report.suggestions.first().target.id)
        assertEquals(report.suggestions.first().target.name, card.target)
        assertTrue(card.caption, card.caption.contains("Try: ${card.target}"))
    }

    private fun forecast(site: Site, cloud: Int = 0): Forecast {
        val start = java.time.Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            site.latitude,
            site.longitude,
            "gem_seamless",
            start,
            (0 until 240).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = cloud,
                    humidity = 60,
                    windKmh = 6.0,
                    gustKmh = 10.0,
                    jetStreamKmh = 80.0,
                    seeing = 3,
                    transparency = 2,
                )
            },
        )
    }
}
