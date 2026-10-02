package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ModelAgreementTest {
    private val home = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 10)
    private val kit = GearCatalog.exampleKit

    private fun report(secondary: (Int) -> Int?): NightReport {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        val forecast = Forecast(
            home.latitude, home.longitude, "gem_seamless", start,
            (0 until 240).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L, cloudCover = 10, humidity = 60,
                    windKmh = 6.0, gustKmh = 10.0, jetStreamKmh = 80.0,
                    seeing = 3, transparency = 2,
                    cloudCoverSecondary = secondary(it),
                )
            },
        )
        return NightPlanner.plan(home, date, forecast, kit)
    }

    @Test
    fun agreesWhenModelsTrack() {
        val agreement = ModelAgreement.assess(report { 12 })!!
        assertEquals(ModelConfidence.AGREE, agreement.confidence)
        assertEquals(2, agreement.meanAbsDiff)
        assertEquals("Models agree on clouds", ModelAgreement.line(agreement))
    }

    @Test
    fun mixedOnModerateSpread() {
        val agreement = ModelAgreement.assess(report { 25 })!!
        assertEquals(ModelConfidence.MIXED, agreement.confidence)
        assertTrue(ModelAgreement.line(agreement).startsWith("Models differ a little"))
    }

    @Test
    fun disagreesOnLargeSpread() {
        val agreement = ModelAgreement.assess(report { 90 })!!
        assertEquals(ModelConfidence.DISAGREE, agreement.confidence)
        assertEquals(80, agreement.meanAbsDiff)
        assertTrue(ModelAgreement.line(agreement).startsWith("Models disagree"))
    }

    @Test
    fun nullWhenTooFewOverlappingHours() {
        // Secondary only on the first two hours; darkness needs at least three overlaps.
        val sparse = report { if (it < 2) 12 else null }
        val darkOverlaps = sparse.timeline.count { it.isDark && it.cloudCover != null && it.cloudSecondary != null }
        assertTrue("expected fewer than 3 dark overlaps, got $darkOverlaps", darkOverlaps < ModelAgreement.MIN_HOURS)
        assertNull(ModelAgreement.assess(sparse))
    }

    @Test
    fun nullWhenSecondModelMissing() {
        assertNull(ModelAgreement.assess(report { null }))
    }

    @Test
    fun digestPutsConfidenceAfterTheVerdictLine() {
        val digest = DigestComposer.compose(report { 90 }, emptyList())
        assertEquals("Models disagree on clouds (avg 80 pts apart)", digest.lines[1])
    }

    @Test
    fun digestOmitsConfidenceWithoutASecondModel() {
        val digest = DigestComposer.compose(report { null }, emptyList())
        assertTrue("unexpected confidence line in ${digest.lines}", digest.lines.none { it.startsWith("Models ") })
    }
}
