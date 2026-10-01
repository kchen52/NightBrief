package app.nightbrief.score

import java.time.Duration
import kotlin.math.roundToInt

data class SiteAlternative(
    val report: NightReport,
    /** Points better than the primary site. */
    val delta: Int,
    /** Human-readable reasons, most significant first, e.g. "moon rises 40 min later there". */
    val reasons: List<String>,
)

object SiteComparison {
    /** How much better another site must score before the digest mentions it. */
    const val DEFAULT_THRESHOLD = 15
    private const val FACTOR_DELTA = 3.0
    private const val MIN_MOON_MINUTES = 15L

    fun bestAlternative(
        primary: NightReport,
        others: List<NightReport>,
        threshold: Int = DEFAULT_THRESHOLD,
    ): SiteAlternative? {
        val base = primary.scoreValue ?: return null
        val best = others
            .filter { it.site.id != primary.site.id }
            .filter { it.scoreValue != null }
            .maxByOrNull { it.scoreValue!! } ?: return null
        val delta = best.scoreValue!! - base
        if (delta < threshold) return null
        return SiteAlternative(best, delta, reasons(primary, best))
    }

    fun reasons(primary: NightReport, alt: NightReport): List<String> {
        val ps = primary.score ?: return emptyList()
        val asc = alt.score ?: return emptyList()
        val candidates = mutableListOf<Pair<Double, String>>()

        for (f in Factor.entries) {
            val gain = asc.factor(f).points - ps.factor(f).points
            when (f) {
                Factor.LIGHT_POLLUTION -> {
                    val pb = primary.site.effectiveBortle
                    val ab = alt.site.effectiveBortle
                    if (pb - ab >= 2) candidates += (pb - ab) * 2.0 to "Bortle $ab skies"
                }
                Factor.CLOUD -> if (gain >= FACTOR_DELTA) {
                    val pc = averageCloud(primary)
                    val ac = averageCloud(alt)
                    val text = if (pc != null && ac != null) "clearer skies ($ac% vs $pc% cloud)" else "clearer skies"
                    candidates += gain to text
                }
                Factor.MOON -> if (gain >= FACTOR_DELTA) candidates += gain to moonReason(primary, alt)
                Factor.TRANSPARENCY -> if (gain >= FACTOR_DELTA) candidates += gain to "better transparency"
                Factor.WIND -> if (gain >= FACTOR_DELTA) candidates += gain to "calmer winds"
                Factor.SEEING -> if (gain >= FACTOR_DELTA) candidates += gain to "steadier seeing"
            }
        }
        return candidates.sortedByDescending { it.first }.map { it.second }.take(3)
    }

    /**
     * Moonrise/moonset are compared relative to each site's own darkness, since that is what
     * determines moon-free shooting time (sites further west see both shift later together).
     */
    internal fun moonReason(primary: NightReport, alt: NightReport): String {
        val p = primary.ephemeris
        val a = alt.ephemeris
        val pDark = p.darkWindow
        val aDark = a.darkWindow
        if (pDark != null && aDark != null) {
            val pRise = p.moonrise?.takeIf { it in pDark }?.let { Duration.between(pDark.start, it) }
            val aRise = a.moonrise?.takeIf { it in aDark }?.let { Duration.between(aDark.start, it) }
            if (pRise != null && aRise != null) {
                val later = (aRise - pRise).toMinutes()
                if (later >= MIN_MOON_MINUTES) return "moon rises $later min later there"
            }
            val pSet = p.moonset?.takeIf { it in pDark }?.let { Duration.between(it, pDark.end) }
            val aSet = a.moonset?.takeIf { it in aDark }?.let { Duration.between(it, aDark.end) }
            if (pSet != null && aSet != null) {
                val earlier = (aSet - pSet).toMinutes()
                if (earlier >= MIN_MOON_MINUTES) return "moon sets $earlier min earlier there"
            }
        }
        val extra = (a.moonFreeDarkDuration - p.moonFreeDarkDuration).toMinutes()
        if (extra >= MIN_MOON_MINUTES) return "$extra min more moon-free darkness"
        return if (a.darkness.ordinal < p.darkness.ordinal) "darker twilight" else "less moonlight"
    }

    internal fun averageCloud(r: NightReport): Int? =
        r.timeline.filter { it.isDark }.mapNotNull { it.cloudCover }.takeIf { it.isNotEmpty() }
            ?.average()?.roundToInt()

    fun joinReasons(reasons: List<String>): String = when (reasons.size) {
        0 -> ""
        1 -> reasons[0]
        else -> reasons.dropLast(1).joinToString(", ") + " and " + reasons.last()
    }
}
