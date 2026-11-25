package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.weather.ForecastStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One night row inside a [WeekPlanCard].
 */
data class WeekPlanNight(
    val dayLabel: String,
    val dateLabel: String,
    /** Null when the night has no score (no darkness, missing, or beyond forecast). */
    val score: Int?,
    val scoreText: String,
    val summary: String,
    val moonText: String,
    val isBest: Boolean,
    val estimated: Boolean = false,
)

/**
 * Facts for a shared picture of one site's week outlook.
 *
 * [place] is rounded to 0.1° (about 11 km) and is drawn on the picture.
 * The caption leaves coordinates out. The card does not carry the site pin.
 * The shared picture uses the ordinary blue palette even when night vision is on.
 */
data class WeekPlanCard(
    val siteName: String,
    val place: String,
    val dateRangeLabel: String,
    /** Best night's day and date (e.g. `Mon 12 Aug`), or null when no night is scored. */
    val bestDayLabel: String?,
    /** Best night's score, or null when no night is scored. */
    val bestScore: Int?,
    val bestScoreText: String,
    /** Verdict and band for the best night, or a plain status line when none is scored. */
    val bestVerdictLabel: String,
    val nights: List<WeekPlanNight>,
    /** Set when the forecast comes from a saved file, so the picture is not read as fresh. */
    val saved: String? = null,
    /** True when any night in the week used estimated factors. */
    val estimated: Boolean = false,
) {
    /** Text that rides along with the image for apps that share a caption. */
    val caption: String
        get() {
            val bestLine = if (bestDayLabel != null && bestScore != null) {
                "Best: $bestDayLabel ($bestScoreText)"
            } else {
                bestVerdictLabel
            }
            val daily = nights.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { "${it.dayLabel} ${it.scoreText}" }
            return listOfNotNull(
                siteName,
                dateRangeLabel.takeIf { it.isNotBlank() && it != "—" },
                bestLine,
                daily,
                saved,
                if (estimated) "Estimated" else null,
                "NightBrief",
            ).joinToString(" · ")
        }

    companion object {
        private val fullDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ROOT)
        private val shortDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ROOT)
        private val dayMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ROOT)

        fun from(
            outlook: WeeklyOutlook,
            forecastStatus: ForecastStatus? = null,
            forecastFetchedAt: Instant? = null,
        ): WeekPlanCard {
            val best = outlook.best
            val allMissing = outlook.nights.isNotEmpty() && outlook.nights.all { it.forecastMissing }
            val effectiveBest = if (allMissing) null else best
            val nights = outlook.nights.map { night ->
                val baseLabel = when {
                    night.darkness == Darkness.NONE -> "No darkness"
                    night.forecastMissing -> "No forecast"
                    night.coverage == ForecastCoverage.NONE -> "Beyond forecast"
                    else -> night.band?.label ?: ""
                }
                val summary = if (night.estimated) {
                    if (baseLabel.isNotBlank()) "$baseLabel · est." else "est."
                } else {
                    baseLabel
                }
                val pct = (night.moonIllumination.coerceIn(0.0, 1.0) * 100.0).roundToInt()
                WeekPlanNight(
                    dayLabel = shortDayFormat.format(night.date),
                    dateLabel = dayMonthFormat.format(night.date),
                    score = night.score,
                    scoreText = night.score?.toString() ?: "—",
                    summary = summary,
                    moonText = "${moonSymbol(night.moonIllumination)} $pct%",
                    isBest = effectiveBest != null && night == effectiveBest,
                    estimated = night.estimated,
                )
            }
            val bestScore = effectiveBest?.score
            val bestVerdictLabel = when {
                effectiveBest != null && bestScore != null -> {
                    val verdict = Verdict.of(bestScore).label
                    val band = Band.of(bestScore).label
                    "$verdict · $band"
                }
                allMissing -> "Couldn't load the forecast"
                else -> "No forecast this week"
            }
            val saved = if (forecastStatus != null) {
                SavedForecast.shortLabel(forecastStatus, forecastFetchedAt, outlook.site.zone)
            } else {
                null
            }
            return WeekPlanCard(
                siteName = outlook.site.name,
                place = NightPlanCard.place(outlook.site.latitude, outlook.site.longitude),
                dateRangeLabel = dateRange(outlook.nights.firstOrNull()?.date, outlook.nights.lastOrNull()?.date),
                bestDayLabel = effectiveBest?.date?.let { fullDayFormat.format(it) },
                bestScore = bestScore,
                bestScoreText = bestScore?.toString() ?: "—",
                bestVerdictLabel = bestVerdictLabel,
                nights = nights,
                saved = saved,
                estimated = nights.any { it.estimated },
            )
        }

        internal fun dateRange(first: LocalDate?, last: LocalDate?): String {
            if (first == null || last == null) return "—"
            if (first == last) return fullDayFormat.format(first)
            return if (first.month == last.month && first.year == last.year) {
                "${first.dayOfMonth}–${dayMonthFormat.format(last)}"
            } else {
                "${dayMonthFormat.format(first)}–${dayMonthFormat.format(last)}"
            }
        }

        private fun moonSymbol(illumination: Double): String = when {
            illumination < 0.05 -> "○"
            illumination < 0.45 -> "◔"
            illumination < 0.55 -> "◑"
            illumination < 0.95 -> "◕"
            else -> "●"
        }
    }
}
