package app.nightbrief.work

import app.nightbrief.data.AppState
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
