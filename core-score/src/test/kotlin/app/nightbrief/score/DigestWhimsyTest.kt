package app.nightbrief.score

import app.nightbrief.astro.MeteorShowers
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
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

class DigestWhimsyTest {
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 8)
    private val longPoint = Site("lp", "Long Point", 42.58, -80.40, "America/Toronto", bortle = 2)
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun digestTitleIsUnchangedOnAnOrdinaryNight() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 50)
        val score = report.score!!
        assertTrue("score was ${score.score}", score.score < BigNightAlerts.threshold)
        assertTrue("meteor was ${report.meteor}", report.meteor == null || report.meteor!!.daysFromPeak != 0)
        assertNull(report.aurora)
        val digest = DigestComposer.compose(report, emptyList())
        assertEquals("Tonight: ${score.score} at Home · ${score.band.label}", digest.title)
        assertTrue(digest.lines.first(), digest.lines.first().startsWith("${score.verdict.label}:"))
        assertNull(DigestComposer.winkTitle(report))
    }

    @Test
    fun aShowerPeakThatIsAGoWinksTheTitleAndLeavesTheVerdictFirst() {
        val base = plan(longPoint, LocalDate.of(2024, 4, 8), cloud = 0)
        assertEquals(Verdict.GO, base.score!!.verdict)
        val perseids = MeteorShowers.annual.first { it.id == "perseids" }
        val report = base.copy(meteor = peak(perseids))
        val digest = DigestComposer.compose(report, emptyList())
        assertEquals("🌠 Perseids + clear skies. Cancel your plans.", digest.title)
        assertTrue(digest.lines.first(), digest.lines.first().startsWith("${report.score!!.verdict.label}:"))
        assertTrue(digest.lines.none { it.contains("Cancel your plans") })
    }

    @Test
    fun aShowerPeakDoesNotCallEstimatedCloudClear() {
        val base = plan(longPoint, LocalDate.of(2024, 4, 8), cloud = 0, dropHalfTheCloud = true)
        assertTrue(base.score!!.factor(Factor.CLOUD).estimated)
        assertEquals(Verdict.GO, base.score!!.verdict)
        val perseids = MeteorShowers.annual.first { it.id == "perseids" }
        val digest = DigestComposer.compose(base.copy(meteor = peak(perseids)), emptyList())
        assertEquals("🌠 Perseids peak, and it's a Go. Cancel your plans.", digest.title)
        assertFalse(digest.title.contains("clear skies"))
    }

    @Test
    fun likelyAuroraWinksTheTitleWithoutReplacingTheFactualBody() {
        val base = plan(home, LocalDate.of(2024, 3, 15), cloud = 90)
        val report = base.copy(
            aurora = AuroraOutlook(6.0, "G2", KpStatus.PREDICTED, true, AuroraChance.LIKELY, 62.0),
        )
        val digest = DigestComposer.compose(report, emptyList())
        assertEquals("🌠 Aurora likely. Cancel your plans.", digest.title)
        assertTrue(digest.lines.first(), digest.lines.first().startsWith("Aurora likely:"))
        assertTrue(digest.lines.none { it.contains("Cancel your plans") })
    }

    @Test
    fun aBigNightWinksTheTitleAndKeepsTheVerdictFirst() {
        val report = plan(longPoint, LocalDate.of(2024, 4, 8), cloud = 0)
        val score = report.score!!
        assertTrue("score was ${score.score}", score.score >= 85)
        assertNull(report.meteor)
        val digest = DigestComposer.compose(report, emptyList())
        assertEquals("🌠 Big Night. Cancel your plans.", digest.title)
        assertTrue(digest.lines.first(), digest.lines.first().startsWith("${score.verdict.label}:"))
    }

    @Test
    fun aLowerThresholdWinksAndAHigherOneKeepsTheFactualTitle() {
        val report = plan(home, LocalDate.of(2024, 3, 15), cloud = 15)
        val score = report.score!!
        assertTrue("score was ${score.score}", score.score in 70..94)
        val winked = DigestComposer.compose(report, emptyList(), bigNightThreshold = score.score)
        assertEquals("🌠 Big Night. Cancel your plans.", winked.title)
        val factual = DigestComposer.compose(report, emptyList(), bigNightThreshold = score.score + 1)
        assertEquals("Tonight: ${score.score} at Home · ${score.band.label}", factual.title)
    }

    @Test
    fun aBetterSiteKeepsTheComparisonTitle() {
        val homeReport = plan(home, LocalDate.of(2024, 4, 8), cloud = 10).copy(
            meteor = peak(MeteorShowers.annual.first { it.id == "perseids" }),
        )
        val elsewhere = plan(longPoint, LocalDate.of(2024, 4, 8), cloud = 0)
        val digest = DigestComposer.compose(homeReport, listOf(elsewhere))
        assertNotNull(digest.alternativeSiteId)
        assertTrue(digest.title, digest.title.startsWith("Tonight: ${homeReport.scoreValue} at Home, but"))
        assertFalse(digest.title.contains("🌠"))
    }

    private fun peak(shower: app.nightbrief.astro.MeteorShower) = MeteorOutlook(
        shower = shower,
        peakDate = LocalDate.of(2024, 8, 12),
        daysFromPeak = 0,
        expectedZhr = 80,
        peakRadiantAltitudeDeg = 55.0,
        peakRadiantTime = Instant.parse("2024-04-09T06:00:00Z"),
        peakRadiantAzimuthDeg = 30.0,
        moonIllumination = 0.05,
        moonUpAtRadiantPeak = false,
        moonRadiantSeparationDeg = 90.0,
        interference = MeteorInterference.NONE,
    )

    private fun plan(
        site: Site,
        date: LocalDate,
        cloud: Int,
        dropHalfTheCloud: Boolean = false,
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
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
        return NightPlanner.plan(site, date, forecast, kit)
    }
}
