package app.nightbrief.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Sanity-checks the shipped Falchi atlas grids. The files live under
 * `data/src/main/assets/` and are read by path so this stays a plain JVM test.
 */
class BortleAtlasAssetTest {
    @Test
    fun shippedGridsMatchKnownSkies() {
        val naFile = asset("bortle_na.nblp.gz")
        val worldFile = asset("bortle_world.nblp.gz")
        println("bortle_na.nblp.gz ${naFile.length()} bytes")
        println("bortle_world.nblp.gz ${worldFile.length()} bytes")

        val naBytes = gunzip(naFile)
        val worldBytes = gunzip(worldFile)
        val naHeader = NblpHeader.read(DataInputStream(ByteArrayInputStream(naBytes)))
        val worldHeader = NblpHeader.read(DataInputStream(ByteArrayInputStream(worldBytes)))

        assertEquals(5760, naHeader.rows)
        assertEquals(14400, naHeader.cols)
        assertEquals(-170.0, naHeader.west, 0.0)
        assertEquals(1.0 / 120.0, naHeader.cell, 0.0)
        // Half a 30-arcsec pixel south of 24°, snapped to the atlas pixel edge.
        assertEquals(24.0 - 0.5 / 120.0, naHeader.south, 1e-4)
        assertEquals(3600, worldHeader.rows)
        assertEquals(7200, worldHeader.cols)
        assertEquals(-90.0, worldHeader.south, 0.0)
        assertEquals(-180.0, worldHeader.west, 0.0)
        assertEquals(0.05, worldHeader.cell, 0.0)

        val naMemory = GridBortleLookup.read(ByteArrayInputStream(naBytes))
        val worldMemory = GridBortleLookup.read(ByteArrayInputStream(worldBytes))
        val naStream = StreamingGridBortleLookup {
            BufferedInputStream(GZIPInputStream(naFile.inputStream()))
        }
        val worldStream = StreamingGridBortleLookup {
            BufferedInputStream(GZIPInputStream(worldFile.inputStream()))
        }
        val composite = CompositeBortleLookup(listOf(naStream, worldStream))

        val probes = listOf(
            Probe("Toronto", 43.65, -79.38, 8, 9, na = true),
            Probe("Long Point ON", 42.58, -80.40, 3, 4, na = true),
            Probe("Torrance Barrens", 44.94, -79.51, 2, 3, na = true),
            Probe("Cherry Springs PA", 41.66, -77.82, 2, 3, na = true),
            Probe("Edmonton", 53.55, -113.49, 8, 9, na = true),
            Probe("London", 51.507, -0.128, 8, 9, na = false),
            Probe("Paranal", -24.627, -70.404, 1, 2, na = false),
        )
        println("site                  lat      lon      value")
        for (probe in probes) {
            val value = composite.lookup(probe.lat, probe.lon)
            println(
                "%-20s %8.3f %8.3f  %s".format(probe.name, probe.lat, probe.lon, value?.toString() ?: "null"),
            )
            assertTrue("${probe.name} lookup was $value", value != null && value in probe.low..probe.high)
            if (probe.na) {
                assertEquals(naMemory.lookup(probe.lat, probe.lon), value)
                assertEquals(value, naStream.lookup(probe.lat, probe.lon))
            } else {
                assertNull(naMemory.lookup(probe.lat, probe.lon))
                assertNull(naStream.lookup(probe.lat, probe.lon))
                assertEquals(worldMemory.lookup(probe.lat, probe.lon), value)
                assertEquals(value, worldStream.lookup(probe.lat, probe.lon))
            }
        }

        // Atlas coverage is about -60°..85°. Poles and the deep south are no-data (byte 0).
        assertNull(composite.lookup(89.0, 0.0))
        assertNull(composite.lookup(-70.0, 0.0))
        assertNull(worldMemory.lookup(89.0, 0.0))
        assertNull(worldMemory.lookup(-70.0, 0.0))

        val cornerLat = naHeader.south + (naHeader.rows - 0.5) * naHeader.cell
        val cornerLon = naHeader.west + (naHeader.cols - 0.5) * naHeader.cell
        assertEquals(naMemory.lookup(cornerLat, cornerLon), naStream.lookup(cornerLat, cornerLon))
        val timesMs = DoubleArray(3) {
            val start = System.nanoTime()
            val value = naStream.lookup(cornerLat, cornerLon)
            val ms = (System.nanoTime() - start) / 1_000_000.0
            assertEquals(naMemory.lookup(cornerLat, cornerLon), value)
            ms
        }
        println(
            "NA far-corner streaming lookup ms (lat %.5f lon %.5f): %s".format(
                cornerLat,
                cornerLon,
                timesMs.joinToString { "%.1f".format(it) },
            ),
        )
    }

    private data class Probe(
        val name: String,
        val lat: Double,
        val lon: Double,
        val low: Int,
        val high: Int,
        val na: Boolean,
    )

    private fun gunzip(file: File): ByteArray =
        GZIPInputStream(file.inputStream().buffered()).use { it.readBytes() }

    private fun asset(name: String): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .map { File(it, "data/src/main/assets/$name") }
            .firstOrNull { it.isFile }
            ?: error("missing data/src/main/assets/$name (started at ${start.absolutePath})")
    }
}
