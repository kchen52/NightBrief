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
 * Threshold is [Band.EXCELLENT] (85). One alert per site id and local night date.
 */
object BigNightAlerts {
    val threshold: Int get() = Band.EXCELLENT.min

    fun select(
        candidates: List<BigNightCandidate>,
        alreadyAlerted: Map<String, String>,
        enabled: Boolean,
    ): List<BigNightAlert> {
        if (!enabled) return emptyList()
        return candidates.mapNotNull { candidate ->
            val score = candidate.score ?: return@mapNotNull null
            if (score < threshold) return@mapNotNull null
            val key = candidate.date.toString()
            if (alreadyAlerted[candidate.siteId] == key) return@mapNotNull null
            BigNightAlert(candidate.siteId, candidate.siteName, score, candidate.date)
        }
    }
}
