package app.nightbrief.score

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One site scored for tonight, before the Big Night threshold is applied. */
data class BigNightCandidate(
    val siteId: String,
    val siteName: String,
    val date: LocalDate,
    val score: Int?,
)

/** A notification to post, and the dedupe key to store for that site. */
data class BigNightAlert(
    val siteId: String,
    val siteName: String,
    val score: Int,
    val date: LocalDate,
) {
    val nightKey: String get() = date.toString()

    /** Short weekday and day, e.g. "Wed Aug 12". [nightKey] stays the ISO date used for dedupe. */
    fun dateLabel(locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))
}

/**
 * Which sites should get a Big Night notification.
 * The default threshold is [Band.EXCELLENT] (85). Settings can lower it to [MIN_THRESHOLD]
 * so a bright home site still hears about a genuinely good night. One alert per site id and local night date.
 */
object BigNightAlerts {
    const val MIN_THRESHOLD = 70
    const val MAX_THRESHOLD = 95

    /** Default bar: Excellent. */
    val threshold: Int get() = Band.EXCELLENT.min

    fun clamp(value: Int): Int = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    fun select(
        candidates: List<BigNightCandidate>,
        alreadyAlerted: Map<String, String>,
        enabled: Boolean,
        threshold: Int = this.threshold,
    ): List<BigNightAlert> {
        if (!enabled) return emptyList()
        val bar = clamp(threshold)
        return candidates.mapNotNull { candidate ->
            val score = candidate.score ?: return@mapNotNull null
            if (score < bar) return@mapNotNull null
            val key = candidate.date.toString()
            if (alreadyAlerted[candidate.siteId] == key) return@mapNotNull null
            BigNightAlert(candidate.siteId, candidate.siteName, score, candidate.date)
        }
    }
}

/** A "tonight collapsed" notification to post, and the dedupe key to store for that site. */
data class CollapseAlert(
    val siteId: String,
    val siteName: String,
    val score: Int,
    val date: LocalDate,
) {
    val nightKey: String get() = date.toString()

    /** Short weekday and day, e.g. "Wed Aug 12". [nightKey] stays the ISO date used for dedupe. */
    fun dateLabel(locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))
}

/**
 * The downward counterpart of [BigNightAlerts.select].
 *
 * A site that already earned a Big Night alert for tonight ([bigNightAlerts] holds tonight's key
 * for it) whose refreshed score fell more than [MARGIN] below the threshold gets one
 * "tonight collapsed" notification, so only a user who would have driven out hears the cancel.
 * One alert per site id and local night date. The score itself is unchanged.
 */
object NightCollapse {
    /** How far below the Big Night bar a refreshed score must fall before the night is called off. */
    const val MARGIN = 10

    fun select(
        candidates: List<BigNightCandidate>,
        bigNightAlerts: Map<String, String>,
        alreadyAlerted: Map<String, String>,
        enabled: Boolean,
        threshold: Int = BigNightAlerts.threshold,
    ): List<CollapseAlert> {
        if (!enabled) return emptyList()
        val bar = BigNightAlerts.clamp(threshold)
        return candidates.mapNotNull { candidate ->
            val key = candidate.date.toString()
            // Only a site that earned a Big Night alert for this night can collapse.
            if (bigNightAlerts[candidate.siteId] != key) return@mapNotNull null
            if (alreadyAlerted[candidate.siteId] == key) return@mapNotNull null
            val score = candidate.score ?: return@mapNotNull null
            if (score >= bar - MARGIN) return@mapNotNull null
            CollapseAlert(candidate.siteId, candidate.siteName, score, candidate.date)
        }
    }
}
