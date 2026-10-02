package app.nightbrief.data

import android.content.Context
import android.util.Log
import app.nightbrief.score.BriefingService
import app.nightbrief.score.BriefingSource
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.CompositeBortleLookup
import app.nightbrief.sites.StreamingGridBortleLookup
import app.nightbrief.weather.CachingKpSource
import app.nightbrief.weather.CachingTleSource
import app.nightbrief.weather.CelestrakClient
import app.nightbrief.weather.FileForecastCache
import app.nightbrief.weather.ForecastRepository
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.NetworkTimingListener
import app.nightbrief.weather.OpenMeteoClient
import app.nightbrief.weather.SevenTimerClient
import app.nightbrief.weather.SwpcKpClient
import app.nightbrief.weather.TimeZoneLookup
import app.nightbrief.weather.defaultHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /**
     * One client for every weather API. Sharing the dispatcher and connection pool avoids
     * opening a pool per host per client and lets consecutive briefings reuse connections.
     * The timing listener logs one line per HTTP round trip (host, coarse location, duration);
     * filter logcat on [NET_LOG_TAG] to see which product dominates a slow refresh.
     */
    private val http = defaultHttpClient(timing = NetworkTimingListener { url, durationMs, code ->
        Log.d(NET_LOG_TAG, "${sanitizeUrl(url)} took=${durationMs}ms code=${code ?: "threw"}")
    })

    private val realForecasts: ForecastSource = ForecastRepository(
            openMeteo = OpenMeteoClient(http = http),
            sevenTimer = SevenTimerClient(http = http),
            cache = FileForecastCache(File(context.filesDir, "forecasts")),
        )

    var forecasts: ForecastSource = realForecasts
        private set

    val timeZoneLookup = TimeZoneLookup(http = http)

    val issTles = CachingTleSource(CelestrakClient(http = http), File(context.filesDir, "tle/iss.txt"))

    private val realBriefings: BriefingSource =
        BriefingService(realForecasts, kp = CachingKpSource(SwpcKpClient(http = http)), iss = issTles)

    var briefings: BriefingSource = realBriefings
        private set

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
        /** Logcat tag for per-request network timings. Capture with `adb logcat -s NightBriefNet`. */
        const val NET_LOG_TAG = "NightBriefNet"

        /**
         * One log-safe line per request: host, path, model, and coordinates rounded to 0.1°
         * (about 11 km, the same rounding the share image uses), so a shared log never
         * carries an exact pin.
         */
        internal fun sanitizeUrl(raw: String): String {
            val url = raw.toHttpUrlOrNull() ?: return "unparseable-url"
            val lat = url.queryParameter("latitude") ?: url.queryParameter("lat")
            val lon = url.queryParameter("longitude") ?: url.queryParameter("lon")
            val models = url.queryParameter("models")?.let { " models=$it" }.orEmpty()
            val where = if (lat != null && lon != null) {
                val rLat = runCatching { "%.1f".format(java.util.Locale.ROOT, lat.toDouble()) }.getOrDefault("?")
                val rLon = runCatching { "%.1f".format(java.util.Locale.ROOT, lon.toDouble()) }.getOrDefault("?")
                " ~$rLat,$rLon"
            } else {
                ""
            }
            return "${url.host}${url.encodedPath}$models$where"
        }

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

    /**
     * Points prefetch and digest at fakes without opening a second settings file.
     * [resetSourcesForTest] puts the real clients back.
     */
    fun replaceSourcesForTest(forecasts: ForecastSource, briefings: BriefingSource) {
        this.forecasts = forecasts
        this.briefings = briefings
    }

    fun resetSourcesForTest() {
        forecasts = realForecasts
        briefings = realBriefings
    }
}
