package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PassportTest {
    @Test
    fun noSessionsLeavesEveryStampLocked() {
        val stamps = Passport.evaluate(emptyList())
        assertEquals(
            listOf(
                "first-go",
                "dark-sky",
                "peak-hunter",
                "station-spotter",
                "frostbitten",
                "lunar-regular",
                "night-owl",
                "cloud-chaser",
                "site-hopper",
            ),
            stamps.map { it.id },
        )
        assertTrue("empty log should lock every stamp", stamps.all { it.earnedOn == null })
    }

    @Test
    fun firstGoNightNeedsAScoreOfAtLeast70() {
        assertEquals(date("2026-01-02"), earned("first-go", entry("2026-01-03", score = 69), entry("2026-01-02", score = 70)))
        assertEquals(date("2026-02-01"), earned("first-go", entry("2026-02-01", score = 100)))
        assertNull(earned("first-go", entry("2026-01-02", score = 69)))
        assertNull(earned("first-go", entry("2026-01-02", score = null, rating = 5)))
    }

    @Test
    fun darkSkyPilgrimNeedsBortleOneToThree() {
        assertEquals(date("2026-03-01"), earned("dark-sky", entry("2026-03-02", bortle = 4), entry("2026-03-01", bortle = 3)))
        assertEquals(date("2026-03-04"), earned("dark-sky", entry("2026-03-04", bortle = 1)))
        assertEquals(date("2026-03-05"), earned("dark-sky", entry("2026-03-05", bortle = 2)))
        assertNull(earned("dark-sky", entry("2026-03-01", bortle = 4)))
        assertNull(earned("dark-sky", entry("2026-03-01", bortle = 9)))
        assertNull(earned("dark-sky", entry("2026-03-01", bortle = null)))
    }

    @Test
    fun peakHunterIncludesTheDayEitherSideOfAShowerPeak() {
        assertEquals(date("2026-08-11"), earned("peak-hunter", entry("2026-08-10"), entry("2026-08-12"), entry("2026-08-11")))
        assertEquals(date("2026-08-13"), earned("peak-hunter", entry("2026-08-13")))
        assertEquals(date("2026-01-02"), earned("peak-hunter", entry("2026-01-04"), entry("2026-01-02")))
        assertEquals(date("2026-01-03"), earned("peak-hunter", entry("2026-01-03")))
        assertNull(earned("peak-hunter", entry("2026-08-10")))
        assertNull(earned("peak-hunter", entry("2026-01-01")))
        assertNull(earned("peak-hunter", entry("2025-12-30")))
        assertNull(earned("peak-hunter", entry("2026-03-01")))
        assertTrue("Perseid peak", Passport.onShowerPeak(date("2026-08-12")))
        assertFalse("two nights before the Perseids", Passport.onShowerPeak(date("2026-08-10")))
    }

    @Test
    fun stationSpotterNeedsAVisibleIssPass() {
        assertEquals(date("2026-04-02"), earned("station-spotter", entry("2026-04-04", issVisible = false), entry("2026-04-02", issVisible = true)))
        assertNull(earned("station-spotter", entry("2026-04-02", issVisible = false)))
        assertNull(earned("station-spotter", entry("2026-04-02", issVisible = null)))
    }

    @Test
    fun frostbittenNeedsAnOvernightLowBelowFreezing() {
        assertEquals(date("2026-01-15"), earned("frostbitten", entry("2026-01-20", overnightLowC = 0.0), entry("2026-01-15", overnightLowC = -0.1)))
        assertEquals(date("2026-02-01"), earned("frostbitten", entry("2026-02-01", overnightLowC = -12.0)))
        assertNull(earned("frostbitten", entry("2026-01-15", overnightLowC = 0.0)))
        assertNull(earned("frostbitten", entry("2026-01-15", overnightLowC = 3.5)))
        assertNull(earned("frostbitten", entry("2026-01-15", overnightLowC = null)))
    }

    @Test
    fun lunarRegularNeedsFiveSessionsInsideOneSynodicMonth() {
        val start = date("2026-01-01")
        assertEquals(
            start.plusDays(29),
            earned("lunar-regular", *offsets(start, 0, 7, 14, 21, 29)),
        )
        assertNull(earned("lunar-regular", *offsets(start, 0, 7, 14, 21, 30)))
        assertNull(earned("lunar-regular", *offsets(start, 0, 1, 2, 3)))
        assertNull(earned("lunar-regular", *offsets(start, 0, 8, 16, 24, 32)))
        assertEquals(start, earned("lunar-regular", *List(5) { entry("2026-01-01", siteId = "s$it") }.toTypedArray()))
    }

    @Test
    fun lunarRegularEarnsOnTheNightTheWindowReachesFive() {
        val start = date("2026-01-01")
        assertEquals(start.plusDays(4), earned("lunar-regular", *offsets(start, 0, 1, 2, 3, 4, 40)))
        val later = date("2026-02-14")
        val cluster = listOf(entry("2026-01-01")) + offsets(later.minusDays(4), 0, 1, 2, 3, 4).toList()
        assertEquals(later, earned("lunar-regular", *cluster.toTypedArray()))
    }

    @Test
    fun nightOwlNeedsTenSessions() {
        val nine = (1..9).map { entry("2026-01-${it.toString().padStart(2, '0')}") }
        assertNull(earned("night-owl", *nine.toTypedArray()))
        val tenth = entry("2026-01-20", createdAtEpochSec = 0)
        val eleventh = entry("2026-01-21", createdAtEpochSec = 50)
        assertEquals(date("2026-01-20"), earned("night-owl", *(nine + eleventh + tenth).toTypedArray()))
    }

    @Test
    fun cloudChaserNeedsFourStarsOnANoGoNight() {
        assertEquals(date("2026-05-02"), earned("cloud-chaser", entry("2026-05-04", score = 49, rating = 3), entry("2026-05-02", score = 49, rating = 4)))
        assertEquals(date("2026-05-08"), earned("cloud-chaser", entry("2026-05-08", score = 0, rating = 5)))
        assertNull(earned("cloud-chaser", entry("2026-05-02", score = 49, rating = 3)))
        assertNull(earned("cloud-chaser", entry("2026-05-02", score = 50, rating = 5)))
        assertNull(earned("cloud-chaser", entry("2026-05-02", score = 80, rating = 5)))
        assertNull(earned("cloud-chaser", entry("2026-05-02", score = null, rating = 5)))
    }

    @Test
    fun siteHopperNeedsThreeDifferentSites() {
        assertNull(
            earned(
                "site-hopper",
                entry("2026-06-01", siteId = "a"),
                entry("2026-06-02", siteId = "a"),
                entry("2026-06-03", siteId = "b"),
            ),
        )
        assertEquals(
            date("2026-06-05"),
            earned(
                "site-hopper",
                entry("2026-06-05", siteId = "a"),
                entry("2026-06-06", siteId = "a"),
                entry("2026-06-01", siteId = "b"),
                entry("2026-06-04", siteId = "c"),
            ),
        )
    }

    private fun earned(id: String, vararg sessions: SessionEntry): LocalDate? =
        Passport.evaluate(sessions.toList()).first { it.id == id }.earnedOn

    private fun entry(
        night: String,
        siteId: String = "home",
        score: Int? = null,
        rating: Int = 3,
        bortle: Int? = null,
        issVisible: Boolean? = null,
        overnightLowC: Double? = null,
        createdAtEpochSec: Long = 0,
    ) = SessionEntry(
        siteId = siteId,
        nightDate = night,
        score = score,
        rating = rating,
        bortle = bortle,
        issVisible = issVisible,
        overnightLowC = overnightLowC,
        createdAtEpochSec = createdAtEpochSec,
    )

    private fun date(iso: String): LocalDate = LocalDate.parse(iso)

    private fun offsets(start: LocalDate, vararg days: Int): Array<SessionEntry> =
        days.map { day -> entry(start.plusDays(day.toLong()).toString(), siteId = "s$day") }.toTypedArray()
}
