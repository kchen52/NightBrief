package app.nightbrief.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class BortleLookupTest {
    @Test
    fun streamingMatchesInMemoryForEveryCellAndOutsideTheGrid() {
        val rows = 4
        val cols = 5
        val payload = ByteArray(rows * cols) { index ->
            when (index) {
                3 -> 0
                else -> (index % 9 + 1).toByte()
            }
        }
        val bytes = nblp(south = 10.0, west = -20.0, cell = 0.5, rows = rows, cols = cols, payload = payload)
        val memory = GridBortleLookup.read(ByteArrayInputStream(bytes))
        val streaming = StreamingGridBortleLookup { ByteArrayInputStream(bytes) }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val lat = 10.0 + (r + 0.5) * 0.5
                val lon = -20.0 + (c + 0.5) * 0.5
                assertEquals(memory.lookup(lat, lon), streaming.lookup(lat, lon))
            }
        }
        // The zero byte is inside the grid and is "no data" for both readers.
        assertNull(memory.lookup(10.25, -18.25))
        assertNull(streaming.lookup(10.25, -18.25))

        val outside = listOf(
            10.0 - 0.001 to -19.75, // south of the grid
            10.0 + rows * 0.5 to -19.75, // exact north edge
            10.25 to -20.0 - 0.001, // west of the grid
            10.25 to -20.0 + cols * 0.5, // exact east edge
            0.0 to 0.0,
        )
        for ((lat, lon) in outside) {
            assertNull(memory.lookup(lat, lon))
            assertNull(streaming.lookup(lat, lon))
        }
    }

    @Test
    fun headerIsFortyBytes() {
        val bytes = nblp(south = 1.0, west = 2.0, cell = 1.0, rows = 1, cols = 1, payload = byteArrayOf(6))
        val counting = CountingInputStream(ByteArrayInputStream(bytes))
        val streaming = StreamingGridBortleLookup { counting }
        assertEquals(6, streaming.lookup(1.5, 2.5))
        // 40-byte header + the single payload byte. Index 0 does not skip.
        assertEquals(NblpHeader.BYTES + 1L, counting.readBytes)
    }

    @Test
    fun shortSkipsStillLandOnTheRightByte() {
        val rows = 3
        val cols = 4
        val payload = ByteArray(rows * cols) { (it % 9 + 1).toByte() }
        payload[5] = 0
        val bytes = nblp(south = 40.0, west = -80.0, cell = 1.0, rows = rows, cols = cols, payload = payload)
        val memory = GridBortleLookup.read(ByteArrayInputStream(bytes))
        val streaming = StreamingGridBortleLookup {
            ShortSkipInputStream(ByteArrayInputStream(bytes), maxSkip = 3)
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val lat = 40.0 + r + 0.5
                val lon = -80.0 + c + 0.5
                assertEquals(memory.lookup(lat, lon), streaming.lookup(lat, lon))
            }
        }
        assertNull(streaming.lookup(39.0, -79.5))
        assertNull(streaming.lookup(43.0, -79.5))
    }

    @Test
    fun gzipBufferedStreamMatchesMemory() {
        val payload = byteArrayOf(1, 0, 9, 4)
        val raw = nblp(south = -10.0, west = 20.0, cell = 2.0, rows = 2, cols = 2, payload = payload)
        val gzipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(raw) }
        }.toByteArray()
        val memory = GridBortleLookup.read(ByteArrayInputStream(raw))
        val streaming = StreamingGridBortleLookup {
            java.io.BufferedInputStream(GZIPInputStream(ByteArrayInputStream(gzipped)))
        }
        assertEquals(1, streaming.lookup(-9.0, 21.0))
        assertNull(streaming.lookup(-9.0, 23.0))
        assertEquals(9, streaming.lookup(-7.0, 21.0))
        assertEquals(memory.lookup(-7.0, 23.0), streaming.lookup(-7.0, 23.0))
    }

    @Test
    fun compositeReturnsTheFirstNonNull() {
        val coarse = GridBortleLookup.read(
            ByteArrayInputStream(
                nblp(south = 0.0, west = 0.0, cell = 10.0, rows = 1, cols = 1, payload = byteArrayOf(4)),
            ),
        )
        val wide = GridBortleLookup.read(
            ByteArrayInputStream(
                nblp(south = 0.0, west = 0.0, cell = 10.0, rows = 2, cols = 1, payload = byteArrayOf(9, 2)),
            ),
        )
        var secondCalls = 0
        val countingSecond = BortleLookup { lat, lon ->
            secondCalls++
            wide.lookup(lat, lon)
        }
        val composite = CompositeBortleLookup(listOf(coarse, countingSecond))

        assertEquals(4, composite.lookup(5.0, 5.0))
        assertEquals(0, secondCalls)
        assertEquals(2, composite.lookup(15.0, 5.0))
        assertEquals(1, secondCalls)
        assertNull(composite.lookup(-1.0, 5.0))
    }

    @Test
    fun compositeFallsThroughANoDataByte() {
        val hole = GridBortleLookup.read(
            ByteArrayInputStream(
                nblp(south = 0.0, west = 0.0, cell = 1.0, rows = 1, cols = 2, payload = byteArrayOf(0, 8)),
            ),
        )
        val fallback = GridBortleLookup.read(
            ByteArrayInputStream(
                nblp(south = 0.0, west = 0.0, cell = 1.0, rows = 1, cols = 2, payload = byteArrayOf(3, 1)),
            ),
        )
        val composite = CompositeBortleLookup(listOf(hole, fallback))
        assertEquals(3, composite.lookup(0.5, 0.5))
        assertEquals(8, composite.lookup(0.5, 1.5))
        assertNull(CompositeBortleLookup(emptyList()).lookup(0.0, 0.0))
    }

    private fun nblp(
        south: Double,
        west: Double,
        cell: Double,
        rows: Int,
        cols: Int,
        payload: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(0x4E424C50)
            data.writeInt(1)
            data.writeDouble(south)
            data.writeDouble(west)
            data.writeDouble(cell)
            data.writeInt(rows)
            data.writeInt(cols)
            data.write(payload)
        }
        return out.toByteArray()
    }
}

/** Counts bytes actually pulled from the delegate, including via [skip]. */
private class CountingInputStream(private val source: InputStream) : InputStream() {
    var readBytes: Long = 0
        private set

    override fun read(): Int {
        val value = source.read()
        if (value >= 0) readBytes++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = source.read(b, off, len)
        if (n > 0) readBytes += n
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = source.skip(n)
        if (skipped > 0) readBytes += skipped
        return skipped
    }
}

/**
 * Returns short skips, and 0 on every other call, so [skipFully] must fall back to read.
 * Bulk reads are also short, the way a parcelled asset stream behaves.
 */
private class ShortSkipInputStream(
    private val source: InputStream,
    private val maxSkip: Long,
) : InputStream() {
    private var skipCalls = 0

    override fun read(): Int = source.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        source.read(b, off, minOf(len, 4))

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        skipCalls++
        if (skipCalls % 2 == 0) return 0
        return source.skip(minOf(n, maxSkip))
    }
}
