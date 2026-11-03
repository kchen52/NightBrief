package app.nightbrief.sites

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Bit layout of WMSK v1, and the shipped Natural Earth mask. The file lives
 * under `data/src/main/assets/` and is read by path so this stays a plain JVM test.
 */
class OpenWaterTest {
    @Test
    fun packedBitsFollowTheSouthWestHighBitOrder() {
        // Row 0 (south): water, water, then six land cells. Row 1: four land, four water.
        val mask = GridOpenWater {
            ByteArrayInputStream(
                wmsk(
                    south = 0.0,
                    west = 0.0,
                    cell = 1.0,
                    rows = 2,
                    cols = 8,
                    payload = byteArrayOf(0b1100_0000.toByte(), 0b0000_1111.toByte()),
                ),
            )
        }
        assertTrue(mask.isOpenWater(0.5, 0.5))
        assertTrue(mask.isOpenWater(0.5, 1.5))
        assertFalse(mask.isOpenWater(0.5, 2.5))
        assertFalse(mask.isOpenWater(1.5, 0.5))
        assertTrue(mask.isOpenWater(1.5, 4.5))
        assertTrue(mask.isOpenWater(1.5, 7.5))
        // Exact north and east edges belong to the last cell.
        assertFalse("north edge was water", mask.isOpenWater(2.0, 0.5))
        assertFalse("east edge was water", mask.isOpenWater(0.5, 8.0))
        assertFalse(mask.isOpenWater(3.0, 0.5))
        assertFalse(mask.isOpenWater(0.5, -0.1))
        assertFalse(mask.isOpenWater(Double.NaN, 0.5))
    }

    @Test
    fun antimeridianReadsTheSameColumn() {
        val mask = GridOpenWater {
            ByteArrayInputStream(
                wmsk(
                    south = -90.0,
                    west = -180.0,
                    cell = 90.0,
                    rows = 2,
                    cols = 4,
                    // Only the south-west cell (lat -90..0, lon -180..-90) is water.
                    payload = byteArrayOf(0b1000_0000.toByte()),
                ),
            )
        }
        assertTrue(mask.isOpenWater(-45.0, -180.0))
        assertTrue(mask.isOpenWater(-45.0, 180.0))
        assertFalse(mask.isOpenWater(-45.0, -90.0))
    }

    @Test
    fun unreadableMaskReportsLand() {
        val mask = GridOpenWater { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        assertFalse(mask.isOpenWater(43.6, -79.4))
        assertFalse(mask.isOpenWater(0.0, 0.0))
    }

    @Test(expected = CancellationException::class)
    fun cancelledReadIsNotSwallowed() {
        val mask = GridOpenWater { throw CancellationException("stop") }
        mask.isOpenWater(1.0, 2.0)
    }

    @Test
    fun shippedMaskKeepsTorontoOnLandAndTheLakeOffshore() {
        val file = asset("water_mask.wmsk.gzip")
        assertTrue("mask gzip is ${file.length()} bytes", file.length() in 1..1_000_000)
        val mask = GridOpenWater {
            GZIPInputStream(file.inputStream().buffered())
        }
        assertFalse(mask.isOpenWater(43.6532, -79.3832))
        assertFalse(mask.isOpenWater(43.80, -79.40))
        assertTrue(mask.isOpenWater(43.60, -77.80))
        assertFalse("Manitoulin was water", mask.isOpenWater(45.80, -82.15))
        assertFalse(mask.isOpenWater(39.0, -98.0))
        assertTrue(mask.isOpenWater(0.0, -150.0))
        assertTrue(mask.isOpenWater(0.0, 180.0))
        assertEquals(mask.isOpenWater(0.0, 180.0), mask.isOpenWater(0.0, -180.0))

        // Issue #53: these bearings from downtown Toronto were saved as shooting sites.
        for ((distanceKm, bearingDeg) in offshoreFromToronto) {
            val (lat, lon) = destination(43.6532, -79.3832, distanceKm, bearingDeg)
            assertTrue(
                "expected open water at $distanceKm km bearing $bearingDeg ($lat, $lon)",
                mask.isOpenWater(lat, lon),
            )
        }
        for ((distanceKm, bearingDeg) in landFromToronto) {
            val (lat, lon) = destination(43.6532, -79.3832, distanceKm, bearingDeg)
            assertFalse(
                "expected land at $distanceKm km bearing $bearingDeg ($lat, $lon)",
                mask.isOpenWater(lat, lon),
            )
        }
    }

    private fun wmsk(
        south: Double,
        west: Double,
        cell: Double,
        rows: Int,
        cols: Int,
        payload: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeInt(0x574D534B)
            stream.writeInt(1)
            stream.writeDouble(south)
            stream.writeDouble(west)
            stream.writeDouble(cell)
            stream.writeInt(rows)
            stream.writeInt(cols)
            stream.write(payload)
        }
        return out.toByteArray()
    }

    /** Great-circle destination. Same sphere and formula as DarkSkyFinder.destination. */
    private fun destination(latitude: Double, longitude: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val angular = distanceKm / 6371.0
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val bearing = Math.toRadians(bearingDeg)
        val lat2 = kotlin.math.asin(
            kotlin.math.sin(lat1) * kotlin.math.cos(angular) +
                kotlin.math.cos(lat1) * kotlin.math.sin(angular) * kotlin.math.cos(bearing),
        )
        val lon2 = lon1 + kotlin.math.atan2(
            kotlin.math.sin(bearing) * kotlin.math.sin(angular) * kotlin.math.cos(lat1),
            kotlin.math.cos(angular) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    private fun asset(name: String): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .map { File(it, "data/src/main/assets/$name") }
            .firstOrNull { it.isFile }
            ?: error("missing data/src/main/assets/$name (started at ${start.absolutePath})")
    }

    private companion object {
        val offshoreFromToronto = listOf(
            30.0 to 90.0,
            30.0 to 135.0,
            30.0 to 180.0,
            45.0 to 90.0,
            45.0 to 135.0,
        )
        val landFromToronto = listOf(
            30.0 to 0.0,
            30.0 to 315.0,
            60.0 to 0.0,
        )
    }
}
