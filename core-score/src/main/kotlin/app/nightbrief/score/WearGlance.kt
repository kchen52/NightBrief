package app.nightbrief.score

/**
 * Snapshot the watch tile and the watch app show for the phone's primary site.
 * Not a [NightScoreEngine] input. The phone publishes it; the watch only displays it.
 */
data class WearGlance(
    /** False until onboarding has a named primary site. */
    val ready: Boolean,
    val siteName: String,
    val scoreText: String,
    val verdictText: String,
    /** Set when the score comes from a saved forecast, e.g. "Saved Tue 18:40". */
    val savedText: String? = null,
) {
    /** Lines in display order. Setup is a single line so the tile does not invent a score. */
    fun lines(): List<String> = if (!ready) {
        listOf(verdictText)
    } else {
        listOfNotNull(siteName, scoreText, verdictText, savedText)
    }

    companion object {
        const val PATH = "/nightbrief/glance"
        const val KEY_READY = "ready"
        const val KEY_SITE = "site"
        const val KEY_SCORE = "score"
        const val KEY_VERDICT = "verdict"
        const val KEY_UPDATED = "updated"
        const val KEY_SAVED = "saved"

        const val SETUP = "Set up NightBrief"
        const val NO_SCORE = "No score"

        fun from(
            onboardingComplete: Boolean,
            siteName: String?,
            score: Int?,
            savedText: String? = null,
        ): WearGlance {
            if (!onboardingComplete || siteName.isNullOrBlank()) {
                return WearGlance(ready = false, siteName = "", scoreText = "—", verdictText = SETUP)
            }
            if (score == null) {
                return WearGlance(
                    ready = true,
                    siteName = siteName,
                    scoreText = "—",
                    verdictText = NO_SCORE,
                    savedText = savedText,
                )
            }
            return WearGlance(
                ready = true,
                siteName = siteName,
                scoreText = score.toString(),
                verdictText = Verdict.of(score).label,
                savedText = savedText,
            )
        }
    }
}
