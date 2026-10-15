package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaceSearchTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun search() = PlaceSearch(baseUrl = server.url("/v1/search").toString())

    @Test
    fun sendsTheTypedNameForServerSideFuzzyMatch() = runTest {
        server.enqueue(MockResponse().setBody(BANFF))
        val result = search().search("Banf", language = "en")
        assertFalse(result.failed)
        assertEquals("Banff", result.places.single().name)
        assertEquals(51.1784, result.places.single().latitude, 0.0001)
        assertEquals(-115.5708, result.places.single().longitude, 0.0001)
        assertEquals("Alberta, Canada", result.places.single().detail())
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("name=Banf"))
        assertTrue(path, path.contains("count=${PlaceSearch.RESULT_COUNT}"))
        assertTrue(path, path.contains("language=en"))
        assertTrue(path, path.contains("format=json"))
    }

    @Test
    fun shortQueryDoesNotCallTheNetwork() = runTest {
        val result = search().search(" ")
        assertFalse(result.failed)
        assertTrue(result.places.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun skipsHitsWithoutAUsableCoordinate() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results":[
                  {"name":" ","latitude":1.0,"longitude":2.0},
                  {"name":"Nowhere","longitude":2.0},
                  {"name":"Off World","latitude":120.0,"longitude":0.0},
                  {"name":"Kept","latitude":10.0,"longitude":20.0,"admin1":null,"country":"Test"}
                ]}
                """.trimIndent(),
            ),
        )
        val result = search().search("kept")
        assertFalse(result.failed)
        assertEquals("Kept", result.places.single().name)
        assertEquals("Test", result.places.single().detail())
    }

    @Test
    fun emptyBodyIsNoMatchRatherThanAFailure() = runTest {
        server.enqueue(MockResponse().setBody("""{"generationtime_ms":0.2}"""))
        val result = search().search("zzzz")
        assertFalse(result.failed)
        assertTrue(result.places.isEmpty())
    }

    @Test
    fun httpFailureLeavesTheMapUsable() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val result = search().search("Banff")
        assertTrue(result.failed)
        assertTrue(result.places.isEmpty())
    }

    @Test
    fun malformedJsonIsAFailure() = runTest {
        server.enqueue(MockResponse().setBody("not-json"))
        val result = search().search("Banff")
        assertTrue(result.failed)
        assertTrue(result.places.isEmpty())
    }

    @Test
    fun cityStateDropsTheRepeatedRegion() {
        val berlin = PlaceMatch("Berlin", 52.52, 13.405, region = "Berlin", country = "Germany")
        assertEquals("Germany", berlin.detail())
        assertNull(PlaceMatch("At Sea", 0.0, 0.0).detail())
    }

    private companion object {
        const val BANFF =
            """{"results":[{"id":5892532,"name":"Banff","latitude":51.1784,"longitude":-115.5708,""" +
                """"admin1":"Alberta","country":"Canada","timezone":"America/Edmonton"}]}"""
    }
}
