package app.nightbrief.score

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Facts for a shared picture of one night.
 *
 * [place] is rounded to 0.1° (about 11 km). The card does not carry the site pin.
 * Clock times are `HH:mm` in the site zone, matching the digest. Not a [NightScoreEngine] input.
 * The shared picture uses the ordinary blue palette even when night vision is on.
 */
data class NightPlanCard(
    val siteName: String,
    val place: String,
    val dateLabel: String,
    /** Null when the forecast does not cover this night. */
    val score: Int?,
    val scoreText: String,
    val verdictLabel: String,
    /**
     * `HH:mm–HH:mm (score)` in the site zone. Null when [showsBestWindow] is false,
     * including a no-go night whose best window scores under 50.
     */
    val bestWindow: String?,
    val milkyWay: String,
    /** Name of the first imaging suggestion, or null when there is nothing to try. */
    val target: String?,
    /** Set when the score comes from a saved forecast, so the picture is not read as fresh. */
    val saved: String?,
    /**
     * True when any night-score factor used a proxy or neutral default.
     * Mirrors [NightScoreEngine] per-factor `estimated`: the picture must not
     * present an estimated score as an exact measurement.
     */
    val estimated: Boolean = false,
) {
    /** Text that rides along with the image for apps that share a caption. */
    val caption: String
        get() {
            val headline = if (score == null) verdictLabel else "$scoreText $verdictLabel"
            return listOfNotNull(
                siteName,
                place,
                headline,
                bestWindow?.let { "Best $it" },
                milkyWay,
                target?.let { "Try: $it" },
                saved,
                if (estimated) "Estimated" else null,
                "NightBrief",
            ).joinToString(" · ")
        }

    companion object {
        private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ROOT)
        private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        /**
         * True when Tonight's hero shows the best window: any go or maybe night, and a no-go
         * night only when that window still scores at least 50.
         */
        fun showsBestWindow(score: NightScore): Boolean {
            if (score.bestWindow == null) return false
            return score.verdict != Verdict.NO_GO || score.bestWindowScore >= 50
        }

        /** Latitude and longitude rounded to 0.1°, with a hemisphere letter. */
        fun place(latitude: Double, longitude: Double): String {
            val lat = roundToTenth(latitude).coerceIn(-90.0, 90.0)
            val lon = roundToTenth(longitude).coerceIn(-180.0, 180.0)
            return "${hemisphere(lat, "N", "S")} ${hemisphere(lon, "E", "W")}"
        }

        fun from(report: NightReport): NightPlanCard {
            val zone = report.site.zone
            val score = report.score
            val milkyWay = WidgetCopy.milkyWayLine(
                report.ephemeris.milkyWay?.window,
                zone,
                clearsHorizonAt = report.ephemeris.milkyWay?.clearsHorizonAt,
                blockedByHorizon = report.ephemeris.milkyWayBlockedByHorizon,
            )
            return NightPlanCard(
                siteName = report.site.name,
                place = place(report.site.latitude, report.site.longitude),
                dateLabel = dateFormat.format(report.date),
                score = score?.score,
                scoreText = score?.score?.toString() ?: "—",
                verdictLabel = if (score == null) "No score" else "${score.verdict.label} · ${score.band.label}",
                bestWindow = score?.let { bestWindow(it, zone) },
                milkyWay = milkyWay,
                target = report.suggestions.firstOrNull()?.target?.name,
                saved = SavedForecast.shortLabel(report.forecastStatus, report.forecastFetchedAt, zone),
                estimated = score?.factors?.any { it.estimated } == true,
            )
        }

        private fun bestWindow(score: NightScore, zone: ZoneId): String? {
            val window = score.bestWindow ?: return null
            if (!showsBestWindow(score)) return null
            val fmt = clockFormat.withZone(zone)
            return "${fmt.format(window.start)}–${fmt.format(window.end)} (${score.bestWindowScore})"
        }

        /** Half away from zero, so 43.65° and −43.65° both move to the farther tenth. */
        private fun roundToTenth(value: Double): Double {
            if (value.isNaN() || value.isInfinite()) return 0.0
            val rounded = floor(abs(value) * 10.0 + 0.5) / 10.0
            return if (value < 0.0) -rounded else rounded
        }

        private fun hemisphere(value: Double, positive: String, negative: String): String {
            val letter = if (value >= 0.0) positive else negative
            return String.format(Locale.ROOT, "%.1f°%s", abs(value), letter)
        }
    }
}
