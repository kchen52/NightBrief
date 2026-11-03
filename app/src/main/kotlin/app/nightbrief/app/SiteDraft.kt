package app.nightbrief.app

import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.LocalHorizon
import app.nightbrief.sites.Site
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

private fun String?.toCoordOrNull(range: ClosedRange<Double>): Double? =
    this?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in range }

/**
 * Editable form state for a site. Coordinates are kept as text so partially typed values are allowed.
 *
 * [zoneEdited] is set when the zone field is changed by hand in this session.
 * [savedLatitude], [savedLongitude], and [savedZoneId] are copied from an existing site and stay null for a new one.
 */
@Serializable
data class SiteDraft(
    val id: String? = null,
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val bortle: Int? = null,
    val bortleSource: BortleSource = BortleSource.USER,
    val zoneId: String = ZoneId.systemDefault().id,
    val zoneEdited: Boolean = false,
    val savedLatitude: String? = null,
    val savedLongitude: String? = null,
    val savedZoneId: String? = null,
    val digestTimeOverride: String? = null,
    val makePrimary: Boolean = false,
    val horizon: LocalHorizon = LocalHorizon(),
    /**
     * True once the name field is typed in this session. An untouched default name can be
     * replaced by a place picked on the map.
     */
    val nameEdited: Boolean = false,
) {
    val lat: Double? get() = latitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon: Double? get() = longitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val zoneValid: Boolean get() = runCatching { ZoneId.of(zoneId.trim()) }.isSuccess

    val errors: List<Int>
        get() = buildList {
            if (name.isBlank()) add(R.string.site_error_name)
            when {
                latitude.isBlank() -> add(R.string.site_error_location)
                lat == null -> add(R.string.site_error_latitude)
            }
            when {
                longitude.isBlank() -> add(R.string.site_error_location)
                lon == null -> add(R.string.site_error_longitude)
            }
            if (!zoneValid) add(R.string.site_error_zone)
            digestTimeOverride?.let { if (runCatching { LocalTime.parse(it) }.isFailure) add(R.string.site_error_digest_time) }
        }

    val isValid: Boolean get() = errors.isEmpty()

    /** True when [lat] and [lon] are not the coordinates loaded from a saved site. A new site always differs. */
    /**
     * Name to keep after a place pick. An untouched "Home" or a blank name takes the place name.
     * A name the user typed stays.
     */
    fun nameAfterPlacePick(placeName: String?): String {
        val incoming = placeName?.trim().orEmpty()
        if (incoming.isEmpty() || nameEdited) return name
        if (name.isBlank() || name == UNTOUCHED_NAME) return incoming
        return name
    }

    fun coordinatesDifferFromSaved(lat: Double, lon: Double): Boolean {
        val savedLat = savedLatitude.toCoordOrNull(-90.0..90.0)
        val savedLon = savedLongitude.toCoordOrNull(-180.0..180.0)
        if (savedLat == null || savedLon == null) return true
        return lat != savedLat || lon != savedLon
    }

    fun toSite(): Site = Site(
        id = id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        latitude = lat!!,
        longitude = lon!!,
        zoneId = ZoneId.of(zoneId.trim()).id,
        bortle = bortle,
        bortleSource = bortleSource,
        digestTimeOverride = digestTimeOverride,
        horizon = horizon,
    )

    companion object {
        /** Default onboarding name. A place pick replaces it until the user types a name. */
        const val UNTOUCHED_NAME = "Home"

        fun from(site: Site, isPrimary: Boolean): SiteDraft {
            val latitude = "%.5f".format(java.util.Locale.ROOT, site.latitude)
            val longitude = "%.5f".format(java.util.Locale.ROOT, site.longitude)
            return SiteDraft(
                id = site.id,
                name = site.name,
                latitude = latitude,
                longitude = longitude,
                bortle = site.bortle,
                bortleSource = site.bortleSource,
                zoneId = site.zoneId,
                savedLatitude = latitude,
                savedLongitude = longitude,
                savedZoneId = site.zoneId,
                digestTimeOverride = site.digestTimeOverride,
                makePrimary = isPrimary,
                horizon = site.horizon,
                nameEdited = true,
            )
        }
    }
}
