package app.nightbrief.weather

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ForecastRepositoryTest {
    private lateinit var server: MockWebServer
    // The dispatcher runs on MockWebServer's connection threads while the test thread reads
    // these, so they must cross threads safely. A plain list made the assertions see a stale
    // size or miss a recorded request now and then.
    @Volatile private var openMeteoCode = 200
    @Volatile private var secondModelCode = 200
    @Volatile private var sevenTimerCode = 200
    @Volatile private var responseDelayMs = 0L
    private val requests = CopyOnWriteArrayList<String>()

    private fun resource(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                val response = when {
                    path.startsWith("/om") && "icon_seamless" in path -> MockResponse().setResponseCode(secondModelCode)
                        .setBody(if (secondModelCode == 200) resource("open_meteo_gem.json") else "{\"error\":true,\"reason\":\"down\"}")
                    path.startsWith("/om") -> MockResponse().setResponseCode(openMeteoCode)
                        .setBody(if (openMeteoCode == 200) resource("open_meteo_gem.json") else "{\"error\":true,\"reason\":\"down\"}")
                    path.startsWith("/7t") -> MockResponse().setResponseCode(sevenTimerCode)
                        .setBody(if (sevenTimerCode == 200) resource("seven_timer_astro.json") else "")
                    else -> MockResponse().setResponseCode(404)
                }
                return response.setBodyDelay(responseDelayMs, TimeUnit.MILLISECONDS)
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
        assertEquals(315, h.windDirectionDeg)
        assertEquals(5, h.seeing)
        assertEquals(5, h.transparency)
        assertEquals(4, f.at(Instant.ofEpochSecond(1790866800 + 3 * 3600))!!.seeing)
        assertNull("hours before 7Timer coverage keep null seeing", f.hours.first().seeing)
        assertTrue(
            "expected a gem request, got $requests",
            requests.any { it.startsWith("/om") && "models=gem_seamless" in it },
        )
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
    fun parsesWindDirectionAndRequestsIt() = runTest {
        val result = repo().forecast(43.65, -79.38)
        val h = result.forecast.at(Instant.ofEpochSecond(1790866800))!!
        assertEquals("expected the fixture's 315°, got ${h.windDirectionDeg}", 315, h.windDirectionDeg)
        val primary = requests.first { "models=gem_seamless" in it }
        assertTrue("primary should ask for wind direction, got $primary", primary.contains("winddirection_10m"))
    }

    @Test
    fun secondModelAsksForCloudCoverOnly() = runTest {
        repo().forecast(43.65, -79.38)
        val icon = requests.first { "models=icon_seamless" in it }
        assertTrue("expected cloud-only secondary request, got $icon", icon.contains("cloud_cover"))
        assertTrue("secondary should not fetch dew point, got $icon", !icon.contains("dew_point_2m"))
        assertTrue("secondary should not fetch wind, got $icon", !icon.contains("wind_speed_10m"))
        assertTrue("secondary should not fetch wind direction, got $icon", !icon.contains("winddirection_10m"))
        val primary = requests.first { "models=gem_seamless" in it }
        assertTrue("primary should keep the full series, got $primary", primary.contains("dew_point_2m"))
    }

    @Test
    fun concurrentForecastsForOneCellShareOneFetch() = runTest {
        responseDelayMs = 250
        val results = coroutineScope {
            val source = repo()
            listOf(
                async { source.forecast(43.65, -79.38) },
                async { source.forecast(43.65, -79.38) },
            ).awaitAll()
        }
        assertTrue(results.all { it.status == ForecastStatus.FRESH || it.status == ForecastStatus.CACHED })
        val primary = requests.count { it.startsWith("/om") && "icon_seamless" !in it }
        assertEquals("expected one primary fetch, got $requests", 1, primary)
    }

    @Test
    fun http429BacksOffAndDoesNotSayTheBody() = runTest {
        openMeteoCode = 429
        val source = repo()
        val first = runCatching { source.forecast(43.65, -79.38) }.exceptionOrNull()
        assertTrue(first is WeatherApiException)
        val busy = first as WeatherApiException
        assertEquals(ForecastProblem.BUSY, busy.problem)
        assertEquals("Weather service busy — try again shortly", busy.userText())
        assertFalse(busy.userText().contains("HTTP"))
        val after = requests.size
        val second = runCatching { source.forecast(43.65, -79.38) }.exceptionOrNull()
        assertEquals(ForecastProblem.BUSY, (second as WeatherApiException).problem)
        assertEquals("cooldown should not call the network again, requests=$requests", after, requests.size)
        runCatching { source.forecast(43.65, -79.38, forceRefresh = true) }
        assertTrue("a forced refresh may try again", requests.size > after)
    }

    @Test
    fun http429ServesASavedForecastWithoutCallingItOffline() = runTest {
        val source = repo()
        source.forecast(43.65, -79.38)
        clock.now = clock.now.plus(Duration.ofHours(3))
        openMeteoCode = 429
        val stale = source.forecast(43.65, -79.38)
        assertEquals(ForecastStatus.STALE, stale.status)
        assertEquals("Weather service busy — try again shortly", stale.warnings.single())
        assertFalse(stale.warnings.single().contains("Offline"))
        val after = requests.size
        clock.now = clock.now.plus(Duration.ofSeconds(10))
        assertEquals(ForecastStatus.STALE, source.forecast(43.65, -79.38).status)
        assertEquals(after, requests.size)
    }

    @Test
    fun transportFailuresAreOfflineOrTimeout() {
        val offline = WeatherApiException.transport(java.net.UnknownHostException("api.open-meteo.com"))
        assertEquals(ForecastProblem.OFFLINE, offline.problem)
        assertEquals("No connection", offline.userText())
        val timeout = WeatherApiException.transport(java.net.SocketTimeoutException("read timed out"))
        assertEquals(ForecastProblem.TIMEOUT, timeout.problem)
        val wrapped = WeatherApiException.transport(IOException("connect", java.net.ConnectException("refused")))
        assertEquals(ForecastProblem.OFFLINE, wrapped.problem)
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
        assertEquals(315, result.forecast.at(Instant.ofEpochSecond(1790866800))!!.windDirectionDeg)
        dir.deleteRecursively()
    }

    @Test
    fun concurrentWritesLeaveAReadableForecast() = runTest {
        val dir = kotlin.io.path.createTempDirectory("fc-race").toFile()
        val cache = FileForecastCache(dir)
        val results = coroutineScope {
            (1..8).map { async { repo(cache).forecast(43.65, -79.38) } }.awaitAll()
        }
        assertTrue(
            "every caller should keep the forecast, got ${results.map { it.status }}",
            results.all { it.status == ForecastStatus.FRESH && it.forecast.hours.size == 24 },
        )
        val stored = File(dir, "fc_43.65_-79.38.json")
        assertTrue("cache file missing", stored.isFile)
        assertFalse("temp file left behind", File(dir, "fc_43.65_-79.38.json.tmp").exists())
        val again = repo(cache).forecast(43.65, -79.38)
        assertEquals(ForecastStatus.CACHED, again.status)
        dir.deleteRecursively()
    }

    @Test
    fun anUnwritableCacheStillReturnsTheForecast() = runTest {
        val blocker = kotlin.io.path.createTempFile("not-a-dir").toFile()
        val result = repo(FileForecastCache(blocker)).forecast(43.65, -79.38)
        assertEquals(ForecastStatus.FRESH, result.status)
        assertEquals(24, result.forecast.hours.size)
        blocker.delete()
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
