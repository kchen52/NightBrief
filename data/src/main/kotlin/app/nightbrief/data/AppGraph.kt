package app.nightbrief.data

import android.content.Context
import app.nightbrief.score.BriefingService
import app.nightbrief.sites.BortleLookup
import app.nightbrief.sites.GridBortleLookup
import app.nightbrief.weather.FileForecastCache
import app.nightbrief.weather.ForecastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Process-wide dependencies, shared by the UI and background workers. */
class AppGraph private constructor(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository =
        SettingsRepository.create(File(context.filesDir, "datastore/app_state.json"), appScope)

    val forecasts = ForecastRepository(cache = FileForecastCache(File(context.filesDir, "forecasts")))

    val briefings = BriefingService(forecasts)

    /** Null when the app ships without a light-pollution grid; users then pick the Bortle class themselves. */
    val bortleLookup: BortleLookup? = runCatching {
        context.assets.open(BORTLE_ASSET).use(GridBortleLookup::read)
    }.getOrNull()

    companion object {
        const val BORTLE_ASSET = "bortle.nblp"

        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
