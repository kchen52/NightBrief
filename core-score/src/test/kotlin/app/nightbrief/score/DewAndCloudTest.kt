package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DewAndCloudTest {
    private val start = Instant.parse("2024-08-13T03:00:00Z")

    private fun hour(offsetHours: Long, temperature: Double?, dewPoint: Double?) = DewSample(
        start.plusSeconds(offsetHours * 3600),
        temperature,
        dewPoint,
    )

    @Test
    fun spreadOfTwoDegreesIsDewAndTheLineNamesThatHour() {
        val outlook = DewRisk.assess(
            listOf(
                hour(0, temperature = 12.0, dewPoint = 8.0),
                hour(1, temperature = 10.0, dewPoint = 8.0),
            ),
        )
        assertEquals(start.plusSeconds(3600), outlook.dewFrom)
        assertNull(outlook.frostFrom)
        assertEquals(
            "Dew likely from 23:40 — bring a heater",
            DewRisk.line(outlook) { "23:40" },
        )
    }

    @Test
    fun aWiderSpreadIsNotDew() {
        val outlook = DewRisk.assess(listOf(hour(0, temperature = 10.0, dewPoint = 7.9)))
        assertNull(outlook.dewFrom)
        assertNull(DewRisk.line(outlook) { "23:40" })
    }

    @Test
    fun freezingAirIsFrostFromThatHour() {
        val outlook = DewRisk.assess(listOf(hour(0, temperature = 0.0, dewPoint = -1.0)))
        assertEquals(start, outlook.frostFrom)
        assertEquals(
            "Frost likely from 02:10 — bring a heater",
            DewRisk.line(outlook) { "02:10" },
        )
    }

    @Test
    fun dewThatLaterFreezesMentionsBothTimes() {
        val outlook = DewRisk.assess(
            listOf(
                hour(0, temperature = 3.0, dewPoint = 2.0),
                hour(2, temperature = -1.0, dewPoint = -1.5),
            ),
        )
        assertTrue(outlook.frostFrom!!.isAfter(outlook.dewFrom))
        assertEquals(
            "Dew likely from 23:40 — frost from 02:10 — bring a heater",
            DewRisk.line(outlook) { instant ->
                if (instant == outlook.dewFrom) "23:40" else "02:10"
            },
        )
    }

    @Test
    fun overnightLowUsesTheColdestHourAndAMinusSign() {
        val outlook = DewRisk.assess(
            listOf(
                hour(0, temperature = 1.2, dewPoint = 1.0),
                hour(1, temperature = -3.6, dewPoint = null),
            ),
        )
        assertEquals(-3.6, outlook.overnightLowC!!, 0.0)
        assertEquals("Dress for −4 °C", DewRisk.dressLine(outlook))
        assertEquals(start, outlook.dewFrom)
        assertNull(outlook.frostFrom)
    }

    @Test
    fun noTemperaturesHaveNothingToSay() {
        val outlook = DewRisk.assess(listOf(hour(0, temperature = null, dewPoint = null)))
        assertFalse(outlook.hasContent)
        assertNull(DewRisk.dressLine(outlook))
    }

    @Test
    fun highCloudThatMakesUpMostOfTheTotalIsThinCirrus() {
        val reason = CloudLayers.reason(
            listOf(
                CloudSample(total = 40, low = 5, mid = 8, high = 35),
                CloudSample(total = 40, low = 4, mid = 6, high = 36),
            ),
        )
        assertEquals("high thin cloud", reason)
    }

    @Test
    fun lowStratusIsNotCalledHighThinCloud() {
        assertNull(
            CloudLayers.reason(listOf(CloudSample(total = 40, low = 38, mid = 10, high = 8))),
        )
    }

    @Test
    fun aClearNightAndATieDoNotGetACloudReason() {
        assertNull(CloudLayers.reason(listOf(CloudSample(total = 8, low = 0, mid = 0, high = 8))))
        assertNull(CloudLayers.reason(listOf(CloudSample(total = 40, low = 20, mid = 20, high = 20))))
        assertNull(CloudLayers.reason(emptyList()))
    }

    @Test
    fun planningAttachesDewAndCloudWithoutChangingTheScore() {
        val site = Site("home", "Home", 43.6532, -79.3832, "America/Toronto", bortle = 4)
        val date = LocalDate.of(2024, 8, 10)
        val kit = GearCatalog.exampleKit
        val plain = NightPlanner.plan(site, date, hours(site, temperature = null, dewPoint = null, high = null), kit)
        val dewy = NightPlanner.plan(site, date, hours(site, temperature = -4.2, dewPoint = -5.0, high = 36), kit)

        assertEquals(plain.scoreValue, dewy.scoreValue)
        assertEquals(plain.score!!.factors, dewy.score!!.factors)
        assertEquals("high thin cloud", dewy.cloudReason)
        val line = DewRisk.line(dewy.dew!!) {
            DigestComposer.DEFAULT_TIME_FORMAT.withZone(site.zone).format(it)
        }
        assertTrue(line, line!!.startsWith("Frost likely from ") && line.endsWith(" — bring a heater"))
        assertEquals("Dress for −4 °C", DewRisk.dressLine(dewy.dew!!))
        assertTrue(dewy.timeline.all { it.cloudLow == 4 && it.cloudMid == 6 && it.cloudHigh == 36 })

        val digest = DigestComposer.compose(dewy, emptyList())
        assertTrue(digest.lines.any { it.startsWith("Frost likely from ") && it.endsWith(" — bring a heater") })
        assertEquals(listOf("Dress for −4 °C"), digest.lines.filter { it.startsWith("Dress for") })
        assertFalse(digest.summary.startsWith("Frost"))
        assertFalse(digest.lines.contains("high thin cloud"))
    }

    private fun hours(site: Site, temperature: Double?, dewPoint: Double?, high: Int?): Forecast {
        val epoch = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        return Forecast(
            site.latitude, site.longitude, "gem_seamless", epoch,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = epoch + it * 3600L,
                    cloudCover = 40,
                    cloudLow = if (high == null) null else 4,
                    cloudMid = if (high == null) null else 6,
                    cloudHigh = high,
                    humidity = 40,
                    temperatureC = temperature,
                    dewPointC = dewPoint,
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
