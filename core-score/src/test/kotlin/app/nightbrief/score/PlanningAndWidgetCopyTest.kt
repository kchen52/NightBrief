package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.OpenMeteoClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlanningAndWidgetCopyTest {
    @Test
    fun plannerStepsAcrossTheOpenMeteoHorizonAndStopsOnTheEnds() {
        val tonight = LocalDate.of(2026, 8, 12)
        val last = NightPlanner.planningLastDate(tonight)
        assertEquals(OpenMeteoClient.DEFAULT_FORECAST_DAYS, 8)
        assertEquals(tonight.plusDays(7), last)
        assertFalse(NightPlanner.canStepToPreviousNight(tonight, tonight))
        assertTrue(NightPlanner.canStepToPreviousNight(tonight.plusDays(1), tonight))
        assertTrue(NightPlanner.canStepToNextNight(tonight, tonight))
        assertTrue(NightPlanner.canStepToNextNight(last.minusDays(1), tonight))
        assertFalse(NightPlanner.canStepToNextNight(last, tonight))
        assertEquals(tonight, NightPlanner.planningLastDate(tonight, forecastDays = 1))
    }

    @Test
    fun widgetScoreLineNamesTheBand() {
        assertEquals("No score", WidgetCopy.scoreLine(null))
        assertEquals("85 Excellent", WidgetCopy.scoreLine(85))
        assertEquals("84 Good", WidgetCopy.scoreLine(84))
        assertEquals("70 Good", WidgetCopy.scoreLine(70))
        assertEquals("50 Fair", WidgetCopy.scoreLine(50))
        assertEquals("30 Marginal", WidgetCopy.scoreLine(30))
        assertEquals("0 Poor", WidgetCopy.scoreLine(0))
    }

    @Test
    fun widgetMilkyWayLineUsesTheSiteZoneOrSaysTheCoreIsDown() {
        val zone = ZoneId.of("America/Toronto")
        assertEquals("Milky Way core not up", WidgetCopy.milkyWayLine(null, zone))
        val eph = NightEphemeris.compute(LocalDate.of(2024, 8, 12), zone, 43.65, -79.38)
        val window = eph.milkyWay!!.window
        val line = WidgetCopy.milkyWayLine(window, zone)
        assertTrue(line.startsWith("Milky Way "))
        assertTrue(line.contains("–"))
        assertFalse(line.contains("not up"))
    }

    @Test
    fun aRecentForecastIsUnlabeledAndAStaleOneNamesWhenItWasSaved() {
        val zone = ZoneId.of("America/Toronto")
        val savedAt = Instant.parse("2026-10-01T22:40:00Z")
        assertNull(SavedForecast.shortLabel(ForecastStatus.FRESH, savedAt, zone))
        assertNull(SavedForecast.shortLabel(ForecastStatus.CACHED, savedAt, zone))
        assertEquals("Saved Thu 18:40", SavedForecast.shortLabel(ForecastStatus.STALE, savedAt, zone))
        assertEquals("Saved forecast", SavedForecast.shortLabel(ForecastStatus.STALE, null, zone))
        assertEquals("Offline — forecast saved Thu 18:40", SavedForecast.detail(savedAt, zone))
    }
}
