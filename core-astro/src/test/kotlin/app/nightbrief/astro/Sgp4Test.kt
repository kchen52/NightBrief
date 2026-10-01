package app.nightbrief.astro

import app.nightbrief.astro.sgp4.Sgp4
import app.nightbrief.astro.sgp4.Tem
import app.nightbrief.astro.sgp4.Tle
import app.nightbrief.astro.sgp4.TleFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Published TEME vectors from Vallado's companion `tcppver.out`
 * (brandon-rhodes/python-sgp4, `sgp4/SGP4-VER.TLE`).
 * Position components are the printed kilometres; tolerance is 1e-6 km.
 */
class Sgp4Test {
    @Test
    fun satellite00005AtEpoch_positionErrorUnder1e6Km() {
        val tle = Tle.parse(VANGUARD_1, VANGUARD_2)
        assertEquals(Instant.parse("2000-06-27T18:50:19.733568Z"), tle.epoch)
        val tem = Sgp4.propagate(VANGUARD_1, VANGUARD_2, tle.epoch)
        assertPosition(
            "sat 00005 t=0",
            tem,
            7022.46529266,
            -1400.08296755,
            0.03995155,
        )
    }

    @Test
    fun satellite00005At360Minutes_positionErrorUnder1e6Km() {
        val epoch = Tle.parse(VANGUARD_1, VANGUARD_2).epoch
        val tem = Sgp4.propagate(VANGUARD_1, VANGUARD_2, epoch.plus(Duration.ofMinutes(360)))
        assertPosition(
            "sat 00005 t=360 min",
            tem,
            -7154.03120202,
            -3783.17682504,
            -3536.19412294,
        )
    }

    @Test
    fun deepSpaceMolniya08195AtEpoch_positionErrorUnder1e6Km() {
        val tle = Tle.parse(MOLNIYA_1, MOLNIYA_2)
        assertEquals(Instant.parse("2006-06-25T07:58:18.143616Z"), tle.epoch)
        val tem = Sgp4.propagate(MOLNIYA_1, MOLNIYA_2, tle.epoch)
        assertPosition(
            "sat 08195 (Molniya, deep space) t=0",
            tem,
            2349.89483350,
            -14785.93811562,
            0.02119378,
        )
    }

    @Test
    fun deepSpaceMolniya08195At360Minutes_positionErrorUnder1e6Km() {
        val epoch = Tle.parse(MOLNIYA_1, MOLNIYA_2).epoch
        val tem = Sgp4.propagate(MOLNIYA_1, MOLNIYA_2, epoch.plus(Duration.ofMinutes(360)))
        assertPosition(
            "sat 08195 (Molniya, deep space) t=360 min",
            tem,
            19089.29762968,
            3107.89495018,
            39958.14661370,
        )
    }

    @Test
    fun malformedTleThrowsInsteadOfCrashing() {
        val error = runCatching {
            Sgp4.propagate("this is not a tle", "neither is this", Instant.EPOCH)
        }.exceptionOrNull()
        assertTrue(error is TleFormatException)
    }

    private fun assertPosition(label: String, actual: Tem, x: Double, y: Double, z: Double) {
        val dx = abs(actual.xKm - x)
        val dy = abs(actual.yKm - y)
        val dz = abs(actual.zKm - z)
        val err = maxOf(dx, dy, dz)
        assertTrue(
            "$label TEME position error $err km (dx=$dx dy=$dy dz=$dz); tolerance 1e-6 km; " +
                "actual=(${actual.xKm}, ${actual.yKm}, ${actual.zKm})",
            err < 1e-6,
        )
    }

    companion object {
        // Satellite 00005, the classic near-Earth verification case.
        private const val VANGUARD_1 = "1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753"
        private const val VANGUARD_2 = "2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667"

        // Satellite 08195, MOLNIYA 2-14, a 12-hour deep-space case from SGP4-VER.TLE.
        private const val MOLNIYA_1 = "1 08195U 75081A   06176.33215444  .00000099  00000-0  11873-3 0   813"
        private const val MOLNIYA_2 = "2 08195  64.1586 279.0717 6877146 264.7651  20.2257  2.00491383225656"
    }
}
