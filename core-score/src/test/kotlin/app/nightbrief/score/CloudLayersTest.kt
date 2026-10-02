package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CloudLayersTest {
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)

    @Test
    fun thinHighCirrusAtFortyPercent() {
        val hours = listOf(hour(total = 40, low = 0, mid = 0, high = 40))
        assertEquals(CloudReason.HIGH_THIN, CloudLayers.reason(hours))
    }

    @Test
    fun lowStratusAtFortyPercentIsNotHighThinCloud() {
        val hours = listOf(hour(total = 40, low = 40, mid = 0, high = 0))
        assertNull(CloudLayers.reason(hours))
    }

    @Test
    fun exactlyHalfTheTotalIsNotMost() {
        assertNull(CloudLayers.reason(listOf(hour(total = 40, low = 0, mid = 0, high = 20))))
        assertEquals(
            CloudReason.HIGH_THIN,
            CloudLayers.reason(listOf(hour(total = 40, low = 10, mid = 5, high = 21))),
        )
    }

    @Test
    fun highDoesNotWinWhenLowOrMidIsAsThick() {
        assertNull(CloudLayers.reason(listOf(hour(total = 50, low = 40, mid = 5, high = 30))))
        assertNull(CloudLayers.reason(listOf(hour(total = 50, low = 5, mid = 40, high = 30))))
    }

    @Test
    fun sumsAcrossHoursAndSkipsAMissingHigh() {
        val mixed = listOf(
            hour(total = 100, low = 0, mid = 0, high = 100),
            hour(total = 100, low = 0, mid = 0, high = 0),
        )
        assertNull(CloudLayers.reason(mixed))
        val mostlyHigh = listOf(
            hour(total = 40, low = 0, mid = 0, high = 40),
            hour(total = 40, low = 0, mid = 0, high = 30),
            HourlyWeather(epochSecond = 3, cloudCover = 90, cloudLow = 90),
        )
        assertEquals(CloudReason.HIGH_THIN, CloudLayers.reason(mostlyHigh))
    }

    @Test
    fun clearSkyAndMissingLayersHaveNoReason() {
        assertNull(CloudLayers.reason(emptyList()))
        assertNull(CloudLayers.reason(listOf(HourlyWeather(epochSecond = 0, cloudCover = 40))))
        assertNull(CloudLayers.reason(listOf(hour(total = 0, low = 0, mid = 0, high = 0))))
    }

    @Test
    fun plannerUsesDarkHoursOnlyAndDoesNotChangeTheScore() {
        val plain = NightPlanner.plan(home, date, forecast(), kit)
        val dark = plain.timeline.filter { it.isDark }.map { it.time.epochSecond }.toSet()
        val high = NightPlanner.plan(home, date, forecast { epoch ->
            if (epoch in dark) Triple(0, 0, 40) else Triple(40, 0, 0)
        }, kit)
        val low = NightPlanner.plan(home, date, forecast { epoch ->
            if (epoch in dark) Triple(40, 0, 0) else Triple(0, 0, 80)
        }, kit)

        assertEquals(plain.scoreValue, high.scoreValue)
        assertEquals(plain.score!!.factors, high.score!!.factors)
        assertEquals(plain.score!!.factors, low.score!!.factors)
        assertEquals(CloudReason.HIGH_THIN, high.cloudReason)
        assertNull(low.cloudReason)
        assertEquals(40, high.timeline.first { it.isDark }.cloudHigh)
        assertEquals(0, high.timeline.first { it.isDark }.cloudLow)
        assertEquals(40, high.timeline.first { !it.isDark && it.cloudLow != null }.cloudLow)
    }

    private fun hour(total: Int, low: Int?, mid: Int?, high: Int?) = HourlyWeather(
        epochSecond = 0,
        cloudCover = total,
        cloudLow = low,
        cloudMid = mid,
        cloudHigh = high,
    )

    /** Total cloud is 40% in every hour. [layers] is low, mid, high for that epoch. */
    private fun forecast(layers: ((Long) -> Triple<Int, Int, Int>)? = null): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                val epoch = start + i * 3600L
                val layer = layers?.invoke(epoch)
                HourlyWeather(
                    epochSecond = epoch,
                    cloudCover = 40,
                    cloudLow = layer?.first,
                    cloudMid = layer?.second,
                    cloudHigh = layer?.third,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                )
            },
        )
    }
}
