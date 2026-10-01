package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPass
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.gear.GearKit
import app.nightbrief.sites.Site
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.ForecastStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One row of the Tonight screen's hour-by-hour timeline. */
data class TimelineHour(
    val time: Instant,
    val isDark: Boolean,
    val sunAltitudeDeg: Double,
    val cloudCover: Int?,
    val moonAltitudeDeg: Double,
    val moonIllumination: Double,
    val galacticCenterAltitudeDeg: Double,
    val seeingIndex: Int?,
    val transparencyIndex: Int?,
    val windKmh: Double?,
    val score: Int,
)

enum class ForecastCoverage { FULL, PARTIAL, NONE }

data class NightReport(
    val site: Site,
    val date: LocalDate,
    val ephemeris: NightEphemeris,
    /** Null when the forecast doesn't reach this night at all. */
    val score: NightScore?,
    val timeline: List<TimelineHour>,
    val suggestions: List<TargetSuggestion>,
    val coverage: ForecastCoverage,
    val forecastStatus: ForecastStatus?,
    val warnings: List<String> = emptyList(),
    /** Planetary Kp for this night's dark window, when SWPC data covers it. Not a score input. */
    val aurora: AuroraOutlook? = null,
    /** Best active shower for this night, or null when none is active. Not a score input. */
    val meteor: MeteorOutlook? = null,
    /** ISS passes whose peak falls in the dark window. Empty when no TLE was available. */
    val issPasses: List<IssPass> = emptyList(),
) {
    val scoreValue: Int? get() = score?.score
}

object NightPlanner {

    /**
     * "Tonight" in [zone]: today's evening, except in the small hours when the current night
     * (which started yesterday evening) is still under way.
     */
    fun tonight(now: Instant, zone: ZoneId): LocalDate {
        val local = now.atZone(zone)
        return if (local.toLocalTime().isBefore(LocalTime.of(6, 0))) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun plan(
        site: Site,
        date: LocalDate,
        forecast: Forecast?,
        kit: GearKit,
        forecastStatus: ForecastStatus? = null,
        warnings: List<String> = emptyList(),
        includeSuggestions: Boolean = true,
    ): NightReport {
        val eph = NightEphemeris.compute(date, site.zone, site.latitude, site.longitude)
        val dark = eph.darkWindow
        val bortle = site.effectiveBortle

        val inputs = eph.hourly.map { a ->
            val w = forecast?.at(a.time)
            HourInput(
                time = a.time,
                cloudCover = w?.cloudCover,
                windKmh = w?.windKmh,
                gustKmh = w?.gustKmh,
                seeingIndex = w?.seeing,
                transparencyIndex = w?.transparency,
                humidity = w?.humidity,
                jetStreamKmh = w?.jetStreamKmh,
                sunAltitudeDeg = a.sunAltitudeDeg,
                moonAltitudeDeg = a.moonAltitudeDeg,
                moonIllumination = a.moonIllumination,
                bortle = bortle,
            ) to w
        }

        val darkInputs = darkHours(eph, inputs.map { it.first })
        val covered = darkInputs.count { forecast?.at(it.time)?.cloudCover != null }
        val coverage = when {
            darkInputs.isEmpty() -> if (forecast != null) ForecastCoverage.FULL else ForecastCoverage.NONE
            covered == darkInputs.size -> ForecastCoverage.FULL
            covered == 0 -> ForecastCoverage.NONE
            else -> ForecastCoverage.PARTIAL
        }
        val score = when {
            eph.darkness == Darkness.NONE -> NightScore.NO_DARKNESS
            coverage == ForecastCoverage.NONE -> null
            else -> NightScoreEngine.scoreNight(darkInputs)
        }

        val timeline = inputs.map { (input, w) ->
            val a = eph.hourly.first { it.time == input.time }
            TimelineHour(
                time = input.time,
                isDark = dark != null && input.time in dark,
                sunAltitudeDeg = input.sunAltitudeDeg,
                cloudCover = w?.cloudCover,
                moonAltitudeDeg = input.moonAltitudeDeg,
                moonIllumination = input.moonIllumination,
                galacticCenterAltitudeDeg = a.galacticCenterAltitudeDeg,
                seeingIndex = w?.seeing,
                transparencyIndex = w?.transparency,
                windKmh = w?.windKmh,
                score = NightScoreEngine.scoreHour(input).score,
            )
        }

        return NightReport(
            site = site,
            date = date,
            ephemeris = eph,
            score = score,
            timeline = timeline,
            suggestions = if (includeSuggestions) TargetAdvisor.suggest(eph, bortle, kit) else emptyList(),
            coverage = coverage,
            forecastStatus = forecastStatus,
            warnings = warnings,
            meteor = MeteorAdvisor.forNight(eph),
        )
    }

    /** Whole hours inside the dark window; for very short nights, the hour nearest its middle. */
    internal fun darkHours(eph: NightEphemeris, inputs: List<HourInput>): List<HourInput> {
        val dark = eph.darkWindow ?: return emptyList()
        val inside = inputs.filter { it.time in dark }
        if (inside.isNotEmpty()) return inside
        val mid = dark.start.plus(dark.duration.dividedBy(2))
        return listOfNotNull(inputs.minByOrNull { kotlin.math.abs(it.time.epochSecond - mid.epochSecond) })
    }
}
