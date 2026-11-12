package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AirQualityClientTest {
    private lateinit var server: MockWebServer
    private val client = AirQualityClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun parsesHourlyArraysAndQueriesTheChosenFields() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "latitude": 49.25, "longitude": -123.0, "timezone": "GMT",
                  "hourly": {
                    "time": [1754870400, 1754874000],
                    "pm2_5": [12.5, null],
                    "pm10": [22.0, -1.0],
                    "aerosol_optical_depth": [0.4, null]
                  }
                }
                """.trimIndent(),
            ),
        )
        val source = AirQualityClient(
            http = defaultHttpClient(),
            baseUrl = server.url("/v1/air-quality").toString(),
        )

        val series = source.fetch(49.2827, -123.1207)

        assertEquals("expected two samples, got ${series.samples.size}", 2, series.samples.size)
        assertEquals("expected pm2_5 on the first sample, got ${series.samples[0].pm25}", 12.5, series.samples[0].pm25)
        assertEquals("expected pm10 on the first sample, got ${series.samples[0].pm10}", 22.0, series.samples[0].pm10)
        assertEquals(
            "expected aerosol depth on the first sample, got ${series.samples[0].aerosolOpticalDepth}",
            0.4,
            series.samples[0].aerosolOpticalDepth,
        )
        assertNull("a null reading must stay null, got ${series.samples[1].pm25}", series.samples[1].pm25)
        assertNull("a negative reading is not a measurement, got ${series.samples[1].pm10}", series.samples[1].pm10)

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("expected the hourly fields, got ${url.queryParameter("hourly")}", "pm2_5,pm10,aerosol_optical_depth", url.queryParameter("hourly"))
        assertEquals("expected unixtime, got ${url.queryParameter("timeformat")}", "unixtime", url.queryParameter("timeformat"))
        assertEquals("expected GMT, got ${url.queryParameter("timezone")}", "GMT", url.queryParameter("timezone"))
        assertEquals("expected 7 days, got ${url.queryParameter("forecast_days")}", "7", url.queryParameter("forecast_days"))
        assertEquals("expected NightBrief user agent, got ${request.getHeader("User-Agent")}", "NightBrief/0.1", request.getHeader("User-Agent"))
    }

    @Test
    fun anErrorBodyThrowsInsteadOfReturningAnEmptySeries() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"error":true,"reason":"Latitude must be in range"}"""),
        )
        val source = AirQualityClient(
            http = defaultHttpClient(),
            baseUrl = server.url("/v1/air-quality").toString(),
        )
        try {
            source.fetch(120.0, 0.0)
            fail("expected an Open-Meteo error body to throw")
        } catch (_: WeatherApiException) {
            // Upstream drops the smoke card for this site; the score is untouched.
        }
    }

    @Test
    fun atUsesTheNearestSampleInsideTheTolerance() {
        val series = AirQualitySeries(
            listOf(
                AirQualitySample(epochSecond = at("2026-08-11T02:00:00Z").epochSecond, pm25 = 10.0),
                AirQualitySample(epochSecond = at("2026-08-11T05:00:00Z").epochSecond, pm25 = 60.0),
            ),
        )

        val inside = series.at(at("2026-08-11T03:20:00Z"))
        assertEquals("expected the 02:00 sample inside the 90-minute tolerance, got ${inside?.time}", at("2026-08-11T02:00:00Z"), inside?.time)

        val outside = series.at(at("2026-08-11T06:40:00Z"))
        assertNull("expected no sample beyond the tolerance, got ${outside?.time}", outside)
    }

    @Test
    fun aTieGoesToTheEarlierSample() {
        val series = AirQualitySeries(
            listOf(
                AirQualitySample(epochSecond = at("2026-08-11T02:00:00Z").epochSecond, pm25 = 10.0),
                AirQualitySample(epochSecond = at("2026-08-11T04:00:00Z").epochSecond, pm25 = 20.0),
            ),
        )
        val hit = series.at(at("2026-08-11T03:00:00Z"))
        assertEquals("expected the earlier sample on a tie, got ${hit?.time}", at("2026-08-11T02:00:00Z"), hit?.time)
    }

    @Test
    fun anEmptySeriesHasNoReading() {
        assertNull("an empty series must not report a reading", AirQualitySeries(emptyList()).at(at("2026-08-11T03:00:00Z")))
    }

    private var now = Instant.parse("2026-08-11T00:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private class FakeAirQuality(var result: Result<AirQualitySeries> = Result.success(series())) : AirQualitySource {
        var calls = 0
        override suspend fun fetch(latitude: Double, longitude: Double): AirQualitySeries {
            calls++
            return result.getOrThrow()
        }
    }

    @Test
    fun secondFetchWithinTtlReusesTheCachedSeries() = runTest {
        val origin = FakeAirQuality()
        val cached = CachingAirQualitySource(origin, clock = clock)
        val first = cached.fetch(49.28, -123.12)
        now = now.plus(Duration.ofMinutes(30))
        val second = cached.fetch(49.28, -123.12)
        assertEquals("expected 1 origin call, got ${origin.calls}", 1, origin.calls)
        assertSame("second fetch within TTL should reuse the cached series, got a fresh object", first, second)
    }

    @Test
    fun eachCellIsCachedSeparately() = runTest {
        val origin = FakeAirQuality()
        val cached = CachingAirQualitySource(origin, clock = clock)
        cached.fetch(49.28, -123.12)
        cached.fetch(45.52, -122.68)
        assertEquals("expected one origin call per cell, got ${origin.calls}", 2, origin.calls)
    }

    @Test
    fun failureWithinCooldownThrowsWithoutCallingTheOrigin() = runTest {
        val origin = FakeAirQuality()
        val cached = CachingAirQualitySource(origin, clock = clock)
        cached.fetch(49.28, -123.12)
        origin.result = Result.failure(WeatherApiException("air quality down"))
        now = now.plus(Duration.ofHours(2))
        runCatching { cached.fetch(49.28, -123.12) }
        assertEquals("expected 2 origin calls, got ${origin.calls}", 2, origin.calls)
        now = now.plus(Duration.ofMinutes(10))
        try {
            cached.fetch(49.28, -123.12)
            fail("expected the cooldown to fail fast, it returned a series")
        } catch (_: WeatherApiException) {
            // Inside 30 minutes of a failure the network is not retried.
        }
        assertEquals("expected no origin call inside the cooldown, got ${origin.calls}", 2, origin.calls)
    }

    @Test
    fun aClockThatMovedBackwardsDoesNotPoisonTheCooldown() = runTest {
        val origin = FakeAirQuality()
        val cached = CachingAirQualitySource(origin, clock = clock)
        origin.result = Result.failure(WeatherApiException("air quality down"))
        runCatching { cached.fetch(49.28, -123.12) }
        now = now.minus(Duration.ofHours(1))
        origin.result = Result.success(series())
        val recovered = cached.fetch(49.28, -123.12)
        assertTrue("a backwards clock must not keep the cooldown armed", recovered.samples.isNotEmpty())
        assertEquals("expected the origin to be retried, got ${origin.calls}", 2, origin.calls)
    }

    private fun at(text: String): Instant = Instant.parse(text)

    companion object {
        private fun series() = AirQualitySeries(
            listOf(AirQualitySample(Instant.parse("2026-08-11T00:00:00Z").epochSecond, pm25 = 12.0)),
        )
    }
}
