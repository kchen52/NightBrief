package app.nightbrief.score

import app.nightbrief.sites.Site
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSample
import app.nightbrief.weather.KpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AuroraAlertsTest {
    private val yellowknife = Site("yk", "Yellowknife", 62.45, -114.35, "America/Yellowknife", bortle = 2)
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 6)
    private val bin = Instant.parse("2026-11-12T03:00:00Z")

    @Test
    fun aStormAtOrAboveTheThresholdAlertsAHighLatitudeSite() {
        val alerts = AuroraAlerts.select(
            sites = listOf(yellowknife),
            forecast = series(bin, 5.0),
            threshold = 5,
            alreadyAlerted = emptyMap(),
            now = bin.plusSeconds(40 * 60),
        )
        val alert = alerts.single()
        assertEquals("yk", alert.siteId)
        assertEquals(5.0, alert.kp, 0.001)
        assertEquals(AuroraChance.LIKELY, alert.chance)
        assertEquals("the dedupe key is the bin start, got ${alert.binEpochSecond}", bin.epochSecond, alert.binEpochSecond)
    }

    @Test
    fun belowTheThresholdIsQuiet() {
        val alerts = AuroraAlerts.select(
            sites = listOf(toronto),
            forecast = series(bin, 5.9),
            threshold = 6,
            alreadyAlerted = emptyMap(),
            now = bin.plusSeconds(60),
        )
        assertTrue("Kp 5.9 is below a Kp 6 threshold, got $alerts", alerts.isEmpty())
    }

    @Test
    fun aLowLatitudeSiteStaysQuietWhenTheViewLineStaysPoleward() {
        val alerts = AuroraAlerts.select(
            sites = listOf(toronto),
            forecast = series(bin, 4.0),
            threshold = 3,
            alreadyAlerted = emptyMap(),
            now = bin.plusSeconds(60),
        )
        assertTrue("Kp 4 reaches about 58°, so Toronto is out of range, got $alerts", alerts.isEmpty())
    }

    @Test
    fun aStormReachingMidLatitudesAlertsThereToo() {
        val alerts = AuroraAlerts.select(
            sites = listOf(toronto),
            // Kp 8 puts the view line at 42°, just equatorward of Toronto at 43.65°.
            forecast = series(bin, 8.0),
            threshold = 5,
            alreadyAlerted = emptyMap(),
            now = bin.plusSeconds(60),
        )
        assertEquals(AuroraChance.POSSIBLE, alerts.single().chance)
    }

    @Test
    fun theSameBinIsNotAnnouncedTwiceButTheNextBinIs() {
        val fired = AuroraAlerts.select(
            sites = listOf(yellowknife),
            forecast = series(bin, 6.0),
            threshold = 5,
            alreadyAlerted = emptyMap(),
            now = bin.plusSeconds(90 * 60),
        )
        val dedupe = fired.associate { it.siteId to it.binEpochSecond }
        val again = AuroraAlerts.select(
            sites = listOf(yellowknife),
            forecast = series(bin, 6.5),
            threshold = 5,
            alreadyAlerted = dedupe,
            now = bin.plusSeconds(2 * 3600),
        )
        assertTrue("a revised Kp inside the same bin must not post again, got $again", again.isEmpty())

        val nextBin = bin.plusSeconds(KpForecast.BIN_SECONDS)
        val later = AuroraAlerts.select(
            sites = listOf(yellowknife),
            forecast = series(nextBin, 6.5),
            threshold = 5,
            alreadyAlerted = dedupe,
            now = nextBin.plusSeconds(60),
        )
        assertEquals("a new bin is a new alert", 1, later.size)
    }

    @Test
    fun aStaleSeriesNamesNothing() {
        val stale = bin.plusSeconds(KpForecast.BIN_SECONDS + 60)
        val alerts = AuroraAlerts.select(
            sites = listOf(yellowknife),
            forecast = series(bin, 8.0),
            threshold = 5,
            alreadyAlerted = emptyMap(),
            now = stale,
        )
        assertTrue("the newest row is more than one bin old, got $alerts", alerts.isEmpty())
    }

    @Test
    fun currentBinPicksTheRowThatContainsNow() {
        val forecast = KpForecast(
            listOf(
                KpSample(bin.minusSeconds(KpForecast.BIN_SECONDS).epochSecond, 2.0, KpStatus.OBSERVED),
                KpSample(bin.epochSecond, 6.0, KpStatus.OBSERVED),
                // A forecast product's future row must not win the nowcast.
                KpSample(bin.plusSeconds(KpForecast.BIN_SECONDS).epochSecond, 9.0, KpStatus.OBSERVED),
            ),
        )
        assertEquals(6.0, AuroraAlerts.currentBin(forecast, bin.plusSeconds(60))!!.kp, 0.001)
        assertEquals(
            "an empty series has no current bin",
            null,
            AuroraAlerts.currentBin(KpForecast(emptyList()), bin),
        )
    }

    @Test
    fun thresholdIsClampedToTheSlider() {
        assertEquals(AuroraAlerts.MIN_THRESHOLD, AuroraAlerts.clamp(0))
        assertEquals(AuroraAlerts.MAX_THRESHOLD, AuroraAlerts.clamp(12))
        assertEquals(6, AuroraAlerts.clamp(6))
    }

    @Test
    fun theAlertLineNamesTheObservedKpAndThePolewardHorizon() {
        assertEquals("Observed Kp 6.3 — look north now", AuroraCopy.alertLine(6.33, 62.45))
        assertEquals("Observed Kp 4 — look south now", AuroraCopy.alertLine(4.0, -54.8))
    }

    private fun series(at: Instant, kp: Double) =
        KpForecast(listOf(KpSample(at.epochSecond, kp, KpStatus.OBSERVED)))
}
