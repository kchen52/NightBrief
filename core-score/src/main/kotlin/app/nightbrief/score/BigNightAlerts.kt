package app.nightbrief.score

import java.time.LocalDate
import java.time.format.TextStyle
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

    fun dateLabel(locale: Locale = Locale.getDefault()): String {
        val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return "$day $nightKey"
    }
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
