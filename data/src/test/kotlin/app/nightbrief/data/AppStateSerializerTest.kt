package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
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
    }
}
