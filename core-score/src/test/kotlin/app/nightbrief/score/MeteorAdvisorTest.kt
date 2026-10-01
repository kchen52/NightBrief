package app.nightbrief.score

import app.nightbrief.astro.MeteorShower
import app.nightbrief.astro.MeteorShowers
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.RaDec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.round

class MeteorAdvisorTest {
    private val zone: ZoneId = ZoneId.of("America/Toronto")
    private val latitude = 43.65
    private val longitude = -79.38

    @Test
    fun perseidsOnThePeakEveningAtToronto() {
        val date = LocalDate.of(2026, 8, 12)
        val eph = NightEphemeris.compute(date, zone, latitude, longitude)
        val outlook = MeteorAdvisor.forNight(eph)
        assertNotNull(outlook)
        outlook!!
        assertEquals("perseids", outlook.shower.id)
        // The night is keyed by the evening date, which is the Perseids peak day.
        assertEquals(0, outlook.daysFromPeak)
        assertEquals(LocalDate.of(2026, 8, 12), outlook.peakDate)
        assertEquals(100, outlook.expectedZhr)
        assertTrue(
            "Perseid radiant should be well up at 44°N, was ${outlook.peakRadiantAltitudeDeg}",
            outlook.peakRadiantAltitudeDeg > 20.0,
        )
        assertEquals(outlook.peakRadiantAltitudeDeg >= 20.0 && outlook.expectedZhr >= 10, outlook.worthWatching)
        assertTrue(outlook.worthWatching)
        assertEquals(expectedInterference(outlook), outlook.interference)
        assertTrue(outlook.moonRadiantSeparationDeg in 0.0..180.0)
    }

    @Test
    fun marchHasNoActiveShower() {
        val eph = NightEphemeris.compute(LocalDate.of(2026, 3, 15), zone, latitude, longitude)
        assertNull(MeteorAdvisor.forNight(eph))
    }

    @Test
    fun quadrantidsOnPeakNightAtToronto() {
        val eph = NightEphemeris.compute(LocalDate.of(2026, 1, 3), zone, latitude, longitude)
        val outlook = MeteorAdvisor.forNight(eph)
        assertNotNull(outlook)
        val quadrantids = MeteorShowers.annual.first { it.name == "Quadrantids" }
        assertEquals(quadrantids.id, outlook!!.shower.id)
        assertEquals(0, outlook.daysFromPeak)
    }

    @Test
    fun lyridsTwoDaysAfterPeakUseTheNarrowGaussian() {
        val date = LocalDate.of(2026, 4, 24)
        val outlook = MeteorAdvisor.forNight(NightEphemeris.compute(date, zone, latitude, longitude))
        assertNotNull(outlook)
        outlook!!
        assertEquals("lyrids", outlook.shower.id)
        assertEquals(2, outlook.daysFromPeak)
        // sigma = 2 days: round(18 * exp(-0.5 * (2/2)^2)) = 11
        assertEquals(11, outlook.expectedZhr)
        assertEquals(gaussianZhr(18, days = 2, sigma = 2.0), outlook.expectedZhr)
    }

    @Test
    fun etaAquariidsFourDaysAfterPeakUseTheWideGaussian() {
        val date = LocalDate.of(2026, 5, 10)
        val outlook = MeteorAdvisor.forNight(NightEphemeris.compute(date, zone, latitude, longitude))
        assertNotNull(outlook)
        outlook!!
        assertEquals("eta-aquariids", outlook.shower.id)
        assertEquals(4, outlook.daysFromPeak)
        // sigma = 4 days: round(50 * exp(-0.5)) = 30
        assertEquals(30, outlook.expectedZhr)
    }

    @Test
    fun missingDarkWindowStillReturnsTheActiveShower() {
        val eph = NightEphemeris.compute(LocalDate.of(2026, 8, 12), zone, latitude, longitude)
        val fromHours = MeteorAdvisor.forNight(eph.copy(darkWindow = null))
        assertNotNull(fromHours)
        assertEquals("perseids", fromHours!!.shower.id)
        assertTrue(fromHours.peakRadiantAltitudeDeg > 0.0)

        val fromMidnight = MeteorAdvisor.forNight(eph.copy(darkWindow = null, hourly = emptyList()))
        assertNotNull(fromMidnight)
        assertEquals("perseids", fromMidnight!!.shower.id)
    }

    @Test
    fun digestLineNamesTonightAndTheMoon() {
        val tonight = sampleOutlook(daysFromPeak = 0, interference = MeteorInterference.NONE)
        val line = MeteorAdvisor.digestLine(tonight)
        assertTrue(line.contains("tonight"))
        assertTrue(line.contains("ZHR"))
        assertFalse(line.contains("\n"))
        assertFalse(line.contains("Moon"))

        val strong = MeteorAdvisor.digestLine(sampleOutlook(daysFromPeak = 0, interference = MeteorInterference.STRONG))
        assertTrue(strong.contains("bright Moon"))
        assertFalse(strong.contains("\n"))

        val moderate = MeteorAdvisor.digestLine(sampleOutlook(daysFromPeak = 2, interference = MeteorInterference.MODERATE))
        assertTrue(moderate.contains("Moon up"))
        assertTrue(moderate.contains("peaked 2 days ago"))
        assertFalse(moderate.contains("\n"))

        val ahead = MeteorAdvisor.digestLine(sampleOutlook(daysFromPeak = -2, interference = MeteorInterference.NONE))
        assertTrue(ahead.contains("in 2 days"))
        val yesterday = MeteorAdvisor.digestLine(sampleOutlook(daysFromPeak = 1, interference = MeteorInterference.NONE))
        assertTrue(yesterday.contains("peaked yesterday"))
    }

    @Test
    fun overlappingShowersKeepTheHigherExpectedRate() {
        val date = LocalDate.of(2026, 8, 12)
        assertTrue(MeteorShowers.activeOn(date).any { it.id == "southern-delta-aquariids" })
        val outlook = MeteorAdvisor.forNight(NightEphemeris.compute(date, zone, latitude, longitude))
        assertEquals("perseids", outlook!!.shower.id)
        assertTrue(outlook.expectedZhr > 25)
    }

    @Test
    fun quadrantidsAtTheEndOfTheWindowFloorTheRateAndAreNotWorthWatching() {
        val outlook = MeteorAdvisor.forNight(
            NightEphemeris.compute(LocalDate.of(2026, 1, 12), zone, latitude, longitude),
        )
        assertNotNull(outlook)
        outlook!!
        assertEquals("quadrantids", outlook.shower.id)
        assertEquals(9, outlook.daysFromPeak)
        assertEquals(1, outlook.expectedZhr)
        assertFalse(outlook.worthWatching)
    }

    @Test
    fun ursidsFromTheFarSouthNeverClearTheHorizon() {
        val eph = NightEphemeris.compute(LocalDate.of(2026, 12, 22), zone, latitudeDeg = -54.8, longitudeDeg = -68.3)
        val outlook = MeteorAdvisor.forNight(eph)
        assertEquals("ursids", outlook!!.shower.id)
        assertTrue("radiant was ${outlook.peakRadiantAltitudeDeg}", outlook.peakRadiantAltitudeDeg < 20.0)
        assertFalse(outlook.worthWatching)
    }

    @Test
    fun moonlightClassificationFollowsIlluminationSeparationAndWhetherTheMoonIsUp() {
        assertEquals(MeteorInterference.NONE, MeteorAdvisor.classifyInterference(0.95, moonUp = false, separationDeg = 10.0))
        assertEquals(MeteorInterference.NONE, MeteorAdvisor.classifyInterference(0.3, moonUp = true, separationDeg = 10.0))
        assertEquals(MeteorInterference.MODERATE, MeteorAdvisor.classifyInterference(0.4, moonUp = true, separationDeg = 10.0))
        assertEquals(MeteorInterference.MODERATE, MeteorAdvisor.classifyInterference(0.9, moonUp = true, separationDeg = 60.0))
        assertEquals(MeteorInterference.STRONG, MeteorAdvisor.classifyInterference(0.7, moonUp = true, separationDeg = 59.9))
    }

    @Test
    fun detailMentionsMoonlightOnlyWhenItInterferes() {
        val dark = MeteorAdvisor.detail(sampleOutlook(daysFromPeak = 0, interference = MeteorInterference.NONE))
        assertFalse(dark.contains("Moon"))
        val bright = MeteorAdvisor.detail(sampleOutlook(daysFromPeak = 0, interference = MeteorInterference.STRONG))
        assertTrue(bright.contains("Moon"))
        val up = MeteorAdvisor.detail(sampleOutlook(daysFromPeak = 0, interference = MeteorInterference.MODERATE))
        assertTrue(up.contains("Moon"))
        assertTrue(MeteorAdvisor.detail(sampleOutlook(daysFromPeak = -1, interference = MeteorInterference.NONE)).contains("tomorrow"))
        assertTrue(MeteorAdvisor.detail(sampleOutlook(daysFromPeak = -3, interference = MeteorInterference.NONE)).contains("in 3 days"))
        assertTrue(MeteorAdvisor.detail(sampleOutlook(daysFromPeak = 4, interference = MeteorInterference.NONE)).contains("peaked 4 days ago"))
    }

    private fun expectedInterference(outlook: MeteorOutlook): MeteorInterference = when {
        outlook.moonIllumination >= 0.7 && outlook.moonUpAtRadiantPeak && outlook.moonRadiantSeparationDeg < 60.0 ->
            MeteorInterference.STRONG
        outlook.moonIllumination >= 0.4 && outlook.moonUpAtRadiantPeak -> MeteorInterference.MODERATE
        else -> MeteorInterference.NONE
    }

    private fun gaussianZhr(peakZhr: Int, days: Int, sigma: Double): Int {
        val x = days / sigma
        return maxOf(1, round(peakZhr * exp(-0.5 * x * x)).toInt())
    }

    private fun sampleOutlook(daysFromPeak: Int, interference: MeteorInterference): MeteorOutlook {
        val shower = MeteorShower(
            id = "sample",
            name = "Perseids",
            peakMonth = 8,
            peakDay = 12,
            peakZhr = 100,
            radiant = RaDec(48.0, 58.0),
            activeStartMonth = 7,
            activeStartDay = 17,
            activeEndMonth = 8,
            activeEndDay = 24,
        )
        return MeteorOutlook(
            shower = shower,
            peakDate = LocalDate.of(2026, 8, 12),
            daysFromPeak = daysFromPeak,
            expectedZhr = 40,
            peakRadiantAltitudeDeg = 62.0,
            peakRadiantTime = Instant.parse("2026-08-13T06:00:00Z"),
            peakRadiantAzimuthDeg = 40.0,
            moonIllumination = 0.9,
            moonUpAtRadiantPeak = interference != MeteorInterference.NONE,
            moonRadiantSeparationDeg = if (interference == MeteorInterference.STRONG) 30.0 else 90.0,
            interference = interference,
        )
    }
}
