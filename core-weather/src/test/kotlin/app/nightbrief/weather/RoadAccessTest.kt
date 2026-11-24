package app.nightbrief.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun concurrentLookupsRespectMaxConcurrentPermits() = runTest {
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val active = inFlight.incrementAndGet()
                peakInFlight.accumulateAndGet(active, ::maxOf)
                Thread.sleep(80)
                inFlight.decrementAndGet()
                return MockResponse()
                    .setBody(ways("""{ "highway": "residential", "surface": "asphalt" }"""))
                    .setBodyDelay(0, TimeUnit.MILLISECONDS)
            }
        }
        val limited = OverpassRoadAccessClient(
            baseUrl = server.url("/api/interpreter").toString(),
            maxConcurrent = 2,
        )
        val results = coroutineScope {
            (0 until 5).map { i ->
                async(Dispatchers.IO) { limited.accessFor(43.65 + i * 0.01, -79.38) }
            }.awaitAll()
        }
        assertEquals(List(5) { RoadAccess.DRIVE_UP }, results)
        assertTrue(
            "expected peak concurrent Overpass requests <= 2, got ${peakInFlight.get()}",
            peakInFlight.get() <= 2,
        )
    }

    @Test
    fun cachingRoadAccessReusesCellAndNeverCachesFailuresOrUnknown() = runTest {
        var calls = 0
        var nextResult: Result<RoadAccess> = Result.success(RoadAccess.DRIVE_UP)
        val cached = CachingRoadAccessSource(
            origin = RoadAccessSource { _, _ ->
                calls++
                nextResult.getOrThrow()
            },
        )
        assertEquals(RoadAccess.DRIVE_UP, cached.accessFor(43.6501, -79.3801))
        assertEquals(RoadAccess.DRIVE_UP, cached.accessFor(43.6502, -79.3802))
        assertEquals("same ~500m cell should hit cache, got $calls", 1, calls)

        // Failures and UNKNOWN are never cached so later searches can retry.
        nextResult = Result.failure(WeatherApiException("429"))
        assertTrue(runCatching { cached.accessFor(44.0, -79.0) }.exceptionOrNull() is WeatherApiException)
        assertEquals(2, calls)
        nextResult = Result.success(RoadAccess.UNKNOWN)
        assertEquals(RoadAccess.UNKNOWN, cached.accessFor(44.0, -79.0))
        assertEquals(3, calls)
        nextResult = Result.success(RoadAccess.HIKE_IN)
        assertEquals(RoadAccess.HIKE_IN, cached.accessFor(44.0, -79.0))
        assertEquals(4, calls)
        assertEquals(RoadAccess.HIKE_IN, cached.accessFor(44.0, -79.0))
        assertEquals("definitive HIKE_IN should now be cached, got $calls", 4, calls)
    }
}
