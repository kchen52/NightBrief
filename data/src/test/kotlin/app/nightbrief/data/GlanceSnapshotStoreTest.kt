package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.NightPlanner
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GlanceSnapshotStoreTest {
    private val site = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2026, 8, 12)

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    private fun sampleReport(forSite: Site = site) = NightPlanner.plan(
        site = forSite,
        date = date,
        forecast = Forecast(
            forSite.latitude, forSite.longitude, "test",
            Instant.parse("2026-08-12T12:00:00Z").epochSecond,
            (0 until 48).map {
                HourlyWeather(
                    epochSecond = Instant.parse("2026-08-12T00:00:00Z").epochSecond + it * 3600L,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        ),
        kit = GearCatalog.exampleKit,
        forecastStatus = ForecastStatus.FRESH,
    )

    @Test
    fun persistsToDiskAndRehydratesInAFreshStore() = runTest {
        val dir = kotlin.io.path.createTempDirectory("glance-snap").toFile()
        try {
            val file = File(dir, "glance_snapshot.json")
            val clock = MutableClock(Instant.parse("2026-08-12T18:00:00Z"))
            val report = sampleReport()

            val writer = GlanceSnapshotStore(file, clock)
            writer.save(site, report)

            val reader = GlanceSnapshotStore(file, clock)
            val snap = reader.currentFor(site, date)
            assertNotNull("expected snapshot rehydrated from disk", snap)
            assertEquals(site.name, snap!!.siteName)
            assertEquals(report.scoreValue, snap.scoreValue)
            assertEquals(report.flavorLine, snap.flavorLine)
            assertEquals(report.forecastStatus, snap.parsedForecastStatus)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun invalidatesOnSiteEditNextDayOrExpiry() = runTest {
        val dir = kotlin.io.path.createTempDirectory("glance-inv").toFile()
        try {
            val file = File(dir, "glance_snapshot.json")
            val clock = MutableClock(Instant.parse("2026-08-12T18:00:00Z"))
            val store = GlanceSnapshotStore(file, clock, maxAge = Duration.ofHours(6))
            store.save(site, sampleReport())

            assertNull(
                "renamed site must not reuse old snapshot",
                store.currentFor(site.copy(name = "Cabin"), date),
            )
            assertNull(
                "moved site must not reuse old snapshot",
                store.currentFor(site.copy(latitude = 44.0), date),
            )
            assertNull(
                "next night must not reuse yesterday's snapshot",
                store.currentFor(site, date.plusDays(1)),
            )

            clock.now = clock.now.plus(Duration.ofHours(7))
            assertNull("expired snapshot must not be served", store.currentFor(site, date))
        } finally {
            dir.deleteRecursively()
        }
    }
}
