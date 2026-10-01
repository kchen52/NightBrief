package app.nightbrief.astro

import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

internal const val DEG = PI / 180.0
internal const val RAD = 180.0 / PI

internal fun sinD(x: Double) = sin(x * DEG)
internal fun cosD(x: Double) = cos(x * DEG)
internal fun tanD(x: Double) = tan(x * DEG)
internal fun asinD(x: Double) = asin(x.coerceIn(-1.0, 1.0)) * RAD
internal fun atan2D(y: Double, x: Double) = atan2(y, x) * RAD

internal fun norm360(x: Double): Double {
    val r = x % 360.0
    return if (r < 0) r + 360.0 else r
}

/** Equatorial coordinates in degrees. */
data class RaDec(val raDeg: Double, val decDeg: Double)

/** Horizontal coordinates in degrees; azimuth measured from north through east. */
data class AltAz(val altitudeDeg: Double, val azimuthDeg: Double)

internal object AstroTime {
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val J2000_JD = 2451545.0

    fun julianDay(instant: Instant): Double =
        instant.epochSecond / 86_400.0 + instant.nano / 86_400e9 + UNIX_EPOCH_JD

    /** Days since J2000.0. UT is used throughout; the ~70 s TT-UT offset is negligible here. */
    fun daysSinceJ2000(instant: Instant): Double = julianDay(instant) - J2000_JD

    fun greenwichSiderealDeg(d: Double): Double {
        val t = d / 36525.0
        return norm360(280.46061837 + 360.98564736629 * d + 0.000387933 * t * t)
    }
}

internal fun equatorialToHorizontal(
    pos: RaDec,
    latitudeDeg: Double,
    longitudeDeg: Double,
    d: Double,
): AltAz {
    val lst = AstroTime.greenwichSiderealDeg(d) + longitudeDeg
    val ha = norm360(lst - pos.raDeg)
    val alt = asinD(
        sinD(latitudeDeg) * sinD(pos.decDeg) + cosD(latitudeDeg) * cosD(pos.decDeg) * cosD(ha),
    )
    val az = norm360(
        atan2D(sinD(ha), cosD(ha) * sinD(latitudeDeg) - tanD(pos.decDeg) * cosD(latitudeDeg)) + 180.0,
    )
    return AltAz(alt, az)
}

internal fun eclipticToEquatorial(lambdaDeg: Double, betaDeg: Double, epsDeg: Double): RaDec {
    val ra = atan2D(
        sinD(lambdaDeg) * cosD(epsDeg) - tanD(betaDeg) * sinD(epsDeg),
        cosD(lambdaDeg),
    )
    val dec = asinD(sinD(betaDeg) * cosD(epsDeg) + cosD(betaDeg) * sinD(epsDeg) * sinD(lambdaDeg))
    return RaDec(norm360(ra), dec)
}
