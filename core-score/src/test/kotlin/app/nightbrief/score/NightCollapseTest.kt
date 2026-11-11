package app.nightbrief.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class NightCollapseTest {
    private val tonight = LocalDate.of(2026, 8, 12)
    private val home = BigNightCandidate("home", "Home", tonight, score = 40)

    @Test
    fun aSiteNeedsABigNightAlertForTheSameNight() {
        val alerts = NightCollapse.select(
            listOf(home),
            bigNightAlerts = mapOf("home" to tonight.toString()),
            alreadyAlerted = emptyMap(),
            enabled = true,
        )
        assertEquals(listOf("home"), alerts.map { it.siteId })
        assertEquals("2026-08-12", alerts.single().nightKey)

        assertTrue(
            "no prior alert means no collapse",
            NightCollapse.select(listOf(home), emptyMap(), emptyMap(), enabled = true).isEmpty(),
        )
        assertTrue(
            "yesterday's alert does not cover tonight",
            NightCollapse.select(
                listOf(home),
                bigNightAlerts = mapOf("home" to tonight.minusDays(1).toString()),
                alreadyAlerted = emptyMap(),
                enabled = true,
            ).isEmpty(),
        )
    }

    @Test
    fun theScoreMustFallMoreThanTheMarginBelowTheBar() {
        fun alertsFor(score: Int?) = NightCollapse.select(
            listOf(home.copy(score = score)),
            bigNightAlerts = mapOf("home" to tonight.toString()),
            alreadyAlerted = emptyMap(),
            enabled = true,
        )
        assertTrue("exactly ten below the bar is not a collapse", alertsFor(75).isEmpty())
        assertEquals("eleven below the bar is", listOf("home"), alertsFor(74).map { it.siteId })
        assertTrue("a missing score cannot collapse", alertsFor(null).isEmpty())
    }

    @Test
    fun theThresholdIsClampedLikeBigNightAlerts() {
        // A threshold of 50 clamps to 70, so the collapse bar is 60.
        val alerts = NightCollapse.select(
            listOf(home.copy(score = 59)),
            bigNightAlerts = mapOf("home" to tonight.toString()),
            alreadyAlerted = emptyMap(),
            enabled = true,
            threshold = 50,
        )
        assertEquals(listOf(59), alerts.map { it.score })
        assertTrue(
            NightCollapse.select(
                listOf(home.copy(score = 60)),
                mapOf("home" to tonight.toString()),
                emptyMap(),
                enabled = true,
                threshold = 50,
            ).isEmpty(),
        )
    }

    @Test
    fun aCollapsedSiteAndNightIsSkipped() {
        assertTrue(
            NightCollapse.select(
                listOf(home),
                bigNightAlerts = mapOf("home" to tonight.toString()),
                alreadyAlerted = mapOf("home" to tonight.toString()),
                enabled = true,
            ).isEmpty(),
        )
        // The next night is a new key.
        val tomorrow = home.copy(date = tonight.plusDays(1))
        val alerts = NightCollapse.select(
            listOf(tomorrow),
            bigNightAlerts = mapOf("home" to tomorrow.date.toString()),
            alreadyAlerted = mapOf("home" to tonight.toString()),
            enabled = true,
        )
        assertEquals(tomorrow.date.toString(), alerts.single().nightKey)
    }

    @Test
    fun disabledCollapsePostsNothing() {
        assertTrue(
            NightCollapse.select(
                listOf(home),
                mapOf("home" to tonight.toString()),
                emptyMap(),
                enabled = false,
            ).isEmpty(),
        )
    }

    @Test
    fun dateLabelUsesTheLocaleWeekdayAndIsoDate() {
        val label = CollapseAlert("home", "Home", 40, tonight).dateLabel(Locale.US)
        assertEquals("Wed Aug 12", label)
    }
}
