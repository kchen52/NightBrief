package app.nightbrief.score

import kotlin.math.roundToInt

/** How closely the secondary model tracks the primary on cloud. Not a score input. */
enum class ModelConfidence { AGREE, MIXED, DISAGREE }

/**
 * Second-model cloud spread over the dark hours.
 *
 * Null (from [assess]) means there is nothing to compare: the second fetch failed
 * or fewer than [MIN_HOURS] dark hours carry both values.
 */
data class CloudAgreement(
    val confidence: ModelConfidence,
    /** Mean absolute primary-vs-secondary cloud gap in points. */
    val meanAbsDiff: Int,
    val hoursCompared: Int,
)

object ModelAgreement {
    const val MIN_HOURS = 3
    private const val AGREE_MAX_DIFF = 10
    private const val MIXED_MAX_DIFF = 25

    fun assess(report: NightReport): CloudAgreement? {
        val gaps = report.timeline
            .filter { it.isDark }
            .mapNotNull { h ->
                val primary = h.cloudCover
                val secondary = h.cloudSecondary
                if (primary != null && secondary != null) kotlin.math.abs(primary - secondary) else null
            }
        if (gaps.size < MIN_HOURS) return null
        val mean = gaps.average().roundToInt()
        val confidence = when {
            mean <= AGREE_MAX_DIFF -> ModelConfidence.AGREE
            mean <= MIXED_MAX_DIFF -> ModelConfidence.MIXED
            else -> ModelConfidence.DISAGREE
        }
        return CloudAgreement(confidence, mean, gaps.size)
    }

    /** One digest line. Agreeing models need no number; a split names the average gap. */
    fun line(agreement: CloudAgreement): String = when (agreement.confidence) {
        ModelConfidence.AGREE -> "Models agree on clouds"
        ModelConfidence.MIXED -> "Models differ a little on clouds (avg ${agreement.meanAbsDiff} pts apart)"
        ModelConfidence.DISAGREE -> "Models disagree on clouds (avg ${agreement.meanAbsDiff} pts apart)"
    }
}
