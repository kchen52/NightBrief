package app.nightbrief.score

import app.nightbrief.astro.TimeWindow
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WindowAlarmTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 10)
    private val kit = GearCatalog.exampleKit

    @Test
    fun aShownBestWindowProducesAnAlarmAtItsStart() {
        val report = clearReport()
        val alarm = WindowAlarms.forReport(report)
        assertNotNull("expected an alarm when bestWindow is shown", alarm)
        assertEquals(home.id, alarm!!.siteId)
        assertEquals(date.toString(), alarm.nightDate)
        assertEquals(report.score!!.bestWindow!!.start, alarm.start)
        assertEquals(report.score!!.bestWindow!!.end, alarm.end)
        assertEquals(report.score!!.bestWindowScore, alarm.windowScore)
        assertTrue(WindowAlarms.isArmedFor(alarm, report))
    }

    @Test
    fun aNoGoNightWithoutAWorthwhileWindowProducesNoAlarm() {
        val report = clearReport()
        val hidden = report.copy(score = report.score!!.copy(score = 20, bestWindowScore = 40))
        assertNull("a weak window on a No-go night is hidden, so it should not arm", WindowAlarms.forReport(hidden))
        assertNull("a report with no score has no window", WindowAlarms.forReport(report.copy(score = null)))
    }

    @Test
    fun syncWithBriefingMovesTheAlarmWhenTheWindowShifts() {
        val initial = clearReport()
        val armed = WindowAlarms.forReport(initial)!!
        val shiftedWindow = TimeWindow(
            armed.start.plusSeconds(3600),
            armed.end.plusSeconds(3600),
        )
        val shiftedReport = initial.copy(
            score = initial.score!!.copy(bestWindow = shiftedWindow, bestWindowScore = 91),
        )
        val briefing = Briefing(Instant.EPOCH, listOf(shiftedReport), emptyList())
        val synced = WindowAlarms.syncWithBriefing(armed, briefing, SiteBook().add(home))
        assertNotNull(synced)
        assertEquals(shiftedWindow.start, synced!!.start)
        assertEquals(91, synced.windowScore)
    }

    @Test
    fun syncWithBriefingCancelsWhenTheWindowDisappearsOrTheSiteIsDeleted() {
        val initial = clearReport()
        val armed = WindowAlarms.forReport(initial)!!
        val overcast = initial.copy(score = initial.score!!.copy(score = 15, bestWindowScore = 20))
        val collapsedBriefing = Briefing(Instant.EPOCH, listOf(overcast), emptyList())
        assertNull(
            "a night whose window collapsed should cancel the reminder",
            WindowAlarms.syncWithBriefing(armed, collapsedBriefing, SiteBook().add(home)),
        )

        val validBriefing = Briefing(Instant.EPOCH, listOf(initial), emptyList())
        assertNull(
            "deleting the site should cancel the reminder",
            WindowAlarms.syncWithBriefing(armed, validBriefing, SiteBook()),
        )
        assertFalse(WindowAlarms.isArmedFor(null, initial))
    }

    private fun clearReport(): NightReport {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        val forecast = Forecast(
            home.latitude,
            home.longitude,
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
        return NightPlanner.plan(home, date, forecast, kit)
    }
}
