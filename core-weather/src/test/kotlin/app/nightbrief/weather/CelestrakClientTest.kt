package app.nightbrief.weather

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
