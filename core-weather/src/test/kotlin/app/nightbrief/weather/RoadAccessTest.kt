package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoadAccessTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client(radiusM: Int = 500) = OverpassRoadAccessClient(
        baseUrl = server.url("/api/interpreter").toString(),
        radiusM = radiusM,
    )

    private fun ways(vararg tags: String): String {
        val elements = tags.joinToString(",") { "{ \"type\": \"way\", \"id\": 1, \"tags\": $it }" }
        return "{ \"elements\": [$elements] }"
    }

    @Test
    fun pavedResidentialIsDriveUp() = runTest {
        server.enqueue(MockResponse().setBody(ways("""{ "highway": "residential", "surface": "asphalt" }""")))
        assertEquals(RoadAccess.DRIVE_UP, client().accessFor(43.65, -79.38))
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("data="))
        assertTrue(path, path.contains("way"))
    }

    @Test
    fun missingSurfaceOnMotorableRoadCountsAsPaved() = runTest {
        server.enqueue(MockResponse().setBody(ways("""{ "highway": "tertiary" }""")))
        assertEquals(RoadAccess.DRIVE_UP, client().accessFor(43.65, -79.38))
    }

    @Test
    fun gravelServiceRoadIsHikeIn() = runTest {
        server.enqueue(MockResponse().setBody(ways("""{ "highway": "service", "surface": "gravel" }""")))
        assertEquals(RoadAccess.HIKE_IN, client().accessFor(43.65, -79.38))
    }

    @Test
    fun trackIsHikeInEvenWhenPaved() = runTest {
        server.enqueue(MockResponse().setBody(ways("""{ "highway": "track", "surface": "asphalt" }""")))
        assertEquals(RoadAccess.HIKE_IN, client().accessFor(43.65, -79.38))
    }

    @Test
    fun emptyElementsAreHikeIn() = runTest {
        server.enqueue(MockResponse().setBody("""{ "elements": [] }"""))
        assertEquals(RoadAccess.HIKE_IN, client().accessFor(43.65, -79.38))
    }

    @Test
    fun onePavedRoadAmongUnpavedIsDriveUp() = runTest {
        server.enqueue(
            MockResponse().setBody(
                ways(
                    """{ "highway": "track", "surface": "dirt" }""",
                    """{ "highway": "residential", "surface": "paved" }""",
                ),
            ),
        )
        assertEquals(RoadAccess.DRIVE_UP, client().accessFor(43.65, -79.38))
    }

    @Test(expected = WeatherApiException::class)
    fun httpFailureThrowsSoCallersCanMarkUnknown() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        client().accessFor(43.65, -79.38)
    }

    @Test(expected = WeatherApiException::class)
    fun malformedBodyThrows() = runTest {
        server.enqueue(MockResponse().setBody("not-json"))
        client().accessFor(43.65, -79.38)
    }

    @Test
    fun queryCarriesRadiusAndCoordinates() {
        val query = OverpassRoadAccessClient.buildQuery(43.6532, -79.3832, 500)
        assertTrue(query, query.contains("around:500,43.65320,-79.38320"))
        assertTrue(query, query.contains("[highway]"))
    }
}
