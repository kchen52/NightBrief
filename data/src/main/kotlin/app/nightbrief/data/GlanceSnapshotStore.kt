package app.nightbrief.data

import app.nightbrief.astro.TimeWindow
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Shared glance snapshot of the primary site's tonight briefing so the home-screen widget
 * and Wear publisher render directly from what the app or morning digest already scored.
 */
@Serializable
data class PrimaryGlanceSnapshot(
    val siteId: String = "",
    val siteName: String = "",
    val siteFingerprint: String = "",
    val nightDate: String = "",
    val generatedAtEpochSec: Long = 0L,
    val scoreValue: Int? = null,
    val flavorLine: String? = null,
    val milkyWayStartEpochSec: Long? = null,
    val milkyWayEndEpochSec: Long? = null,
    val milkyWayClearsHorizonAtEpochSec: Long? = null,
    val milkyWayBlockedByHorizon: Boolean = false,
    val forecastStatus: String? = null,
    val forecastFetchedAtEpochSec: Long? = null,
) {
    val generatedAt: Instant get() = Instant.ofEpochSecond(generatedAtEpochSec)

    val milkyWayWindow: TimeWindow?
        get() {
            val start = milkyWayStartEpochSec ?: return null
            val end = milkyWayEndEpochSec ?: return null
            return TimeWindow(Instant.ofEpochSecond(start), Instant.ofEpochSecond(end))
        }

    val milkyWayClearsHorizonAt: Instant?
        get() = milkyWayClearsHorizonAtEpochSec?.let(Instant::ofEpochSecond)

    val parsedForecastStatus: ForecastStatus?
        get() = forecastStatus?.let { name -> ForecastStatus.entries.firstOrNull { it.name == name } }

    val forecastFetchedAt: Instant?
        get() = forecastFetchedAtEpochSec?.let(Instant::ofEpochSecond)

    companion object {
        /**
         * Identifies the exact site configuration that produced a snapshot so renaming,
         * moving, changing Bortle/horizon, or switching primary invalidates it.
         */
        fun fingerprint(site: Site): String = buildString {
            append(site.id).append('|')
            append(site.name).append('|')
            append(String.format(Locale.ROOT, "%.5f,%.5f", site.latitude, site.longitude)).append('|')
            append(site.zoneId).append('|')
            append(site.bortle ?: "").append('|')
            append(site.horizon)
        }

        fun from(site: Site, report: NightReport, generatedAt: Instant): PrimaryGlanceSnapshot {
            val mw = report.ephemeris.milkyWay
            return PrimaryGlanceSnapshot(
                siteId = site.id,
                siteName = site.name,
                siteFingerprint = fingerprint(site),
                nightDate = report.date.toString(),
                generatedAtEpochSec = generatedAt.epochSecond,
                scoreValue = report.scoreValue,
                flavorLine = report.flavorLine,
                milkyWayStartEpochSec = mw?.window?.start?.epochSecond,
                milkyWayEndEpochSec = mw?.window?.end?.epochSecond,
                milkyWayClearsHorizonAtEpochSec = mw?.clearsHorizonAt?.epochSecond,
                milkyWayBlockedByHorizon = report.ephemeris.milkyWayBlockedByHorizon,
                forecastStatus = report.forecastStatus?.name,
                forecastFetchedAtEpochSec = report.forecastFetchedAt?.epochSecond,
            )
        }
    }
}

/**
 * In-memory and disk-backed store for [PrimaryGlanceSnapshot].
 * Disk failures are non-fatal: the in-memory copy remains available for the process lifetime.
 */
class GlanceSnapshotStore(
    private val file: File,
    private val clock: Clock = Clock.systemUTC(),
    private val maxAge: Duration = Duration.ofHours(6),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val ioMutex = Mutex()
    @Volatile private var memory: PrimaryGlanceSnapshot? = null
    @Volatile private var loadedFromDisk = false

    suspend fun save(site: Site, report: NightReport) {
        val snapshot = PrimaryGlanceSnapshot.from(site, report, clock.instant())
        memory = snapshot
        loadedFromDisk = true
        val encoded = json.encodeToString(PrimaryGlanceSnapshot.serializer(), snapshot)
        ioMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val parent = file.parentFile
                    if (parent != null && !parent.isDirectory) parent.mkdirs()
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(encoded)
                    if (!tmp.renameTo(file)) {
                        file.writeText(encoded)
                        tmp.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    // In-memory snapshot is already updated; a disk error must not fail the caller.
                }
            }
        }
    }

    suspend fun currentFor(site: Site, tonight: LocalDate): PrimaryGlanceSnapshot? {
        val snapshot = current() ?: return null
        if (snapshot.siteFingerprint != PrimaryGlanceSnapshot.fingerprint(site)) return null
        if (snapshot.nightDate != tonight.toString()) return null
        val age = Duration.between(snapshot.generatedAt, clock.instant())
        if (age.isNegative || age > maxAge) return null
        return snapshot
    }

    suspend fun clear() {
        memory = null
        loadedFromDisk = true
        ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { file.delete() }
            }
        }
    }

    /** Synchronous reset for unit tests swapping [AppGraph] sources. */
    internal fun clearSync() {
        memory = null
        loadedFromDisk = true
        runCatching { file.delete() }
    }

    private suspend fun current(): PrimaryGlanceSnapshot? {
        memory?.let { return it }
        if (loadedFromDisk) return null
        return ioMutex.withLock {
            memory?.let { return@withLock it }
            if (loadedFromDisk) return@withLock null
            val decoded = withContext(Dispatchers.IO) {
                if (!file.isFile) return@withContext null
                runCatching {
                    json.decodeFromString(PrimaryGlanceSnapshot.serializer(), file.readText())
                }.getOrNull()
            }
            memory = decoded
            loadedFromDisk = true
            decoded
        }
    }
}
