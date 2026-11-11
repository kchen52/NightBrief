package app.nightbrief.score

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Whether the night score reads high or low against the user's own star ratings, per site.
 *
 * The bias is the mean of `rating − ForecastSurprise.expectedStars(score)` over the sessions
 * that carry a score, in stars. Negative means the user rated nights worse than the score
 * implied, so the score reads optimistic; positive means it reads pessimistic. Fewer than
 * [MIN_NIGHTS] scored sessions, or a mean under a quarter star, says too little to print.
 * Display only — never a score input.
 */
object ScoreBias {
    /** Scored nights needed before a sentence is offered. */
    const val MIN_NIGHTS = 5

    /** Mean rating-minus-expected stars over the scored sessions, or null below [MIN_NIGHTS]. */
    fun meanBias(sessions: List<SessionEntry>): Double? {
        val deltas = deltas(sessions)
        if (deltas.size < MIN_NIGHTS) return null
        return deltas.average()
    }

    /** One sentence for the session history, or null when there is too little to say. */
    fun line(siteName: String, sessions: List<SessionEntry>): String? {
        val deltas = deltas(sessions)
        if (deltas.size < MIN_NIGHTS) return null
        val bias = deltas.average()
        val magnitude = abs(bias)
        if (magnitude < 0.25) return null
        val size = if (magnitude < 0.75) {
            "half a star"
        } else {
            when (val stars = magnitude.roundToInt().coerceAtLeast(1)) {
                1 -> "a star"
                2 -> "two stars"
                else -> "$stars stars"
            }
        }
        val direction = if (bias < 0) "optimistic" else "pessimistic"
        return "$siteName scores about $size $direction over your last ${deltas.size} scored nights."
    }

    private fun deltas(sessions: List<SessionEntry>): List<Int> =
        sessions.mapNotNull { s -> s.score?.let { s.rating - ForecastSurprise.expectedStars(it) } }
}
