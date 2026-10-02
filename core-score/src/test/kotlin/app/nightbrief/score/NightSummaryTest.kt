package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class NightSummaryTest {
    @Test
    fun clearMoonlessNightNamesTheMeasuredReasons() {
        val score = NightScoreEngine.scoreNight(night())
        assertEquals(Verdict.GO, score.verdict)
        assertEquals(
            "Clear skies, little moonlight, and good transparency.",
            NightSummary.whyGood(score),
        )
    }

    @Test
    fun mostlyClearNightStillExplainsTheGo() {
        val score = NightScoreEngine.scoreNight(night(cloud = { 20 }))
        assertEquals(Verdict.GO, score.verdict)
        assertEquals(
            "Mostly clear skies, little moonlight, and good transparency.",
            NightSummary.whyGood(score),
        )
    }

    @Test
    fun brightMoonThatSetsDuringDarknessIsNamedThatWay() {
        val site = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        val forecast = Forecast(
            site.latitude, site.longitude, "best_match", start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
        val report = NightPlanner.plan(site, LocalDate.of(2024, 8, 12), forecast, GearCatalog.exampleKit)
        val score = report.score!!
        assertEquals(Verdict.GO, score.verdict)
        assertEquals(
            "Clear skies, a moon that sets early, and good transparency.",
            NightSummary.whyGood(score, report.ephemeris),
        )
    }

    @Test
    fun estimatedTransparencyIsNotCitedAsAMeasurement() {
        val score = NightScoreEngine.scoreNight(night(transparency = null, seeing = null))
        assertEquals(Verdict.GO, score.verdict)
        assertEquals(
            "Clear skies, little moonlight, and calm air.",
            NightSummary.whyGood(score),
        )
    }

    @Test
    fun fullMoonAndOvercastNightsHaveNoGoLine() {
        assertNull(NightSummary.whyGood(NightScoreEngine.scoreNight(night(moonAlt = 40.0, moonIllum = 1.0))))
        assertNull(
            NightSummary.whyGood(
                NightScoreEngine.scoreNight(night(cloud = { 100 }, transparency = 8, seeing = 5)),
            ),
        )
    }

    @Test
    fun estimatedFactorsAreCalledOutInsteadOfInvented() {
        val score = nightScore(
            points = 80,
            factors = Factor.entries.map { FactorScore(it, quality = 0.95, points = it.weight * 0.95, estimated = true) },
        )
        assertEquals(
            "The score looks strong, but the main weather inputs are estimated.",
            NightSummary.whyGood(score),
        )
    }

    @Test
    fun aGoWithoutAStandoutFactorStillGetsALine() {
        val score = nightScore(
            points = 72,
            factors = Factor.entries.map { FactorScore(it, quality = 0.6, points = it.weight * 0.6, estimated = false) },
        )
        assertEquals("Conditions line up well enough to go.", NightSummary.whyGood(score))
    }

    private fun nightScore(points: Int, factors: List<FactorScore>) = NightScore(
        score = points,
        factors = factors,
        hours = emptyList(),
        bestWindow = null,
        bestWindowScore = points,
    )

    private fun night(
        cloud: (Int) -> Int? = { 0 },
        moonAlt: Double = -20.0,
        moonIllum: Double = 0.0,
        transparency: Int? = 2,
        seeing: Int? = 3,
    ): List<HourInput> {
        val start = Instant.parse("2024-08-11T02:00:00Z")
        return (0 until 6).map { i ->
            HourInput(
                time = start.plus(Duration.ofHours(i.toLong())),
                cloudCover = cloud(i),
                windKmh = 8.0,
                gustKmh = 10.4,
                seeingIndex = seeing,
                transparencyIndex = transparency,
                humidity = 60,
                jetStreamKmh = 90.0,
                sunAltitudeDeg = -25.0,
                moonAltitudeDeg = moonAlt,
                moonIllumination = moonIllum,
                bortle = 4,
            )
        }
    }
}
