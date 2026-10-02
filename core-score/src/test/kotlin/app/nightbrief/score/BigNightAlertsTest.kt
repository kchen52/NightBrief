package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class BigNightAlertsTest {
    private val tonight = LocalDate.of(2026, 8, 12)
    private val home = BigNightCandidate("home", "Home", tonight, score = 90)
    private val cabin = BigNightCandidate("cabin", "Cabin", tonight, score = 85)

    @Test
    fun thresholdIsExcellentAndEightyFiveIsIncluded() {
        assertEquals(85, BigNightAlerts.threshold)
        val alerts = BigNightAlerts.select(listOf(home, cabin), alreadyAlerted = emptyMap(), enabled = true)
        assertEquals(listOf("home", "cabin"), alerts.map { it.siteId })
        assertEquals(listOf(90, 85), alerts.map { it.score })
    }

    @Test
    fun scoresBelowTheThresholdAndMissingScoresAreSkipped() {
        val candidates = listOf(
            home.copy(score = 84),
            cabin.copy(score = null),
            BigNightCandidate("ridge", "Ridge", tonight, score = 0),
        )
        assertTrue(BigNightAlerts.select(candidates, emptyMap(), enabled = true).isEmpty())
    }

    @Test
    fun anAlertedSiteAndNightIsSkippedWithoutDroppingAnotherSite() {
        val alerts = BigNightAlerts.select(
            listOf(home, cabin),
            alreadyAlerted = mapOf("home" to tonight.toString(), "other" to "2026-01-01"),
            enabled = true,
        )
        assertEquals(listOf("cabin"), alerts.map { it.siteId })
        assertEquals("2026-08-12", alerts.single().nightKey)
    }

    @Test
    fun theSameSiteAlertsAgainOnALaterNight() {
        val later = home.copy(date = tonight.plusDays(1), score = 91)
        val alerts = BigNightAlerts.select(
            listOf(later),
            alreadyAlerted = mapOf("home" to tonight.toString()),
            enabled = true,
        )
        assertEquals(tonight.plusDays(1).toString(), alerts.single().nightKey)
    }

    @Test
    fun disabledAlertsPostNothing() {
        assertTrue(BigNightAlerts.select(listOf(home), emptyMap(), enabled = false).isEmpty())
    }

    @Test
    fun aLowerThresholdIncludesAGoodNightAndStillSkipsBelowIt() {
        val good = home.copy(score = 70)
        val fair = cabin.copy(score = 69)
        val alerts = BigNightAlerts.select(
            listOf(good, fair),
            alreadyAlerted = emptyMap(),
            enabled = true,
            threshold = 70,
        )
        assertEquals(listOf("home"), alerts.map { it.siteId })
    }

    @Test
    fun thresholdsOutsideTheSliderAreClamped() {
        assertEquals(70, BigNightAlerts.clamp(50))
        assertEquals(95, BigNightAlerts.clamp(100))
        val alerts = BigNightAlerts.select(
            listOf(home.copy(score = 70)),
            alreadyAlerted = emptyMap(),
            enabled = true,
            threshold = 40,
        )
        assertEquals(listOf(70), alerts.map { it.score })
        assertTrue(
            BigNightAlerts.select(
                listOf(home.copy(score = 94)),
                emptyMap(),
                enabled = true,
                threshold = 200,
            ).isEmpty(),
        )
    }

    @Test
    fun dateLabelUsesTheLocaleWeekdayAndIsoDate() {
        val label = BigNightAlert("home", "Home", 90, tonight).dateLabel(Locale.US)
        assertEquals("Wednesday 2026-08-12", label)
    }
}
