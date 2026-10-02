package app.nightbrief.score

/** One hour of total cloud and the low, mid, and high layers. Any field may be missing. */
data class CloudSample(
    val total: Int?,
    val low: Int?,
    val mid: Int?,
    val high: Int?,
)

/**
 * Describes the cloud when the high layer is what the total is made of.
 * Does not change [NightScoreEngine] cloud weight.
 */
object CloudLayers {
    /** Shown on Tonight when high cloud makes up most of the total. */
    const val HIGH_THIN = "high thin cloud"

    /**
     * Below this mean total (%), a night is clear enough that a layer reason is noise.
     * A few percent of cirrus should not compete with the score.
     */
    const val MIN_TOTAL_PERCENT = 15.0

    /**
     * Dark hours whose high cloud is at least half the total, and strictly more than
     * the low and mid layers. Hours missing a total or a high value are skipped.
     */
    fun reason(samples: List<CloudSample>): String? {
        val usable = samples.filter { it.total != null && it.high != null }
        if (usable.isEmpty()) return null
        val total = usable.map { it.total!!.toDouble() }.average()
        val high = usable.map { it.high!!.toDouble() }.average()
        if (total < MIN_TOTAL_PERCENT || high < total * 0.5) return null
        val low = averagePresent(usable.map { it.low })
        val mid = averagePresent(usable.map { it.mid })
        if (low != null && high <= low) return null
        if (mid != null && high <= mid) return null
        return HIGH_THIN
    }

    private fun averagePresent(values: List<Int?>): Double? {
        val present = values.filterNotNull()
        if (present.isEmpty()) return null
        return present.map { it.toDouble() }.average()
    }
}
