package app.nightbrief.data

import android.content.Context
import app.nightbrief.score.BriefingService
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.CompositeBortleLookup
import app.nightbrief.sites.StreamingGridBortleLookup
import app.nightbrief.weather.CachingTleSource
import app.nightbrief.weather.CelestrakClient
import app.nightbrief.weather.FileForecastCache
import app.nightbrief.weather.ForecastRepository
import app.nightbrief.weather.SwpcKpClient
import app.nightbrief.weather.TimeZoneLookup
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

    val timeZoneLookup = TimeZoneLookup()

    val issTles = CachingTleSource(CelestrakClient(), File(context.filesDir, "tle/iss.txt"))

    val briefings = BriefingService(forecasts, kp = SwpcKpClient(), iss = issTles)

    /**
     * North America grid, then the world fallback. Null only when neither asset is packaged.
     *
     * Lookup does blocking I/O (up to about a second) and must be called off the main thread.
     * Each call opens the gzip asset, reads the header, and skips to one byte.
     * The files are named `.gzip` so the asset merger does not unpack them (it gunzips `*.gz`).
     * `AssetManager.open` returns those bytes; it undoes APK deflate only.
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
        /** North America grid, gzip bytes. The `.gzip` suffix keeps the asset merger from unpacking it. */
        const val BORTLE_NA_ASSET = "bortle_na.nblp.gzip"
        /** World grid, gzip bytes. The `.gzip` suffix keeps the asset merger from unpacking it. */
        const val BORTLE_WORLD_ASSET = "bortle_world.nblp.gzip"

        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
