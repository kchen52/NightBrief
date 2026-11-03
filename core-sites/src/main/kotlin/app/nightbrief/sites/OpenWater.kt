package app.nightbrief.sites

import kotlinx.coroutines.CancellationException
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.floor

/**
 * Whether a coordinate is open water (ocean or a lake).
 *
 * True means the point is not a place to set up a telescope. False means land,
 * a cell the mask does not cover, or a mask that could not be read. A failed
 * read must not be treated as a surveyed shoreline: callers that drop water
 * keep the sample instead of discarding every candidate.
 *
 * The lookup does blocking I/O on the first call when the implementation
 * inflates a grid. Call it off the main thread.
 */
fun interface OpenWater {
    fun isOpenWater(latitude: Double, longitude: Double): Boolean
}

/**
 * WMSK v1 header shared by [GridOpenWater].
 *
 * Big-endian, 40 bytes: magic "WMSK" (int), version (int, 1), south latitude
 * (double), west longitude (double), cell size in degrees (double), rows (int),
 * cols (int). The payload is packed bits, row-major from the south-west corner,
 * eight cells per byte, first cell in the high bit. A set bit is open water.
 *
 * A cell covers `[south + r*cell, south + (r+1)*cell)` by
 * `[west + c*cell, west + (c+1)*cell)`. Longitude +180° is the same column as
 * −180°. A point exactly on the north or east edge belongs to the last cell.
 */
internal data class WmskHeader(
    val south: Double,
    val west: Double,
    val cell: Double,
    val rows: Int,
    val cols: Int,
) {
    /** Payload bit index of the cell containing [latitude]/[longitude], or null if outside. */
    fun indexOf(latitude: Double, longitude: Double): Int? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        var lon = longitude % 360.0
        if (lon >= 180.0) lon -= 360.0
        if (lon < -180.0) lon += 360.0
        var row = floor((latitude - south) / cell).toInt()
        var col = floor((lon - west) / cell).toInt()
        if (row == rows) row = rows - 1
        if (col == cols) col = cols - 1
        if (row !in 0 until rows || col !in 0 until cols) return null
        return row * cols + col
    }

    companion object {
        const val BYTES = 40
        private const val MAGIC = 0x574D534B // "WMSK"
        private const val VERSION = 1

        fun read(input: DataInputStream): WmskHeader {
            require(input.readInt() == MAGIC) { "not a NightBrief water mask" }
            require(input.readInt() == VERSION) { "unsupported water mask version" }
            val south = input.readDouble()
            val west = input.readDouble()
            val cell = input.readDouble()
            val rows = input.readInt()
            val cols = input.readInt()
            require(rows > 0 && cols > 0 && cell > 0.0) { "invalid water mask header" }
            return WmskHeader(south, west, cell, rows, cols)
        }
    }
}

/**
 * Open-water bit grid (see `tools/build_water_mask.py`).
 *
 * The first [isOpenWater] inflates [open] and keeps the bits. A search reads
 * every sample, and a gzip stream cannot seek, so one inflate is the point of
 * holding them. The shipped mask is about 3 MB uncompressed. This is not the
 * Bortle grid: those stay streamed because they are tens of megabytes and a
 * lookup wants one byte.
 *
 * Pass gzip bytes through [java.util.zip.GZIPInputStream] (see `AppGraph`).
 * A short or malformed stream makes every later call return false (land) so
 * one bad file does not drop every darker-sky candidate. [CancellationException]
 * is rethrown.
 */
class GridOpenWater(
    private val open: () -> InputStream,
) : OpenWater {
    private val grid: Loaded? by lazy { load() }

    override fun isOpenWater(latitude: Double, longitude: Double): Boolean {
        val loaded = grid ?: return false
        val index = loaded.header.indexOf(latitude, longitude) ?: return false
        val byte = loaded.data[index ushr 3].toInt() and 0xFF
        val bit = 7 - (index and 7)
        return (byte ushr bit) and 1 == 1
    }

    private fun load(): Loaded? =
        try {
            open().use { input ->
                val stream = DataInputStream(input.buffered())
                val header = WmskHeader.read(stream)
                val nbytes = (header.rows.toLong() * header.cols + 7) / 8
                if (nbytes !in 1..(16L * 1024 * 1024)) return null
                val data = ByteArray(nbytes.toInt())
                stream.readFully(data)
                Loaded(header, data)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private data class Loaded(val header: WmskHeader, val data: ByteArray)
}
