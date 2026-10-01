package app.nightbrief.score

import app.nightbrief.astro.AltAz
import app.nightbrief.astro.Ephemeris
import app.nightbrief.astro.MeteorShower
import app.nightbrief.astro.MeteorShowers
import app.nightbrief.astro.NightEphemeris
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MeteorInterference { NONE, MODERATE, STRONG }

data class MeteorOutlook(
    val shower: MeteorShower,
    val peakDate: LocalDate,
    val daysFromPeak: Int,
    val expectedZhr: Int,
    val peakRadiantAltitudeDeg: Double,
    val peakRadiantTime: Instant,
    val peakRadiantAzimuthDeg: Double,
    val moonIllumination: Double,
    val moonUpAtRadiantPeak: Boolean,
    val moonRadiantSeparationDeg: Double,
    val interference: MeteorInterference,
) {
    /** True when the radiant clears 20° during the dark window and expected ZHR is at least 10. */
    val worthWatching: Boolean
        get() = peakRadiantAltitudeDeg >= 20.0 && expectedZhr >= 10
}

/**
 * Picks one active shower for a night and estimates rate, radiant height, and moonlight.
 * Planning aid only: ZHR is scaled with a Gaussian in days from peak, not a full activity profile.
 */
object MeteorAdvisor {
    private val STEP: Duration = Duration.ofMinutes(10)
    private const val DEG = Math.PI / 180.0
    private const val RAD = 180.0 / Math.PI

    /**
     * The single best active shower for this night, or null when none is active.
     * Rank by expected ZHR, then peak radiant altitude. Still return a shower that is not worthWatching
     * if it is the best active one (the UI decides whether to hide it).
     */
    fun forNight(ephemeris: NightEphemeris): MeteorOutlook? {
        val active = MeteorShowers.activeOn(ephemeris.date)
        if (active.isEmpty()) return null
        return active
            .map { outlook(it, ephemeris) }
            .maxWith(compareBy<MeteorOutlook> { it.expectedZhr }.thenBy { it.peakRadiantAltitudeDeg })
    }

    fun digestLine(outlook: MeteorOutlook): String {
        val whenText = when (val days = outlook.daysFromPeak) {
            0 -> "${outlook.shower.name} tonight"
            1 -> "${outlook.shower.name} peaked yesterday"
            -1 -> "${outlook.shower.name} in 1 day"
            else -> if (days > 0) {
                "${outlook.shower.name} peaked $days days ago"
            } else {
                "${outlook.shower.name} in ${-days} days"
            }
        }
        val moon = when (outlook.interference) {
            MeteorInterference.STRONG -> " · bright Moon"
            MeteorInterference.MODERATE -> " · Moon up"
            MeteorInterference.NONE -> ""
        }
        val alt = outlook.peakRadiantAltitudeDeg.roundToInt()
        return "$whenText · ZHR ~${outlook.expectedZhr} · radiant $alt°$moon"
    }

    fun detail(outlook: MeteorOutlook): String {
        val name = outlook.shower.name
        val alt = outlook.peakRadiantAltitudeDeg.roundToInt()
        val zhr = outlook.expectedZhr
        val lead = when (val days = outlook.daysFromPeak) {
            0 -> "$name peak tonight."
            1 -> "$name peaked yesterday."
            -1 -> "$name peak tomorrow."
            else -> if (days > 0) "$name peaked $days days ago." else "$name peak in ${-days} days."
        }
        val sky = when (outlook.interference) {
            MeteorInterference.NONE ->
                "The radiant reaches about $alt° and the expected rate is about $zhr an hour."
            MeteorInterference.MODERATE ->
                "The radiant reaches about $alt° (about $zhr an hour), but the Moon is up and will brighten the sky."
            MeteorInterference.STRONG ->
                "The radiant reaches about $alt° (about $zhr an hour), but a bright Moon near it will wash out fainter meteors."
        }
        return "$lead $sky"
    }

    private fun outlook(shower: MeteorShower, ephemeris: NightEphemeris): MeteorOutlook {
        val days = MeteorShowers.daysFromPeak(shower, ephemeris.date)
        val peak = radiantPeak(shower, ephemeris)
        val moon = Ephemeris.moon(peak.time, ephemeris.latitudeDeg, ephemeris.longitudeDeg)
        val radiant = AltAz(peak.altitudeDeg, peak.azimuthDeg)
        val moonUp = moon.altitudeDeg > 0.0
        val separation = separationDeg(radiant, AltAz(moon.altitudeDeg, moon.azimuthDeg))
        return MeteorOutlook(
            shower = shower,
            peakDate = MeteorShowers.peakDate(shower, seasonPeakYear(shower, ephemeris.date)),
            daysFromPeak = days,
            expectedZhr = expectedZhr(shower, days),
            peakRadiantAltitudeDeg = peak.altitudeDeg,
            peakRadiantTime = peak.time,
            peakRadiantAzimuthDeg = peak.azimuthDeg,
            moonIllumination = moon.illumination,
            moonUpAtRadiantPeak = moonUp,
            moonRadiantSeparationDeg = separation,
            interference = interference(moon.illumination, moonUp, separation),
        )
    }

    private fun seasonPeakYear(shower: MeteorShower, date: LocalDate): Int {
        val peak = MeteorShowers.daysFromPeak(shower, date)
        return date.plusDays((-peak).toLong()).year
    }

    /**
     * Short showers (active window under 20 days: Quadrantids, Lyrids, Geminids, Ursids) use a
     * 2-day Gaussian. Leonids run Nov 6–30 (25 days), so they use the 4-day width with the longer showers.
     */
    private fun expectedZhr(shower: MeteorShower, daysFromPeak: Int): Int {
        val sigma = if (inclusiveActiveDays(shower) < 20) 2.0 else 4.0
        val x = daysFromPeak / sigma
        val scaled = shower.peakZhr * exp(-0.5 * x * x)
        return maxOf(1, round(scaled).toInt())
    }

    private fun inclusiveActiveDays(shower: MeteorShower): Int {
        val start = LocalDate.of(2021, shower.activeStartMonth, shower.activeStartDay)
        val endSameYear = LocalDate.of(2021, shower.activeEndMonth, shower.activeEndDay)
        val end = if (endSameYear.isBefore(start)) endSameYear.plusYears(1) else endSameYear
        return ChronoUnit.DAYS.between(start, end).toInt() + 1
    }

    private fun interference(illumination: Double, moonUp: Boolean, separationDeg: Double): MeteorInterference {
        if (!moonUp) return MeteorInterference.NONE
        if (illumination >= 0.7 && separationDeg < 60.0) return MeteorInterference.STRONG
        if (illumination >= 0.4) return MeteorInterference.MODERATE
        return MeteorInterference.NONE
    }

    private fun radiantPeak(shower: MeteorShower, ephemeris: NightEphemeris): RadiantSample {
        val dark = ephemeris.darkWindow
        if (dark != null) {
            return maxSample(ephemeris, shower, dark.start, dark.end, STEP)
        }
        if (ephemeris.hourly.isNotEmpty()) {
            var best: RadiantSample? = null
            for (hour in ephemeris.hourly) {
                val sample = sampleAt(ephemeris, shower, hour.time)
                if (best == null || sample.altitudeDeg > best.altitudeDeg) best = sample
            }
            return best!!
        }
        val midnight = ephemeris.date.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(ephemeris.zone).toInstant()
        return sampleAt(ephemeris, shower, midnight)
    }

    private fun maxSample(
        ephemeris: NightEphemeris,
        shower: MeteorShower,
        start: Instant,
        end: Instant,
        step: Duration,
    ): RadiantSample {
        var best = sampleAt(ephemeris, shower, start)
        var t = start.plus(step)
        while (!t.isAfter(end)) {
            val sample = sampleAt(ephemeris, shower, t)
            if (sample.altitudeDeg > best.altitudeDeg) best = sample
            t = t.plus(step)
        }
        return best
    }

    private fun sampleAt(ephemeris: NightEphemeris, shower: MeteorShower, time: Instant): RadiantSample {
        val pos = Ephemeris.position(shower.radiant, time, ephemeris.latitudeDeg, ephemeris.longitudeDeg)
        return RadiantSample(pos.altitudeDeg, time, pos.azimuthDeg)
    }

    /** Great-circle angle between two horizontal positions, in degrees. */
    private fun separationDeg(a: AltAz, b: AltAz): Double {
        val alt1 = a.altitudeDeg * DEG
        val alt2 = b.altitudeDeg * DEG
        val dAz = (a.azimuthDeg - b.azimuthDeg) * DEG
        val cosSep = sin(alt1) * sin(alt2) + cos(alt1) * cos(alt2) * cos(dAz)
        return acos(cosSep.coerceIn(-1.0, 1.0)) * RAD
    }

    private data class RadiantSample(val altitudeDeg: Double, val time: Instant, val azimuthDeg: Double)
}
