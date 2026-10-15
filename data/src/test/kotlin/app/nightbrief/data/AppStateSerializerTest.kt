package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.SessionEntry
import app.nightbrief.score.UnitSystem
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppStateSerializerTest {
    @Test
    fun roundTrips() = runTest {
        val state = AppState(
            onboardingComplete = true,
            sites = SiteBook().add(Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 8)),
            gear = GearCatalog.exampleKit,
            digestTime = "07:45",
            bigNightAlertsEnabled = false,
            bigNightThreshold = 72,
            lastBigNightAlerts = mapOf("home" to "2026-08-12"),
            nightVision = true,
            units = UnitSystem.IMPERIAL,
        )
        val out = ByteArrayOutputStream()
        AppStateSerializer.writeTo(state, out)
        assertEquals(state, AppStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun defaultsSeedExampleGear() {
        assertEquals("canon-r7", AppState().gear.primaryBody!!.id)
        assertEquals("08:00", AppState().digestTime)
    }

    @Test
    fun toleratesUnknownFields() = runTest {
        val json = """{"onboardingComplete":true,"futureField":42}"""
        assertEquals(true, AppStateSerializer.readFrom(ByteArrayInputStream(json.toByteArray())).onboardingComplete)
    }

    @Test
    fun missingBigNightKeysUseDefaults() = runTest {
        val json = """{"onboardingComplete":true}"""
        val state = AppStateSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals(true, state.bigNightAlertsEnabled)
        assertEquals(85, state.bigNightThreshold)
        assertEquals(emptyMap<String, String>(), state.lastBigNightAlerts)
        assertEquals(false, state.nightVision)
        assertEquals(null, state.units)
    }

    @Test
    fun olderAppStateWithoutPassportFieldsDecodes() = runTest {
        val json = """
            {
              "onboardingComplete": true,
              "sessions": [
                {"siteId": "home", "nightDate": "2026-08-12", "rating": 4, "score": 58}
              ],
              "dismissedSessionPrompts": ["home/2026-08-11"]
            }
        """.trimIndent()
        val state = AppStateSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals(true, state.onboardingComplete)
        assertEquals(emptySet<String>(), state.seenPassportStamps)
        assertEquals(setOf("home/2026-08-11"), state.dismissedSessionPrompts)
        val session = state.sessions.single()
        assertEquals("home", session.siteId)
        assertEquals("2026-08-12", session.nightDate)
        assertEquals(58, session.score)
        assertEquals(4, session.rating)
        assertEquals(null, session.bortle)
        assertEquals(null, session.issVisible)
        assertEquals(null, session.overnightLowC)
    }

    @Test
    fun passportFieldsRoundTrip() = runTest {
        val state = AppState(
            onboardingComplete = true,
            sessions = listOf(
                SessionEntry(
                    siteId = "home",
                    nightDate = "2026-01-15",
                    score = 42,
                    rating = 4,
                    bortle = 2,
                    issVisible = true,
                    overnightLowC = -4.5,
                ),
            ),
            seenPassportStamps = setOf("frostbitten"),
        )
        val out = ByteArrayOutputStream()
        AppStateSerializer.writeTo(state, out)
        assertEquals(state, AppStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }
}
