package app.nightbrief.astro

import kotlin.math.cos

/**
 * Low-precision solar and lunar positions from the Astronomical Almanac
 * ("Low precision formulas for the Sun / Moon"). Sun is good to ~0.01°,
 * Moon to ~0.3° in longitude, which keeps rise/set times within a couple of minutes.
 */
internal object Bodies {

    fun obliquity(d: Double): Double = 23.439 - 0.0000004 * d

    fun sunEclipticLongitude(d: Double): Double {
        val l = norm360(280.460 + 0.9856474 * d)
        val g = norm360(357.528 + 0.9856003 * d)
        return norm360(l + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
    }

    fun sunEquatorial(d: Double): RaDec =
        eclipticToEquatorial(sunEclipticLongitude(d), 0.0, obliquity(d))

    data class MoonEcliptic(val lambdaDeg: Double, val betaDeg: Double, val parallaxDeg: Double)

    fun moonEcliptic(d: Double): MoonEcliptic {
        val t = d / 36525.0
        val lambda = 218.32 + 481267.881 * t +
            6.29 * sinD(135.0 + 477198.87 * t) -
            1.27 * sinD(259.3 - 413335.36 * t) +
            0.66 * sinD(235.7 + 890534.22 * t) +
            0.21 * sinD(269.9 + 954397.74 * t) -
            0.19 * sinD(357.5 + 35999.05 * t) -
            0.11 * sinD(186.5 + 966404.03 * t)
        val beta = 5.13 * sinD(93.3 + 483202.02 * t) +
            0.28 * sinD(228.2 + 960400.89 * t) -
            0.28 * sinD(318.3 + 6003.15 * t) -
            0.17 * sinD(217.6 - 407332.21 * t)
        val parallax = 0.9508 +
            0.0518 * cosD(135.0 + 477198.87 * t) +
            0.0095 * cosD(259.3 - 413335.36 * t) +
            0.0078 * cosD(235.7 + 890534.22 * t) +
            0.0028 * cosD(269.9 + 954397.74 * t)
        return MoonEcliptic(norm360(lambda), beta, parallax)
    }

    fun moonEquatorial(d: Double): Pair<RaDec, Double> {
        val m = moonEcliptic(d)
        return eclipticToEquatorial(m.lambdaDeg, m.betaDeg, obliquity(d)) to m.parallaxDeg
    }

    /** Illuminated fraction (0..1) and phase (0 = new, 0.5 = full, cycling to 1). */
    fun moonIllumination(d: Double): Pair<Double, Double> {
        val m = moonEcliptic(d)
        val sunLambda = sunEclipticLongitude(d)
        val cosElongation = cosD(m.betaDeg) * cosD(m.lambdaDeg - sunLambda)
        val fraction = (1 - cosElongation) / 2
        val phase = norm360(m.lambdaDeg - sunLambda) / 360.0
        return fraction to phase
    }

    /** Converts a geocentric lunar altitude to topocentric (parallax lowers the Moon by up to ~1°). */
    fun topocentricAltitude(geocentricAltDeg: Double, parallaxDeg: Double): Double =
        geocentricAltDeg - asinD(sinD(parallaxDeg) * cos(geocentricAltDeg * DEG))
}
