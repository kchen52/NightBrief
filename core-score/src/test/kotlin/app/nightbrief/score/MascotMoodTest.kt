package app.nightbrief.score

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import app.nightbrief.weather.KpStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MascotMoodTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 12)

    @Test
    fun verdictPicksTelescopeSquintOrSleep() {
        val base = clearNight()
        assertEquals(MascotMood.AWAKE_TELESCOPE, MascotMood.of(withScore(base, 70)))
        assertEquals(MascotMood.AWAKE_TELESCOPE, MascotMood.of(withScore(base, 100)))
        assertEquals(MascotMood.SQUINTING_AT_CLOUD, MascotMood.of(withScore(base, 69)))
        assertEquals(MascotMood.SQUINTING_AT_CLOUD, MascotMood.of(withScore(base, 50)))
        assertEquals(MascotMood.ASLEEP, MascotMood.of(withScore(base, 49)))
        assertEquals(MascotMood.ASLEEP, MascotMood.of(withScore(base, 0)))
    }

    @Test
    fun aMissingScoreSquints() {
        val report = clearNight().copy(score = null, dew = null, aurora = null)
        assertEquals(MascotMood.SQUINTING_AT_CLOUD, MascotMood.of(report))
        assertEquals("Owl squinting at a cloud: no score yet", MascotMood.of(report).contentDescription(report))
    }

    @Test
    fun dewAndFrostOverrideTheVerdictAndFrostWinsOverDew() {
        val go = withScore(clearNight(), 90)
        val dew = go.copy(dew = dew(frost = false))
        val frost = go.copy(dew = dew(frost = true))
        assertEquals(MascotMood.UMBRELLA, MascotMood.of(dew))
        assertEquals(MascotMood.SCARF, MascotMood.of(frost))
        assertEquals("Owl holding an umbrella: dew is likely", MascotMood.of(dew).contentDescription(dew))
        assertEquals("Owl wearing a scarf: frost is likely", MascotMood.of(frost).contentDescription(frost))
    }

    @Test
    fun auroraOverridesDewAndAnUnlikelyAuroraDoesNot() {
        val go = withScore(clearNight(), 90).copy(dew = dew(frost = true))
        val likely = go.copy(aurora = aurora(AuroraChance.LIKELY))
        val possible = go.copy(aurora = aurora(AuroraChance.POSSIBLE))
        val quiet = go.copy(aurora = aurora(AuroraChance.UNLIKELY))
        assertEquals(MascotMood.LOOKING_UP, MascotMood.of(likely))
        assertEquals(MascotMood.LOOKING_UP, MascotMood.of(possible))
        assertEquals(MascotMood.SCARF, MascotMood.of(quiet))
        assertEquals(
            "Owl looking up at the aurora: aurora is likely",
            MascotMood.of(likely).contentDescription(likely),
        )
        assertEquals(
            "Owl looking up at the aurora: aurora is possible",
            MascotMood.of(possible).contentDescription(possible),
        )
    }

    @Test
    fun contentDescriptionNamesTheVerdict() {
        val go = withScore(clearNight(), 90)
        val maybe = withScore(clearNight(), 55)
        val noGo = withScore(clearNight(), 20)
        assertEquals("Owl awake with a telescope: a go night", MascotMood.of(go).contentDescription(go))
        assertEquals("Owl squinting at a cloud: a maybe night", MascotMood.of(maybe).contentDescription(maybe))
        assertEquals("Owl asleep under a blanket: a no-go night", MascotMood.of(noGo).contentDescription(noGo))
    }

    private fun withScore(report: NightReport, score: Int): NightReport =
        report.copy(score = report.score!!.copy(score = score), dew = null, aurora = null)

    private fun dew(frost: Boolean) = DewOutlook(
        from = Instant.parse("2024-08-11T03:00:00Z"),
        frostFrom = if (frost) Instant.parse("2024-08-11T06:00:00Z") else null,
        overnightLowC = if (frost) -4.0 else 6.0,
        spreadKnown = true,
    )

    private fun aurora(chance: AuroraChance) = AuroraOutlook(
        peakKp = 5.0,
        noaaScale = "G1",
        status = KpStatus.PREDICTED,
        highLatitude = false,
        chance = chance,
        latitudeDeg = home.latitude,
    )

    private fun clearNight(): NightReport =
        NightPlanner.plan(home, date, forecast(), GearCatalog.exampleKit)

    private fun forecast(): Forecast {
        val start = Instant.parse("2024-08-12T00:00:00Z").epochSecond
        return Forecast(
            home.latitude,
            home.longitude,
            "best_match",
            start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 5,
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
