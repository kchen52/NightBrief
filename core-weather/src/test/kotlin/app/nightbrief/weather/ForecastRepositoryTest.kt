package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ForecastRepositoryTest {
    private lateinit var server: MockWebServer
    private var openMeteoCode = 200
    private var secondModelCode = 200
    private var sevenTimerCode = 200
    private val requests = mutableListOf<String>()

    private fun resource(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                return when {
                    path.startsWith("/om") && "icon_seamless" in path -> MockResponse().setResponseCode(secondModelCode)
                        .setBody(if (secondModelCode == 200) resource("open_meteo_gem.json") else "{\"error\":true,\"reason\":\"down\"}")
                    path.startsWith("/om") -> MockResponse().setResponseCode(openMeteoCode)
                        .setBody(if (openMeteoCode == 200) resource("open_meteo_gem.json") else "{\"error\":true,\"reason\":\"down\"}")
                    path.startsWith("/7t") -> MockResponse().setResponseCode(sevenTimerCode)
                        .setBody(if (sevenTimerCode == 200) resource("seven_timer_astro.json") else "")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-01T12:00:00Z"))

    private fun repo(cache: ForecastCache = InMemoryForecastCache()) = ForecastRepository(
        openMeteo = OpenMeteoClient(baseUrl = server.url("/om").toString(), clock = clock),
        sevenTimer = SevenTimerClient(baseUrl = server.url("/7t").toString()),
        cache = cache,
        clock = clock,
    )

    @Test
    fun mergesOpenMeteoAndSevenTimer() = runTest {
        val result = repo().forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        val f = result.forecast
        assertEquals("gem_seamless", f.model)
        assertEquals(24, f.hours.size)
        val h = f.at(Instant.ofEpochSecond(1790866800))!!
        assertEquals(100, h.cloudCover)
        assertEquals(99.6, h.jetStreamKmh!!, 0.01)
        assertEquals(5, h.seeing)
        assertEquals(5, h.transparency)
        assertEquals(4, f.at(Instant.ofEpochSecond(1790866800 + 3 * 3600))!!.seeing)
        assertNull("hours before 7Timer coverage keep null seeing", f.hours.first().seeing)
        assertTrue(requests.first { it.startsWith("/om") }.contains("models=gem_seamless"))
    }

    @Test
    fun sevenTimerOutageIsNonFatal() = runTest {
        sevenTimerCode = 503
        val result = repo().forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        assertTrue(result.forecast.hours.all { it.seeing == null })
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun fetchesASecondModelForConfidence() = runTest {
        val result = repo().forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        assertTrue("expected a gem request, got $requests", requests.any { "models=gem_seamless" in it })
        assertTrue("expected an icon request, got $requests", requests.any { "models=icon_seamless" in it })
        val h = result.forecast.at(Instant.ofEpochSecond(1790866800))!!
        assertEquals("secondary should mirror the fixture: ${h.cloudCoverSecondary}", h.cloudCover, h.cloudCoverSecondary)
    }

    @Test
    fun secondModelOutageIsNonFatal() = runTest {
        secondModelCode = 500
        val result = repo().forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        assertTrue(result.forecast.hours.all { it.cloudCoverSecondary == null })
    }

    @Test
    fun secondModelAsksForCloudCoverOnly() = runTest {
        repo().forecast(43.65, -79.38)
        val icon = requests.first { "models=icon_seamless" in it }
        assertTrue("expected cloud-only secondary request, got $icon", icon.contains("cloud_cover"))
        assertTrue("secondary should not fetch dew point, got $icon", !icon.contains("dew_point_2m"))
        assertTrue("secondary should not fetch wind, got $icon", !icon.contains("wind_speed_10m"))
        val primary = requests.first { "models=gem_seamless" in it }
        assertTrue("primary should keep the full series, got $primary", primary.contains("dew_point_2m"))
    }

    @Test
    fun servesRecentCacheWithoutNetwork() = runTest {
        val r = repo()
        r.forecast(43.65, -79.38)
        val before = requests.size
        clock.now = clock.now.plus(Duration.ofMinutes(30))
        assertEquals(ForecastStatus.CACHED, r.forecast(43.65, -79.38).status)
        assertEquals(before, requests.size)
    }

    @Test
    fun fallsBackToStaleCacheWhenFetchFails() = runTest {
        val r = repo()
        r.forecast(43.65, -79.38)
        clock.now = clock.now.plus(Duration.ofHours(6))
        openMeteoCode = 500
        val result = r.forecast(43.65, -79.38)
        assertEquals(ForecastStatus.STALE, result.status)
        assertEquals(24, result.forecast.hours.size)
    }

    @Test(expected = WeatherApiException::class)
    fun failsWithoutCache() = runTest {
        openMeteoCode = 500
        repo().forecast(43.65, -79.38)
    }

    @Test
    fun servesAForecastOlderThan48HoursWhenTheFetchFails() = runTest {
        val r = repo()
        val saved = r.forecast(43.65, -79.38).forecast
        clock.now = clock.now.plus(Duration.ofHours(72))
        openMeteoCode = 500
        val result = r.forecast(43.65, -79.38)
        assertEquals(ForecastStatus.STALE, result.status)
        assertEquals(saved.fetchedAt, result.forecast.fetchedAt)
        assertEquals(24, result.forecast.hours.size)
    }

    @Test
    fun aWorkingNetworkReplacesAnOldForecast() = runTest {
        val r = repo()
        r.forecast(43.65, -79.38)
        clock.now = clock.now.plus(Duration.ofDays(5))
        val result = r.forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        assertEquals(clock.now, result.forecast.fetchedAt)
    }

    @Test
    fun fileCacheRoundTrips() = runTest {
        val dir = kotlin.io.path.createTempDirectory("fc").toFile()
        repo(FileForecastCache(dir)).forecast(43.65, -79.38)
        openMeteoCode = 500
        clock.now = clock.now.plus(Duration.ofHours(2))
        val result = repo(FileForecastCache(dir)).forecast(43.65, -79.38)
        assertEquals(ForecastStatus.STALE, result.status)
        assertEquals(5, result.forecast.at(Instant.ofEpochSecond(1790866800))!!.seeing)
        dir.deleteRecursively()
    }

    @Test
    fun sevenTimerBoundaryAttachesAt90MinutesAndExcludesBeyond() {
        val base = 1_790_866_800L
        val forecast = Forecast(
            43.65, -79.38, "gem_seamless", base,
            listOf(HourlyWeather(epochSecond = base, cloudCover = 10)),
        )
        val merged = forecast.withSevenTimer(
            listOf(
                SevenTimerPoint(epochSecond = base - 5400, seeing = 1, transparency = 1, cloudIndex = 1),
                SevenTimerPoint(epochSecond = base + 5401, seeing = 8, transparency = 8, cloudIndex = 9),
            ),
        )
        assertEquals(1, merged.hours.single().seeing)
        assertEquals(1, merged.hours.single().transparency)
    }

    @Test
    fun sevenTimerMergePicksNearestAndEarlierOnTies() {
        val base = 1_790_866_800L
        val forecast = Forecast(
            43.65, -79.38, "gem_seamless", base,
            listOf(
                HourlyWeather(epochSecond = base, cloudCover = 10),
                HourlyWeather(epochSecond = base + 3600, cloudCover = 10),
            ),
        )
        val merged = forecast.withSevenTimer(
            listOf(
                SevenTimerPoint(epochSecond = base + 2400, seeing = 4, transparency = 4, cloudIndex = 3),
                SevenTimerPoint(epochSecond = base + 4800, seeing = 6, transparency = 6, cloudIndex = 5),
            ),
        )
        assertEquals("expected nearest point seeing 4, got ${merged.hours[0].seeing}", 4, merged.hours[0].seeing)
        assertEquals(
            "expected tied points to resolve to the earlier seeing 4, got ${merged.hours[1].seeing}",
            4,
            merged.hours[1].seeing,
        )
    }

    @Test
    fun modelSelection() {
        assertEquals(WeatherModel.GEM_SEAMLESS, WeatherModel.forLocation(43.65, -79.38)) // Toronto
        assertEquals(WeatherModel.GEM_SEAMLESS, WeatherModel.forLocation(53.55, -113.49)) // Edmonton
        assertEquals(WeatherModel.BEST_MATCH, WeatherModel.forLocation(33.0, -112.0)) // Arizona
        assertEquals(WeatherModel.BEST_MATCH, WeatherModel.forLocation(51.5, -0.12)) // London
    }
}
