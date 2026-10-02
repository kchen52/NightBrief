package app.nightbrief.sites

import kotlinx.serialization.Serializable

/**
 * Minimum altitude, in degrees, that the sky must clear in each of eight compass sectors.
 *
 * Sectors are centred on N, NE, E, SE, S, SW, W, and NW. A null sector adds no obstruction, so a
 * target keeps its own minimum altitude. 0° is an open horizon in that direction.
 */
@Serializable
data class LocalHorizon(
    val north: Double? = null,
    val northEast: Double? = null,
    val east: Double? = null,
    val southEast: Double? = null,
    val south: Double? = null,
    val southWest: Double? = null,
    val west: Double? = null,
    val northWest: Double? = null,
) {
    init {
        altitudes().forEach { alt ->
            require(alt == null || alt in 0.0..90.0) { "horizon altitude out of range: $alt" }
        }
    }

    /** True when every sector is unset. An all-zero mask is set, and still blocks nothing. */
    val isUnset: Boolean get() = altitudes().all { it == null }

    /** N, NE, E, SE, S, SW, W, NW. */
    fun altitudes(): List<Double?> = listOf(
        north, northEast, east, southEast, south, southWest, west, northWest,
    )

    /**
     * Obstruction altitude for [azimuthDeg], measured from north through east.
     * An unset sector returns 0.
     */
    fun obstructionDeg(azimuthDeg: Double): Double = altitudes()[sectorIndex(azimuthDeg)] ?: 0.0

    fun withSector(index: Int, altitudeDeg: Double?): LocalHorizon {
        require(index in altitudes().indices) { "horizon sector out of range: $index" }
        val values = altitudes().toMutableList()
        values[index] = altitudeDeg
        return fromAltitudes(values)
    }

    companion object {
        val ABBREVIATIONS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        /** All eight sectors set to the horizon. This is how the site form starts a mask. */
        fun open(): LocalHorizon = LocalHorizon(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /** Sector index for [azimuthDeg]: 0 is north and the index steps clockwise by 45°. */
        fun sectorIndex(azimuthDeg: Double): Int =
            ((((azimuthDeg % 360.0) + 360.0) + 22.5) / 45.0).toInt() % 8

        private fun fromAltitudes(values: List<Double?>): LocalHorizon = LocalHorizon(
            north = values[0],
            northEast = values[1],
            east = values[2],
            southEast = values[3],
            south = values[4],
            southWest = values[5],
            west = values[6],
            northWest = values[7],
        )
    }
}
