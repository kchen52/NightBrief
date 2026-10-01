package app.nightbrief.astro

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A recurring annual meteor shower. Dates are month/day in the civil calendar, not a specific year.
 * Radiant is J2000. ZHR and the active window are IMO-typical planning values.
 */
data class MeteorShower(
    val id: String,
    val name: String,
    /** Recurring peak, month 1..12 and day-of-month. Not year-specific. */
    val peakMonth: Int,
    val peakDay: Int,
    val peakZhr: Int,
    val radiant: RaDec,
    /** Inclusive active window, month/day. May wrap the new year (Quadrantids). */
    val activeStartMonth: Int,
    val activeStartDay: Int,
    val activeEndMonth: Int,
    val activeEndDay: Int,
)

object MeteorShowers {
    /** Major annual showers in calendar order of peak. */
    val annual: List<MeteorShower> = listOf(
        MeteorShower(
            id = "quadrantids",
            name = "Quadrantids",
            peakMonth = 1,
            peakDay = 3,
            peakZhr = 80,
            radiant = RaDec(230.0, 49.0),
            activeStartMonth = 12,
            activeStartDay = 28,
            activeEndMonth = 1,
            activeEndDay = 12,
        ),
        MeteorShower(
            id = "lyrids",
            name = "Lyrids",
            peakMonth = 4,
            peakDay = 22,
            peakZhr = 18,
            radiant = RaDec(272.0, 34.0),
            activeStartMonth = 4,
            activeStartDay = 14,
            activeEndMonth = 4,
            activeEndDay = 30,
        ),
        MeteorShower(
            id = "eta-aquariids",
            name = "Eta Aquariids",
            peakMonth = 5,
            peakDay = 6,
            peakZhr = 50,
            radiant = RaDec(338.0, -1.0),
            activeStartMonth = 4,
            activeStartDay = 19,
            activeEndMonth = 5,
            activeEndDay = 28,
        ),
        MeteorShower(
            id = "southern-delta-aquariids",
            name = "Southern Delta Aquariids",
            peakMonth = 7,
            peakDay = 30,
            peakZhr = 25,
            radiant = RaDec(340.0, -16.0),
            activeStartMonth = 7,
            activeStartDay = 12,
            activeEndMonth = 8,
            activeEndDay = 23,
        ),
        MeteorShower(
            id = "perseids",
            name = "Perseids",
            peakMonth = 8,
            peakDay = 12,
            peakZhr = 100,
            radiant = RaDec(48.0, 58.0),
            activeStartMonth = 7,
            activeStartDay = 17,
            activeEndMonth = 8,
            activeEndDay = 24,
        ),
        MeteorShower(
            id = "orionids",
            name = "Orionids",
            peakMonth = 10,
            peakDay = 21,
            peakZhr = 20,
            radiant = RaDec(95.0, 16.0),
            activeStartMonth = 10,
            activeStartDay = 2,
            activeEndMonth = 11,
            activeEndDay = 7,
        ),
        MeteorShower(
            id = "leonids",
            name = "Leonids",
            peakMonth = 11,
            peakDay = 17,
            peakZhr = 15,
            radiant = RaDec(152.0, 22.0),
            activeStartMonth = 11,
            activeStartDay = 6,
            activeEndMonth = 11,
            activeEndDay = 30,
        ),
        MeteorShower(
            id = "geminids",
            name = "Geminids",
            peakMonth = 12,
            peakDay = 14,
            peakZhr = 150,
            radiant = RaDec(113.0, 33.0),
            activeStartMonth = 12,
            activeStartDay = 4,
            activeEndMonth = 12,
            activeEndDay = 17,
        ),
        MeteorShower(
            id = "ursids",
            name = "Ursids",
            peakMonth = 12,
            peakDay = 22,
            peakZhr = 10,
            radiant = RaDec(217.0, 76.0),
            activeStartMonth = 12,
            activeStartDay = 17,
            activeEndMonth = 12,
            activeEndDay = 26,
        ),
    )

    /** Showers whose inclusive active window contains [date], in peak-calendar order. */
    fun activeOn(date: LocalDate): List<MeteorShower> = annual.filter { contains(it, date) }

    /** Peak date in [year]. */
    fun peakDate(shower: MeteorShower, year: Int): LocalDate =
        LocalDate.of(year, shower.peakMonth, shower.peakDay)

    /**
     * Whole days from the peak of the active season to [date].
     * Negative when the peak is still ahead (Quadrantids on Dec 30); positive after it (Jan 4).
     */
    fun daysFromPeak(shower: MeteorShower, date: LocalDate): Int =
        ChronoUnit.DAYS.between(seasonPeak(shower, date), date).toInt()

    private fun contains(shower: MeteorShower, date: LocalDate): Boolean {
        val day = monthDay(date.monthValue, date.dayOfMonth)
        val start = monthDay(shower.activeStartMonth, shower.activeStartDay)
        val end = monthDay(shower.activeEndMonth, shower.activeEndDay)
        return if (start <= end) day in start..end else day >= start || day <= end
    }

    /**
     * Peak belonging to the active season that contains [date].
     * A window that wraps New Year (Quadrantids) peaks in the January year:
     * a December date looks ahead to next year, a January date uses this year.
     */
    private fun seasonPeak(shower: MeteorShower, date: LocalDate): LocalDate {
        if (!wrapsYear(shower)) return peakDate(shower, date.year)
        val day = monthDay(date.monthValue, date.dayOfMonth)
        val end = monthDay(shower.activeEndMonth, shower.activeEndDay)
        val peak = monthDay(shower.peakMonth, shower.peakDay)
        return if (day <= end) {
            if (peak <= end) peakDate(shower, date.year) else peakDate(shower, date.year - 1)
        } else {
            if (peak <= end) peakDate(shower, date.year + 1) else peakDate(shower, date.year)
        }
    }

    private fun wrapsYear(shower: MeteorShower): Boolean =
        monthDay(shower.activeStartMonth, shower.activeStartDay) >
            monthDay(shower.activeEndMonth, shower.activeEndDay)

    private fun monthDay(month: Int, day: Int): Int = month * 100 + day
}
