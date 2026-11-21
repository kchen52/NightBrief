package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Guards the single-pass solar scan against the independent scans it replaced.
 * The reference below deliberately uses the older [Ephemeris.crossings] /
 * [Ephemeris.intervalsBelow] path so a change in either implementation is caught.
 */
class NightEphemerisSinglePassTest {
    private val cases = listOf(
        Case(LocalDate.of(2024, 6, 21), "America/Toronto", 43.6532, -79.3832),
        Case(LocalDate.of(2024, 3, 10), "America/Toronto", 43.6532, -79.3832),
        Case(LocalDate.of(2024, 11, 3), "America/Toronto", 43.6532, -79.3832),
        Case(LocalDate.of(2024, 3, 20), "America/Guayaquil", 0.18, -78.47),
        Case(LocalDate.of(2024, 6, 21), "Europe/Oslo", 69.65, 18.96),
        Case(LocalDate.of(2024, 12, 21), "Europe/Oslo", 69.65, 18.96),
        Case(LocalDate.of(2024, 8, 10), "America/Vancouver", 49.28, -123.12) { az ->
            if (az in 90.0..270.0) 3.0 else 0.0
        },
    )

    @Test
    fun singlePassSolarScanMatchesIndependentScans() {
        for (case in cases) {
            val actual = case.compute()
            val start = case.date.atTime(LocalTime.NOON).atZone(case.zone).toInstant()
            val end = case.date.plusDays(1).atTime(LocalTime.NOON).atZone(case.zone).toInstant()
            val sunAlt = { t: Instant -> Ephemeris.sunAltitude(t, case.latitude, case.longitude) }
            fun firstSetting(threshold: Double) =
                Ephemeris.crossings(start, end, threshold, altitude = sunAlt).firstOrNull { !it.rising }?.time
            fun lastRising(threshold: Double) =
                Ephemeris.crossings(start, end, threshold, altitude = sunAlt).lastOrNull { it.rising }?.time
            fun longestBelow(threshold: Double) =
                Ephemeris.intervalsBelow(start, end, threshold, altitude = sunAlt).maxByOrNull { it.duration }

            val astro = longestBelow(Ephemeris.ASTRONOMICAL_TWILIGHT)
            val nautical = longestBelow(Ephemeris.NAUTICAL_TWILIGHT)
            val civil = longestBelow(Ephemeris.CIVIL_TWILIGHT)
            val (darkness, darkWindow) = when {
                astro != null -> Darkness.ASTRONOMICAL to astro
                nautical != null -> Darkness.NAUTICAL to nautical
                civil != null -> Darkness.CIVIL to civil
                else -> Darkness.NONE to null
            }
            val where = "${case.date} ${case.zone}"
            assertEquals("$where sunset", firstSetting(Ephemeris.HORIZON_ALTITUDE), actual.sunset)
            assertEquals("$where sunrise", lastRising(Ephemeris.HORIZON_ALTITUDE), actual.sunrise)
            assertEquals("$where nautical dusk", firstSetting(Ephemeris.NAUTICAL_TWILIGHT), actual.nauticalDusk)
            assertEquals("$where nautical dawn", lastRising(Ephemeris.NAUTICAL_TWILIGHT), actual.nauticalDawn)
            assertEquals("$where astronomical dusk", firstSetting(Ephemeris.ASTRONOMICAL_TWILIGHT), actual.astronomicalDusk)
            assertEquals("$where astronomical dawn", lastRising(Ephemeris.ASTRONOMICAL_TWILIGHT), actual.astronomicalDawn)
            assertEquals("$where darkness", darkness, actual.darkness)
            assertEquals("$where dark window", darkWindow, actual.darkWindow)
        }
    }

    private data class Case(
        val date: LocalDate,
        val zoneName: String,
        val latitude: Double,
        val longitude: Double,
        val horizon: (Double) -> Double = { 0.0 },
    ) {
        val zone: ZoneId get() = ZoneId.of(zoneName)

        fun compute(): NightEphemeris = NightEphemeris.compute(
            date, zone, latitude, longitude, horizonObstructionDeg = horizon,
        )
    }
}
