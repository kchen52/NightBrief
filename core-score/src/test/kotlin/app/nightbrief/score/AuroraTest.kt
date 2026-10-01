package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSample
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.KpStatus
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AuroraTest {
    private val kit = GearCatalog.exampleKit
    private val date = LocalDate.of(2024, 8, 10)
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 6)
    private val yellowknife = Site("yk", "Yellowknife", 62.45, -114.35, "America/Yellowknife", bortle = 2)
    private val reykjavik = Site("rey", "Reykjavik", 64.15, -21.94, "Atlantic/Reykjavik", bortle = 4)
    private val highBorder = Site("hi", "Border", 55.0, -114.0, "America/Edmonton", bortle = 3)

    @Test
    fun viewLineMatchesThePlanningTable() {
        assertEquals(70.0, Aurora.viewLineDeg(0.0), 0.001)
        assertEquals(62.0, Aurora.viewLineDeg(3.0), 0.001)
        assertEquals(54.0, Aurora.viewLineDeg(5.0), 0.001)
        assertEquals(46.0, Aurora.viewLineDeg(7.0), 0.001)
        assertEquals(38.0, Aurora.viewLineDeg(9.0), 0.001)
    }

    @Test
    fun formatKpRoundsToTenthsAndDropsAZeroFraction() {
        assertEquals("5", AuroraCopy.formatKp(5.0))
        assertEquals("4.3", AuroraCopy.formatKp(4.33))
        assertEquals("3.7", AuroraCopy.formatKp(3.67))
        assertEquals("2.3", AuroraCopy.formatKp(2.33))
        assertEquals("0.3", AuroraCopy.formatKp(0.33))
    }

    @Test
    fun highLatitudeUsesGeographicLatitudeNotTheGemBox() {
        // Toronto is inside the GEM weather box and is not high-latitude.
        val quietToronto = outlook(toronto, kp = 4.0)
        assertFalse(quietToronto.highLatitude)
        assertEquals(AuroraChance.UNLIKELY, quietToronto.chance)
        assertFalse(quietToronto.prominent)

        // Reykjavik is outside the GEM box (longitude −21.9) and is geographically high-latitude.
        // Kp 4 already puts it poleward of the view line, so the chance is likely without needing the boost.
        val iceland = outlook(reykjavik, kp = 4.0)
        assertTrue(iceland.highLatitude)
        assertEquals(AuroraChance.LIKELY, iceland.chance)
        assertTrue(iceland.prominent)
    }

    @Test
    fun stormReachingAMidLatitudeSiteLeadsTheDigestWithoutChangingTheScore() {
        val plain = NightPlanner.plan(toronto, date, forecast(toronto), kit)
        val storm = plain.copy(aurora = outlook(toronto, kp = 8.0, scale = "G4"))
        assertEquals(plain.scoreValue, storm.scoreValue)
        assertEquals(AuroraChance.POSSIBLE, storm.aurora!!.chance)
        assertFalse(storm.aurora!!.highLatitude)
        val digest = DigestComposer.compose(storm, emptyList())
        assertTrue(digest.lines.first(), digest.lines.first().startsWith("Aurora possible: Kp 8 G4"))
        assertTrue(digest.summary.startsWith("Aurora possible:"))
    }

    @Test
    fun yellowknifeStormIsLikelyAndAQuietNightStaysOffTheLead() {
        val storm = outlook(yellowknife, kp = 5.0, scale = "G1", status = KpStatus.PREDICTED)
        assertEquals(AuroraChance.LIKELY, storm.chance)
        assertTrue(storm.prominent)
        assertEquals("Aurora likely: Kp 5 G1 (forecast)", AuroraCopy.digestLine(storm))

        val quiet = NightPlanner.plan(yellowknife, date, forecast(yellowknife), kit)
            .copy(aurora = outlook(yellowknife, kp = 2.0, status = KpStatus.OBSERVED))
        assertEquals(AuroraChance.UNLIKELY, quiet.aurora!!.chance)
        assertFalse(quiet.aurora!!.prominent)
        val digest = DigestComposer.compose(quiet, emptyList())
        assertFalse(digest.lines.first().startsWith("Kp "))
        assertTrue(digest.lines.last().contains("aurora quiet tonight"))
        assertTrue(digest.summary.startsWith("Go:") || digest.summary.startsWith("Maybe:") || digest.summary.startsWith("No-go:"))
    }

    @Test
    fun highLatitudeBorderBoostsKp4() {
        val boosted = outlook(highBorder, kp = 4.0)
        assertTrue(boosted.highLatitude)
        assertEquals(AuroraChance.POSSIBLE, boosted.chance)
        val justSouth = outlook(highBorder.copy(id = "lo", latitude = 54.9), kp = 4.0)
        assertFalse(justSouth.highLatitude)
        assertEquals(AuroraChance.UNLIKELY, justSouth.chance)
    }

    @Test
    fun ignoresSamplesOutsideTheDarkWindowAndKeepsTheStrongestScale() {
        val eph = NightEphemeris.compute(date, toronto.zone, toronto.latitude, toronto.longitude)
        val window = eph.darkWindow!!
        val inside = window.start.plusSeconds(60)
        val outside = window.end.plusSeconds(6 * 3600)
        val forecast = KpForecast(
            listOf(
                KpSample(binStart(inside), 5.33, KpStatus.PREDICTED, null),
                KpSample(binStart(inside) + 3 * 3600, 4.67, KpStatus.PREDICTED, "G1"),
                KpSample(binStart(outside), 9.0, KpStatus.PREDICTED, "G5"),
            ),
        )
        val aurora = Aurora.forNight(toronto, eph, forecast)!!
        assertEquals(5.33, aurora.peakKp, 0.001)
        assertEquals("G1", aurora.noaaScale)
        assertNull(Aurora.forNight(toronto, eph, KpForecast(listOf(KpSample(binStart(outside), 9.0, KpStatus.PREDICTED, "G5")))))
    }

    @Test
    fun southernHemisphereCopyPointsTheOtherWay() {
        val ushuaia = Site("ush", "Ushuaia", -54.8, -68.3, "America/Argentina/Ushuaia", bortle = 3)
        val line = AuroraCopy.digestLine(outlook(ushuaia, kp = 2.0))
        assertTrue(line, line.contains("this far north"))
    }

    @Test
    fun briefingAttachesKpAndAFailedFetchDoesNotChangeTheScore() = runTest {
        val clock = Clock.fixed(Instant.parse("2024-08-10T16:00:00Z"), ZoneOffset.UTC)
        val source = FakeSource(forecast(toronto))
        val plain = BriefingService(source, clock).brief(listOf(toronto), kit, outlookDays = 1)
        val withKp = BriefingService(source, clock, kp = FixedKp(seriesCovering(toronto))).brief(listOf(toronto), kit, outlookDays = 1)
        assertEquals(plain.reportFor("home")!!.scoreValue, withKp.reportFor("home")!!.scoreValue)
        assertEquals(1, withKp.kp!!.samples.size)
        assertEquals(6.0, withKp.reportFor("home")!!.aurora!!.peakKp, 0.001)

        val failed = BriefingService(source, clock, kp = object : KpSource {
            override suspend fun fetch(): KpForecast = throw WeatherApiException("swpc down")
        }).brief(listOf(toronto), kit, outlookDays = 1)
        assertNull(failed.kp)
        assertNull(failed.reportFor("home")!!.aurora)
        assertEquals(plain.reportFor("home")!!.scoreValue, failed.reportFor("home")!!.scoreValue)
        assertTrue(failed.reportFor("home")!!.warnings.isEmpty())
    }

    private fun outlook(
        site: Site,
        kp: Double,
        scale: String? = null,
        status: KpStatus = KpStatus.PREDICTED,
    ): AuroraOutlook {
        val eph = NightEphemeris.compute(date, site.zone, site.latitude, site.longitude)
        val start = eph.darkWindow!!.start
        val forecast = KpForecast(listOf(KpSample(binStart(start.plusSeconds(60)), kp, status, scale)))
        return Aurora.forNight(site, eph, forecast)!!
    }

    private fun seriesCovering(site: Site): KpForecast {
        val eph = NightEphemeris.compute(date, site.zone, site.latitude, site.longitude)
        return KpForecast(listOf(KpSample(binStart(eph.darkWindow!!.start.plusSeconds(60)), 6.0, KpStatus.PREDICTED, "G2")))
    }

    private fun binStart(time: Instant): Long = time.epochSecond - Math.floorMod(time.epochSecond, 3 * 3600L)

    private fun forecast(site: Site): Forecast {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            site.latitude, site.longitude, "best_match", start,
            (0 until 96).map {
                HourlyWeather(epochSecond = start + it * 3600L, cloudCover = 10, humidity = 50, windKmh = 8.0)
            },
        )
    }

    private class FakeSource(val forecast: Forecast) : ForecastSource {
        override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean) =
            ForecastResult(forecast, ForecastStatus.FRESH)
    }

    private class FixedKp(val forecast: KpForecast) : KpSource {
        override suspend fun fetch(): KpForecast = forecast
    }
}
