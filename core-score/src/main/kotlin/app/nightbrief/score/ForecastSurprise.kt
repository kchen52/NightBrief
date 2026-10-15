package app.nightbrief.score

import kotlin.math.roundToInt

/**
 * Compares a session rating with the stars the night score implied.
 *
 * Expected stars are `round(1 + score / 25)`, clamped to 1..5.
 * A 58 expects 3, so 4 stars beat it. A 100 expects 5, so a rating cannot beat it.
 * A null score was not at hand and cannot beat the forecast.
 */
object ForecastSurprise {
    fun expectedStars(score: Int): Int {
        val clamped = score.coerceIn(0, 100)
        return (1.0 + clamped / 25.0).roundToInt().coerceIn(1, 5)
    }

    fun beat(rating: Int, score: Int?): Boolean {
        val points = score ?: return false
        return rating >= expectedStars(points) + 1
    }

    /** Sentence for a session that beat its score, or null when it did not. */
    fun line(siteName: String, entry: SessionEntry): String? {
        val score = entry.score ?: return null
        if (!beat(entry.rating, score)) return null
        return "The sky surprised you at $siteName: ${entry.rating}★ on a $score."
    }
}
