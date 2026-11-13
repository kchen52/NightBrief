package app.nightbrief.score

import kotlin.math.roundToInt

/**
 * A second reading of the same night for a different kind of imaging.
 *
 * [DEEP_SKY] repeats the primary weights from [NightScoreEngine]; it is the published score,
 * so the widget, digest, and Wear verdict stay unchanged. [PLANETARY] moves points from the
 * Moon and transparency to seeing and wind, which are what a lunar or planetary night needs.
 * The gates are the same ones the primary engine applies.
 */
enum class ImagingMode(val label: String, internal val weights: Map<Factor, Int>) {
    PLANETARY(
        "planets",
        mapOf(
            Factor.CLOUD to 25,
            Factor.MOON to 5,
            Factor.TRANSPARENCY to 10,
            Factor.WIND to 20,
            Factor.SEEING to 35,
            Factor.LIGHT_POLLUTION to 5,
        ),
    ),
    DEEP_SKY(
        "deep sky",
        mapOf(
            Factor.CLOUD to 35,
            Factor.MOON to 25,
            Factor.TRANSPARENCY to 15,
            Factor.WIND to 10,
            Factor.SEEING to 10,
            Factor.LIGHT_POLLUTION to 5,
        ),
    );

    init {
        require(weights.keys == Factor.entries.toSet()) { "$name must weight every factor" }
        require(weights.values.sum() == 100) { "$name weights must sum to 100" }
    }
}

/** The alternate score for one [ImagingMode] on a night. */
data class ImagingScore(val mode: ImagingMode, val score: Int) {
    val band: Band get() = Band.of(score)
    val verdict: Verdict get() = Verdict.of(score)
}

/**
 * Alternate scores computed from the per-hour qualities [NightScore] already holds.
 *
 * A factor's gate is recovered from its published points (`points = weight × quality × gate`),
 * so the alternate table changes only the weights, never the cloud or moon/twilight gates. An
 * alternate is worth showing only when it crosses a band; a one-point change inside the same
 * band is noise. [DEEP_SKY] is reported as the primary score itself, so it never disagrees.
 */
object ImagingModes {

    fun score(night: NightScore, mode: ImagingMode): Int {
        if (mode == ImagingMode.DEEP_SKY) return night.score
        val hours = night.hours
        if (hours.isEmpty()) return 0
        val windowSize = minOf(NightScoreEngine.BEST_WINDOW_HOURS, hours.size)

        fun hourPoints(hour: HourScore): Double = mode.weights.entries.sumOf { (factor, weight) ->
            val fs = hour.factor(factor)
            if (fs.quality <= 0.0) 0.0 else {
                val gate = (fs.points / (fs.factor.weight * fs.quality)).coerceIn(0.0, 1.0)
                weight * fs.quality * gate
            }
        }

        val all = hours.sumOf(::hourPoints) / hours.size
        val best = (0..hours.size - windowSize).maxOf { start ->
            hours.subList(start, start + windowSize).sumOf(::hourPoints)
        } / windowSize
        return (0.5 * all + 0.5 * best).roundToInt().coerceIn(0, 100)
    }

    fun scores(night: NightScore): List<ImagingScore> =
        ImagingMode.entries.map { ImagingScore(it, score(night, it)) }

    /** Alternates in a different band from the primary score, planetary first. */
    fun disagreeing(night: NightScore): List<ImagingScore> =
        scores(night).filter { it.band != night.band }
}
