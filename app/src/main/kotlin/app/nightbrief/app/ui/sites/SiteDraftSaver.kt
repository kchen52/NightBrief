package app.nightbrief.app.ui.sites

import androidx.compose.runtime.saveable.Saver
import app.nightbrief.app.SiteDraft
import app.nightbrief.sites.BortleSource

/** Saves a [SiteDraft] across process death / rotation as a flat list of primitives. */
val SiteDraftSaver: Saver<SiteDraft, Any> = Saver(
    save = {
        arrayListOf(
            it.id, it.name, it.latitude, it.longitude, it.bortle, it.bortleSource.name,
            it.zoneId, it.zoneEdited, it.savedLatitude, it.savedLongitude, it.savedZoneId,
            it.digestTimeOverride, it.makePrimary,
        )
    },
    restore = { saved ->
        val v = saved as List<*>
        SiteDraft(
            id = v[0] as String?,
            name = v[1] as String,
            latitude = v[2] as String,
            longitude = v[3] as String,
            bortle = v[4] as Int?,
            bortleSource = BortleSource.valueOf(v[5] as String),
            zoneId = v[6] as String,
            zoneEdited = v[7] as Boolean,
            savedLatitude = v[8] as String?,
            savedLongitude = v[9] as String?,
            savedZoneId = v[10] as String?,
            digestTimeOverride = v[11] as String?,
            makePrimary = v[12] as Boolean,
        )
    },
)
