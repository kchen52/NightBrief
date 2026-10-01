package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPass
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.weather.ForecastStatus
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class Digest(
    val siteId: String,
    val score: Int?,
    val title: String,
    /** One-line summary for the collapsed notification. */
    val summary: String,
    /** Detail lines for the expanded notification. */
    val lines: List<String>,
    val alternativeSiteId: String?,
)

object DigestComposer {
    val DEFAULT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun compose(
        report: NightReport,
        others: List<NightReport>,
        timeFormat: DateTimeFormatter = DEFAULT_TIME_FORMAT,
        alternativeThreshold: Int = SiteComparison.DEFAULT_THRESHOLD,
    ): Digest {
        val zone = report.site.zone
        fun fmt(t: Instant) = timeFormat.withZone(zone).format(t)
        val score = report.score
        val alt = SiteComparison.bestAlternative(report, others, alternativeThreshold)
        val name = report.site.name

        val title = when {
            score == null -> "Tonight at $name: forecast not available yet"
            report.ephemeris.darkness == Darkness.NONE -> "Tonight at $name: no darkness"
            alt != null -> "Tonight: ${score.score} at $name, but ${alt.report.scoreValue} at ${alt.report.site.name}"
            else -> "Tonight: ${score.score} at $name · ${score.band.label}"
        }

        val lines = mutableListOf<String>()
        if (score != null && report.ephemeris.darkness != Darkness.NONE) {
            val window = score.bestWindow?.let { "best ${fmt(it.start)}–${fmt(it.end)} (${score.bestWindowScore})" }
            val cloud = SiteComparison.averageCloud(report)?.let { "cloud $it%" }
            lines += listOfNotNull("${score.verdict.label}: ${score.band.label.lowercase()}", window, cloud)
                .joinToString(" · ")
        }
        lines += moonLine(report.ephemeris, ::fmt)
        if (report.ephemeris.darkness != Darkness.ASTRONOMICAL) {
            lines += report.ephemeris.darkness.label
        }
        report.ephemeris.milkyWay?.let { mw ->
            lines += "Milky Way core ${fmt(mw.window.start)}–${fmt(mw.window.end)}, " +
                "peak ${mw.peakAltitudeDeg.roundToInt()}° ${compass(mw.peakAzimuthDeg)}"
        }
        report.suggestions.firstOrNull()?.let { s ->
            lines += "Try: ${s.target.name}" + (s.exposure?.let { " · ${it.summary}" } ?: "")
        }
        alt?.let {
            val why = SiteComparison.joinReasons(it.reasons)
            lines += "${it.report.site.name} +${it.delta}" + if (why.isNotEmpty()) ": $why" else ""
        }
        report.meteor?.takeIf { it.worthWatching }?.let { lines += MeteorAdvisor.digestLine(it) }
        issLine(report.issPasses, ::fmt)?.let { lines += it }
        report.aurora?.let { aurora ->
            val line = AuroraCopy.digestLine(aurora)
            if (aurora.prominent) lines.add(0, line) else lines += line
        }
        if (report.forecastStatus == ForecastStatus.STALE) {
            lines += "Offline — showing the last saved forecast"
        }

        return Digest(
            siteId = report.site.id,
            score = score?.score,
            title = title,
            summary = lines.firstOrNull().orEmpty(),
            lines = lines,
            alternativeSiteId = alt?.report?.site?.id,
        )
    }

    fun moonLine(eph: NightEphemeris, fmt: (Instant) -> String): String {
        val pct = (eph.moonIllumination * 100).roundToInt()
        val dark = eph.darkWindow
        val phase = "${eph.moonPhaseName.label} ($pct%)"
        if (eph.moonIllumination < NightEphemeris.NEGLIGIBLE_MOON || dark == null) return phase
        val free = eph.moonFreeDarkDuration
        val set = eph.moonset
        val rise = eph.moonrise
        return when {
            free >= dark.duration.minusMinutes(5) -> "$phase, below the horizon all night"
            free.isZero -> "$phase, up all night"
            set != null && set in dark -> "$phase, sets ${fmt(set)}"
            rise != null && rise in dark -> "$phase, rises ${fmt(rise)}"
            else -> "$phase, ${free.toMinutes() / 60}h ${free.toMinutes() % 60}m moon-free"
        }
    }

    /** One line for the highest pass, plus a count when the dark window has more than one. */
    fun issLine(passes: List<IssPass>, fmt: (Instant) -> String): String? {
        if (passes.isEmpty()) return null
        val best = passes.maxBy { it.peakAltitudeDeg }
        val rise = best.rise
        val set = best.set
        val peak = "${best.peakAltitudeDeg.roundToInt()}° ${compass(best.peakAzimuthDeg)}"
        val span = when {
            rise != null && set != null -> "${fmt(rise)}–${fmt(set)}"
            rise != null -> "from ${fmt(rise)}"
            set != null -> "until ${fmt(set)}"
            else -> "at ${fmt(best.peak)}"
        }
        val extra = if (passes.size > 1) " · ${passes.size} passes" else ""
        return "ISS $span, peak $peak$extra"
    }

    fun compass(azimuthDeg: Double): String {
        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return points[(((azimuthDeg % 360) + 360 + 22.5) / 45).toInt() % 8]
    }
}
