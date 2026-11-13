package app.nightbrief.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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

class MpcCometClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun elementRowIsAcceptedAndTheUserAgentIsSent() = runTest {
        server.enqueue(MockResponse().setBody(ROW))
        val clock = Clock.fixed(FETCHED, ZoneOffset.UTC)
        val file = MpcCometClient(url = server.url("/Soft00Cmt.txt").toString(), clock = clock).fetch()
        assertEquals(ROW, file.text)
        assertEquals(FETCHED, file.fetchedAt)
        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("Soft00Cmt.txt"))
        assertEquals("NightBrief/0.1", request.getHeader("User-Agent"))
    }

    @Test
    fun htmlAndAShortBodyThrow() {
        val html = "<html><body>down</body></html>"
        val error = runCatching { MpcCometClient().parse(html) }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
        assertTrue(error?.message?.contains("observable-comets") == true)
        assertTrue(runCatching { MpcCometClient().parse("not elements\n") }.exceptionOrNull() is WeatherApiException)
    }

    @Test
    fun http500ThrowsWeatherApiException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("unavailable"))
        val error = runCatching {
            MpcCometClient(url = server.url("/Soft00Cmt.txt").toString()).fetch()
        }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun cacheSkipsAFreshHitAndServesAStaleFileWhenTheOriginFails() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-comets").resolve("nested/observable.txt").toFile()
        val origin = MpcCometClient(url = server.url("/Soft00Cmt.txt").toString(), clock = clock)
        val source = CachingCometSource(
            origin = origin,
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            staleMaxAge = Duration.ofDays(7),
            clock = clock,
        )
        server.enqueue(MockResponse().setBody(ROW))
        val first = withContext(Dispatchers.IO) { source.fetch() }
        assertTrue(first.text.contains("C/2023 A3"))
        assertEquals(1, server.requestCount)

        val second = withContext(Dispatchers.IO) { source.fetch() }
        assertEquals(FETCHED, second.fetchedAt)
        assertEquals(1, server.requestCount)

        clock.instant = FETCHED.plus(Duration.ofHours(2))
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val stale = withContext(Dispatchers.IO) { source.fetch() }
        assertEquals(ROW, stale.text)
        assertEquals(FETCHED, stale.fetchedAt)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun cacheOlderThanTheStaleLimitRethrowsAndACorruptFileIsReplaced() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-comets-stale").resolve("observable.txt").toFile()
        val source = CachingCometSource(
            origin = MpcCometClient(url = server.url("/Soft00Cmt.txt").toString(), clock = clock),
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            staleMaxAge = Duration.ofDays(7),
            clock = clock,
        )
        server.enqueue(MockResponse().setBody(ROW))
        withContext(Dispatchers.IO) { source.fetch() }

        clock.instant = FETCHED.plus(Duration.ofDays(7)).plusSeconds(1)
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val expired = runCatching { withContext(Dispatchers.IO) { source.fetch() } }.exceptionOrNull()
        assertTrue(expired is WeatherApiException)

        cacheFile.writeText("garbage")
        clock.instant = FETCHED.plus(Duration.ofHours(2))
        server.enqueue(MockResponse().setBody(ROW))
        val replaced = withContext(Dispatchers.IO) { source.fetch() }
        assertTrue(replaced.text.contains("C/2023 A3"))
        assertTrue(cacheFile.readText().startsWith("v1\n"))
    }

    @Test
    fun missingCachePlusOriginFailureThrows() = runTest {
        val cacheFile = Files.createTempDirectory("nightbrief-comets-miss").resolve("missing/observable.txt").toFile()
        server.enqueue(MockResponse().setResponseCode(500).setBody("down"))
        val source = CachingCometSource(
            origin = MpcCometClient(url = server.url("/Soft00Cmt.txt").toString()),
            cacheFile = cacheFile,
        )
        val error = runCatching { source.fetch() }.exceptionOrNull()
        assertTrue(error is WeatherApiException)
    }

    @Test
    fun originTimeoutServesStaleWithoutWaiting() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-comets-timeout").resolve("observable.txt").toFile()
        CachingCometSource(
            origin = FakeComets(Result.success(CometElementsFile(ROW, FETCHED))),
            cacheFile = cacheFile,
            clock = clock,
        ).fetch()

        clock.instant = FETCHED.plus(Duration.ofHours(2))
        val hanging = CachingCometSource(
            origin = object : CometSource {
                override suspend fun fetch(): CometElementsFile {
                    delay(Duration.ofMinutes(5).toMillis())
                    return CometElementsFile(ROW, clock.instant)
                }
            },
            cacheFile = cacheFile,
            maxAge = Duration.ofHours(1),
            originTimeout = Duration.ofSeconds(5),
            clock = clock,
        )
        val stale = hanging.fetch()
        assertEquals(FETCHED, stale.fetchedAt)
        assertTrue(stale.text.contains("C/2023 A3"))
    }

    @Test
    fun failureCooldownSkipsTheNetworkUntilItPasses() = runTest {
        val clock = SettableClock(FETCHED)
        val cacheFile = Files.createTempDirectory("nightbrief-comets-cooldown").resolve("observable.txt").toFile()
        val origin = FakeComets(Result.failure(WeatherApiException("mpc down")))
        val source = CachingCometSource(
            origin = origin,
            cacheFile = cacheFile,
            failureCooldown = Duration.ofMinutes(10),
            clock = clock,
        )
        assertTrue(runCatching { source.fetch() }.exceptionOrNull() is WeatherApiException)
        assertEquals(1, origin.calls)
        assertTrue(runCatching { source.fetch() }.exceptionOrNull() is WeatherApiException)
        assertEquals(1, origin.calls)
        clock.instant = FETCHED.plus(Duration.ofMinutes(11))
        assertTrue(runCatching { source.fetch() }.exceptionOrNull() is WeatherApiException)
        assertEquals(2, origin.calls)
    }

    @Test
    fun defaultUrlIsTheObservableCometsFile() {
        assertEquals(
            "https://minorplanetcenter.net/iau/Ephemerides/Comets/Soft00Cmt.txt",
            MpcCometClient.DEFAULT_URL,
        )
    }

    private class FakeComets(var result: Result<CometElementsFile>) : CometSource {
        var calls = 0
        override suspend fun fetch(): CometElementsFile {
            calls++
            return result.getOrThrow()
        }
    }

    private class SettableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    companion object {
        private val FETCHED: Instant = Instant.parse("2024-10-12T12:00:00Z")

        /** One real MPC row, shortened only by trusting the column layout the parser checks. */
        private val ROW = "    CK23A030  2024 09 27.8434  0.391289  1.000183  308.5896   21.6836  139.0978  " +
            "20261112   6.5  3.2  C/2023 A3 (Tsuchinshan-ATLAS)                            MPEC 2026-O98\n"
    }
}
