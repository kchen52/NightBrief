package app.nightbrief.astro

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Osculating comet elements. Angles are J2000 ecliptic degrees.
 * [absoluteMagnitude] and [slope] are the M1 and K1 of the IAU total-magnitude law.
 */
data class CometOrbit(
    val name: String,
    /** Perihelion passage. TT is stored as if it were UT. */
    val perihelion: Instant,
    /** Perihelion distance, AU. */
    val qAu: Double,
    val eccentricity: Double,
    val argPeriDeg: Double,
    val nodeDeg: Double,
    val inclDeg: Double,
    val absoluteMagnitude: Double,
    val slope: Double,
) {
    init {
        require(name.isNotBlank())
        require(qAu > 0.0) { "perihelion distance must be positive" }
        require(eccentricity >= 0.0) { "eccentricity must be non-negative" }
        require(absoluteMagnitude.isFinite() && slope.isFinite())
    }
}

/** Geocentric geometry at one instant. Light-time is not applied. */
data class CometGeometry(
    /** J2000 equatorial coordinates. Precession from the element frame is ignored. */
    val raDec: RaDec,
    /** IAU total magnitude. Not a phase-corrected visual magnitude. */
    val magnitude: Double,
    /** Distance from the Sun, AU. */
    val heliocentricAu: Double,
    /** Distance from the Earth/Moon barycentre, AU. */
    val geocentricAu: Double,
)

/**
 * Planning-grade two-body positions from osculating comet elements.
 *
 * Elliptic, parabolic, and hyperbolic orbits are propagated from perihelion with
 * the Gaussian gravitational constant. Non-gravitational forces near perihelion
 * are ignored, as are light-time, aberration, nutation, and precession.
 * TT is approximated by UT. [Planets.earthBarycentre] stands in for the geocentre.
 *
 * Magnitude is the IAU total-magnitude law `M1 + 5 log10(Δ) + K1 log10(r)` with no
 * phase term. [CometOrbit.absoluteMagnitude] and [CometOrbit.slope] are those M1 and K1.
 *
 * Checked against JPL Horizons solution 113 for C/2023 A3 (Tsuchinshan-ATLAS):
 * geocentric J2000 RA/Dec stays within 0.05° on 2024-05-18 (the element epoch) and
 * on 2024-10-12 (the evening apparition).
 */
object Comets {
    /** Gaussian gravitational constant, AU^(3/2) per day. */
    private const val GAUSS_K = 0.01720209895

    fun geometry(orbit: CometOrbit, instant: Instant): CometGeometry {
        val dtDays = Duration.between(orbit.perihelion, instant).toNanos() / 86_400e9
        val (r, nu) = radiusAndAnomaly(orbit.qAu, orbit.eccentricity, dtDays)
        val xp = r * cos(nu)
        val yp = r * sin(nu)
        val w = orbit.argPeriDeg
        val om = orbit.nodeDeg
        val incl = orbit.inclDeg
        val x = (cosD(w) * cosD(om) - sinD(w) * sinD(om) * cosD(incl)) * xp +
            (-sinD(w) * cosD(om) - cosD(w) * sinD(om) * cosD(incl)) * yp
        val y = (cosD(w) * sinD(om) + sinD(w) * cosD(om) * cosD(incl)) * xp +
            (-sinD(w) * sinD(om) + cosD(w) * cosD(om) * cosD(incl)) * yp
        val z = (sinD(w) * sinD(incl)) * xp + (cosD(w) * sinD(incl)) * yp
        val earth = Planets.earthBarycentre(instant)
        val gx = x - earth[0]
        val gy = y - earth[1]
        val gz = z - earth[2]
        val dist = sqrt(gx * gx + gy * gy + gz * gz)
        if (!r.isFinite() || !dist.isFinite() || dist <= 0.0 || r <= 0.0) {
            throw IllegalArgumentException("comet elements did not produce a position")
        }
        val lambda = norm360(atan2(gy, gx) * RAD)
        val beta = asinD(gz / dist)
        val magnitude = orbit.absoluteMagnitude + 5.0 * log10(dist) + orbit.slope * log10(r)
        if (!magnitude.isFinite()) {
            throw IllegalArgumentException("comet magnitude is not finite")
        }
        return CometGeometry(
            raDec = eclipticToEquatorial(lambda, beta, Planets.J2000_OBLIQUITY),
            magnitude = magnitude,
            heliocentricAu = r,
            geocentricAu = dist,
        )
    }

    /** Heliocentric distance (AU) and true anomaly (radians) at [dtDays] from perihelion. */
    private fun radiusAndAnomaly(q: Double, eccentricity: Double, dtDays: Double): Pair<Double, Double> {
        if (abs(dtDays) < 1e-12) return q to 0.0
        val (r, nu) = when {
            abs(eccentricity - 1.0) < 1e-8 -> parabolic(q, dtDays)
            eccentricity < 1.0 -> elliptic(q, eccentricity, dtDays)
            else -> hyperbolic(q, eccentricity, dtDays)
        }
        if (!r.isFinite() || !nu.isFinite() || r <= 0.0) {
            throw IllegalArgumentException("comet orbit did not converge")
        }
        return r to nu
    }

    private fun elliptic(q: Double, eccentricity: Double, dtDays: Double): Pair<Double, Double> {
        val a = q / (1.0 - eccentricity)
        val n = GAUSS_K / (a * sqrt(a))
        val mean = (n * dtDays + PI).mod(2.0 * PI) - PI
        var eccentric = mean
        for (iteration in 0 until 30) {
            val delta = (mean - (eccentric - eccentricity * sin(eccentric))) /
                (1.0 - eccentricity * cos(eccentric))
            eccentric += delta
            if (abs(delta) < 1e-12) break
        }
        val half = eccentric / 2.0
        val nu = 2.0 * atan2(
            sqrt(1.0 + eccentricity) * sin(half),
            sqrt(1.0 - eccentricity) * cos(half),
        )
        val r = a * (1.0 - eccentricity * cos(eccentric))
        return r to nu
    }

    private fun hyperbolic(q: Double, eccentricity: Double, dtDays: Double): Pair<Double, Double> {
        val a = q / (eccentricity - 1.0)
        val n = GAUSS_K / (a * sqrt(a))
        val mean = n * dtDays
        if (abs(mean) < 1e-14) return q to 0.0
        var anomaly = sign(mean) * ln(2.0 * abs(mean) / eccentricity + 1.8)
        for (iteration in 0 until 60) {
            val delta = (mean - (eccentricity * sinh(anomaly) - anomaly)) /
                (eccentricity * cosh(anomaly) - 1.0)
            anomaly += delta
            if (abs(delta) < 1e-12) break
        }
        val half = anomaly / 2.0
        val nu = 2.0 * atan2(
            sqrt(eccentricity + 1.0) * sinh(half),
            sqrt(eccentricity - 1.0) * cosh(half),
        )
        val r = a * (eccentricity * cosh(anomaly) - 1.0)
        return r to nu
    }

    private fun parabolic(q: Double, dtDays: Double): Pair<Double, Double> {
        val w = GAUSS_K * dtDays / sqrt(2.0 * q * q * q)
        var s = if (abs(w) < 1.0) w else sign(w) * (3.0 * abs(w)).pow(1.0 / 3.0)
        for (iteration in 0 until 40) {
            val delta = (w - (s + s * s * s / 3.0)) / (1.0 + s * s)
            s += delta
            if (abs(delta) < 1e-12) break
        }
        return q * (1.0 + s * s) to 2.0 * atan(s)
    }
}

/**
 * MPC "Ephemerides and Orbital Elements" comet format, the column layout of
 * `Soft00Cmt.txt`. A line that is not that layout is skipped. An empty result
 * means nothing in the text parsed, not that the file was missing.
 */
object MpcComets {
    fun parse(text: String): List<CometOrbit> = text.lineSequence().mapNotNull(::parseLine).toList()

    private fun parseLine(line: String): CometOrbit? {
        if (line.length < 100 || !line[4].isLetter()) return null
        val year = field(line, 15, 18).trim().toIntOrNull() ?: return null
        val month = field(line, 20, 21).trim().toIntOrNull() ?: return null
        val day = field(line, 23, 29).trim().toDoubleOrNull() ?: return null
        val q = field(line, 31, 39).trim().toDoubleOrNull() ?: return null
        val eccentricity = field(line, 42, 49).trim().toDoubleOrNull() ?: return null
        val arg = field(line, 52, 59).trim().toDoubleOrNull() ?: return null
        val node = field(line, 62, 69).trim().toDoubleOrNull() ?: return null
        val incl = field(line, 72, 79).trim().toDoubleOrNull() ?: return null
        val absolute = field(line, 92, 95).trim().toDoubleOrNull() ?: return null
        val slope = field(line, 97, 100).trim().toDoubleOrNull() ?: return null
        val wholeDay = day.toInt()
        if (month !in 1..12 || wholeDay !in 1..31) return null
        val fraction = day - wholeDay
        if (fraction < 0.0 || fraction >= 1.0) return null
        val date = try {
            LocalDate.of(year, month, wholeDay)
        } catch (_: DateTimeException) {
            return null
        }
        var millis = (fraction * 86_400_000.0).roundToLong()
        val perihelionDate = if (millis >= 86_400_000L) {
            millis -= 86_400_000L
            date.plusDays(1)
        } else {
            date
        }
        val named = field(line, 103, 158).trim()
        val packed = (field(line, 1, 4) + field(line, 5, 12)).trim()
        val name = named.ifBlank { packed }
        if (name.isBlank() || q <= 0.0 || eccentricity < 0.0 || eccentricity >= 20.0) return null
        if (!absolute.isFinite() || !slope.isFinite()) return null
        return CometOrbit(
            name = name,
            perihelion = perihelionDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis(millis),
            qAu = q,
            eccentricity = eccentricity,
            argPeriDeg = arg,
            nodeDeg = node,
            inclDeg = incl,
            absoluteMagnitude = absolute,
            slope = slope,
        )
    }

    /** [start] and [end] are 1-indexed and inclusive, matching the MPC column notes. */
    private fun field(line: String, start: Int, end: Int): String {
        val from = start - 1
        if (from >= line.length) return ""
        return line.substring(from, minOf(end, line.length))
    }
}
