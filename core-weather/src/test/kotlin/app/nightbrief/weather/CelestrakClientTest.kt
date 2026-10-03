package app.nightbrief.weather

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CelestrakClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun threeLineBodyParsesTheElementLines() = runTest {
        server.enqueue(MockResponse().setBody(THREE_LINE))
        val clock = Clock.fixed(FETCHED, ZoneOffset.UTC)
        val tle = CelestrakClient(url = server.url("/gp.php").toString(), clock = clock).fetchIss()
        assertEquals(LINE1, tle.line1)
        assertEquals(LINE2, tle.line2)
        assertEquals(FETCHED, tle.fetchedAt)
        assertTrue(server.takeRequest().path.orEmpty().contains("gp.php"))
    }

    @Test
    fun twoLineBodyParses() = runTest {
        server.enqueue(MockResponse().setBody("$LINE1\n$LINE2\n"))
        val tle = CelestrakClient(url = server.url("/tle").toString()).fetchIss()
        assertEquals(LINE1, tle.line1)
        assertEquals(LINE2, tle.line2)
    }

    @Test
    fun http500ThrowsWeatherApiException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("unavailable"))
        val error = runCatching {
            CelestrakClient(url = server.url("/gp.php").toString()).fetchIss()
        }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun malformedBodyThrowsWeatherApiException() = runTest {
        server.enqueue(MockResponse().setBody("ISS (ZARYA)\nnot a tle\n"))
        val error = runCatching {
            CelestrakClient(url = server.url("/gp.php").toString()).fetchIss()
        }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun cacheSkipsAFreshHitAndServesAStaleElementWhenTheOriginFails() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-tle").resolve("nested/iss.tle").toFile()
        val origin = CelestrakClient(url = server.url("/gp.php").toString(), clock = clock)
        val source = CachingTleSource(
            origin = origin,
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            staleMaxAge = Duration.ofDays(7),
            clock = clock,
        )
        server.enqueue(MockResponse().setBody(THREE_LINE))
        val first = source.fetchIss()
        assertEquals(LINE1, first.line1)
        assertEquals(1, server.requestCount)

        val second = source.fetchIss()
        assertEquals(LINE1, second.line1)
        assertEquals(FETCHED, second.fetchedAt)
        assertEquals(1, server.requestCount)

        clock.instant = FETCHED.plus(Duration.ofHours(2))
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val stale = source.fetchIss()
        assertEquals(LINE1, stale.line1)
        assertEquals(LINE2, stale.line2)
        assertEquals(FETCHED, stale.fetchedAt)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun cacheOlderThanTheStaleLimitRethrowsAndACorruptFileIsReplaced() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-tle-stale").resolve("iss.tle").toFile()
        val origin = CelestrakClient(url = server.url("/gp.php").toString(), clock = clock)
        val source = CachingTleSource(
            origin = origin,
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            staleMaxAge = Duration.ofDays(7),
            clock = clock,
        )
        server.enqueue(MockResponse().setBody(THREE_LINE))
        source.fetchIss()

        clock.instant = FETCHED.plus(Duration.ofDays(7))
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val stillUsable = source.fetchIss()
        assertEquals(LINE1, stillUsable.line1)

        clock.instant = FETCHED.plus(Duration.ofDays(7)).plusSeconds(1)
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val expired = runCatching { source.fetchIss() }.exceptionOrNull()
        assertTrue(expired is WeatherApiException)

        cacheFile.writeText("garbage")
        clock.instant = FETCHED.plus(Duration.ofHours(2))
        server.enqueue(MockResponse().setBody("$LINE1\n$LINE2\n"))
        val replaced = source.fetchIss()
        assertEquals(LINE2, replaced.line2)
        assertTrue(cacheFile.readText().startsWith("v1\n"))
    }

    @Test
    fun badChecksumThrowsWeatherApiException() = runTest {
        val bad = LINE1.dropLast(1) + ((LINE1.last().digitToInt() + 1) % 10)
        server.enqueue(MockResponse().setBody("$bad\n$LINE2\n"))
        val error = runCatching {
            CelestrakClient(url = server.url("/gp.php").toString()).fetchIss()
        }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun missingCachePlusOriginFailureThrows() = runTest {
        val cacheFile = Files.createTempDirectory("nightbrief-tle-miss").resolve("does-not-exist/iss.tle").toFile()
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val source = CachingTleSource(
            origin = CelestrakClient(url = server.url("/gp.php").toString()),
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
        )
        val error = runCatching { source.fetchIss() }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun concurrentFetchesShareOneOriginCall() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-tle-shared").resolve("iss.tle").toFile()
        val origin = FakeTle(Result.success(IssTle(LINE1, LINE2, FETCHED)))
        val source = CachingTleSource(origin = origin, cacheFile = cacheFile, clock = clock)
        coroutineScope {
            repeat(5) { launch { source.fetchIss() } }
        }
        assertEquals("expected 1 origin call, got ${origin.calls}", 1, origin.calls)
    }

    @Test
    fun originTimeoutServesStaleWithoutWaiting() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-tle-timeout").resolve("iss.tle").toFile()
        CachingTleSource(
            origin = FakeTle(Result.success(IssTle(LINE1, LINE2, FETCHED))),
            cacheFile = cacheFile,
            clock = clock,
        ).fetchIss()

        clock.instant = FETCHED.plus(Duration.ofHours(2))
        val hanging = CachingTleSource(
            origin = object : TleSource {
                override suspend fun fetchIss(): IssTle {
                    delay(Duration.ofMinutes(5).toMillis())
                    return IssTle(LINE1, LINE2, clock.instant)
                }
            },
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            originTimeout = Duration.ofSeconds(5),
            clock = clock,
        )
        val stale = hanging.fetchIss()
        assertEquals("a hanging origin should fall back to the stale element, got ${stale.line1}", LINE1, stale.line1)
        assertEquals(FETCHED, stale.fetchedAt)
    }

    @Test
    fun failureCooldownSkipsTheNetworkUntilItPasses() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-tle-cooldown").resolve("iss.tle").toFile()
        val origin = FakeTle(Result.failure(WeatherApiException("celestrak down")))
        val source = CachingTleSource(
            origin = origin,
            cacheFile = cacheFile,
            failureCooldown = Duration.ofMinutes(10),
            clock = clock,
        )
        assertTrue(runCatching { source.fetchIss() }.exceptionOrNull() is WeatherApiException)
        assertEquals("expected 1 origin call, got ${origin.calls}", 1, origin.calls)
        assertTrue(runCatching { source.fetchIss() }.exceptionOrNull() is WeatherApiException)
        assertEquals("expected no origin call inside the cooldown, got ${origin.calls}", 1, origin.calls)
        clock.instant = FETCHED.plus(Duration.ofMinutes(11))
        assertTrue(runCatching { source.fetchIss() }.exceptionOrNull() is WeatherApiException)
        assertEquals("expected a retry after the cooldown, got ${origin.calls}", 2, origin.calls)
    }

    private class FakeTle(var result: Result<IssTle>) : TleSource {
        var calls = 0
        override suspend fun fetchIss(): IssTle {
            calls++
            return result.getOrThrow()
        }
    }

    @Test
    fun defaultUrlMatchesTheDocumentedQueryForm() {
        assertEquals(
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE",
            CelestrakClient.DEFAULT_URL,
        )
    }

    @Test
    fun csvBodyThrowsASelfDescribingError() {
        val csv = "OBJECT_NAME,OBJECT_ID,EPOCH\nISS (ZARYA),1998-067A,2026-10-02T11:10:18.655680\n"
        val error = runCatching { CelestrakClient().parse(csv) }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
        assertTrue("expected a CSV-specific message, got: ${error?.message}", error?.message?.contains("CSV") == true)
    }

    private class SettableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    companion object {
        private val FETCHED: Instant = Instant.parse("2024-03-14T12:00:00Z")
        private const val LINE1 = "1 25544U 98067A   24074.41191032  .00014407  00000+0  26068-3 0  9998"
        private const val LINE2 = "2 25544  51.6400  65.1219 0006168   5.0268 148.9614 15.49907257443850"
        private val THREE_LINE = "ISS (ZARYA)\n$LINE1\n$LINE2\n"
    }
}
