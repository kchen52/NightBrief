package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ImagingModesTest {

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

    @Test
    fun clearMoonlessNightIsGoInBothModes() {
        val s = NightScoreEngine.scoreNight(night())
        assertTrue("primary was ${s.score}", s.score in 86..95)
        assertEquals(s.score, ImagingModes.score(s, ImagingMode.DEEP_SKY))
        val planetary = ImagingModes.score(s, ImagingMode.PLANETARY)
        assertTrue("planetary was $planetary", planetary in 86..100)
        assertTrue(ImagingModes.disagreeing(s).isEmpty())
    }

    @Test
    fun overcastNightStaysNoGoInBothModes() {
        val s = NightScoreEngine.scoreNight(night(cloud = { 100 }, transparency = 8, seeing = 5))
        assertTrue("primary was ${s.score}", s.score in 9..21)
        assertEquals(s.score, ImagingModes.score(s, ImagingMode.DEEP_SKY))
        val planetary = ImagingModes.score(s, ImagingMode.PLANETARY)
        assertTrue("planetary was $planetary", planetary < Band.MARGINAL.min)
        assertTrue(ImagingModes.disagreeing(s).isEmpty())
    }

    @Test
    fun fullMoonNightReadsBetterForPlanetsThanDeepSky() {
        val s = NightScoreEngine.scoreNight(night(moonAlt = 40.0, moonIllum = 1.0))
        assertTrue("primary was ${s.score}", s.score in 49..61)
        assertEquals(s.score, ImagingModes.score(s, ImagingMode.DEEP_SKY))
        val planetary = ImagingModes.score(s, ImagingMode.PLANETARY)
        assertTrue("planetary $planetary should beat primary ${s.score}", planetary >= s.score + 10)
        assertTrue(Band.of(planetary) != s.band)
        val disagreeing = ImagingModes.disagreeing(s)
        assertEquals(listOf(ImagingMode.PLANETARY), disagreeing.map { it.mode })
        assertEquals(planetary, disagreeing.single().score)
        assertFalse(disagreeing.any { it.mode == ImagingMode.DEEP_SKY })
    }

    @Test
    fun everyModeWeightsEveryFactorAndSumsToOneHundred() {
        ImagingMode.entries.forEach { mode ->
            assertEquals(Factor.entries.toSet(), mode.weights.keys)
            assertEquals(100, mode.weights.values.sum())
        }
        assertEquals(primaryWeights, ImagingMode.DEEP_SKY.weights)
    }

    private companion object {
        /** The published table from `NightScoreEngine`, restated so a drift is a test failure. */
        val primaryWeights = mapOf(
            Factor.CLOUD to 35,
            Factor.MOON to 25,
            Factor.TRANSPARENCY to 15,
            Factor.WIND to 10,
            Factor.SEEING to 10,
            Factor.LIGHT_POLLUTION to 5,
        )
    }
}
