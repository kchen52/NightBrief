package app.nightbrief.data

import android.content.Context
import app.nightbrief.score.BriefingService
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.CompositeBortleLookup
import app.nightbrief.sites.StreamingGridBortleLookup
import app.nightbrief.weather.FileForecastCache
import app.nightbrief.weather.ForecastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Process-wide dependencies, shared by the UI and background workers. */
class AppGraph private constructor(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository =
        SettingsRepository.create(File(context.filesDir, "datastore/app_state.json"), appScope)

    val forecasts = ForecastRepository(cache = FileForecastCache(File(context.filesDir, "forecasts")))

    val briefings = BriefingService(forecasts)

    /**
     * North America grid, then the world fallback. Null only when neither asset is packaged.
     *
     * Lookup does blocking I/O (~up to a second) and must be called off the main thread.
     * Each call opens the gzip asset, reads the header, and skips to a single byte.
     * `AssetManager.open` returns the raw gzip bytes (it undoes APK deflate only);
     * the stream below gunzips them. The packaged names end in `.gzip` rather than
     * `.gz`: the asset merger unpacks `*.gz` and would undo this compression.
     */
    val bortleLookup: BortleLookup? = listOfNotNull(
        streamingBortle(context, BORTLE_NA_ASSET),
        streamingBortle(context, BORTLE_WORLD_ASSET),
    ).let { lookups -> if (lookups.isEmpty()) null else CompositeBortleLookup(lookups) }

    private fun streamingBortle(context: Context, asset: String): BortleLookup? {
        if (!assetExists(context, asset)) return null
        return StreamingGridBortleLookup {
            BufferedInputStream(GZIPInputStream(context.assets.open(asset)))
        }
    }

    private fun assetExists(context: Context, asset: String): Boolean =
        try {
            context.assets.open(asset).close()
            true
        } catch (_: IOException) {
            false
        }

    companion object {
        /** Gzip bytes. Not `*.gz`: AGP would unpack that name during the asset merge. */
        const val BORTLE_NA_ASSET = "bortle_na.nblp.gzip"
        /** Gzip bytes. Not `*.gz`: AGP would unpack that name during the asset merge. */
        const val BORTLE_WORLD_ASSET = "bortle_world.nblp.gzip"

        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
