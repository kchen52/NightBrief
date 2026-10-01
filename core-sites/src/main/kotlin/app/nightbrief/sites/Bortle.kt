package app.nightbrief.sites

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
}

/**
 * Reads a compact light-pollution raster (see `tools/build_bortle_grid.py`).
 *
 * Format (big-endian): magic "NBLP", int version (1), double south latitude, double west longitude,
 * double cell size in degrees, int rows, int cols, then rows*cols bytes in row-major order from the
 * south-west corner. Each byte is a Bortle class 1..9, or 0 for no data.
 */
class GridBortleLookup private constructor(
    private val south: Double,
    private val west: Double,
    private val cell: Double,
    private val rows: Int,
    private val cols: Int,
    private val data: ByteArray,
) : BortleLookup {

    override fun lookup(latitude: Double, longitude: Double): Int? {
        val r = floor((latitude - south) / cell).toInt()
        val c = floor((longitude - west) / cell).toInt()
        if (r !in 0 until rows || c !in 0 until cols) return null
        val v = data[r * cols + c].toInt()
        return if (v in 1..9) v else null
    }

    companion object {
        private const val MAGIC = 0x4E424C50 // "NBLP"

        fun read(input: InputStream): GridBortleLookup = DataInputStream(input.buffered()).use { s ->
            require(s.readInt() == MAGIC) { "not a NightBrief light-pollution grid" }
            require(s.readInt() == 1) { "unsupported grid version" }
            val south = s.readDouble()
            val west = s.readDouble()
            val cell = s.readDouble()
            val rows = s.readInt()
            val cols = s.readInt()
            require(rows > 0 && cols > 0 && cell > 0) { "invalid grid header" }
            val data = ByteArray(rows * cols)
            s.readFully(data)
            GridBortleLookup(south, west, cell, rows, cols, data)
        }
    }
}
