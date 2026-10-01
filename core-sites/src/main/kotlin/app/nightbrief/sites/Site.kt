package app.nightbrief.sites

import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.ZoneId

@Serializable
enum class BortleSource { MAP, USER }

@Serializable
data class Site(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** IANA zone id, e.g. "America/Toronto". */
    val zoneId: String,
    /** Bortle class 1..9, or null if unknown. */
    val bortle: Int? = null,
    val bortleSource: BortleSource = BortleSource.USER,
    /**
     * Per-site digest time ("HH:mm"). When null the primary site uses the global digest time
     * and other sites send no digest of their own.
     */
    val digestTimeOverride: String? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
        require(bortle == null || bortle in 1..9) { "bortle must be 1..9" }
    }

    val zone: ZoneId get() = ZoneId.of(zoneId)
    val digestTime: LocalTime? get() = digestTimeOverride?.let(LocalTime::parse)

    /** Bortle used for scoring when the class is unknown: a typical rural/suburban transition sky. */
    val effectiveBortle: Int get() = bortle ?: DEFAULT_BORTLE

    companion object {
        const val DEFAULT_BORTLE = 5
    }
}
