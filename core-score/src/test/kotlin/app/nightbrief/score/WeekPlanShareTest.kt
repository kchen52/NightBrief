package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.sites.Site
import app.nightbrief.weather.ForecastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeekPlanShareTest {
    private val longPoint = Site("lp", "Long Point", 42.5832, -80.4019, "America/Toronto", bortle = 3)
    private val aug12 = LocalDate.of(2024, 8, 12)

    @Test
    fun dateRangeHandlesSameMonthCrossMonthAndSingleNight() {
        assertEquals("12–18 Aug", WeekPlanCard.dateRange(aug12, aug12.plusDays(6)))
        assertEquals("29 Aug–4 Sep", WeekPlanCard.dateRange(LocalDate.of(2024, 8, 29), LocalDate.of(2024, 9, 4)))
        assertEquals("Mon 12 Aug", WeekPlanCard.dateRange(aug12, aug12))
        assertEquals("—", WeekPlanCard.dateRange(null, null))
    }

    @Test
    fun weekCardHighlightsBestNightAndDropsExactPin() {
        val nights = listOf(
            OutlookNight(aug12, 74, 0.35, Darkness.ASTRONOMICAL, ForecastCoverage.FULL),
            OutlookNight(aug12.plusDays(1), 91, 0.25, Darkness.ASTRONOMICAL, ForecastCoverage.FULL),
            OutlookNight(aug12.plusDays(2), 45, 0.15, Darkness.ASTRONOMICAL, ForecastCoverage.FULL, estimated = true),
            OutlookNight(aug12.plusDays(3), null, 0.04, Darkness.ASTRONOMICAL, ForecastCoverage.NONE),
        )
        val card = WeekPlanCard.from(WeeklyOutlook(longPoint, nights))

        assertEquals("Long Point", card.siteName)
        assertEquals("42.6°N 80.4°W", card.place)
        assertEquals("12–15 Aug", card.dateRangeLabel)
        assertEquals("Tue 13 Aug", card.bestDayLabel)
        assertEquals(91, card.bestScore)
        assertEquals("91", card.bestScoreText)
        assertEquals("Go · Excellent", card.bestVerdictLabel)
        assertEquals(4, card.nights.size)
        assertFalse(card.nights[0].isBest)
        assertTrue(card.nights[1].isBest)
        assertEquals("Marginal · est.", card.nights[2].summary)
        assertEquals("Beyond forecast", card.nights[3].summary)
        assertEquals("○ 4%", card.nights[3].moonText)
        assertTrue(card.estimated)
        assertNull(card.saved)

        assertTrue(card.caption, card.caption.contains("Long Point"))
        assertTrue(card.caption, card.caption.contains("12–15 Aug"))
        assertTrue(card.caption, card.caption.contains("Best: Tue 13 Aug (91)"))
        assertTrue(card.caption, card.caption.contains("Tue 91"))
        assertTrue(card.caption, card.caption.contains("Estimated"))
        assertTrue(card.caption, card.caption.endsWith("NightBrief"))
        assertFalse(card.caption, card.caption.contains(longPoint.latitude.toString()))
        assertFalse(card.caption, card.caption.contains(longPoint.longitude.toString()))
        assertFalse(card.caption, card.caption.contains("°"))
    }

    @Test
    fun staleForecastAddsSavedLabelAndMissingForecastHasNoBestNight() {
        val nights = listOf(
            OutlookNight(aug12, null, 0.50, Darkness.ASTRONOMICAL, ForecastCoverage.NONE, forecastMissing = true),
            OutlookNight(aug12.plusDays(1), null, 0.60, Darkness.NONE, ForecastCoverage.NONE, forecastMissing = true),
        )
        val fetchedAt = Instant.parse("2024-08-11T12:00:00Z")
        val card = WeekPlanCard.from(
            WeeklyOutlook(longPoint, nights),
            forecastStatus = ForecastStatus.STALE,
            forecastFetchedAt = fetchedAt,
        )

        assertNull(card.bestDayLabel)
        assertNull(card.bestScore)
        assertEquals("—", card.bestScoreText)
        assertEquals("Couldn't load the forecast", card.bestVerdictLabel)
        assertEquals("No forecast", card.nights[0].summary)
        assertEquals("No darkness", card.nights[1].summary)
        assertEquals(SavedForecast.shortLabel(ForecastStatus.STALE, fetchedAt, longPoint.zone), card.saved)
        assertTrue(card.caption, card.caption.contains(card.saved!!))
        assertFalse(card.estimated)
    }
}
