package app.nightbrief.score

import app.nightbrief.astro.IssPass
import app.nightbrief.astro.MeteorShowers
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.KpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NightFlavorTest {
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val darkSite = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 2)
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun overcastNightSaysTheOwlsAreStayingIn() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 100)
        val line = report.flavorLine
        assertTrue("line was $line", line in OVERCAST)
        assertEquals(line, NightFlavor.line(report))
    }

    @Test
    fun clearDarkMoonlessNightSaysTheUniverseIsShowingOff() {
        val report = plan(darkSite, LocalDate.of(2024, 4, 8), cloud = 0)
        assertTrue("illumination was ${report.ephemeris.moonIllumination}", report.ephemeris.moonIllumination < NightEphemeris.NEGLIGIBLE_MOON)
        assertEquals(Verdict.GO, report.score!!.verdict)
        val line = report.flavorLine
        assertTrue("line was $line", line in CLEAR)
    }

    @Test
    fun aBrightMoonUpAllNightHogsTheStage() {
        val report = plan(home, LocalDate.of(2024, 6, 21), cloud = 20)
        assertTrue("illumination was ${report.ephemeris.moonIllumination}", report.ephemeris.moonIllumination >= 0.70)
        val line = report.flavorLine
        assertTrue("line was $line", line in MOON)
    }

    @Test
    fun highThinCloudWearsAVeil() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 40, cloudHigh = 40, cloudLow = 0, cloudMid = 0)
        assertEquals(CloudReason.HIGH_THIN, report.cloudReason)
        val line = report.flavorLine
        assertTrue("line was $line", line in VEIL)
    }

    @Test
    fun aLateClearingCoreIsFashionablyLate() {
        val date = LocalDate.of(2024, 5, 15)
        val flat = NightEphemeris.compute(date, zone, home.latitude, home.longitude)
        val core = flat.milkyWay
        assertNotNull(core)
        val south = LocalHorizon(
            south = core!!.peakAltitudeDeg - 1,
            southEast = core.peakAltitudeDeg - 1,
            southWest = core.peakAltitudeDeg - 1,
        )
        val report = NightPlanner.plan(home.copy(horizon = south), date, forecast = null, kit)
        val at = report.ephemeris.milkyWay?.clearsHorizonAt
        assertNotNull("expected the core to clear late, window=${report.ephemeris.milkyWay}", at)
        assertEquals(
            WidgetCopy.clearsTreeline(at!!, zone) + ". Fashionably late.",
            report.flavorLine,
        )
    }

    @Test
    fun frostAndDewAreNamedOnlyFromMeasuredTemperatures() {
        val frost = plan(home, LocalDate.of(2024, 3, 15), cloud = 30, temperatureC = -2.0, dewPointC = -3.0)
        assertTrue("expected frost", frost.dew?.frost == true)
        assertTrue("line was ${frost.flavorLine}", frost.flavorLine in FROST)

        val dew = plan(home, LocalDate.of(2024, 3, 15), cloud = 30, temperatureC = 6.0, dewPointC = 5.0)
        assertTrue("expected dew without frost", dew.dew?.risk == true && dew.dew?.frost == false)
        assertTrue("line was ${dew.flavorLine}", dew.flavorLine in DEW)
    }

    @Test
    fun auroraAndAVisitingShowerAndTheIssEachGetALine() {
        val quiet = plan(home, LocalDate.of(2024, 3, 15), cloud = 40)
        val likely = quiet.copy(
            aurora = AuroraOutlook(6.0, "G2", KpStatus.PREDICTED, true, AuroraChance.LIKELY, 62.0),
        )
        assertTrue("line was ${likely.flavorLine}", likely.flavorLine in AURORA_LIKELY)

        val possible = quiet.copy(
            aurora = AuroraOutlook(5.0, null, KpStatus.PREDICTED, false, AuroraChance.POSSIBLE, 43.0),
        )
        assertTrue("line was ${possible.flavorLine}", possible.flavorLine in AURORA_POSSIBLE)

        val perseids = MeteorShowers.annual.first { it.id == "perseids" }
        val shower = quiet.copy(meteor = watching(perseids, daysFromPeak = 3))
        assertTrue("line was ${shower.flavorLine}", shower.flavorLine!!.contains("Perseids"))

        val iss = quiet.copy(
            issPasses = listOf(
                IssPass(
                    rise = Instant.parse("2024-03-16T02:10:00Z"),
                    set = Instant.parse("2024-03-16T02:16:00Z"),
                    peak = Instant.parse("2024-03-16T02:13:00Z"),
                    peakAltitudeDeg = 40.0,
                    peakAzimuthDeg = 180.0,
                ),
            ),
        )
        assertTrue("line was ${iss.flavorLine}", iss.flavorLine in ISS)
    }

    @Test
    fun anOrdinaryMeasuredNightHasNoLine() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 45)
        assertTrue("cloud quality was ${report.score!!.factor(Factor.CLOUD).quality}", report.score!!.factor(Factor.CLOUD).quality in 0.26..0.89)
        assertNull(report.flavorLine)
    }

    @Test
    fun aClearGoNightWithAHalfMoonStillGetsALine() {
        val report = plan(home, LocalDate.of(2024, 9, 9), cloud = 0)
        val moon = report.ephemeris.moonIllumination
        assertTrue("illumination was $moon", moon in NightEphemeris.NEGLIGIBLE_MOON..0.70)
        assertEquals(Verdict.GO, report.score!!.verdict)
        val line = report.flavorLine
        assertTrue("line was $line", line in CLEAR_GO)
    }

    @Test
    fun theSameSiteAndDateKeepsTheSameLine() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 100)
        val first = report.flavorLine
        assertEquals(first, NightFlavor.line(report))
        assertEquals(first, NightFlavor.line(report.copy()))
        val variants = mutableSetOf<String>()
        for (i in 0 until 40) {
            val shifted = report.copy(site = report.site.copy(id = "site-$i"))
            val line = NightFlavor.line(shifted)
            assertEquals(line, NightFlavor.line(shifted))
            variants += line!!
        }
        assertTrue("expected both overcast wordings, got $variants", variants.size >= 2)
        variants.forEach { line ->
            assertTrue("line was $line", line in OVERCAST)
        }
    }

    @Test
    fun estimatedFactorNeverClaimsAMeasurement() {
        val measured = plan(home, LocalDate.of(2024, 3, 15), cloud = 100)
        assertFalse(measured.score!!.factor(Factor.CLOUD).estimated)
        assertTrue("line was ${measured.flavorLine}", measured.flavorLine!!.contains("Overcast"))

        val estimated = plan(home, LocalDate.of(2024, 3, 15), cloud = 100, dropHalfTheCloud = true)
        assertTrue(estimated.score!!.factor(Factor.CLOUD).estimated)
        val line = estimated.flavorLine
        assertTrue("line was $line", line == null || BANNED_MEASUREMENTS.none { line.contains(it, ignoreCase = true) })
        assertTrue("line was $line", line!!.contains("stand-in"))

        val thin = plan(
            home,
            LocalDate.of(2024, 3, 15),
            cloud = 40,
            cloudHigh = 40,
            cloudLow = 0,
            cloudMid = 0,
            dropHalfTheCloud = true,
        )
        assertTrue(thin.score!!.factor(Factor.CLOUD).estimated)
        assertEquals(CloudReason.HIGH_THIN, thin.cloudReason)
        val veil = thin.flavorLine
        assertTrue("line was $veil", veil == null || BANNED_MEASUREMENTS.none { veil.contains(it, ignoreCase = true) })
    }

    @Test
    fun aSavedForecastDoesNotDescribeTheSkyAsIfItWereFresh() {
        val report = plan(
            darkSite,
            LocalDate.of(2024, 4, 8),
            cloud = 0,
            forecastStatus = ForecastStatus.STALE,
        )
        val line = report.flavorLine
        assertTrue("line was $line", line in STALE)
        assertTrue("line was $line", BANNED_MEASUREMENTS.none { line!!.contains(it, ignoreCase = true) })
    }

    private fun plan(
        site: Site,
        date: LocalDate,
        cloud: Int,
        cloudLow: Int? = null,
        cloudMid: Int? = null,
        cloudHigh: Int? = null,
        temperatureC: Double? = null,
        dewPointC: Double? = null,
        dropHalfTheCloud: Boolean = false,
        forecastStatus: ForecastStatus? = null,
    ): NightReport {
        val start = date.minusDays(1).atStartOfDay(zone).toInstant()
        val forecast = Forecast(
            site.latitude,
            site.longitude,
            "test",
            start.epochSecond,
            (0 until 72).map { offset ->
                val missing = dropHalfTheCloud && offset % 2 == 1
                HourlyWeather(
                    epochSecond = start.epochSecond + offset * 3600L,
                    cloudCover = if (missing) null else cloud,
                    cloudLow = if (missing) null else cloudLow,
                    cloudMid = if (missing) null else cloudMid,
                    cloudHigh = if (missing) null else cloudHigh,
                    humidity = 50,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 80.0,
                    seeing = 2,
                    transparency = 2,
                    temperatureC = temperatureC,
                    dewPointC = dewPointC,
                )
            },
        )
        return NightPlanner.plan(site, date, forecast, kit, forecastStatus = forecastStatus)
    }

    private fun watching(shower: app.nightbrief.astro.MeteorShower, daysFromPeak: Int) = MeteorOutlook(
        shower = shower,
        peakDate = LocalDate.of(2024, 8, 12),
        daysFromPeak = daysFromPeak,
        expectedZhr = 40,
        peakRadiantAltitudeDeg = 50.0,
        peakRadiantTime = Instant.parse("2024-03-16T06:00:00Z"),
        peakRadiantAzimuthDeg = 40.0,
        moonIllumination = 0.1,
        moonUpAtRadiantPeak = false,
        moonRadiantSeparationDeg = 80.0,
        interference = MeteorInterference.NONE,
    )

    private companion object {
        val OVERCAST = setOf(
            "Overcast. Even the owls are staying in.",
            "Overcast. Even the owls stayed home.",
        )
        val CLEAR = setOf(
            "Clear, dark, and moonless. The universe is showing off.",
            "Clear, dark, and moonless. The universe is in a generous mood.",
        )
        val CLEAR_GO = setOf(
            "Clear skies on the menu. Go make something.",
            "The clouds took the night off. You shouldn't.",
        )
        val MOON = setOf(
            "The Moon is hogging the stage tonight.",
            "The Moon is hogging the stage and will not share.",
        )
        val VEIL = setOf(
            "Thin high cloud: the sky's wearing a veil.",
            "High thin cloud. The sky's wearing a veil.",
        )
        val FROST = setOf(
            "Frost is likely. The grass will crunch.",
            "Frost is invited. Dress like you mean it.",
        )
        val DEW = setOf(
            "Dew is settling in. A heater earns its place.",
            "Dew is likely. Lenses prefer a heater tonight.",
        )
        val ISS = setOf(
            "The ISS is dropping by. Wave if you're out.",
            "A bright guest is crossing the sky. That's the ISS.",
        )
        val AURORA_LIKELY = setOf(
            "Aurora is likely. The sky may throw its own show.",
            "Aurora is likely. Look toward the pole.",
        )
        val AURORA_POSSIBLE = setOf(
            "Aurora is possible. Worth a glance toward the pole.",
            "The pole might glow. Aurora is possible.",
        )
        val STALE = setOf(
            "This is the forecast we saved. The sky may have rewritten it.",
            "Numbers from the drawer. The sky may have changed its mind.",
        )
        val BANNED_MEASUREMENTS = listOf("clear", "overcast", "veil", "thin high", "dew", "frost", "cloud")
    }
}
