package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.astro.Ephemeris
import app.nightbrief.astro.NightEphemeris
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Sky state for the Tonight hero backdrop.
 *
 * [twilight] comes from sun altitudes already stored on [NightEphemeris.hourly],
 * not a second ephemeris. Day is above the set altitude (−0.833°), civil down to −6°,
 * nautical down to −12°, and astro from there (astronomical twilight and night).
 *
 * [moonTerminatorDeg] is [Ephemeris.moonTerminatorAngleDeg] at [midpoint], the same
 * instant [NightEphemeris.moonIllumination] was taken, and the same angle the
 * moon-closeup target quotes. Degrees east of celestial north, in `[0, 360)`.
 *
 * [shootingStar] is the Excellent band (score ≥ 85). [milkyWayAlpha] is 0 when the
 * night has no score and 1 at a score of 100.
 */
enum class SkyTwilight {
    DAY,
    CIVIL,
    NAUTICAL,
    ASTRO,
    ;

    companion object {
        fun fromSunAltitude(altitudeDeg: Double): SkyTwilight = when {
            altitudeDeg > Ephemeris.HORIZON_ALTITUDE -> DAY
            altitudeDeg > Ephemeris.CIVIL_TWILIGHT -> CIVIL
            altitudeDeg > Ephemeris.NAUTICAL_TWILIGHT -> NAUTICAL
            else -> ASTRO
        }
    }
}

data class HeroSky(
    val twilight: SkyTwilight,
    /** 0..1, copied from [NightEphemeris.moonIllumination]. */
    val moonIllumination: Double,
    val moonTerminatorDeg: Double,
    val score: Int?,
) {
    val milkyWayAlpha: Double get() = (score ?: 0).coerceIn(0, 100) / 100.0
    val shootingStar: Boolean get() = (score ?: -1) >= Band.EXCELLENT.min

    companion object {
        /**
         * Middle of [NightEphemeris.darkWindow], or local noon plus 12 hours when the sun never sets.
         * This is the instant [NightEphemeris.compute] uses for illumination and phase.
         */
        fun midpoint(ephemeris: NightEphemeris): Instant {
            val dark = ephemeris.darkWindow
            if (dark != null) return dark.start.plus(dark.duration.dividedBy(2))
            val noon = ephemeris.date.atTime(LocalTime.NOON).atZone(ephemeris.zone).toInstant()
            return noon.plus(Duration.ofHours(12))
        }

        /**
         * Instant used when the hero is a still frame (screenshot or layout preview),
         * so the gradient does not follow the wall clock. Middle of the dark window,
         * or 22:00 in the site zone when that window does not exist.
         */
        fun stillInstant(report: NightReport): Instant {
            val dark = report.ephemeris.darkWindow
            if (dark != null) return dark.start.plus(dark.duration.dividedBy(2))
            return report.date.atTime(22, 0).atZone(report.ephemeris.zone).toInstant()
        }

        fun of(report: NightReport, now: Instant): HeroSky {
            val ephemeris = report.ephemeris
            return HeroSky(
                twilight = twilightAt(ephemeris, now),
                moonIllumination = ephemeris.moonIllumination,
                moonTerminatorDeg = Ephemeris.moonTerminatorAngleDeg(midpoint(ephemeris)),
                score = report.scoreValue,
            )
        }
    }
}

internal fun twilightAt(ephemeris: NightEphemeris, now: Instant): SkyTwilight {
    val altitude = sunAltitudeDeg(ephemeris, now)
    if (altitude != null) return SkyTwilight.fromSunAltitude(altitude)
    val dark = ephemeris.darkWindow
    return when (ephemeris.darkness) {
        Darkness.NONE -> SkyTwilight.DAY
        Darkness.CIVIL -> SkyTwilight.CIVIL
        Darkness.NAUTICAL -> SkyTwilight.NAUTICAL
        Darkness.ASTRONOMICAL ->
            if (dark != null && now in dark) SkyTwilight.ASTRO else SkyTwilight.NAUTICAL
    }
}

/**
 * Sun altitude at [now] from the hourly samples. Null when the night stored none.
 * Instants outside the samples are daytime when the sun rises or sets, and otherwise
 * the nearest sample (polar night has no rise or set).
 */
internal fun sunAltitudeDeg(ephemeris: NightEphemeris, now: Instant): Double? {
    val hours = ephemeris.hourly
    if (hours.isEmpty()) return null
    if (now.isBefore(hours.first().time)) {
        return if (ephemeris.darkness == Darkness.NONE || ephemeris.sunset != null) {
            15.0
        } else {
            hours.first().sunAltitudeDeg
        }
    }
    if (now.isAfter(hours.last().time)) {
        return if (ephemeris.darkness == Darkness.NONE || ephemeris.sunrise != null) {
            15.0
        } else {
            hours.last().sunAltitudeDeg
        }
    }
    val next = hours.indexOfFirst { !it.time.isBefore(now) }
    if (next <= 0) return hours.first().sunAltitudeDeg
    val before = hours[next - 1]
    val after = hours[next]
    val span = (after.time.epochSecond - before.time.epochSecond).coerceAtLeast(1L)
    val t = (now.epochSecond - before.time.epochSecond).toDouble() / span.toDouble()
    return before.sunAltitudeDeg + (after.sunAltitudeDeg - before.sunAltitudeDeg) * t.coerceIn(0.0, 1.0)
}
