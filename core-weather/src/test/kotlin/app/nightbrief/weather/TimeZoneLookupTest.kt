package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TimeZoneLookupTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun lookup() = TimeZoneLookup(baseUrl = server.url("/v1/forecast").toString())

    @Test
    fun returnsIanaZoneFromMinimalQuery() = runTest {
        server.enqueue(MockResponse().setBody("""{"latitude":43.65,"timezone":"America/Toronto"}"""))
        assertEquals("America/Toronto", lookup().zoneFor(43.65, -79.38))
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("latitude=43.6500"))
        assertTrue(path, path.contains("longitude=-79.3800"))
        assertTrue(path, path.contains("timezone=auto"))
        assertTrue(path, !path.contains("hourly="))
        assertTrue(path, !path.contains("current="))
    }

    @Test
    fun nullWhenHttpFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        assertNull(lookup().zoneFor(43.65, -79.38))
    }

    @Test
    fun nullWhenBodyIsNotJson() = runTest {
        server.enqueue(MockResponse().setBody("not-json"))
        assertNull(lookup().zoneFor(1.0, 2.0))
    }

    @Test
    fun nullWhenTimezoneMissing() = runTest {
        server.enqueue(MockResponse().setBody("""{"latitude":1.0}"""))
        assertNull(lookup().zoneFor(1.0, 2.0))
    }

    @Test
    fun nullWhenZoneIdRejectsTheValue() = runTest {
        server.enqueue(MockResponse().setBody("""{"timezone":"Not/AZone"}"""))
        assertNull(lookup().zoneFor(1.0, 2.0))
    }
}
