package app.nightbrief.score

import app.nightbrief.weather.HourlyWeather

/**
 * What the cloud layers say about a night, beyond the single total the score uses.
 * Not a [NightScoreEngine] factor. The cloud weight stays on total cover.
 */
enum class CloudReason {
    /** High cloud is more than half the total, and thicker than the low and mid layers. */
    HIGH_THIN,
}

object CloudLayers {
    /**
     * Dark hours only. A hour counts when it has both a total and a high-cloud percent.
     * High cloud "makes up most of the total" when the high sum is strictly more than half
     * the total sum. It also has to exceed the low sum and the mid sum when those layers
     * were reported, so a night of low stratus is not called thin cirrus.
     */
    fun reason(hours: List<HourlyWeather>): CloudReason? {
        val samples = hours.mapNotNull { hour ->
            val total = hour.cloudCover ?: return@mapNotNull null
            val high = hour.cloudHigh ?: return@mapNotNull null
            if (total < 0 || high < 0) return@mapNotNull null
            Sample(total, hour.cloudLow?.takeIf { it >= 0 }, hour.cloudMid?.takeIf { it >= 0 }, high)
        }
        if (samples.isEmpty()) return null
        val total = samples.sumOf { it.total.toLong() }
        val high = samples.sumOf { it.high.toLong() }
        if (total <= 0L || high * 2 <= total) return null
        val low = samples.mapNotNull { it.low }
        val mid = samples.mapNotNull { it.mid }
        if (low.isNotEmpty() && low.sumOf { it.toLong() } >= high) return null
        if (mid.isNotEmpty() && mid.sumOf { it.toLong() } >= high) return null
        return CloudReason.HIGH_THIN
    }

    private data class Sample(val total: Int, val low: Int?, val mid: Int?, val high: Int)
}
