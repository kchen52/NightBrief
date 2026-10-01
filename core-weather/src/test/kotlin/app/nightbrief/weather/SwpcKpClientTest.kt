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
import java.time.Instant

class SwpcKpClientTest {
    private lateinit var server: MockWebServer
    private val client = SwpcKpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun parsesCurrentObjectArray() {
        val forecast = client.parse(
            """
            [
              {"time_tag":"2026-10-01T21:00:00","kp":2.33,"observed":"estimated","noaa_scale":null},
              {"time_tag":"2026-10-02T00:00:00","kp":5.00,"observed":"predicted","noaa_scale":"G1"},
              {"time_tag":"2026-10-02T03:00:00","kp":"nope","observed":"predicted","noaa_scale":null}
            ]
            """.trimIndent(),
        )
        assertEquals(2, forecast.samples.size)
        val first = forecast.samples[0]
        assertEquals(Instant.parse("2026-10-01T21:00:00Z"), first.time)
        assertEquals(2.33, first.kp, 0.001)
        assertEquals(KpStatus.ESTIMATED, first.status)
        assertNull(first.noaaScale)
        val storm = forecast.samples[1]
        assertEquals(5.0, storm.kp, 0.001)
        assertEquals(KpStatus.PREDICTED, storm.status)
        assertEquals("G1", storm.noaaScale)
    }

    @Test
    fun parsesLegacyHeaderAndStringRows() {
        val forecast = client.parse(
            """
            [
              ["time_tag","kp","observed","noaa_scale"],
              ["2026-10-02 18:00:00","6.67","predicted","G2"],
              ["2026-10-02 21:00:00","3.00","observed",""]
            ]
            """.trimIndent(),
        )
        assertEquals(2, forecast.samples.size)
        assertEquals(Instant.parse("2026-10-02T18:00:00Z"), forecast.samples[0].time)
        assertEquals(6.67, forecast.samples[0].kp, 0.001)
        assertEquals("G2", forecast.samples[0].noaaScale)
        assertEquals(KpStatus.OBSERVED, forecast.samples[1].status)
        assertNull(forecast.samples[1].noaaScale)
    }

    @Test
    fun legacyColumnsFollowTheHeader() {
        val forecast = client.parse(
            """
            [
              ["observed","noaa_scale","kp","time_tag"],
              ["predicted","G3","7.00","2026-10-03T00:00:00Z"]
            ]
            """.trimIndent(),
        )
        val sample = forecast.samples.single()
        assertEquals(7.0, sample.kp, 0.001)
        assertEquals(KpStatus.PREDICTED, sample.status)
        assertEquals("G3", sample.noaaScale)
        assertEquals(Instant.parse("2026-10-03T00:00:00Z"), sample.time)
    }

    @Test
    fun headerOnlyAndEmptyAreEmptyForecasts() {
        assertTrue(client.parse("[]").samples.isEmpty())
        assertTrue(
            client.parse("""[["time_tag","kp","observed","noaa_scale"]]""").samples.isEmpty(),
        )
    }

    @Test
    fun rejectsANonArray() {
        val error = runCatching { client.parse("""{"kp":5}""") }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun rejectsALegacyHeaderMissingColumns() {
        val error = runCatching { client.parse("""[["time_tag","kp"],["2026-10-01 00:00:00","2"]]""") }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun coveringUsesTheThreeHourBin() {
        val forecast = client.parse(
            """
            [
              {"time_tag":"2026-10-02T00:00:00","kp":1.0,"observed":"predicted","noaa_scale":null},
              {"time_tag":"2026-10-02T03:00:00","kp":4.0,"observed":"predicted","noaa_scale":null},
              {"time_tag":"2026-10-02T06:00:00","kp":9.0,"observed":"predicted","noaa_scale":null}
            ]
            """.trimIndent(),
        )
        val start = Instant.parse("2026-10-02T02:30:00Z")
        val end = Instant.parse("2026-10-02T04:00:00Z")
        val covering = forecast.covering(start, end)
        assertEquals(listOf(1.0, 4.0), covering.map { it.kp })
    }

    @Test
    fun fetchReadsTheConfiguredUrl() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"time_tag":"2026-10-02T00:00:00","kp":3.67,"observed":"observed","noaa_scale":null}]""",
            ),
        )
        val fetched = SwpcKpClient(url = server.url("/kp.json").toString()).fetch()
        assertEquals(3.67, fetched.samples.single().kp, 0.001)
        assertEquals(KpStatus.OBSERVED, fetched.samples.single().status)
        assertTrue(server.takeRequest().path.orEmpty().contains("/kp.json"))
    }
}
