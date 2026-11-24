package app.nightbrief.work

import app.nightbrief.astro.TargetCatalog
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearKit
import app.nightbrief.score.SessionEntry
import app.nightbrief.score.UnitSystem
import app.nightbrief.score.WindowAlarm
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class DigestTimesTest {
    private val zone = ZoneId.of("America/Toronto")
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto")
    private val cottage = Site("cottage", "Cottage", 45.0, -78.5, "America/Toronto")
    private val state = AppState(onboardingComplete = true, sites = SiteBook().add(home).add(cottage))

    @Test
    fun primaryUsesGlobalTimeOthersSilent() {
        val schedule = DigestTimes.schedule(state)
        assertEquals(mapOf(LocalTime.of(8, 0) to listOf(home)), schedule)
    }

    @Test
    fun perSiteOverrides() {
        val s = state.copy(
            sites = state.sites
                .update(home.copy(digestTimeOverride = "07:30"))
                .update(cottage.copy(digestTimeOverride = "16:00")),
        )
        assertEquals(listOf(LocalTime.of(7, 30), LocalTime.of(16, 0)), DigestTimes.schedule(s).keys.toList())
        assertEquals(listOf("cottage"), DigestTimes.sitesDueAt(s, LocalTime.of(16, 0)).map { it.id })
    }

    @Test
    fun nextTriggerRollsToTomorrow() {
        val (at, time) = DigestTimes.next(state, Instant.parse("2024-08-10T13:00:00Z"), zone)!! // 09:00 EDT
        assertEquals(LocalTime.of(8, 0), time)
        assertEquals(Instant.parse("2024-08-11T12:00:00Z"), at)
    }

    @Test
    fun nextTriggerLaterToday() {
        val (at, _) = DigestTimes.next(state, Instant.parse("2024-08-10T10:00:00Z"), zone)!! // 06:00 EDT
        assertEquals(Instant.parse("2024-08-10T12:00:00Z"), at)
    }

    @Test
    fun disabledDigestHasNoTrigger() {
        assertNull(DigestTimes.next(state.copy(digestEnabled = false), Instant.now(), zone))
    }

    @Test
    fun needsRescheduleIgnoresNonSchedulingMutations() {
        val unrelated = listOf(
            "collapsedCards" to state.copy(collapsedCards = setOf("timeline")),
            "nightVision" to state.copy(nightVision = true),
            "gear" to state.copy(gear = GearKit()),
            "units" to state.copy(units = UnitSystem.IMPERIAL),
            "alternativeThreshold" to state.copy(alternativeThreshold = 20),
            "bigNightThreshold" to state.copy(bigNightThreshold = 90),
            "auroraKpThreshold" to state.copy(auroraKpThreshold = 6),
            "sessions" to state.copy(sessions = listOf(SessionEntry(siteId = home.id, nightDate = "2026-10-01", score = 88, rating = 5))),
            "seenPassportStamps" to state.copy(seenPassportStamps = setOf("first_light")),
            "dismissedSessionPrompts" to state.copy(dismissedSessionPrompts = setOf("home/2026-10-01")),
            "customTargets" to state.copy(customTargets = listOf(TargetCatalog.milkyWayCore)),
            "skippedUpdateId" to state.copy(skippedUpdateId = "v1.2.0"),
            "issEnabled" to state.copy(issEnabled = true),
            "site name and bortle" to state.copy(sites = state.sites.update(home.copy(name = "Backyard", bortle = 3))),
        )
        for ((label, mutated) in unrelated) {
            assertFalse(
                "expected needsReschedule=false for $label",
                DigestScheduler.needsReschedule(state, mutated),
            )
        }
    }

    @Test
    fun needsRescheduleDetectsEverySchedulingMutation() {
        val alarm = WindowAlarm(
            siteId = home.id,
            nightDate = "2026-10-01",
            startEpochSecond = 1_790_899_200L,
            endEpochSecond = 1_790_910_000L,
            windowScore = 88,
        )
        val extra = Site("park", "Dark Park", 45.5, -78.0, "America/Toronto")
        val relevant = listOf(
            "onboardingComplete" to state.copy(onboardingComplete = false),
            "digestEnabled" to state.copy(digestEnabled = false),
            "digestTime" to state.copy(digestTime = "09:30"),
            "windowAlarm" to state.copy(windowAlarm = alarm),
            "auroraAlertsEnabled" to state.copy(auroraAlertsEnabled = true),
            "site added" to state.copy(sites = state.sites.add(extra)),
            "site removed" to state.copy(sites = state.sites.delete(cottage.id)),
            "primaryId changed" to state.copy(sites = state.sites.setPrimary(cottage.id)),
            "zoneId changed" to state.copy(sites = state.sites.update(home.copy(zoneId = "America/Edmonton"))),
            "digestTimeOverride changed" to state.copy(
                sites = state.sites.update(cottage.copy(digestTimeOverride = "18:00")),
            ),
        )
        for ((label, mutated) in relevant) {
            assertTrue(
                "expected needsReschedule=true for $label",
                DigestScheduler.needsReschedule(state, mutated),
            )
        }
    }
}
