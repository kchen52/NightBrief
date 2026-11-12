package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SwpcObservedKpClientTest {
    private lateinit var server: MockWebServer
    private val client = SwpcObservedKpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun parsesTheObjectArrayWithACapitalKp() {
        val observed = client.parse(
            """
            [
              {"time_tag":"2026-11-12T00:00:00","Kp":1.33,"a_running":5,"station_count":6},
              {"time_tag":"2026-11-12T03:00:00","Kp":6.00,"a_running":48,"station_count":8},
              {"time_tag":"2026-11-12T06:00:00","Kp":"nope","a_running":1,"station_count":8}
            ]
            """.trimIndent(),
        )
        assertEquals("expected two parsed rows, got ${observed.samples.size}", 2, observed.samples.size)
        val first = observed.samples[0]
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), first.time)
        assertEquals(1.33, first.kp, 0.001)
        assertEquals(
            "an observed-product row is a measurement, got ${first.status}",
            KpStatus.OBSERVED,
            first.status,
        )
    }

    @Test
    fun parsesTheHeaderAndStringShapeEvenWithACapitalisedHeader() {
        val observed = client.parse(
            """
            [
              ["time_tag","Kp","a_running","station_count"],
              ["2026-11-11 21:00:00","5.33","30","6"],
              ["2026-11-12 00:00:00","4.67","20","6"]
            ]
            """.trimIndent(),
        )
        assertEquals("expected both rows, got ${observed.samples.map { it.kp }}", 2, observed.samples.size)
        assertEquals(5.33, observed.samples[0].kp, 0.001)
        assertEquals(4.67, observed.samples[1].kp, 0.001)
        assertEquals(Instant.parse("2026-11-11T21:00:00Z"), observed.samples[0].time)
        assertEquals(KpStatus.OBSERVED, observed.samples[0].status)
    }

    @Test
    fun anEmptyProductIsAnEmptySeries() {
        assertTrue(client.parse("[]").samples.isEmpty())
    }

    @Test
    fun aNonArrayBodyThrows() {
        val error = runCatching { client.parse("""{"Kp":5}""") }.exceptionOrNull()
        assertTrue("expected a WeatherApiException, got $error", error is WeatherApiException)
    }

    @Test
    fun aHeaderWithoutKpThrows() {
        val error = runCatching {
            client.parse("""[["time_tag","a_running"],["2026-11-12 00:00:00","5"]]""")
        }.exceptionOrNull()
        assertTrue("expected a WeatherApiException, got $error", error is WeatherApiException)
    }

    @Test
    fun fetchReadsTheObservedUrlWithTheNightBriefAgent() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"time_tag":"2026-11-12T03:00:00","Kp":7.00,"a_running":60,"station_count":8}]""",
            ),
        )
        val fetched = SwpcObservedKpClient(url = server.url("/observed-kp.json").toString()).fetch()
        assertEquals(7.0, fetched.samples.single().kp, 0.001)
        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("/observed-kp.json"))
        assertEquals("NightBrief/0.1", request.getHeader("User-Agent"))
    }

    @Test
    fun theDefaultUrlIsTheObservedProductNotTheForecast() {
        assertEquals(
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",
            SwpcObservedKpClient.DEFAULT_URL,
        )
        assertTrue(SwpcObservedKpClient.DEFAULT_URL != SwpcKpClient.DEFAULT_URL)
    }
}
