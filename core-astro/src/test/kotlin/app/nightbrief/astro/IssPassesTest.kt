package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * ISS (ZARYA) from Celestrak, epoch 2024 day 74 (2024-03-14).
 * Retrieved from the Internet Archive copy of
 * `https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE`.
 *
 * The six-hour window is frozen on a night that contains passes over Toronto.
 */
class IssPassesTest {
    @Test
    fun torontoWindowReturnsPassesInsideTheWindowAboveTenDegrees() {
        val passes = IssPasses.during(LINE1, LINE2, START, END, LAT, LON)
        assertTrue(passes.isNotEmpty())
        assertEquals(4, passes.size)
        for (pass in passes) {
            assertTrue("peak ${pass.peak} is outside the window", !pass.peak.isBefore(START) && !pass.peak.isAfter(END))
            assertTrue("peak altitude ${pass.peakAltitudeDeg} is below 10", pass.peakAltitudeDeg >= 10.0)
            assertTrue(pass.peakAzimuthDeg in 0.0..360.0)
        }
        val first = passes.first()
        assertTrue("expected the overhead pass, altitude was ${first.peakAltitudeDeg}", first.peakAltitudeDeg > 60.0)
        assertTrue(
            "peak ${first.peak} was far from 2024-03-15T00:57Z",
            abs(Duration.between(Instant.parse("2024-03-15T00:57:30Z"), first.peak).seconds) < 120,
        )
        assertNotNull(first.rise)
        assertNotNull(first.set)
        assertTrue(first.rise!!.isBefore(first.peak))
        assertTrue(first.set!!.isAfter(first.peak))
    }

    @Test
    fun higherAltitudeThresholdIsASubset() {
        val loose = IssPasses.during(LINE1, LINE2, START, END, LAT, LON, minPeakAltitudeDeg = 10.0)
        val strict = IssPasses.during(LINE1, LINE2, START, END, LAT, LON, minPeakAltitudeDeg = 90.0)
        assertTrue("this window has a pass below 90°", loose.any { it.peakAltitudeDeg < 90.0 })
        assertTrue(strict.all { it.peakAltitudeDeg >= 90.0 })
        assertTrue(strict.size < loose.size)
        assertTrue(strict.all { high -> loose.any { it.peak == high.peak } })
    }

    @Test
    fun reportedPeakIsAtLeastAsHighAsTwoMinutesEitherSide() {
        val pass = IssPasses.during(LINE1, LINE2, START, END, LAT, LON).first()
        val before = issAltAz(LINE1, LINE2, pass.peak.minusSeconds(120), LAT, LON).altitudeDeg
        val after = issAltAz(LINE1, LINE2, pass.peak.plusSeconds(120), LAT, LON).altitudeDeg
        val atPeak = issAltAz(LINE1, LINE2, pass.peak, LAT, LON).altitudeDeg
        assertEquals(pass.peakAltitudeDeg, atPeak, 1e-6)
        assertTrue(
            "peak ${pass.peakAltitudeDeg}° is below the altitude 2 min earlier ($before) or later ($after) by more than 0.5°",
            pass.peakAltitudeDeg + 0.5 >= before && pass.peakAltitudeDeg + 0.5 >= after,
        )
    }

    companion object {
        private const val LINE1 = "1 25544U 98067A   24074.41191032  .00014407  00000+0  26068-3 0  9998"
        private const val LINE2 = "2 25544  51.6400  65.1219 0006168   5.0268 148.9614 15.49907257443850"
        private val START: Instant = Instant.parse("2024-03-15T00:00:00Z")
        private val END: Instant = Instant.parse("2024-03-15T06:00:00Z")
        private const val LAT = 43.65
        private const val LON = -79.38
    }
}
