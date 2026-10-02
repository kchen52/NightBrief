package app.nightbrief.score

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * One logged shooting session. Local only, no backend.
 *
 * [nightDate] is the ISO local night date (yyyy-MM-dd) in the site's zone, matching
 * [NightReport.date]. [score] is that night's score when it was still available, null when
 * the user logs without a scored report (e.g. an older night). Factors stay on the report;
 * the entry keeps the score beside the user's rating so later tuning can compare them.
 */
@Serializable
data class SessionEntry(
    val siteId: String,
    val nightDate: String,
    /** That night's score, when a scored report was available. Null means unknown. */
    val score: Int? = null,
    /** User rating 1..5. */
    val rating: Int,
    val note: String = "",
    /** Epoch seconds; see [createdAt]. */
    val createdAtEpochSec: Long = 0,
) {
    init {
        require(siteId.isNotBlank()) { "siteId required" }
        require(rating in 1..5) { "rating 1..5" }
        require(score == null || score in 0..100) { "score 0..100" }
    }

    val date: LocalDate get() = LocalDate.parse(nightDate)
    val createdAt: Instant get() = Instant.ofEpochSecond(createdAtEpochSec)

    companion object {
        fun key(siteId: String, nightDate: LocalDate): String = "$siteId/${nightDate}"
    }
}

/**
 * Morning-after prompt. Ask once per site night, from Tonight in the 06:00–12:00 local window.
 * Score gating (Go/Maybe only) waits until tonight scores are persisted; the MVP prompts for any
 * night and stores the score when a report is at hand.
 */
object SessionPrompt {
    private val WINDOW_START = LocalTime.of(6, 0)
    private val WINDOW_END = LocalTime.of(12, 0)

    fun key(siteId: String, nightDate: LocalDate): String = SessionEntry.key(siteId, nightDate)

    fun loggedKeys(sessions: List<SessionEntry>): Set<String> =
        sessions.map { "${it.siteId}/${it.nightDate}" }.toSet()

    /**
     * Yesterday's night date in [zone] for a morning prompt, or null outside 06:00–12:00.
     * Takes the local morning time so tests can use a fixed clock.
     */
    fun morningPromptDate(nowLocal: LocalTime, today: LocalDate): LocalDate? =
        if (!nowLocal.isBefore(WINDOW_START) && nowLocal.isBefore(WINDOW_END)) today.minusDays(1) else null

    fun shouldAsk(
        siteId: String,
        nightDate: LocalDate,
        sessions: List<SessionEntry>,
        dismissed: Set<String>,
    ): Boolean {
        val key = key(siteId, nightDate)
        return key !in dismissed && key !in loggedKeys(sessions)
    }
}
