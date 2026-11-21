package app.nightbrief.sites

import kotlinx.coroutines.CancellationException
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.floor

/** Bortle dark-sky scale, with zenith sky brightness bands (mag/arcsec²). */
enum class BortleClass(val value: Int, val label: String, val description: String) {
    B1(1, "Excellent dark site", "Zodiacal light, gegenschein and airglow visible; M33 obvious naked-eye."),
    B2(2, "Typical truly dark site", "Milky Way shows detailed structure; clouds appear as black holes in the sky."),
    B3(3, "Rural sky", "Some light domes on the horizon; Milky Way still complex."),
    B4(4, "Rural/suburban transition", "Light domes in several directions; Milky Way visible but lacks detail near the horizon."),
    B5(5, "Suburban sky", "Milky Way weak overhead and washed out near the horizon."),
    B6(6, "Bright suburban sky", "Milky Way only faintly visible at the zenith."),
    B7(7, "Suburban/urban transition", "Whole sky has a grey-white hue; Milky Way invisible."),
    B8(8, "City sky", "Sky glows orange-grey; only bright constellations visible."),
    B9(9, "Inner-city sky", "Only the Moon, planets and a few bright stars visible."),
    ;

    companion object {
        fun of(value: Int): BortleClass = entries.first { it.value == value.coerceIn(1, 9) }

        /** Maps zenith sky quality (SQM, mag/arcsec²) to a Bortle class. */
        fun fromSqm(sqm: Double): BortleClass = when {
            sqm >= 21.99 -> B1
            sqm >= 21.89 -> B2
            sqm >= 21.69 -> B3
            sqm >= 20.49 -> B4
            sqm >= 19.50 -> B5
            sqm >= 18.94 -> B6
            sqm >= 18.38 -> B7
            sqm >= 17.80 -> B8
            else -> B9
        }

        /**
         * Maps artificial zenith brightness (mcd/m², as published in the World Atlas of
         * Artificial Night Sky Brightness) to a Bortle class, assuming a 0.171 mcd/m² natural sky.
         */
        fun fromArtificialBrightness(mcdPerM2: Double): BortleClass {
            val total = 0.171 + mcdPerM2.coerceAtLeast(0.0)
            val sqm = 12.589 - 2.5 * kotlin.math.log10(total / 1000.0)
            return fromSqm(sqm)
        }
    }
}

fun interface BortleLookup {
    /** Returns the Bortle class at a location, or null when no data covers it. */
    fun lookup(latitude: Double, longitude: Double): Int?

    /**
     * Looks up many points, returning a list aligned 1:1 with [points]. Implementors backed by
     * a forward-only stream can satisfy a whole batch in one pass; the default keeps SAM lambdas
     * working. Null means no data, and a failure fails the whole batch.
     */
    fun lookupAll(points: List<Pair<Double, Double>>): List<Int?> =
        points.map { (latitude, longitude) -> lookup(latitude, longitude) }
}

/**
 * NBLP v1 header shared by [GridBortleLookup] and [StreamingGridBortleLookup].
 *
 * Big-endian, 40 bytes: magic "NBLP" (int), version (int, 1), south latitude (double),
 * west longitude (double), cell size in degrees (double), rows (int), cols (int).
 * The payload is `rows * cols` bytes, row-major from the south-west corner.
 * Each byte is a Bortle class 1..9, or 0 for no data.
 */
internal data class NblpHeader(
    val south: Double,
    val west: Double,
    val cell: Double,
    val rows: Int,
    val cols: Int,
) {
    /** Payload index of the cell containing [latitude]/[longitude], or null if outside. */
    fun indexOf(latitude: Double, longitude: Double): Int? {
        val r = floor((latitude - south) / cell).toInt()
        val c = floor((longitude - west) / cell).toInt()
        if (r !in 0 until rows || c !in 0 until cols) return null
        return r * cols + c
    }

    companion object {
        const val BYTES = 40
        private const val MAGIC = 0x4E424C50 // "NBLP"
        private const val VERSION = 1

        fun read(input: DataInputStream): NblpHeader {
            require(input.readInt() == MAGIC) { "not a NightBrief light-pollution grid" }
            require(input.readInt() == VERSION) { "unsupported grid version" }
            val south = input.readDouble()
            val west = input.readDouble()
            val cell = input.readDouble()
            val rows = input.readInt()
            val cols = input.readInt()
            require(rows > 0 && cols > 0 && cell > 0) { "invalid grid header" }
            return NblpHeader(south, west, cell, rows, cols)
        }
    }
}

private fun bortleOrNull(value: Int): Int? = if (value in 1..9) value else null

/**
 * [InputStream.skip] may skip fewer bytes than requested, and some streams return 0
 * before EOF. Keep going, and if [InputStream.skip] returns 0 fall back to [InputStream.read].
 */
internal fun skipFully(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        if (input.read() < 0) {
            throw java.io.EOFException("unexpected end of light-pollution grid")
        }
        remaining -= 1
    }
}

/**
 * Reads a compact light-pollution raster into memory (see `tools/build_bortle_grid.py`).
 * Prefer [StreamingGridBortleLookup] for the shipped grids.
 */
class GridBortleLookup private constructor(
    private val header: NblpHeader,
    private val data: ByteArray,
) : BortleLookup {

    override fun lookup(latitude: Double, longitude: Double): Int? {
        val index = header.indexOf(latitude, longitude) ?: return null
        return bortleOrNull(data[index].toInt() and 0xFF)
    }

    companion object {
        fun read(input: InputStream): GridBortleLookup = DataInputStream(input.buffered()).use { s ->
            val header = NblpHeader.read(s)
            val data = ByteArray(header.rows * header.cols)
            s.readFully(data)
            GridBortleLookup(header, data)
        }
    }
}

/**
 * Looks up NBLP cells without retaining the grid.
 *
 * [lookup] opens [open], parses the 40-byte header, skips to `row * cols + col`,
 * reads one byte, and closes the stream. [lookupAll] opens it once and visits the
 * requested indices in ascending order, so a batch costs one forward inflation no
 * matter how many cells it asks for. Pass a gzip asset through
 * [java.util.zip.GZIPInputStream] (see `AppGraph`). The header is not cached.
 *
 * This does blocking I/O. On the North America grid the worst case reads the whole
 * gzip stream and can take about a second. Call it off the main thread.
 */
class StreamingGridBortleLookup(
    private val open: () -> InputStream,
) : BortleLookup {

    override fun lookup(latitude: Double, longitude: Double): Int? {
        open().use { input ->
            // DataInputStream does not buffer, so the underlying stream stays aligned for skipFully.
            val header = NblpHeader.read(DataInputStream(input))
            val index = header.indexOf(latitude, longitude) ?: return null
            skipFully(input, index.toLong())
            val value = input.read()
            if (value < 0) throw java.io.EOFException("unexpected end of light-pollution grid")
            return bortleOrNull(value)
        }
    }

    override fun lookupAll(points: List<Pair<Double, Double>>): List<Int?> {
        if (points.isEmpty()) return emptyList()
        val result = MutableList<Int?>(points.size) { null }
        open().use { input ->
            val header = NblpHeader.read(DataInputStream(input))
            val targets = ArrayList<Pair<Int, Int>>(points.size)
            points.forEachIndexed { index, (latitude, longitude) ->
                header.indexOf(latitude, longitude)?.let { targets += index to it }
            }
            if (targets.isEmpty()) return result
            // A gzip stream cannot seek back. Visit cells in file order, keeping only the
            // current byte and the cursor, never the grid.
            targets.sortBy { it.second }
            var cursor = 0L
            var lastIndex = -1
            var lastValue: Int? = null
            for ((originalIndex, payloadIndex) in targets) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("light-pollution grid lookup interrupted")
                }
                if (payloadIndex == lastIndex) {
                    result[originalIndex] = lastValue
                    continue
                }
                skipFully(input, payloadIndex.toLong() - cursor)
                val value = input.read()
                if (value < 0) throw java.io.EOFException("unexpected end of light-pollution grid")
                cursor = payloadIndex.toLong() + 1L
                lastIndex = payloadIndex
                lastValue = bortleOrNull(value)
                result[originalIndex] = lastValue
            }
        }
        return result
    }
}

/**
 * Tries each lookup in order and returns the first non-null class.
 * A null means that lookup has no data there (outside its grid, or a 0 byte).
 */
class CompositeBortleLookup(
    private val lookups: List<BortleLookup>,
) : BortleLookup {

    override fun lookup(latitude: Double, longitude: Double): Int? {
        for (lookup in lookups) {
            val value = lookup.lookup(latitude, longitude)
            if (value != null) return value
        }
        return null
    }

    override fun lookupAll(points: List<Pair<Double, Double>>): List<Int?> {
        if (points.isEmpty()) return emptyList()
        val result = MutableList<Int?>(points.size) { null }
        var missing = points.indices.toList()
        for (lookup in lookups) {
            if (missing.isEmpty()) break
            val values = lookup.lookupAll(missing.map { points[it] })
            val stillMissing = ArrayList<Int>(missing.size)
            missing.forEachIndexed { position, originalIndex ->
                val value = values[position]
                if (value == null) stillMissing += originalIndex else result[originalIndex] = value
            }
            missing = stillMissing
        }
        return result
    }
}
