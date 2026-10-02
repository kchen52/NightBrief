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
import java.time.ZoneId

class DewRiskTest {
    private val kit = GearCatalog.exampleKit
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun spreadOfTwoIsDewAndJustWiderIsDry() {
        assertEquals(Condensation.DEW, DewRisk.kindOf(10.0, 8.0))
        assertEquals(Condensation.DEW, DewRisk.kindOf(8.0, 6.0))
        assertNull(DewRisk.kindOf(10.0, 7.99))
        assertNull(DewRisk.kindOf(12.0, 9.0))
    }

    @Test
    fun freezingIncludingZeroIsFrost() {
        assertEquals(Condensation.FROST, DewRisk.kindOf(0.0, -1.0))
        assertEquals(Condensation.FROST, DewRisk.kindOf(-2.0, -3.0))
        assertEquals(Condensation.DEW, DewRisk.kindOf(0.1, -1.0))
        assertNull(DewRisk.kindOf(-4.0, -10.0))
    }

    @Test
    fun missingReadingIsNotFlagged() {
        assertNull(DewRisk.kindOf(null, 0.0))
        assertNull(DewRisk.kindOf(1.0, null))
        assertNull(DewRisk.fromDarkHours(emptyList()))
        assertNull(
            DewRisk.fromDarkHours(
                listOf(HourlyWeather(epochSecond = 0, cloudCover = 10)),
            ),
        )
    }

    @Test
    fun onsetIsTheEarliestFlaggedHourEvenWhenALaterHourFreezes() {
        val dew = Instant.parse("2024-08-13T03:00:00Z")
        val frost = dew.plusSeconds(3600)
        val hours = listOf(
            HourlyWeather(frost.epochSecond, temperatureC = -1.0, dewPointC = -2.0),
            HourlyWeather(dew.epochSecond, temperatureC = 4.0, dewPointC = 3.0),
        )
        val outlook = DewRisk.fromDarkHours(hours)!!
        assertEquals(dew, outlook.onset!!.time)
        assertEquals(Condensation.DEW, outlook.onset!!.kind)
        assertEquals(-1.0, outlook.overnightLowC!!, 0.001)
    }

    @Test
    fun copyUses24HourTimeAndAMinusSign() {
        val at = Instant.parse("2024-08-13T03:40:00Z")
        val fmt = DigestComposer.DEFAULT_TIME_FORMAT.withZone(zone)
        val dew = DewOutlook(DewOnset(at, Condensation.DEW), overnightLowC = -4.4)
        assertEquals(
            "Dew likely from 23:40 — bring a heater",
            DewCopy.warning(dew) { fmt.format(it) },
        )
        assertEquals("Dress for −4 °C", DewCopy.dress(dew))

        val frost = DewOutlook(DewOnset(at, Condensation.FROST), overnightLowC = -4.0)
        assertEquals(
            "Frost likely from 23:40 — bring a heater",
            DewCopy.warning(frost) { fmt.format(it) },
        )
        assertEquals("−4 °C", DewCopy.formatCelsius(-4.5))
        assertEquals("−5 °C", DewCopy.formatCelsius(-4.6))
        assertEquals("3 °C", DewCopy.formatCelsius(2.5))
        assertEquals("0 °C", DewCopy.formatCelsius(0.0))
    }

    @Test
    fun plannerIgnoresTwilightFrostAndDoesNotChangeTheScore() {
        val plain = NightPlanner.plan(home, date, forecast(), kit)
        val dark = plain.timeline.filter { it.isDark }.map { it.time.epochSecond }
        assertTrue("August night at Toronto should have several dark hours", dark.size >= 3)
        val onset = dark.first()
        val cold = dark[dark.size / 2]
        val withDew = forecast { epoch ->
            when {
                epoch == onset -> 8.0 to 6.0
                epoch == cold -> -4.2 to 10.0
                epoch in dark -> 3.0 to 0.0
                else -> 0.0 to 0.0
            }
        }
        val report = NightPlanner.plan(home, date, withDew, kit)
        assertEquals(plain.scoreValue, report.scoreValue)
        assertEquals(plain.score!!.factors, report.score!!.factors)
        val dew = report.dew!!
        assertEquals(Instant.ofEpochSecond(onset), dew.onset!!.time)
        assertEquals(Condensation.DEW, dew.onset!!.kind)
        assertEquals(-4.2, dew.overnightLowC!!, 0.001)
    }

    @Test
    fun digestAddsDewAndDressWithoutLeading() {
        val plain = NightPlanner.plan(home, date, forecast(), kit)
        val dark = plain.timeline.filter { it.isDark }.map { it.time.epochSecond }.toSet()
        val onset = dark.first()
        val report = NightPlanner.plan(home, date, forecast { epoch ->
            when {
                epoch == onset -> -4.0 to -5.0
                epoch in dark -> 2.0 to -5.0
                else -> 15.0 to 0.0
            }
        }, kit)
        assertEquals(Condensation.FROST, report.dew!!.onset!!.kind)
        assertEquals(plain.scoreValue, report.scoreValue)
        val digest = DigestComposer.compose(report, emptyList())
        val warning = DewCopy.warning(report.dew!!) { DigestComposer.DEFAULT_TIME_FORMAT.withZone(zone).format(it) }
        assertEquals("Frost likely from ${DigestComposer.DEFAULT_TIME_FORMAT.withZone(zone).format(report.dew!!.onset!!.time)} — bring a heater", warning)
        assertTrue(digest.lines.contains(warning))
        assertTrue(digest.lines.contains("Dress for −4 °C"))
        assertTrue(digest.summary.startsWith("Go:") || digest.summary.startsWith("Maybe:") || digest.summary.startsWith("No-go:"))
        assertFalse(digest.lines.first().contains("heater"))
    }

    @Test
    fun temperatureWithoutDewPointStillDressesForTheLow() {
        val plain = NightPlanner.plan(home, date, forecast(), kit)
        val dark = plain.timeline.filter { it.isDark }.map { it.time.epochSecond }.toSet()
        val report = NightPlanner.plan(home, date, forecast { epoch ->
            if (epoch in dark) -3.6 to null else 20.0 to 19.0
        }, kit)
        val dew = report.dew!!
        assertNull(dew.onset)
        assertEquals(-3.6, dew.overnightLowC!!, 0.001)
        assertNull(DewCopy.warning(dew) { it.toString() })
        assertEquals("Dress for −4 °C", DewCopy.dress(dew))
        val lines = DigestComposer.compose(report, emptyList()).lines
        assertTrue(lines.contains("Dress for −4 °C"))
        assertTrue(lines.none { it.contains("heater") })
    }

    @Test
    fun noTemperatureOmitsDew() {
        val report = NightPlanner.plan(home, date, forecast(), kit)
        assertNull(report.dew)
        val lines = DigestComposer.compose(report, emptyList()).lines
        assertTrue(lines.none { it.contains("heater") || it.startsWith("Dress for") })
    }

    private fun forecast(temps: ((Long) -> Pair<Double, Double?>)? = null): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude, home.longitude, "best_match", start,
            (0 until 72).map { i ->
                val epoch = start + i * 3600L
                val pair = temps?.invoke(epoch)
                HourlyWeather(
                    epochSecond = epoch,
                    cloudCover = 5,
                    humidity = 40,
                    windKmh = 6.0,
                    gustKmh = 8.0,
                    jetStreamKmh = 70.0,
                    seeing = 2,
                    transparency = 2,
                    temperatureC = pair?.first,
                    dewPointC = pair?.second,
                )
            },
        )
    }
}
