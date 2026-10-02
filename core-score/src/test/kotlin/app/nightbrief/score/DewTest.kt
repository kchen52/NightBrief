package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import app.nightbrief.weather.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DewTest {
    private val kit = GearCatalog.exampleKit
    private val date = LocalDate.of(2024, 8, 10)
    private val toronto = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val zone = ZoneId.of("America/Toronto")
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)

    @Test
    fun spreadOfTwoFlagsAndJustOverDoesNot() {
        val hour = Instant.parse("2024-08-11T03:00:00Z") // 23:00 EDT
        val dew = assess(hour, temperature = 8.0, dewPoint = 6.0)
        assertEquals(hour, dew.from)
        assertNull(dew.frostFrom)
        assertEquals("Dew likely from 23:00 — bring a heater", DewCopy.riskLine(dew) { clock.format(it) })
        assertFalse(assess(hour, temperature = 8.0, dewPoint = 5.99).risk)
    }

    @Test
    fun freezingSaturatedHourIsFrostAndExactZeroCounts() {
        val hour = Instant.parse("2024-08-11T06:00:00Z") // 02:00 EDT
        val frost = assess(hour, temperature = 0.0, dewPoint = -1.0)
        assertEquals(hour, frost.frostFrom)
        assertEquals("Frost likely from 02:00 — bring a heater", DewCopy.riskLine(frost) { clock.format(it) })

        val above = assess(hour, temperature = 0.1, dewPoint = -1.0)
        assertNull(above.frostFrom)
        assertTrue(above.risk)
    }

    @Test
    fun dewThatLaterFreezesNamesBothTimes() {
        val dewHour = Instant.parse("2024-08-11T03:00:00Z")
        val frostHour = Instant.parse("2024-08-11T06:00:00Z")
        val outlook = Dew.assess(
            darkHours = listOf(dewHour, frostHour),
            overnightHours = listOf(dewHour, frostHour),
            forecast = forecast(
                dewHour to hour(dewHour, temperature = 3.0, dewPoint = 2.0),
                frostHour to hour(frostHour, temperature = -4.0, dewPoint = -5.0),
            ),
        )!!
        assertEquals(dewHour, outlook.from)
        assertEquals(frostHour, outlook.frostFrom)
        assertEquals(
            "Dew likely from 23:00, frost from 02:00 — bring a heater",
            DewCopy.riskLine(outlook) { clock.format(it) },
        )
        assertEquals("Dress for −4 °C", DewCopy.lowLine(outlook))
    }

    @Test
    fun dryColdIsNotFrostAndAMissingDewPointStillReportsTheLow() {
        val hour = Instant.parse("2024-08-11T06:00:00Z")
        val dry = assess(hour, temperature = -6.0, dewPoint = -20.0)
        assertFalse(dry.risk)
        assertFalse(dry.frost)
        assertTrue(dry.spreadKnown)
        assertEquals(-6.0, dry.overnightLowC, 0.001)
        assertEquals("Dress for −6 °C", DewCopy.lowLine(dry))
        assertNull(DewCopy.riskLine(dry) { clock.format(it) })

        val noDewPoint = Dew.assess(
            darkHours = listOf(hour),
            overnightHours = listOf(hour),
            forecast = forecast(hour to hour(hour, temperature = -3.4, dewPoint = null)),
        )!!
        assertFalse(noDewPoint.spreadKnown)
        assertFalse(noDewPoint.risk)
        assertEquals("Dress for −3 °C", DewCopy.lowLine(noDewPoint))
        assertTrue(DewCopy.detail(noDewPoint).contains("no dew point"))
    }

    @Test
    fun celsiusRoundsHalfAwayFromZero() {
        assertEquals("−4 °C", DewCopy.formatCelsius(-4.0))
        assertEquals("−4 °C", DewCopy.formatCelsius(-3.5))
        assertEquals("−4 °C", DewCopy.formatCelsius(-3.6))
        assertEquals("0 °C", DewCopy.formatCelsius(-0.4))
        assertEquals("2 °C", DewCopy.formatCelsius(2.2))
        assertEquals("3 °C", DewCopy.formatCelsius(2.5))
    }

    @Test
    fun plannerUsesDarkHoursForDewAndSunsetToSunriseForTheLow() {
        val eph = NightEphemeris.compute(date, toronto.zone, toronto.latitude, toronto.longitude)
        val dark = eph.darkWindow!!
        val darkHours = eph.hourly.map { it.time }.filter { it in dark }
        assertTrue("need several dark hours", darkHours.size >= 4)
        val twilight = eph.hourly.map { it.time }.first { it !in dark }
        val beforeSunset = eph.sunset!!.minusSeconds(4 * 3600).truncatedTo(ChronoUnit.HOURS)
        assertTrue(eph.hourly.none { it.time == beforeSunset })

        val onset = darkHours[1]
        val frost = darkHours[3]
        val hours = mutableMapOf<Instant, HourlyWeather>()
        for (sample in eph.hourly) {
            hours[sample.time] = hour(sample.time, temperature = 12.0, dewPoint = 0.0)
        }
        hours[beforeSunset] = hour(beforeSunset, temperature = -40.0, dewPoint = -40.0)
        hours[twilight] = hour(twilight, temperature = -8.0, dewPoint = 0.0)
        hours[onset] = hour(onset, temperature = 2.0, dewPoint = 1.0)
        hours[frost] = hour(frost, temperature = -1.0, dewPoint = -2.0)

        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        val base = (0 until 96).map { offset ->
            val time = Instant.ofEpochSecond(start + offset * 3600L)
            hours[time] ?: HourlyWeather(
                epochSecond = time.epochSecond,
                cloudCover = 10,
                humidity = 50,
                windKmh = 8.0,
                temperatureC = 20.0,
                dewPointC = 5.0,
            )
        }
        val wet = Forecast(toronto.latitude, toronto.longitude, "best_match", start, base)
        val dry = Forecast(
            toronto.latitude, toronto.longitude, "best_match", start,
            base.map { it.copy(temperatureC = null, dewPointC = null) },
        )

        val plain = NightPlanner.plan(toronto, date, dry, kit)
        val dewy = NightPlanner.plan(toronto, date, wet, kit)
        assertNotNull(plain.scoreValue)
        assertEquals(plain.scoreValue, dewy.scoreValue)
        assertNull(plain.dew)
        assertEquals(onset, dewy.dew!!.from)
        assertEquals(frost, dewy.dew!!.frostFrom)
        assertEquals(-8.0, dewy.dew!!.overnightLowC, 0.001)

        val digest = DigestComposer.compose(dewy, emptyList())
        assertFalse(digest.summary.contains("heater"))
        assertTrue(digest.lines.any { it == DewCopy.riskLine(dewy.dew!!) { clock.format(it) } })
        assertEquals("Dress for −8 °C", digest.lines.last())
    }

    @Test
    fun staleBannerStaysLastAndAMissingForecastOmitsDew() {
        val hour = Instant.parse("2024-08-11T03:00:00Z")
        val outlook = assess(hour, temperature = 4.0, dewPoint = 3.0)
        val report = NightPlanner.plan(toronto, date, forecast(hour to hour(hour, 4.0, 3.0)), kit)
            .copy(dew = outlook, forecastStatus = ForecastStatus.STALE)
        val lines = DigestComposer.compose(report, emptyList()).lines
        assertEquals("Offline — showing the last saved forecast", lines.last())
        assertTrue(lines.any { it.startsWith("Dew likely from") })

        assertNull(Dew.assess(listOf(hour), listOf(hour), forecast = null))
        assertNull(NightPlanner.plan(toronto, date, null, kit).dew)
    }

    private fun assess(hour: Instant, temperature: Double, dewPoint: Double): DewOutlook =
        Dew.assess(
            darkHours = listOf(hour),
            overnightHours = listOf(hour),
            forecast = forecast(hour to hour(hour, temperature, dewPoint)),
        )!!

    private fun hour(time: Instant, temperature: Double, dewPoint: Double?): HourlyWeather =
        HourlyWeather(
            epochSecond = time.truncatedTo(ChronoUnit.HOURS).epochSecond,
            cloudCover = 10,
            humidity = 50,
            windKmh = 8.0,
            temperatureC = temperature,
            dewPointC = dewPoint,
        )

    private fun forecast(vararg hours: Pair<Instant, HourlyWeather>): Forecast =
        Forecast(
            toronto.latitude,
            toronto.longitude,
            "best_match",
            hours.first().second.epochSecond,
            hours.map { it.second },
        )
}
