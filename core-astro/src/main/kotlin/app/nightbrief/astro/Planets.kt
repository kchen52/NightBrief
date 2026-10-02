package app.nightbrief.astro

import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Naked-eye planets. Pluto is not tracked. */
enum class Planet(val label: String) {
    MERCURY("Mercury"),
    VENUS("Venus"),
    MARS("Mars"),
    JUPITER("Jupiter"),
    SATURN("Saturn"),
    URANUS("Uranus"),
    NEPTUNE("Neptune"),
}

/** Geocentric planet geometry for one instant. */
data class PlanetPosition(
    val planet: Planet,
    /** J2000 equatorial coordinates. Precession to date (~0.3° by the mid-2020s) is ignored. */
    val raDec: RaDec,
    /** Geocentric ecliptic longitude/latitude, degrees. */
    val lambdaDeg: Double,
    val betaDeg: Double,
    /** Angular distance from the Sun, 0..180°. Positive [eastOfSun] means an evening object. */
    val elongationDeg: Double,
    val eastOfSun: Boolean,
    /** Visual magnitude, phase- and (for Saturn) ring-corrected. */
    val magnitude: Double,
    /** Planet–Earth distance, AU. */
    val distanceAu: Double,
)

/**
 * Planet positions from the JPL Keplerian-elements table ("Keplerian Elements for
 * Approximate Positions of the Major Planets", Table 1, EM Bary through Neptune, valid
 * 1800–2050). TT is approximated by UT; aberration, nutation, and precession are ignored.
 *
 * Geocentric RA/Dec lands within about a tenth of a degree of DE421 through the 2020s, which is
 * plenty for oppositions, elongations, and close-approach dates. Rise/set times for
 * planets are not computed here; use the separation and elongation numbers for planning.
 */
object Planets {
    /**
     * JPL Table 1 elements at J2000 plus rates per century (valid 1800-2050).
     * Angles in degrees, axis in AU. Table 1 stands alone: the b/c/s/f terms on the JPL
     * page belong to the long-range table and double-count here (verified against DE421).
     */
    private data class Elements(
        val axis: Double,
        val axisRate: Double,
        val ecc: Double,
        val eccRate: Double,
        val incl: Double,
        val inclRate: Double,
        val meanLon: Double,
        val meanLonRate: Double,
        val periLon: Double,
        val periLonRate: Double,
        val node: Double,
        val nodeRate: Double,
    )

    private val ELEMENTS = mapOf(
        Planet.MERCURY to Elements(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        Planet.VENUS to Elements(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        Planet.MARS to Elements(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        Planet.JUPITER to Elements(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        Planet.SATURN to Elements(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794),
        Planet.URANUS to Elements(19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589),
        Planet.NEPTUNE to Elements(30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664),
    )

    /** Earth/Moon barycentre elements; stands in for Earth's heliocentric position. */
    private val EARTH = Elements(1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0)

    /** J2000 mean obliquity, matching the J2000 ecliptic frame above. */
    private const val J2000_OBLIQUITY = 23.43928

    fun position(planet: Planet, instant: Instant): PlanetPosition {
        val t = AstroTime.daysSinceJ2000(instant) / 36525.0
        val earth = heliocentric(EARTH, t)
        val helio = heliocentric(ELEMENTS.getValue(planet), t)
        // Geocentric vector = planet heliocentric minus Earth heliocentric.
        val gx = helio[0] - earth[0]
        val gy = helio[1] - earth[1]
        val gz = helio[2] - earth[2]
        val dist = sqrt(gx * gx + gy * gy + gz * gz)
        val lambda = norm360(atan2(gy, gx) * RAD)
        val beta = asinD(gz / dist)
        // The Sun's geocentric direction is opposite Earth's heliocentric vector.
        val sunLambda = norm360(atan2(-earth[1], -earth[0]) * RAD)
        val elong = separationDeg(lambda, beta, sunLambda, 0.0)
        val east = norm180(lambda - sunLambda) > 0
        val earthSun = sqrt(earth[0] * earth[0] + earth[1] * earth[1] + earth[2] * earth[2])
        val planetSun = sqrt(helio[0] * helio[0] + helio[1] * helio[1] + helio[2] * helio[2])
        val phaseAngle = phaseAngleDeg(planetSun, dist, earthSun)
        return PlanetPosition(
            planet = planet,
            raDec = eclipticToEquatorial(lambda, beta, J2000_OBLIQUITY),
            lambdaDeg = lambda,
            betaDeg = beta,
            elongationDeg = elong,
            eastOfSun = east,
            magnitude = magnitude(planet, planetSun, dist, phaseAngle, earth, helio),
            distanceAu = dist,
        )
    }

    fun all(instant: Instant): Map<Planet, PlanetPosition> =
        Planet.entries.associateWith { position(it, instant) }

    /** Heliocentric J2000 ecliptic position, AU. */
    private fun heliocentric(e: Elements, t: Double): DoubleArray {
        val a = e.axis + e.axisRate * t
        val ecc = e.ecc + e.eccRate * t
        val incl = e.incl + e.inclRate * t
        val meanLon = e.meanLon + e.meanLonRate * t
        val periLon = e.periLon + e.periLonRate * t
        val node = e.node + e.nodeRate * t
        val omega = periLon - node
        var anomaly = meanLon - periLon
        anomaly = ((anomaly + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val eccentric = solveKepler(anomaly, ecc)
        val xp = a * (cos(eccentric * DEG) - ecc)
        val yp = a * (sqrt(1.0 - ecc * ecc) * sin(eccentric * DEG))
        val x = (cosD(omega) * cosD(node) - sinD(omega) * sinD(node) * cosD(incl)) * xp +
            (-sinD(omega) * cosD(node) - cosD(omega) * sinD(node) * cosD(incl)) * yp
        val y = (cosD(omega) * sinD(node) + sinD(omega) * cosD(node) * cosD(incl)) * xp +
            (-sinD(omega) * sinD(node) + cosD(omega) * cosD(node) * cosD(incl)) * yp
        val z = (sinD(omega) * sinD(incl)) * xp + (cosD(omega) * sinD(incl)) * yp
        return doubleArrayOf(x, y, z)
    }

    private fun solveKepler(anomalyDeg: Double, ecc: Double): Double {
        var e = anomalyDeg + ecc * sinD(anomalyDeg) * RAD
        repeat(8) {
            e -= (e - ecc * sinD(e) * RAD - anomalyDeg) / (1 - ecc * cosD(e))
        }
        return e
    }

    /** Sun–planet–Earth phase angle from the three side lengths. */
    private fun phaseAngleDeg(planetSunAu: Double, planetEarthAu: Double, earthSunAu: Double): Double {
        val cosPhase = ((planetSunAu * planetSunAu + planetEarthAu * planetEarthAu - earthSunAu * earthSunAu) /
            (2 * planetSunAu * planetEarthAu)).coerceIn(-1.0, 1.0)
        return kotlin.math.acos(cosPhase) * RAD
    }

    private fun magnitude(
        planet: Planet,
        r: Double,
        dist: Double,
        phaseDeg: Double,
        earth: DoubleArray,
        helio: DoubleArray,
    ): Double {
        val distTerm = 5 * kotlin.math.log10(r * dist)
        return when (planet) {
            // Cubic phase curve fit to JPL Horizons over the Jun–Oct 2024 apparition
            // (max residual 0.16 mag, phases 0–180°). The old quadratic law blows up
            // past dichotomy; Mercury's thin crescent genuinely fades past +5.
            Planet.MERCURY -> -0.61 + distTerm + 0.0505 * phaseDeg - 0.00049 * phaseDeg * phaseDeg +
                0.0000030 * phaseDeg * phaseDeg * phaseDeg
            Planet.VENUS -> -4.34 + distTerm + 0.013 * phaseDeg + 0.000114 * phaseDeg * phaseDeg
            Planet.MARS -> -1.52 + distTerm + 0.016 * phaseDeg
            Planet.JUPITER -> -9.40 + distTerm + 0.005 * phaseDeg
            Planet.SATURN -> {
                // Ring tilt from Earth's saturnicentric latitude.
                val dx = earth[0] - helio[0]
                val dy = earth[1] - helio[1]
                val dz = earth[2] - helio[2]
                val range = sqrt(dx * dx + dy * dy + dz * dz)
                val sinTilt = abs(dz / range)
                -8.88 + distTerm + 0.044 * phaseDeg - 2.60 * sinTilt + 1.25 * sinTilt * sinTilt
            }
            Planet.URANUS -> -7.19 + distTerm
            Planet.NEPTUNE -> -6.87 + distTerm
        }
    }

    /** Angular separation of two ecliptic (or equatorial) points, degrees. */
    fun separationDeg(lambda1: Double, beta1: Double, lambda2: Double, beta2: Double): Double {
        val cosSep = (sinD(beta1) * sinD(beta2) + cosD(beta1) * cosD(beta2) * cosD(lambda1 - lambda2))
            .coerceIn(-1.0, 1.0)
        return kotlin.math.acos(cosSep) * RAD
    }

    private fun norm180(x: Double): Double {
        val r = norm360(x)
        return if (r > 180) r - 360 else r
    }
}
