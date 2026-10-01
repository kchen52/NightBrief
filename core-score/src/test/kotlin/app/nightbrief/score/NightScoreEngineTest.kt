package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NightScoreEngineTest {

    private fun night(
        hours: Int = 6,
        cloud: (Int) -> Int? = { 0 },
        moonAlt: Double = -20.0,
        moonIllum: Double = 0.0,
        transparency: Int? = 2,
        seeing: Int? = 3,
        wind: Double? = 8.0,
        bortle: Int = 4,
        sunAlt: Double = -25.0,
    ): List<HourInput> {
        val start = Instant.parse("2024-08-11T02:00:00Z")
        return (0 until hours).map { i ->
            HourInput(
                time = start.plus(Duration.ofHours(i.toLong())),
                cloudCover = cloud(i),
                windKmh = wind,
                gustKmh = wind?.times(1.3),
                seeingIndex = seeing,
                transparencyIndex = transparency,
                humidity = 60,
                jetStreamKmh = 90.0,
                sunAltitudeDeg = sunAlt,
                moonAltitudeDeg = moonAlt,
                moonIllumination = moonIllum,
                bortle = bortle,
            )
        }
    }

    private fun assertInRange(range: IntRange, actual: Int) =
        assertTrue("expected score in $range but was $actual", actual in range)

    @Test
    fun clearMoonlessNightScoresAround90() {
        val s = NightScoreEngine.scoreNight(night())
        assertInRange(86..95, s.score)
        assertEquals(Band.EXCELLENT, s.band)
        assertEquals(Verdict.GO, s.verdict)
    }

    @Test
    fun overcastNightScoresAround15() {
        val s = NightScoreEngine.scoreNight(night(cloud = { 100 }, transparency = 8, seeing = 5))
        assertInRange(9..21, s.score)
        assertEquals(Band.POOR, s.band)
        assertEquals(Verdict.NO_GO, s.verdict)
    }

    @Test
    fun fullMoonClearNightScoresAround55() {
        val s = NightScoreEngine.scoreNight(night(moonAlt = 40.0, moonIllum = 1.0))
        assertInRange(49..61, s.score)
        assertEquals(Verdict.MAYBE, s.verdict)
    }

    @Test
    fun factorPointsSumToScore() {
        val s = NightScoreEngine.scoreNight(night(cloud = { it * 15 }, moonAlt = 10.0, moonIllum = 0.4))
        assertEquals(s.score.toDouble(), s.factors.sumOf { it.points }, 0.51)
        s.factors.forEach { assertTrue(it.points <= it.maxPoints + 1e-9) }
    }

    @Test
    fun clearingAfterMidnightRewardsBestWindow() {
        val clearsLate = NightScoreEngine.scoreNight(night(hours = 8, cloud = { if (it < 4) 100 else 0 }))
        val allCloudy = NightScoreEngine.scoreNight(night(hours = 8, cloud = { 100 }))
        assertTrue(clearsLate.score > allCloudy.score + 25)
        assertEquals(Instant.parse("2024-08-11T06:00:00Z"), clearsLate.bestWindow!!.start)
        assertInRange(85..95, clearsLate.bestWindowScore)
    }

    @Test
    fun missingSevenTimerDataIsEstimated() {
        val s = NightScoreEngine.scoreNight(night(transparency = null, seeing = null))
        assertTrue(s.factor(Factor.SEEING).estimated)
        assertTrue(s.factor(Factor.TRANSPARENCY).estimated)
        assertInRange(75..92, s.score)
    }

    @Test
    fun twilightOnlyNightIsPenalised() {
        val astro = NightScoreEngine.scoreNight(night())
        val nautical = NightScoreEngine.scoreNight(night(sunAlt = -13.0))
        assertTrue(nautical.score < astro.score - 10)
    }

    @Test
    fun thinCrescentBarelyMatters() {
        val crescent = NightScoreEngine.scoreNight(night(moonAlt = 20.0, moonIllum = 0.08))
        val none = NightScoreEngine.scoreNight(night())
        assertTrue(none.score - crescent.score <= 2)
    }

    @Test
    fun windAndGusts() {
        assertEquals(1.0, NightScoreEngine.windQuality(5.0, 10.0).first, 1e-9)
        assertEquals(0.0, NightScoreEngine.windQuality(45.0, 60.0).first, 1e-9)
        assertTrue(NightScoreEngine.windQuality(10.0, 40.0).first < 1.0)
    }

    @Test
    fun noDarknessScoresZero() {
        assertEquals(0, NightScoreEngine.scoreNight(emptyList()).score)
    }

    @Test
    fun bands() {
        assertEquals(Band.EXCELLENT, Band.of(85))
        assertEquals(Band.GOOD, Band.of(84))
        assertEquals(Band.FAIR, Band.of(50))
        assertEquals(Band.MARGINAL, Band.of(49))
        assertEquals(Band.POOR, Band.of(0))
    }
}
