package app.nightbrief.score

import app.nightbrief.sites.Site
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSample
import java.time.Instant
import kotlin.math.abs

/**
 * One nowcast alert for one site. Display only: not a [NightScoreEngine] input, so the score,
 * its weights, and the three fixtures are unchanged.
 *
 * [binEpochSecond] is the start of the 3-hour Kp bin the alert came from. It is the dedupe key,
 * so a site is told once per bin even as the running Kp is revised inside it.
 */
data class AuroraAlert(
    val siteId: String,
    val siteName: String,
    val kp: Double,
    val chance: AuroraChance,
    val binEpochSecond: Long,
    /** Geographic latitude, so the copy can point at the poleward horizon. */
    val latitudeDeg: Double,
)

/**
 * Decides which sites get an aurora nowcast push right now.
 *
 * The day-ahead path is [Aurora.forNight], which grades the whole dark window from the forecast
 * product. This one reads the observed product so a storm that arrives after the morning digest
 * can still wake someone. It stays quiet below [threshold], where the site's [Aurora.chance] is
 * [AuroraChance.UNLIKELY], and where the bin was already announced.
 */
object AuroraAlerts {
    /** Kp 3 is the lowest threshold the settings slider offers: unsettled, not yet a storm. */
    const val MIN_THRESHOLD = 3

    /** Kp 9 is G5, the top of the NOAA scale. */
    const val MAX_THRESHOLD = 9

    /** Kp 5 is G1. Mid-latitude sites need a storm, high-latitude sites often do not. */
    const val DEFAULT_THRESHOLD = 5

    fun clamp(threshold: Int): Int = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    /**
     * The observed 3-hour bin that contains [now], or null when the series is empty or its newest
     * row is older than one bin. A stale series must not name the sky it no longer describes.
     */
    fun currentBin(forecast: KpForecast, now: Instant): KpSample? =
        forecast.samples
            .filter { it.epochSecond <= now.epochSecond }
            .maxByOrNull { it.epochSecond }
            ?.takeIf { now.epochSecond - it.epochSecond < KpForecast.BIN_SECONDS }

    /**
     * Sites worth a push now, in the order they were given. [alreadyAlerted] is site id to the
     * bin already announced; a newer bin is a new alert.
     */
    fun select(
        sites: List<Site>,
        forecast: KpForecast,
        threshold: Int,
        alreadyAlerted: Map<String, Long>,
        now: Instant,
    ): List<AuroraAlert> {
        val bin = currentBin(forecast, now) ?: return emptyList()
        if (bin.kp + 1e-9 < clamp(threshold)) return emptyList()
        return sites.mapNotNull { site ->
            if (alreadyAlerted[site.id] == bin.epochSecond) return@mapNotNull null
            val absLat = abs(site.latitude)
            val chance = Aurora.chance(absLat, bin.kp, absLat >= Aurora.HIGH_LATITUDE_DEG)
            if (chance == AuroraChance.UNLIKELY) return@mapNotNull null
            AuroraAlert(
                siteId = site.id,
                siteName = site.name,
                kp = bin.kp,
                chance = chance,
                binEpochSecond = bin.epochSecond,
                latitudeDeg = site.latitude,
            )
        }
    }
}

