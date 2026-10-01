package app.nightbrief.app

import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.Site
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Editable form state for a site. Coordinates are kept as text so partially typed values are allowed. */
data class SiteDraft(
    val id: String? = null,
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val bortle: Int? = null,
    val bortleSource: BortleSource = BortleSource.USER,
    val zoneId: String = ZoneId.systemDefault().id,
    val digestTimeOverride: String? = null,
    val makePrimary: Boolean = false,
) {
    val lat: Double? get() = latitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lon: Double? get() = longitude.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val zoneValid: Boolean get() = runCatching { ZoneId.of(zoneId.trim()) }.isSuccess

    val errors: List<String>
        get() = buildList {
            if (name.isBlank()) add("Give the site a name")
            if (lat == null) add("Latitude must be between -90 and 90")
            if (lon == null) add("Longitude must be between -180 and 180")
            if (!zoneValid) add("Unknown time zone")
            digestTimeOverride?.let { if (runCatching { LocalTime.parse(it) }.isFailure) add("Invalid digest time") }
        }

    val isValid: Boolean get() = errors.isEmpty()

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
        fun from(site: Site, isPrimary: Boolean) = SiteDraft(
            id = site.id,
            name = site.name,
            latitude = "%.5f".format(java.util.Locale.ROOT, site.latitude),
            longitude = "%.5f".format(java.util.Locale.ROOT, site.longitude),
            bortle = site.bortle,
            bortleSource = site.bortleSource,
            zoneId = site.zoneId,
            digestTimeOverride = site.digestTimeOverride,
            makePrimary = isPrimary,
        )
    }
}
