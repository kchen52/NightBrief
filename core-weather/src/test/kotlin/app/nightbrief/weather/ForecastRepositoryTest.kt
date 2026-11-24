package app.nightbrief.weather

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    /** Open-Meteo calls other than the cloud-only second model. */
    private fun primaryForecastRequests(): Int =
        requests.count { it.startsWith("/om") && "icon_seamless" !in it }

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
        // The 429 cancels the seeing and second-model calls. MockWebServer can still
        // record those after forecast() returns, so the cooldown is judged on the primary
        // call, which is recorded before that response comes back.
        val primaries = primaryForecastRequests()
        val second = runCatching { source.forecast(43.65, -79.38) }.exceptionOrNull()
        assertEquals(ForecastProblem.BUSY, (second as WeatherApiException).problem)
        assertEquals("cooldown should not call the network again, requests=$requests", primaries, primaryForecastRequests())
        runCatching { source.forecast(43.65, -79.38, forceRefresh = true) }
        assertTrue("a forced refresh may try again, requests=$requests", primaryForecastRequests() > primaries)
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
        val primaries = primaryForecastRequests()
        clock.now = clock.now.plus(Duration.ofSeconds(10))
        assertEquals(ForecastStatus.STALE, source.forecast(43.65, -79.38).status)
        assertEquals("cooldown should not call the network again, requests=$requests", primaries, primaryForecastRequests())
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
    fun fileCacheServesRepeatedGetsFromMemoryAndEvictsLeastRecentlyUsed() = runTest {
        val dir = kotlin.io.path.createTempDirectory("fc-mem").toFile()
        try {
            val cache = FileForecastCache(dir, maxMemoryEntries = 2)
            val first = repo(cache).forecast(43.65, -79.38).forecast
            val key = ForecastRepository.cacheKey(43.65, -79.38)
            val stored = File(dir, "$key.json")
            assertTrue("expected persisted forecast file", stored.isFile)
            assertTrue("expected disk file delete to succeed", stored.delete())

            // Deleting the file on disk proves this hit came from memory, not flash.
            val hit = cache.get(key)
            assertEquals(first.fetchedAtEpochSecond, hit?.fetchedAtEpochSecond)

            // Evict the first entry by inserting two newer keys into the 2-entry LRU.
            cache.put("k2", first.copy(latitude = 44.0))
            cache.put("k3", first.copy(latitude = 45.0))
            assertNull("evicted key whose disk file was deleted must return null", cache.get(key))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cancellingFirstCallerDoesNotAbortWaitingCaller() = runTest {
        responseDelayMs = 200
        val source = repo()
        coroutineScope {
            val first = async(Dispatchers.IO) { source.forecast(43.65, -79.38) }
            val second = async(Dispatchers.IO) {
                delay(40)
                source.forecast(43.65, -79.38)
            }
            delay(80)
            first.cancel()
            val result = second.await()
            assertEquals(ForecastStatus.FRESH, result.status)
            assertEquals(24, result.forecast.hours.size)
        }
    }

    @Test
    fun detachedFlightScopeCompletesSingleRequestWhenFirstCallerCancels() = runTest {
        responseDelayMs = 200
        val flightScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val source = ForecastRepository(
                openMeteo = OpenMeteoClient(baseUrl = server.url("/om").toString(), clock = clock),
                sevenTimer = SevenTimerClient(baseUrl = server.url("/7t").toString()),
                cache = InMemoryForecastCache(),
                clock = clock,
                flightScope = flightScope,
            )
            coroutineScope {
                val first = async(Dispatchers.IO) { source.forecast(43.65, -79.38) }
                val second = async(Dispatchers.IO) {
                    delay(40)
                    source.forecast(43.65, -79.38)
                }
                delay(80)
                first.cancel()
                val result = second.await()
                assertEquals(ForecastStatus.FRESH, result.status)
                assertEquals(
                    "expected 1 primary fetch with detached flightScope, got $requests",
                    1,
                    primaryForecastRequests(),
                )
            }
        } finally {
            flightScope.cancel()
        }
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

    @Test
    fun openMeteoWrapsSchemaErrorsAndIgnoresNonFiniteNumbers() {
        val client = OpenMeteoClient()
        try {
            client.parse("""{"hourly":{"time":[1700000000],"cloud_cover":[{}]}}""", 43.65, -79.38, WeatherModel.BEST_MATCH)
            throw AssertionError("expected WeatherApiException for non-primitive cloud_cover element")
        } catch (_: WeatherApiException) {
            // expected
        }
        val parsed = client.parse(
            """{"hourly":{"time":[1700000000],"cloud_cover":["NaN"],"wind_speed_10m":["Infinity"],"temperature_2m":["-Infinity"]}}""",
            43.65,
            -79.38,
            WeatherModel.BEST_MATCH,
        )
        val hour = parsed.hours.single()
        assertEquals(null, hour.cloudCover)
        assertEquals(null, hour.windKmh)
        assertEquals(null, hour.temperatureC)
    }

    @Test
    fun sevenTimerWrapsMalformedInitAndSeriesAsWeatherApiException() {
        val client = SevenTimerClient()
        try {
            client.parse("""{"init":"bad-date","dataseries":[]}""")
            throw AssertionError("expected WeatherApiException for invalid init")
        } catch (_: WeatherApiException) {
            // expected
        }
        try {
            client.parse("""{"init":"2026081100","dataseries":["not-an-object"]}""")
            throw AssertionError("expected WeatherApiException for non-object dataseries item")
        } catch (_: WeatherApiException) {
            // expected
        }
    }

    @Test
    fun getStringRejectsOversizedResponse() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("x".repeat(64)))
        server.start()
        try {
            val http = defaultHttpClient()
            try {
                http.getString(server.url("/big").toString(), maxBytes = 16)
                throw AssertionError("expected WeatherApiException for oversized body")
            } catch (_: WeatherApiException) {
                // expected
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun forecastBatchCombinesSameModelLocationsIntoOnePrimaryAndOneSecondaryCall() = runTest {
        val sample = resource("open_meteo_gem.json")
        val batchServer = MockWebServer()
        val batchRequests = CopyOnWriteArrayList<String>()
        val primaryCode = java.util.concurrent.atomic.AtomicInteger(200)
        batchServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                batchRequests += path
                val latParam = request.requestUrl?.queryParameter("latitude").orEmpty()
                val count = latParam.split(",").filter { it.isNotBlank() }.size
                val body = if (count > 1) {
                    "[" + List(count) { sample }.joinToString(",") + "]"
                } else {
                    sample
                }
                val code = primaryCode.get()
                return when {
                    path.startsWith("/om") && "icon_seamless" in path ->
                        MockResponse().setResponseCode(200).setBody(body)
                    path.startsWith("/om") ->
                        MockResponse().setResponseCode(code)
                            .setBody(if (code == 200) body else "{\"error\":true,\"reason\":\"down\"}")
                    path.startsWith("/7t") ->
                        MockResponse().setResponseCode(200).setBody(resource("seven_timer_astro.json"))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        batchServer.start()
        try {
            val cache = InMemoryForecastCache()
            val source = ForecastRepository(
                openMeteo = OpenMeteoClient(baseUrl = batchServer.url("/om").toString(), clock = clock),
                sevenTimer = SevenTimerClient(baseUrl = batchServer.url("/7t").toString()),
                cache = cache,
                clock = clock,
            )
            // Five coordinates inside the GEM box.
            val coords = listOf(
                43.65 to -79.38,
                44.10 to -79.90,
                45.42 to -75.69,
                45.50 to -73.57,
                43.25 to -79.87,
            )
            val results = source.forecastBatch(coords)
            assertEquals(5, results.size)
            assertTrue(
                "all 5 batched locations should succeed as FRESH, got $results",
                results.all { it.isSuccess && it.getOrThrow().status == ForecastStatus.FRESH },
            )
            val primaryCalls = batchRequests.count { it.startsWith("/om") && "icon_seamless" !in it }
            val secondaryCalls = batchRequests.count { it.startsWith("/om") && "icon_seamless" in it }
            assertEquals("expected 1 batched primary Open-Meteo call, got $batchRequests", 1, primaryCalls)
            assertEquals("expected 1 batched secondary Open-Meteo call, got $batchRequests", 1, secondaryCalls)

            // Every cell must now be individually cached.
            for ((lat, lon) in coords) {
                val cached = cache.get(ForecastRepository.cacheKey(lat, lon))
                assertTrue("missing cache entry for $lat,$lon", cached != null && cached.hours.size == 24)
            }

            // Advance past cache age and fail the batch with HTTP 429:
            // previously cached cells fall back to STALE, while a brand-new cell fails cleanly.
            clock.now = clock.now.plus(Duration.ofHours(2))
            primaryCode.set(429)
            val mixed = listOf(coords[0], 46.81 to -71.21)
            val fallback = source.forecastBatch(mixed)
            assertEquals(ForecastStatus.STALE, fallback[0].getOrThrow().status)
            assertTrue("uncached cell should fail on 429", fallback[1].isFailure)
            assertEquals(ForecastProblem.BUSY, (fallback[1].exceptionOrNull() as WeatherApiException).problem)
        } finally {
            batchServer.shutdown()
        }
    }
}
