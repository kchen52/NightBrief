package app.nightbrief.score

/**
 * One star in a stick figure. Coordinates are normalized, origin at the top-left, both axes 0..1.
 */
data class StarPoint(val x: Float, val y: Float)

/**
 * A figure the session log can light. [lines] connect star indexes. [stars] are lit from the front of the list.
 */
data class ConstellationLayout(
    val id: String,
    val name: String,
    val stars: List<StarPoint>,
    val lines: List<Pair<Int, Int>>,
) {
    init {
        require(stars.size >= 3) { "constellation needs at least 3 stars" }
        require(stars.all { it.x in 0f..1f && it.y in 0f..1f }) { "star outside 0..1" }
        require(lines.all { (a, b) -> a in stars.indices && b in stars.indices && a != b }) { "bad star line" }
    }
}

/**
 * One figure in the current log. [unlockHint] is set while a previous figure still has a dark star.
 * Missed nights do not clear [lit].
 */
data class ConstellationState(
    val layout: ConstellationLayout,
    val lit: Int,
    val unlocked: Boolean,
    val unlockHint: String?,
) {
    val name: String get() = layout.name
    val total: Int get() = layout.stars.size
}

/**
 * How far the log has walked the figure sequence. [current] is the figure the next session will light,
 * or the last figure once every star is lit.
 */
data class ConstellationProgress(
    val figures: List<ConstellationState>,
) {
    val current: ConstellationState
        get() = figures.firstOrNull { it.unlocked && it.lit < it.total } ?: figures.last()

    val summary: String get() = "${current.name} · ${current.lit} of ${current.total}"
}

/**
 * Each logged session lights one star. Finishing a figure unlocks the next.
 * There is no reset when nights are skipped. Sessions past the last star leave every figure full.
 *
 * Order: Orion, Cassiopeia, Ursa Major, Cygnus, Scorpius.
 */
object ConstellationStreaks {
    val catalog: List<ConstellationLayout> = listOf(
        ConstellationLayout(
            id = "orion",
            name = "Orion",
            stars = listOf(
                StarPoint(0.72f, 0.18f),
                StarPoint(0.28f, 0.22f),
                StarPoint(0.36f, 0.46f),
                StarPoint(0.50f, 0.48f),
                StarPoint(0.64f, 0.50f),
                StarPoint(0.34f, 0.82f),
                StarPoint(0.70f, 0.84f),
            ),
            lines = listOf(0 to 1, 0 to 4, 1 to 2, 2 to 3, 3 to 4, 2 to 5, 4 to 6),
        ),
        ConstellationLayout(
            id = "cassiopeia",
            name = "Cassiopeia",
            stars = listOf(
                StarPoint(0.12f, 0.38f),
                StarPoint(0.32f, 0.70f),
                StarPoint(0.50f, 0.32f),
                StarPoint(0.70f, 0.66f),
                StarPoint(0.90f, 0.28f),
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        ConstellationLayout(
            id = "ursa-major",
            name = "Ursa Major",
            stars = listOf(
                StarPoint(0.18f, 0.32f),
                StarPoint(0.22f, 0.58f),
                StarPoint(0.42f, 0.62f),
                StarPoint(0.40f, 0.36f),
                StarPoint(0.58f, 0.40f),
                StarPoint(0.74f, 0.34f),
                StarPoint(0.92f, 0.22f),
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 3 to 4, 4 to 5, 5 to 6),
        ),
        ConstellationLayout(
            id = "cygnus",
            name = "Cygnus",
            stars = listOf(
                StarPoint(0.50f, 0.10f),
                StarPoint(0.50f, 0.42f),
                StarPoint(0.48f, 0.86f),
                StarPoint(0.82f, 0.36f),
                StarPoint(0.22f, 0.50f),
            ),
            lines = listOf(0 to 1, 1 to 2, 1 to 3, 1 to 4),
        ),
        ConstellationLayout(
            id = "scorpius",
            name = "Scorpius",
            stars = listOf(
                StarPoint(0.18f, 0.18f),
                StarPoint(0.32f, 0.22f),
                StarPoint(0.42f, 0.38f),
                StarPoint(0.55f, 0.55f),
                StarPoint(0.68f, 0.70f),
                StarPoint(0.62f, 0.86f),
                StarPoint(0.48f, 0.90f),
            ),
            lines = listOf(0 to 2, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6),
        ),
    )

    fun progress(sessionCount: Int): ConstellationProgress {
        var remaining = sessionCount.coerceAtLeast(0)
        var previousComplete = true
        var previousName: String? = null
        val figures = catalog.map { layout ->
            val unlocked = previousComplete
            val lit = if (unlocked) minOf(remaining, layout.stars.size) else 0
            if (unlocked) remaining -= lit
            val complete = unlocked && lit == layout.stars.size
            val hint = if (unlocked) null else "Finish $previousName to light ${layout.name}"
            previousComplete = complete
            previousName = layout.name
            ConstellationState(layout, lit, unlocked, hint)
        }
        return ConstellationProgress(figures)
    }
}
