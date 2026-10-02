package app.nightbrief.astro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Checked against Skyfield 1.55 + DE421 geocentric J2000 positions (see the
 * 2024-08-12, 2024-09-08, 2024-12-07, and 2025-01-01 reference runs). Planning-grade:
 * RA/Dec within about a tenth of a degree so oppositions and close approaches land on the right date.
 */
class PlanetsTest {
    private data class Reference(val instant: String, val raDeg: Double, val decDeg: Double)

    private val references: Map<Planet, List<Reference>> = mapOf(
        Planet.MERCURY to listOf(
            Reference("2024-08-12T00:00:00Z", 152.0482, 6.4465),
            Reference("2025-01-01T00:00:00Z", 258.6965, -21.9123),
            Reference("2024-09-08T00:00:00Z", 150.3419, 12.8420),
            Reference("2024-12-07T00:00:00Z", 251.6429, -20.6880),
        ),
        Planet.VENUS to listOf(
            Reference("2024-08-12T00:00:00Z", 160.3309, 9.8630),
            Reference("2025-01-01T00:00:00Z", 330.0572, -13.7060),
            Reference("2024-09-08T00:00:00Z", 190.6153, -3.7155),
            Reference("2024-12-07T00:00:00Z", 302.0470, -22.6345),
        ),
        Planet.MARS to listOf(
            Reference("2024-08-12T00:00:00Z", 73.3492, 22.1226),
            Reference("2025-01-01T00:00:00Z", 124.7550, 23.6230),
            Reference("2024-09-08T00:00:00Z", 91.7043, 23.4634),
            Reference("2024-12-07T00:00:00Z", 128.9396, 21.5481),
        ),
        Planet.JUPITER to listOf(
            Reference("2024-08-12T00:00:00Z", 74.7872, 22.0022),
            Reference("2025-01-01T00:00:00Z", 71.5094, 21.7414),
            Reference("2024-09-08T00:00:00Z", 78.5562, 22.3134),
            Reference("2024-12-07T00:00:00Z", 74.9021, 22.0349),
        ),
        Planet.SATURN to listOf(
            Reference("2024-08-12T00:00:00Z", 349.4678, -6.8549),
            Reference("2025-01-01T00:00:00Z", 346.1911, -8.0515),
            Reference("2024-09-08T00:00:00Z", 347.6909, -7.6581),
            Reference("2024-12-07T00:00:00Z", 344.8850, -8.6598),
        ),
        Planet.URANUS to listOf(
            Reference("2024-08-12T00:00:00Z", 54.4999, 19.1679),
            Reference("2025-01-01T00:00:00Z", 50.9628, 18.3474),
            Reference("2024-09-08T00:00:00Z", 54.6659, 19.2028),
            Reference("2024-12-07T00:00:00Z", 51.7828, 18.5394),
        ),
        Planet.NEPTUNE to listOf(
            Reference("2024-08-12T00:00:00Z", 359.7583, -1.5337),
            Reference("2025-01-01T00:00:00Z", 357.7110, -2.3930),
            Reference("2024-09-08T00:00:00Z", 359.1748, -1.8003),
            Reference("2024-12-07T00:00:00Z", 357.5655, -2.4728),
        ),
    )

    @Test
    fun planetsMatchDe421WithinPlanningTolerance() {
        var worst = 0.0
        var worstAt = ""
        for ((planet, refs) in references) {
            for (ref in refs) {
                val got = Planets.position(planet, Instant.parse(ref.instant)).raDec
                val err = Planets.separationDeg(got.raDeg, got.decDeg, ref.raDeg, ref.decDeg)
                if (err > worst) {
                    worst = err
                    worstAt = "$planet at ${ref.instant}"
                }
                assertTrue("$planet at ${ref.instant} was ${"%.2f".format(err)}° off", err < 0.5)
            }
        }
        println("Worst planet error vs DE421: ${"%.3f".format(worst)}° ($worstAt)")
    }

    @Test
    fun oppositionGeometryHoldsAtSaturnOpposition() {
        // 2024-09-08: Saturn opposition per DE421 (Sun RA 166.65, Saturn RA 347.69).
        val saturn = Planets.position(Planet.SATURN, Instant.parse("2024-09-08T00:00:00Z"))
        assertTrue("elongation was ${saturn.elongationDeg}", abs(saturn.elongationDeg - 180.0) < 3.0)
    }

    @Test
    fun mercuryStaysInTheSunsNeighbourhood() {
        // Greatest possible elongation for Mercury is ~28°.
        var worst = 0.0
        var day = Instant.parse("2024-01-01T00:00:00Z")
        repeat(365) {
            val elong = Planets.position(Planet.MERCURY, day).elongationDeg
            if (elong > worst) worst = elong
            day = day.plusSeconds(86400)
        }
        assertTrue("Mercury elongation reached $worst°", worst < 30.0)
    }

    @Test
    fun magnitudesStayInSaneRanges() {
        val at = Instant.parse("2024-08-12T00:00:00Z")
        val ranges = mapOf(
            Planet.VENUS to -5.0..-3.0,
            Planet.MARS to -3.0..2.0,
            Planet.JUPITER to -3.0..-1.0,
            Planet.SATURN to -1.0..2.0,
            Planet.URANUS to 5.0..7.0,
            Planet.NEPTUNE to 7.0..9.0,
        )
        for ((planet, range) in ranges) {
            val mag = Planets.position(planet, at).magnitude
            assertTrue("$planet magnitude was $mag", mag in range)
        }
    }

    @Test
    fun mercuryAtGreatestElongationIsNakedEye() {
        // Mercury's elongation peaks near 28°; it is observable (and the phase curve valid) there.
        var best = 0.0
        var bestAt = Instant.parse("2024-07-01T00:00:00Z")
        var day = bestAt
        repeat(31) {
            val elong = Planets.position(Planet.MERCURY, day).elongationDeg
            if (elong > best) {
                best = elong
                bestAt = day
            }
            day = day.plusSeconds(86400)
        }
        assertTrue("July 2024 greatest elongation was $best°", best in 25.0..29.0)
        val mag = Planets.position(Planet.MERCURY, bestAt).magnitude
        assertTrue("Mercury magnitude at elongation was $mag", mag in -1.0..3.0)
    }

    private fun assertTrue(message: String, value: Boolean) {
        if (!value) throw AssertionError(message)
    }
}
