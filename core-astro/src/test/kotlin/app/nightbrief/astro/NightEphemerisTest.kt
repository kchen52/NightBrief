package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Reference values generated with Skyfield 1.55 + DE421. */
class NightEphemerisTest {

    private fun assertNear(expected: String, actual: Instant?, toleranceMinutes: Long = 4) {
        assertNotNull("expected event near $expected", actual)
        val diff = abs(Duration.between(Instant.parse(expected), actual).toMinutes())
        assertTrue("expected $expected but was $actual (off by $diff min)", diff <= toleranceMinutes)
    }

    private val toronto = NightEphemeris.compute(
        LocalDate.of(2024, 6, 21), ZoneId.of("America/Toronto"), 43.6532, -79.3832,
    )

    @Test
    fun torontoSolsticeSunAndTwilight() {
        assertNear("2024-06-22T01:02:48Z", toronto.sunset)
        assertNear("2024-06-22T09:36:26Z", toronto.sunrise)
        assertNear("2024-06-22T02:26:00Z", toronto.nauticalDusk)
        assertNear("2024-06-22T03:25:39Z", toronto.astronomicalDusk)
        assertNear("2024-06-22T07:13:35Z", toronto.astronomicalDawn)
        assertEquals(Darkness.ASTRONOMICAL, toronto.darkness)
    }

    @Test
    fun torontoFullMoonRiseAndSet() {
        assertNear("2024-06-22T01:25:24Z", toronto.moonrise, toleranceMinutes = 6)
        assertNear("2024-06-22T09:35:34Z", toronto.moonset, toleranceMinutes = 6)
        assertTrue(toronto.moonIllumination > 0.97)
        assertEquals(MoonPhase.FULL, toronto.moonPhaseName)
        assertTrue("full moon all night leaves no moon-free darkness", toronto.moonFreeDark.isEmpty())
    }

    @Test
    fun torontoMilkyWayCoreIsLowButVisible() {
        val mw = toronto.milkyWay
        assertNotNull(mw)
        mw!!
        assertTrue(mw.peakAltitudeDeg in 15.0..18.0)
        assertTrue("peak should be roughly south", mw.peakAzimuthDeg in 160.0..200.0)
        assertEquals(Duration.ZERO, mw.moonFreeDuration)
    }

    @Test
    fun longPointMoonSetsBeforeMilkyWayWindowEnds() {
        val night = NightEphemeris.compute(
            LocalDate.of(2024, 8, 10), ZoneId.of("America/Toronto"), 42.58, -80.40,
        )
        assertNear("2024-08-11T02:19:18Z", night.astronomicalDusk)
        assertNear("2024-08-11T08:34:45Z", night.astronomicalDawn)
        assertNear("2024-08-11T03:03:51Z", night.moonset, toleranceMinutes = 6)
        val mw = night.milkyWay!!
        assertTrue(mw.moonFreeDuration > Duration.ZERO)
        assertTrue(night.moonFreeDarkDuration > Duration.ofHours(5))
    }

    @Test
    fun edmontonSolsticeHasNoAstronomicalDarkness() {
        val night = NightEphemeris.compute(
            LocalDate.of(2024, 6, 21), ZoneId.of("America/Edmonton"), 53.5461, -113.4938,
        )
        assertEquals(Darkness.NAUTICAL, night.darkness)
        assertNull(night.astronomicalDusk)
        assertNear("2024-06-22T06:37:54Z", night.nauticalDusk)
        assertNear("2024-06-22T08:34:16Z", night.nauticalDawn)
        assertNear("2024-06-22T04:07:21Z", night.sunset)
    }

    @Test
    fun sydneyMoonAndGalacticCentreAltitudes() {
        val t = Instant.parse("2024-04-23T13:30:00Z") // 23:30 AEST
        val moon = Ephemeris.moon(t, -33.8688, 151.2093)
        val gc = Ephemeris.galacticCenter(t, -33.8688, 151.2093)
        assertEquals(68.07, moon.altitudeDeg, 1.0)
        assertEquals(38.46, gc.altitudeDeg, 0.5)
        assertEquals(102.20, gc.azimuthDeg, 1.0)
    }

    @Test
    fun torontoSpotAltitudes() {
        val t = Instant.parse("2024-06-22T03:30:00Z") // 23:30 EDT
        assertEquals(12.34, Ephemeris.moonAltitude(t, 43.6532, -79.3832), 1.0)
        assertEquals(14.43, Ephemeris.galacticCenter(t, 43.6532, -79.3832).altitudeDeg, 0.5)
    }

    @Test
    fun moonPhases() {
        assertTrue(Ephemeris.moonIllumination(Instant.parse("2024-04-23T23:49:00Z")) > 0.995)
        assertTrue(Ephemeris.moonIllumination(Instant.parse("2024-04-08T18:21:00Z")) < 0.005)
        assertEquals(0.50, Ephemeris.moonIllumination(Instant.parse("2024-04-15T19:13:00Z")), 0.02)
        assertEquals(MoonPhase.FIRST_QUARTER, MoonPhase.fromPhase(Ephemeris.moonPhase(Instant.parse("2024-04-15T19:13:00Z"))))
        assertEquals(MoonPhase.NEW, MoonPhase.fromPhase(Ephemeris.moonPhase(Instant.parse("2024-04-08T18:21:00Z"))))
    }

    @Test
    fun terminatorAngleFacesOppositeWaysAtTheQuarters() {
        val first = Ephemeris.moonTerminatorAngleDeg(Instant.parse("2024-04-15T19:13:00Z"))
        val last = Ephemeris.moonTerminatorAngleDeg(Instant.parse("2024-05-01T11:27:00Z"))
        assertTrue("first quarter angle $first", first in 0.0..360.0)
        assertTrue("last quarter angle $last", last in 0.0..360.0)
        val gap = abs(first - last).let { if (it > 180) 360 - it else it }
        assertTrue("quarters should face opposite ways, gap was $gap°", gap > 90)
    }

    @Test
    fun aRaisedHorizonDelaysTheCoreAndAHigherOneHidesIt() {
        // Mid-May: the core rises during darkness. In high summer it is already up at dusk.
        val flat = NightEphemeris.compute(
            LocalDate.of(2024, 5, 15), ZoneId.of("America/Toronto"), 43.6532, -79.3832,
        )
        val flatWindow = flat.milkyWay
        assertNotNull(flatWindow)
        val peak = flatWindow!!.peakAltitudeDeg
        val mid = (NightEphemeris.DEFAULT_MILKY_WAY_MIN_ALTITUDE + peak) / 2.0
        val delayed = NightEphemeris.compute(
            flat.date, flat.zone, flat.latitudeDeg, flat.longitudeDeg,
            horizonObstructionDeg = { mid },
        )
        val delayedWindow = delayed.milkyWay
        assertNotNull("a mask between 10° and the peak should still leave a window", delayedWindow)
        delayedWindow!!
        assertTrue(
            "start ${delayedWindow.window.start} should be after ${flatWindow.window.start}",
            delayedWindow.window.start.isAfter(flatWindow.window.start.plus(Duration.ofMinutes(9))),
        )
        assertEquals(delayedWindow.window.start, delayedWindow.clearsHorizonAt)
        assertTrue(!delayed.milkyWayBlockedByHorizon)

        val hidden = NightEphemeris.compute(
            flat.date, flat.zone, flat.latitudeDeg, flat.longitudeDeg,
            horizonObstructionDeg = { peak + 2 },
        )
        assertNull(hidden.milkyWay)
        assertTrue(hidden.milkyWayBlockedByHorizon)
    }

    @Test
    fun londonNeverSeesTheCoreAboveTenDegrees() {
        val night = NightEphemeris.compute(
            LocalDate.of(2024, 8, 1), ZoneId.of("Europe/London"), 51.5074, -0.1278,
        )
        assertNull(night.milkyWay)
        assertTrue(!night.milkyWayBlockedByHorizon)
    }

    @Test
    fun hourlySamplesSpanSunsetToSunrise() {
        val first = toronto.hourly.first().time
        val last = toronto.hourly.last().time
        assertTrue(!first.isAfter(toronto.sunset))
        assertTrue(!last.isBefore(toronto.sunrise))
        assertTrue(toronto.hourly.zipWithNext().all { (a, b) -> Duration.between(a.time, b.time) == Duration.ofHours(1) })
    }

    @Test
    fun solsticeDeclination() {
        assertEquals(23.44, Ephemeris.sunDeclination(Instant.parse("2024-06-20T20:51:00Z")), 0.05)
    }
}
