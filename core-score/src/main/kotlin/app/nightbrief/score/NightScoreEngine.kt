package app.nightbrief.score

import app.nightbrief.astro.TimeWindow
import java.time.Instant
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Factor(val label: String, val weight: Int) {
    CLOUD("Cloud cover", 35),
    MOON("Moon & twilight", 25),
    TRANSPARENCY("Transparency", 15),
    WIND("Wind", 10),
    SEEING("Seeing", 10),
    LIGHT_POLLUTION("Light pollution", 5),
}

enum class Band(val label: String, val min: Int) {
    EXCELLENT("Excellent", 85),
    GOOD("Good", 70),
    FAIR("Fair", 50),
    MARGINAL("Marginal", 30),
    POOR("Poor", 0);

    companion object {
        fun of(score: Int): Band = entries.first { score >= it.min }
    }
}

enum class Verdict(val label: String) {
    GO("Go"),
    MAYBE("Maybe"),
    NO_GO("No-go");

    companion object {
        fun of(score: Int): Verdict = when {
            score >= 70 -> GO
            score >= 50 -> MAYBE
            else -> NO_GO
        }
    }
}

/** Everything the engine needs to know about one hour at one site. */
data class HourInput(
    val time: Instant,
    val cloudCover: Int?,
    val windKmh: Double?,
    val gustKmh: Double?,
    /** 7Timer seeing index 1..8 (lower is better). */
    val seeingIndex: Int?,
    /** 7Timer transparency index 1..8 (lower is better). */
    val transparencyIndex: Int?,
    val humidity: Int?,
    val jetStreamKmh: Double?,
    val sunAltitudeDeg: Double,
    val moonAltitudeDeg: Double,
    val moonIllumination: Double,
    val bortle: Int,
)

data class FactorScore(
    val factor: Factor,
    /** Raw quality 0..1 before cross-factor gating. */
    val quality: Double,
    /** Points earned after gating; all factors' points sum to the score. */
    val points: Double,
    /** True when the input was missing and a proxy or neutral default was used. */
    val estimated: Boolean,
) {
    val maxPoints: Int get() = factor.weight
}

data class HourScore(val time: Instant, val score: Int, val factors: List<FactorScore>) {
    fun factor(f: Factor): FactorScore = factors.first { it.factor == f }
}

data class NightScore(
    val score: Int,
    val factors: List<FactorScore>,
    val hours: List<HourScore>,
    /** Best contiguous window (up to [NightScoreEngine.BEST_WINDOW_HOURS] hours). */
    val bestWindow: TimeWindow?,
    val bestWindowScore: Int,
) {
    val band: Band get() = Band.of(score)
    val verdict: Verdict get() = Verdict.of(score)
    fun factor(f: Factor): FactorScore = factors.first { it.factor == f }

    companion object {
        val NO_DARKNESS = NightScore(0, Factor.entries.map { FactorScore(it, 0.0, 0.0, false) }, emptyList(), null, 0)
    }
}

/**
 * Night Score (0–100): cloud 35%, moon 25%, transparency 15%, wind 10%, seeing 10%, light pollution 5%.
 *
 * Two gates stop good secondary factors from rescuing a bad night: cloud scales every other factor
 * (an overcast night lands near 15 however calm and dark it is), and moonlight/twilight scales
 * transparency and light pollution (a dark site's advantage is washed out under a bright Moon).
 *
 * The night score blends the mean over all dark hours with the best 3-hour window, so a night
 * that clears after midnight still rates as usable.
 */
object NightScoreEngine {
    const val BEST_WINDOW_HOURS = 3
    private const val CLOUD_GATE_FLOOR = 0.35
    private const val MOON_GATE_FLOOR = 0.40

    fun scoreHour(h: HourInput): HourScore {
        val cloud = cloudQuality(h.cloudCover)
        val moon = moonQuality(h.moonAltitudeDeg, h.moonIllumination) * twilightQuality(h.sunAltitudeDeg)
        val transparency = transparencyQuality(h.transparencyIndex, h.humidity)
        val wind = windQuality(h.windKmh, h.gustKmh)
        val seeing = seeingQuality(h.seeingIndex, h.jetStreamKmh)
        val lp = lightPollutionQuality(h.bortle)

        val cloudGate = CLOUD_GATE_FLOOR + (1 - CLOUD_GATE_FLOOR) * cloud.first
        val moonGate = MOON_GATE_FLOOR + (1 - MOON_GATE_FLOOR) * moon

        fun fs(f: Factor, q: Pair<Double, Boolean>, gate: Double) = FactorScore(f, q.first, f.weight * q.first * gate, q.second)

        val factors = listOf(
            fs(Factor.CLOUD, cloud, 1.0),
            fs(Factor.MOON, moon to false, cloudGate),
            fs(Factor.TRANSPARENCY, transparency, cloudGate * moonGate),
            fs(Factor.WIND, wind, cloudGate),
            fs(Factor.SEEING, seeing, cloudGate),
            fs(Factor.LIGHT_POLLUTION, lp to false, cloudGate * moonGate),
        )
        return HourScore(h.time, factors.sumOf { it.points }.roundToInt().coerceIn(0, 100), factors)
    }

    fun scoreNight(darkHours: List<HourInput>): NightScore {
        if (darkHours.isEmpty()) return NightScore.NO_DARKNESS
        val hours = darkHours.sortedBy { it.time }.map(::scoreHour)
        val windowSize = minOf(BEST_WINDOW_HOURS, hours.size)
        val bestStart = (0..hours.size - windowSize).maxBy { i ->
            hours.subList(i, i + windowSize).sumOf { h -> h.factors.sumOf { it.points } }
        }
        val window = hours.subList(bestStart, bestStart + windowSize)

        val factors = Factor.entries.map { f ->
            val all = hours.map { it.factor(f) }
            val best = window.map { it.factor(f) }
            FactorScore(
                factor = f,
                quality = all.map { it.quality }.average(),
                points = 0.5 * all.map { it.points }.average() + 0.5 * best.map { it.points }.average(),
                estimated = all.any { it.estimated },
            )
        }
        val windowScore = window.map { h -> h.factors.sumOf { it.points } }.average()
        return NightScore(
            score = factors.sumOf { it.points }.roundToInt().coerceIn(0, 100),
            factors = factors,
            hours = hours,
            bestWindow = TimeWindow(window.first().time, window.last().time.plusSeconds(3600)),
            bestWindowScore = windowScore.roundToInt().coerceIn(0, 100),
        )
    }

    internal fun cloudQuality(cloud: Int?): Pair<Double, Boolean> =
        if (cloud == null) 0.5 to true else (1 - cloud.coerceIn(0, 100) / 100.0) to false

    /** Bright, high moons hurt most; illumination^1.5 reflects how quickly a thin crescent stops mattering. */
    internal fun moonQuality(altitudeDeg: Double, illumination: Double): Double {
        if (altitudeDeg <= 0) return 1.0
        val altitudeFactor = ((altitudeDeg + 1) / 21).coerceIn(0.0, 1.0)
        return 1 - illumination.coerceIn(0.0, 1.0).pow(1.5) * altitudeFactor
    }

    internal fun twilightQuality(sunAltitudeDeg: Double): Double = when {
        sunAltitudeDeg <= -18 -> 1.0
        sunAltitudeDeg <= -12 -> 0.4 + 0.6 * (-12 - sunAltitudeDeg) / 6
        sunAltitudeDeg <= -6 -> 0.4 * (-6 - sunAltitudeDeg) / 6
        else -> 0.0
    }

    /** Falls back to a humidity proxy when 7Timer has no data (beyond its 72 h horizon). */
    internal fun transparencyQuality(index: Int?, humidity: Int?): Pair<Double, Boolean> = when {
        index != null -> (8 - index.coerceIn(1, 8)) / 7.0 to false
        humidity != null -> when {
            humidity <= 50 -> 0.8
            humidity >= 95 -> 0.15
            else -> 0.8 - 0.65 * (humidity - 50) / 45.0
        } to true
        else -> 0.5 to true
    }

    internal fun windQuality(windKmh: Double?, gustKmh: Double?): Pair<Double, Boolean> {
        if (windKmh == null && gustKmh == null) return 0.7 to true
        val effective = max(windKmh ?: 0.0, (gustKmh ?: 0.0) * 0.7)
        val q = when {
            effective <= 10 -> 1.0
            effective >= 40 -> 0.0
            else -> 1 - (effective - 10) / 30
        }
        return q to false
    }

    /** Falls back to 250 hPa jet-stream speed as a seeing proxy when 7Timer has no data. */
    internal fun seeingQuality(index: Int?, jetStreamKmh: Double?): Pair<Double, Boolean> = when {
        index != null -> (8 - index.coerceIn(1, 8)) / 7.0 to false
        jetStreamKmh != null -> when {
            jetStreamKmh <= 70 -> 0.8
            jetStreamKmh >= 200 -> 0.2
            else -> 0.8 - 0.6 * (jetStreamKmh - 70) / 130
        } to true
        else -> 0.5 to true
    }

    internal fun lightPollutionQuality(bortle: Int): Double = (9 - bortle.coerceIn(1, 9)) / 8.0
}
