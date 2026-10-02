package app.nightbrief.score

import app.nightbrief.astro.NightEphemeris
import java.util.Locale

/**
 * One sentence for a Go night, naming the measured conditions behind the score.
 *
 * Null when the verdict is not Go. An estimated factor is never described as a measurement:
 * if the only strong factors were estimated, the sentence says so.
 *
 * [ephemeris] lets a bright moon that sets or rises during the dark window be described
 * that way. Without it, the moon line is based only on the factor quality.
 */
object NightSummary {
    fun whyGood(score: NightScore, ephemeris: NightEphemeris? = null): String? {
        if (score.verdict != Verdict.GO) return null
        val reasons = score.factors.mapNotNull { phrase(it, ephemeris) }
        if (reasons.isNotEmpty()) return sentence(reasons.take(MAX_REASONS))
        val estimatedWouldHaveCounted = score.factors.any { factor ->
            factor.estimated && phrase(factor.copy(estimated = false), ephemeris) != null
        }
        return if (estimatedWouldHaveCounted) {
            "The score looks strong, but the main weather inputs are estimated."
        } else {
            "Conditions line up well enough to go."
        }
    }

    private fun phrase(factor: FactorScore, ephemeris: NightEphemeris?): String? {
        if (factor.estimated) return null
        val quality = factor.quality
        return when (factor.factor) {
            Factor.CLOUD -> when {
                quality >= 0.90 -> "clear skies"
                quality >= 0.70 -> "mostly clear skies"
                else -> null
            }
            Factor.MOON -> moonPhrase(quality, ephemeris)
            Factor.TRANSPARENCY -> if (quality >= 0.80) "good transparency" else null
            Factor.WIND -> when {
                quality >= 0.90 -> "calm air"
                quality >= 0.70 -> "light wind"
                else -> null
            }
            Factor.SEEING -> if (quality >= 0.80) "steady seeing" else null
            Factor.LIGHT_POLLUTION -> when {
                quality >= 0.85 -> "a very dark sky"
                quality >= 0.70 -> "a dark sky"
                else -> null
            }
        }
    }

    /**
     * A bright moon that leaves most of the dark window still counts as a reason to go,
     * and the sentence says how, instead of calling that moon dark.
     */
    private fun moonPhrase(quality: Double, ephemeris: NightEphemeris?): String? {
        if (quality < 0.75) return null
        val dark = ephemeris?.darkWindow
        val bright = (ephemeris?.moonIllumination ?: 0.0) >= 0.2
        val moonset = ephemeris?.moonset
        val moonrise = ephemeris?.moonrise
        val setsDuringDark = moonset != null && dark != null && moonset in dark
        val risesDuringDark = moonrise != null && dark != null && moonrise in dark
        return when {
            bright && setsDuringDark -> "a moon that sets early"
            bright && risesDuringDark -> "dark hours before moonrise"
            quality >= 0.92 -> "little moonlight"
            else -> "a faint moon"
        }
    }

    private fun sentence(reasons: List<String>): String {
        val body = when (reasons.size) {
            1 -> reasons[0]
            2 -> "${reasons[0]} and ${reasons[1]}"
            else -> reasons.dropLast(1).joinToString(", ") + ", and " + reasons.last()
        }
        return body.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } + "."
    }

    private const val MAX_REASONS = 3
}
