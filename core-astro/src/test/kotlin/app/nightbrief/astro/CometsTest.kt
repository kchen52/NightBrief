package app.nightbrief.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CometsTest {
    @Test
    fun fixtureFileParsesTheApparitionAndSkipsTheJunkLine() {
        val text = javaClass.classLoader.getResource("comets/soft00-fixture.txt")!!.readText()
        val orbits = MpcComets.parse(text)
        assertEquals("parsed ${orbits.map { it.name }}", 3, orbits.size)

        val haleBopp = orbits.first { it.name.startsWith("C/1995 O1") }
        assertEquals(-2.0, haleBopp.absoluteMagnitude, 1e-9)
        assertEquals(4.0, haleBopp.slope, 1e-9)
        assertEquals(0.926906, haleBopp.qAu, 1e-9)

        val a3 = orbits.first { it.name.startsWith("C/2023 A3") }
        assertEquals("C/2023 A3 (Tsuchinshan-ATLAS)", a3.name)
        assertEquals(0.391289, a3.qAu, 1e-9)
        assertEquals(1.000183, a3.eccentricity, 1e-9)
        assertEquals(308.5896, a3.argPeriDeg, 1e-9)
        assertEquals(21.6836, a3.nodeDeg, 1e-9)
        assertEquals(139.0978, a3.inclDeg, 1e-9)
        assertEquals(6.5, a3.absoluteMagnitude, 1e-9)
        assertEquals(3.2, a3.slope, 1e-9)
        assertEquals(Instant.parse("2024-09-27T20:14:29.760Z"), a3.perihelion)

        val fixture = orbits.first { it.name == "C/2020 A1 (Fixture)" }
        assertEquals(1.234567, fixture.qAu, 1e-9)
        assertEquals(0.5, fixture.eccentricity, 1e-9)
        assertEquals(10.0, fixture.argPeriDeg, 1e-9)
        assertEquals(20.0, fixture.nodeDeg, 1e-9)
        assertEquals(30.0, fixture.inclDeg, 1e-9)
        assertEquals(5.5, fixture.absoluteMagnitude, 1e-9)
        assertEquals(4.0, fixture.slope, 1e-9)
        assertEquals(Instant.parse("2020-01-02T12:00:00Z"), fixture.perihelion)
    }

    @Test
    fun anEmptyOrShortFileParsesToNothing() {
        assertTrue(MpcComets.parse("").isEmpty())
        assertTrue(MpcComets.parse("<html>not elements</html>\n").isEmpty())
        assertTrue(MpcComets.parse("C too short\n").isEmpty())
    }

    @Test
    fun perihelionDistanceMatchesQForEllipseHyperbolaAndParabola() {
        val at = Instant.parse("2024-10-12T00:00:00Z")
        val ellipse = orbit(eccentricity = 0.5, absoluteMagnitude = 5.0).copy(perihelion = at)
        val hyperbola = orbit(eccentricity = 1.2, absoluteMagnitude = 5.0).copy(perihelion = at)
        val parabola = orbit(eccentricity = 1.0, absoluteMagnitude = 5.0).copy(perihelion = at)
        assertEquals(ellipse.qAu, Comets.geometry(ellipse, at).heliocentricAu, 1e-9)
        assertEquals(hyperbola.qAu, Comets.geometry(hyperbola, at).heliocentricAu, 1e-9)
        assertEquals(parabola.qAu, Comets.geometry(parabola, at).heliocentricAu, 1e-9)
    }

    @Test
    fun halfAnEllipticPeriodIsAphelion() {
        val q = 1.234567
        val eccentricity = 0.5
        val a = q / (1.0 - eccentricity)
        val periodDays = 2.0 * PI * sqrt(a * a * a) / 0.01720209895
        val perihelion = Instant.parse("2020-01-02T12:00:00Z")
        val half = perihelion.plusMillis((periodDays / 2.0 * 86_400_000.0).toLong())
        val geometry = Comets.geometry(
            orbit(eccentricity = eccentricity, absoluteMagnitude = 5.5).copy(qAu = q, perihelion = perihelion),
            half,
        )
        assertEquals(a * (1.0 + eccentricity), geometry.heliocentricAu, 1e-4)
    }

    @Test
    fun tsuchinshanAtlasMatchesHorizonsOnTheEpochAndTheEveningApparition() {
        // JPL SBDB orbit 113, public-domain osculating elements at 2024-May-18.0 TDB.
        // M1 8.9 and K1 5.5 are that solution's total-magnitude parameters.
        val jpl = CometOrbit(
            name = "C/2023 A3 (Tsuchinshan-ATLAS)",
            perihelion = jdToInstant(2460581.240845175775),
            qAu = 0.3914300748355564,
            eccentricity = 1.000095368540586,
            argPeriDeg = 308.4917649633916,
            nodeDeg = 21.55947897244586,
            inclDeg = 139.112109080566,
            absoluteMagnitude = 8.9,
            slope = 5.5,
        )
        // Horizons geocenter, astrometric ICRF, solution JPL#113.
        assertNear(
            jpl,
            Instant.parse("2024-05-18T00:00:00Z"),
            raDeg = 189.051487698,
            decDeg = 1.410063582,
            deltaAu = 1.75475525158412,
            magnitude = 12.338,
            magnitudeTolerance = 0.05,
        )
        assertNear(
            jpl,
            Instant.parse("2024-10-12T00:00:00Z"),
            raDeg = 208.214625367,
            decDeg = -1.470729571,
            deltaAu = 0.47343627670701,
            magnitude = 6.0,
            magnitudeTolerance = 0.25,
        )
    }

    private fun assertNear(
        orbit: CometOrbit,
        instant: Instant,
        raDeg: Double,
        decDeg: Double,
        deltaAu: Double,
        magnitude: Double,
        magnitudeTolerance: Double,
    ) {
        val geometry = Comets.geometry(orbit, instant)
        val separation = separationDeg(geometry.raDec.raDeg, geometry.raDec.decDeg, raDeg, decDeg)
        assertTrue(
            "$instant separation $separation° (ra ${geometry.raDec.raDeg}, dec ${geometry.raDec.decDeg})",
            separation < 0.05,
        )
        assertEquals(deltaAu, geometry.geocentricAu, 0.002)
        assertEquals(magnitude, geometry.magnitude, magnitudeTolerance)
    }

    private fun separationDeg(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
        val cosSep = sin(dec1 * DEG) * sin(dec2 * DEG) +
            cos(dec1 * DEG) * cos(dec2 * DEG) * cos((ra1 - ra2) * DEG)
        return acos(cosSep.coerceIn(-1.0, 1.0)) / DEG
    }

    /** Julian day (TDB treated as UT) to an instant. */
    private fun jdToInstant(jd: Double): Instant {
        val unixMillis = ((jd - 2440587.5) * 86_400_000.0).toLong()
        return Instant.ofEpochMilli(unixMillis)
    }

    private fun orbit(eccentricity: Double, absoluteMagnitude: Double) = CometOrbit(
        name = "C/2099 Z1 (Test)",
        perihelion = Instant.parse("2024-10-12T00:00:00Z"),
        qAu = 1.0,
        eccentricity = eccentricity,
        argPeriDeg = 10.0,
        nodeDeg = 20.0,
        inclDeg = 30.0,
        absoluteMagnitude = absoluteMagnitude,
        slope = 4.0,
    )

    companion object {
        private const val DEG = PI / 180.0
    }
}
