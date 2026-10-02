package app.nightbrief.weather

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CachingKpSourceTest {
    private var now = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private class FakeKp(var result: Result<KpForecast> = Result.success(sample())) : KpSource {
        var calls = 0
        override suspend fun fetch(): KpForecast {
            calls++
            return result.getOrThrow()
        }
    }

    @Test
    fun secondFetchWithinTtlReusesTheCachedForecast() = runTest {
        val origin = FakeKp()
        val cached = CachingKpSource(origin, clock = clock)
        val first = cached.fetch()
        now = now.plus(Duration.ofMinutes(30))
        val second = cached.fetch()
        assertEquals("expected 1 origin call, got ${origin.calls}", 1, origin.calls)
        assertSame("second fetch within TTL should reuse the cached forecast, got a fresh object", first, second)
    }

    @Test
    fun fetchAfterTtlRefetches() = runTest {
        val origin = FakeKp()
        val cached = CachingKpSource(origin, clock = clock)
        cached.fetch()
        now = now.plus(Duration.ofMinutes(61))
        cached.fetch()
        assertEquals("expected 2 origin calls, got ${origin.calls}", 2, origin.calls)
    }

    @Test
    fun freshCacheAvoidsTheNetworkEvenWhenTheOriginIsDown() = runTest {
        val origin = FakeKp()
        val cached = CachingKpSource(origin, clock = clock)
        val first = cached.fetch()
        origin.result = Result.failure(WeatherApiException("swpc down"))
        now = now.plus(Duration.ofMinutes(30))
        assertSame(
            "fresh cache should be served without calling the failing origin",
            first,
            cached.fetch(),
        )
        assertEquals("expected 1 origin call, got ${origin.calls}", 1, origin.calls)
    }

    @Test
    fun failureAfterTtlThrowsAndLeavesTheNextFetchUnpoisoned() = runTest {
        val origin = FakeKp()
        val cached = CachingKpSource(origin, clock = clock)
        cached.fetch()
        origin.result = Result.failure(WeatherApiException("swpc down"))
        now = now.plus(Duration.ofHours(2))
        try {
            cached.fetch()
            fail("expected the SWPC failure to throw, it returned a forecast")
        } catch (_: WeatherApiException) {
            // A failed fetch must not fail the briefing upstream; here it must at least throw.
        }
        origin.result = Result.success(sample())
        cached.fetch()
        assertEquals("expected 3 origin calls, got ${origin.calls}", 3, origin.calls)
    }

    companion object {
        private fun sample() = KpForecast(
            listOf(KpSample(Instant.parse("2026-10-01T12:00:00Z").epochSecond, 3.0, KpStatus.PREDICTED)),
        )
    }
}
