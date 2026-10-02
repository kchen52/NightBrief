package app.nightbrief.app

import app.nightbrief.sites.BortleSource
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
) {
    val lat: Double? get() = latitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon: Double? get() = longitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val zoneValid: Boolean get() = runCatching { ZoneId.of(zoneId.trim()) }.isSuccess

    val errors: List<Int>
        get() = buildList {
            if (name.isBlank()) add(R.string.site_error_name)
            if (lat == null) add(R.string.site_error_latitude)
            if (lon == null) add(R.string.site_error_longitude)
            if (!zoneValid) add(R.string.site_error_zone)
            digestTimeOverride?.let { if (runCatching { LocalTime.parse(it) }.isFailure) add(R.string.site_error_digest_time) }
        }

    val isValid: Boolean get() = errors.isEmpty()

    /** True when [lat] and [lon] are not the coordinates loaded from a saved site. A new site always differs. */
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
    )

    companion object {
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
            )
        }
    }
}
