package app.nightbrief.astro

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Deepest level of darkness the sky reaches on a given night. */
enum class Darkness(val label: String) {
    ASTRONOMICAL("Full astronomical darkness"),
    NAUTICAL("Nautical twilight only"),
    CIVIL("Civil twilight only"),
    NONE("Sun stays up"),
}

data class HourlyAstro(
    val time: Instant,
    val sunAltitudeDeg: Double,
    val moonAltitudeDeg: Double,
    val moonIllumination: Double,
    val galacticCenterAltitudeDeg: Double,
    val galacticCenterAzimuthDeg: Double,
)

data class MilkyWayWindow(
    /** Galactic centre at or above the minimum altitude while the sky is dark. */
    val window: TimeWindow,
    val peakAltitudeDeg: Double,
    val peakTime: Instant,
    val peakAzimuthDeg: Double,
    /** Portion of [window] with the Moon below the horizon (or too thin to matter). */
    val moonFree: List<TimeWindow>,
    /**
     * When a local horizon delays the window, this is the instant the core clears it.
     * Null when the site horizon does not push the start back.
     */
    val clearsHorizonAt: Instant? = null,
) {
    val moonFreeDuration: Duration get() = moonFree.fold(Duration.ZERO) { acc, w -> acc + w.duration }
}

data class NightEphemeris(
    val date: LocalDate,
    val zone: ZoneId,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val sunset: Instant?,
    val sunrise: Instant?,
    val nauticalDusk: Instant?,
    val nauticalDawn: Instant?,
    val astronomicalDusk: Instant?,
    val astronomicalDawn: Instant?,
    val darkness: Darkness,
    /** Darkest usable window: astronomical night if it exists, otherwise nautical, otherwise civil. */
    val darkWindow: TimeWindow?,
    val moonrise: Instant?,
    val moonset: Instant?,
    /** Moon illumination and phase at the middle of the night. */
    val moonIllumination: Double,
    val moonPhase: Double,
    /** Parts of [darkWindow] with no Moon in the sky. */
    val moonFreeDark: List<TimeWindow>,
    val milkyWay: MilkyWayWindow?,
    /**
     * True when the core would clear the flat minimum altitude, but the local horizon hides it.
     */
    val milkyWayBlockedByHorizon: Boolean = false,
    /** One sample per whole hour spanning sunset to sunrise (or the whole night at polar latitudes). */
    val hourly: List<HourlyAstro>,
) {
    val moonPhaseName: MoonPhase get() = MoonPhase.fromPhase(moonPhase)
    val moonFreeDarkDuration: Duration get() = moonFreeDark.fold(Duration.ZERO) { acc, w -> acc + w.duration }

    companion object {
        /** Below this illumination the Moon is treated as irrelevant even when up. */
        const val NEGLIGIBLE_MOON = 0.05
        const val DEFAULT_MILKY_WAY_MIN_ALTITUDE = 10.0

        /**
         * Computes the night that starts on the evening of [date] in [zone]
         * (local noon on [date] to local noon the following day).
         *
         * [horizonObstructionDeg] is extra altitude the sky must clear at that azimuth.
         * 0 leaves [milkyWayMinAltitudeDeg] as the only floor.
         */
        fun compute(
            date: LocalDate,
            zone: ZoneId,
            latitudeDeg: Double,
            longitudeDeg: Double,
            milkyWayMinAltitudeDeg: Double = DEFAULT_MILKY_WAY_MIN_ALTITUDE,
            horizonObstructionDeg: (Double) -> Double = { 0.0 },
        ): NightEphemeris {
            val start = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
            val end = date.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
            val sunAlt = { t: Instant -> Ephemeris.sunAltitude(t, latitudeDeg, longitudeDeg) }
            val moonAlt = { t: Instant -> Ephemeris.moonAltitude(t, latitudeDeg, longitudeDeg) }

            // One altitude grid serves every solar threshold. The sample instants and bisection
            // are exactly those Ephemeris.crossings used when each threshold scanned on its own.
            val solarTimes = ArrayList<Instant>()
            var solarTime = start
            while (solarTime.isBefore(end)) {
                solarTimes += solarTime
                solarTime = minOf(solarTime.plus(Duration.ofMinutes(10)), end)
            }
            solarTimes += end
            val solarAlts = DoubleArray(solarTimes.size) { sunAlt(solarTimes[it]) }

            fun solarCrossings(threshold: Double) =
                Ephemeris.crossingsFromSamples(solarTimes, solarAlts, threshold, sunAlt)

            fun longestBelow(crossings: List<Crossing>, threshold: Double) =
                Ephemeris.intervalsBelowFromCrossings(start, end, solarAlts[0] < threshold, crossings)
                    .maxByOrNull { it.duration }

            val horizonCrossings = solarCrossings(Ephemeris.HORIZON_ALTITUDE)
            val civilCrossings = solarCrossings(Ephemeris.CIVIL_TWILIGHT)
            val nauticalCrossings = solarCrossings(Ephemeris.NAUTICAL_TWILIGHT)
            val astroCrossings = solarCrossings(Ephemeris.ASTRONOMICAL_TWILIGHT)
            val astro = longestBelow(astroCrossings, Ephemeris.ASTRONOMICAL_TWILIGHT)
            val nautical = longestBelow(nauticalCrossings, Ephemeris.NAUTICAL_TWILIGHT)
            val civil = longestBelow(civilCrossings, Ephemeris.CIVIL_TWILIGHT)
            val (darkness, darkWindow) = when {
                astro != null -> Darkness.ASTRONOMICAL to astro
                nautical != null -> Darkness.NAUTICAL to nautical
                civil != null -> Darkness.CIVIL to civil
                else -> Darkness.NONE to null
            }

            val mid = darkWindow?.let { it.start.plus(it.duration.dividedBy(2)) }
                ?: start.plus(Duration.ofHours(12))
            val moonIllum = Ephemeris.moonIllumination(mid)
            val moonPhase = Ephemeris.moonPhase(mid)

            val moonEvents = Ephemeris.crossings(start, end, Ephemeris.HORIZON_ALTITUDE, altitude = moonAlt)
            val moonFreeDark = when {
                darkWindow == null -> emptyList()
                moonIllum < NEGLIGIBLE_MOON -> listOf(darkWindow)
                else -> Ephemeris.intervalsBelow(
                    darkWindow.start, darkWindow.end, Ephemeris.HORIZON_ALTITUDE, altitude = moonAlt,
                )
            }

            val (milkyWay, milkyWayBlockedByHorizon) = darkWindow?.let {
                milkyWayWindow(
                    it, moonFreeDark, latitudeDeg, longitudeDeg, milkyWayMinAltitudeDeg, horizonObstructionDeg,
                )
            } ?: (null to false)

            val sunset = horizonCrossings.firstOrNull { !it.rising }?.time
            val sunrise = horizonCrossings.lastOrNull { it.rising }?.time
            val hourlyStart = (sunset ?: darkWindow?.start ?: start.plus(Duration.ofHours(6)))
                .truncatedTo(ChronoUnit.HOURS)
            val hourlyEndRaw = sunrise ?: darkWindow?.end ?: end.minus(Duration.ofHours(6))
            val hourlyEnd = hourlyEndRaw.truncatedTo(ChronoUnit.HOURS).let {
                if (it.isBefore(hourlyEndRaw)) it.plus(Duration.ofHours(1)) else it
            }
            val hourly = generateSequence(hourlyStart) { it.plus(Duration.ofHours(1)) }
                .takeWhile { !it.isAfter(hourlyEnd) }
                .map { t ->
                    val m = Ephemeris.moon(t, latitudeDeg, longitudeDeg)
                    val gc = Ephemeris.galacticCenter(t, latitudeDeg, longitudeDeg)
                    HourlyAstro(
                        time = t,
                        sunAltitudeDeg = sunAlt(t),
                        moonAltitudeDeg = m.altitudeDeg,
                        moonIllumination = m.illumination,
                        galacticCenterAltitudeDeg = gc.altitudeDeg,
                        galacticCenterAzimuthDeg = gc.azimuthDeg,
                    )
                }
                .toList()

            return NightEphemeris(
                date = date,
                zone = zone,
                latitudeDeg = latitudeDeg,
                longitudeDeg = longitudeDeg,
                sunset = sunset,
                sunrise = sunrise,
                nauticalDusk = nauticalCrossings.firstOrNull { !it.rising }?.time,
                nauticalDawn = nauticalCrossings.lastOrNull { it.rising }?.time,
                astronomicalDusk = astroCrossings.firstOrNull { !it.rising }?.time,
                astronomicalDawn = astroCrossings.lastOrNull { it.rising }?.time,
                darkness = darkness,
                darkWindow = darkWindow,
                moonrise = moonEvents.firstOrNull { it.rising }?.time,
                moonset = moonEvents.firstOrNull { !it.rising }?.time,
                moonIllumination = moonIllum,
                moonPhase = moonPhase,
                moonFreeDark = moonFreeDark,
                milkyWay = milkyWay,
                milkyWayBlockedByHorizon = milkyWayBlockedByHorizon,
                hourly = hourly,
            )
        }

        private fun milkyWayWindow(
            dark: TimeWindow,
            moonFreeDark: List<TimeWindow>,
            lat: Double,
            lon: Double,
            minAltitude: Double,
            horizonObstructionDeg: (Double) -> Double,
        ): Pair<MilkyWayWindow?, Boolean> {
            fun excess(obstruction: (Double) -> Double): (Instant) -> Double = { t ->
                val gc = Ephemeris.galacticCenter(t, lat, lon)
                gc.altitudeDeg - maxOf(minAltitude, obstruction(gc.azimuthDeg))
            }
            fun longest(obstruction: (Double) -> Double): TimeWindow? =
                Ephemeris.intervalsAbove(dark.start, dark.end, 0.0, altitude = excess(obstruction))
                    .maxByOrNull { it.duration }

            val flat = longest { 0.0 }
            val window = longest(horizonObstructionDeg) ?: return null to (flat != null)
            var peakTime = window.start
            var peakAlt = Ephemeris.galacticCenter(peakTime, lat, lon).altitudeDeg
            var t = window.start
            while (!t.isAfter(window.end)) {
                val altitude = Ephemeris.galacticCenter(t, lat, lon).altitudeDeg
                if (altitude > peakAlt) {
                    peakAlt = altitude
                    peakTime = t
                }
                t = t.plus(Duration.ofMinutes(5))
            }
            val delayMinutes = flat?.let { Duration.between(it.start, window.start).toMinutes() } ?: 0
            return MilkyWayWindow(
                window = window,
                peakAltitudeDeg = peakAlt,
                peakTime = peakTime,
                peakAzimuthDeg = Ephemeris.galacticCenter(peakTime, lat, lon).azimuthDeg,
                moonFree = moonFreeDark.mapNotNull { it.intersect(window) },
                clearsHorizonAt = if (delayMinutes >= 10) window.start else null,
            ) to false
        }
    }
}
