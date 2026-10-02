package app.nightbrief.astro

import java.time.Duration
import java.time.Instant

data class MoonState(
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    /** 0..1 */
    val illumination: Double,
    /** 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    val phase: Double,
) {
    val waxing: Boolean get() = phase < 0.5
    val phaseName: MoonPhase get() = MoonPhase.fromPhase(phase)
}

enum class MoonPhase(val label: String) {
    NEW("New Moon"),
    WAXING_CRESCENT("Waxing Crescent"),
    FIRST_QUARTER("First Quarter"),
    WAXING_GIBBOUS("Waxing Gibbous"),
    FULL("Full Moon"),
    WANING_GIBBOUS("Waning Gibbous"),
    LAST_QUARTER("Last Quarter"),
    WANING_CRESCENT("Waning Crescent");

    companion object {
        private const val HALF_WIDTH = 0.034 // ~1 day either side of the exact phase

        fun fromPhase(phase: Double): MoonPhase {
            val p = ((phase % 1.0) + 1.0) % 1.0
            return when {
                p < HALF_WIDTH || p > 1 - HALF_WIDTH -> NEW
                p < 0.25 - HALF_WIDTH -> WAXING_CRESCENT
                p <= 0.25 + HALF_WIDTH -> FIRST_QUARTER
                p < 0.5 - HALF_WIDTH -> WAXING_GIBBOUS
                p <= 0.5 + HALF_WIDTH -> FULL
                p < 0.75 - HALF_WIDTH -> WANING_GIBBOUS
                p <= 0.75 + HALF_WIDTH -> LAST_QUARTER
                else -> WANING_CRESCENT
            }
        }
    }
}

data class Crossing(val time: Instant, val rising: Boolean)

data class TimeWindow(val start: Instant, val end: Instant) {
    init {
        require(!end.isBefore(start)) { "end before start" }
    }

    val duration: Duration get() = Duration.between(start, end)
    operator fun contains(t: Instant): Boolean = !t.isBefore(start) && !t.isAfter(end)

    fun intersect(other: TimeWindow): TimeWindow? {
        val s = maxOf(start, other.start)
        val e = minOf(end, other.end)
        return if (e.isAfter(s)) TimeWindow(s, e) else null
    }
}

object Ephemeris {
    /** Standard altitude for sunrise/sunset and moonrise/moonset (refraction + semi-diameter). */
    const val HORIZON_ALTITUDE = -0.833
    const val CIVIL_TWILIGHT = -6.0
    const val NAUTICAL_TWILIGHT = -12.0
    const val ASTRONOMICAL_TWILIGHT = -18.0

    /** Galactic centre (Sgr A*), J2000. */
    val GALACTIC_CENTER = RaDec(raDeg = 266.405, decDeg = -28.936)

    fun sunPosition(time: Instant, latitudeDeg: Double, longitudeDeg: Double): AltAz {
        val d = AstroTime.daysSinceJ2000(time)
        return equatorialToHorizontal(Bodies.sunEquatorial(d), latitudeDeg, longitudeDeg, d)
    }

    fun sunAltitude(time: Instant, latitudeDeg: Double, longitudeDeg: Double): Double =
        sunPosition(time, latitudeDeg, longitudeDeg).altitudeDeg

    fun sunDeclination(time: Instant): Double =
        Bodies.sunEquatorial(AstroTime.daysSinceJ2000(time)).decDeg

    fun moon(time: Instant, latitudeDeg: Double, longitudeDeg: Double): MoonState {
        val d = AstroTime.daysSinceJ2000(time)
        val (pos, parallax) = Bodies.moonEquatorial(d)
        val geo = equatorialToHorizontal(pos, latitudeDeg, longitudeDeg, d)
        val (illum, phase) = Bodies.moonIllumination(d)
        return MoonState(
            altitudeDeg = Bodies.topocentricAltitude(geo.altitudeDeg, parallax),
            azimuthDeg = geo.azimuthDeg,
            illumination = illum,
            phase = phase,
        )
    }

    fun moonAltitude(time: Instant, latitudeDeg: Double, longitudeDeg: Double): Double =
        moon(time, latitudeDeg, longitudeDeg).altitudeDeg

    fun moonIllumination(time: Instant): Double =
        Bodies.moonIllumination(AstroTime.daysSinceJ2000(time)).first

    fun moonPhase(time: Instant): Double =
        Bodies.moonIllumination(AstroTime.daysSinceJ2000(time)).second

    fun moonDeclination(time: Instant): Double =
        Bodies.moonEquatorial(AstroTime.daysSinceJ2000(time)).first.decDeg

    /**
     * Position angle of the lunar terminator, degrees east of celestial north.
     *
     * 0 means the shadow boundary faces north. This is the bright-limb angle plus 180°,
     * from the same low-precision Sun and Moon used for the rest of the ephemeris.
     */
    fun moonTerminatorAngleDeg(time: Instant): Double {
        val d = AstroTime.daysSinceJ2000(time)
        val moon = Bodies.moonEquatorial(d).first
        val sun = Bodies.sunEquatorial(d)
        val brightLimb = atan2D(
            cosD(sun.decDeg) * sinD(sun.raDeg - moon.raDeg),
            sinD(sun.decDeg) * cosD(moon.decDeg) -
                cosD(sun.decDeg) * sinD(moon.decDeg) * cosD(sun.raDeg - moon.raDeg),
        )
        return norm360(brightLimb + 180.0)
    }

    /** Position of a fixed (J2000) object such as a star, nebula or the galactic centre. */
    fun position(target: RaDec, time: Instant, latitudeDeg: Double, longitudeDeg: Double): AltAz =
        equatorialToHorizontal(target, latitudeDeg, longitudeDeg, AstroTime.daysSinceJ2000(time))

    fun galacticCenter(time: Instant, latitudeDeg: Double, longitudeDeg: Double): AltAz =
        position(GALACTIC_CENTER, time, latitudeDeg, longitudeDeg)

    /**
     * Finds the times at which [altitude] crosses [threshold] between [start] and [end].
     * Samples every [step] and refines each bracket by bisection to ~15 s.
     */
    fun crossings(
        start: Instant,
        end: Instant,
        threshold: Double,
        step: Duration = Duration.ofMinutes(10),
        altitude: (Instant) -> Double,
    ): List<Crossing> {
        val result = mutableListOf<Crossing>()
        var t0 = start
        var a0 = altitude(t0) - threshold
        while (t0.isBefore(end)) {
            val t1 = minOf(t0.plus(step), end)
            val a1 = altitude(t1) - threshold
            if ((a0 < 0) != (a1 < 0)) {
                result += Crossing(bisect(t0, t1, threshold, a0 < 0, altitude), rising = a0 < 0)
            }
            t0 = t1
            a0 = a1
        }
        return result
    }

    /** Intervals within [start, end] where [altitude] is strictly below [threshold]. */
    fun intervalsBelow(
        start: Instant,
        end: Instant,
        threshold: Double,
        step: Duration = Duration.ofMinutes(10),
        altitude: (Instant) -> Double,
    ): List<TimeWindow> = intervals(start, end, threshold, step, below = true, altitude)

    /** Intervals within [start, end] where [altitude] is at or above [threshold]. */
    fun intervalsAbove(
        start: Instant,
        end: Instant,
        threshold: Double,
        step: Duration = Duration.ofMinutes(10),
        altitude: (Instant) -> Double,
    ): List<TimeWindow> = intervals(start, end, threshold, step, below = false, altitude)

    private fun intervals(
        start: Instant,
        end: Instant,
        threshold: Double,
        step: Duration,
        below: Boolean,
        altitude: (Instant) -> Double,
    ): List<TimeWindow> {
        fun inside(t: Instant) = (altitude(t) < threshold) == below
        val result = mutableListOf<TimeWindow>()
        var openedAt: Instant? = if (inside(start)) start else null
        for (c in crossings(start, end, threshold, step, altitude)) {
            val entering = if (below) !c.rising else c.rising
            if (entering) {
                openedAt = c.time
            } else {
                openedAt?.let { if (c.time.isAfter(it)) result += TimeWindow(it, c.time) }
                openedAt = null
            }
        }
        openedAt?.let { if (end.isAfter(it)) result += TimeWindow(it, end) }
        return result
    }

    private fun bisect(
        lo: Instant,
        hi: Instant,
        threshold: Double,
        risingFromBelow: Boolean,
        altitude: (Instant) -> Double,
    ): Instant {
        var a = lo
        var b = hi
        while (Duration.between(a, b).seconds > 15) {
            val mid = a.plusMillis(Duration.between(a, b).toMillis() / 2)
            val below = altitude(mid) < threshold
            if (below == risingFromBelow) a = mid else b = mid
        }
        return a.plusMillis(Duration.between(a, b).toMillis() / 2)
    }
}
