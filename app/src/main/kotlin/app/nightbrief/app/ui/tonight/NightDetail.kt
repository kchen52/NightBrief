package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.LabeledValue
import app.nightbrief.app.ui.common.ScoreGauge
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.astro.Darkness
import app.nightbrief.score.DigestComposer
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.NightReport
import app.nightbrief.score.SiteAlternative
import app.nightbrief.score.SiteComparison
import app.nightbrief.score.TargetSuggestion
import app.nightbrief.score.TimelineHour
import app.nightbrief.score.Verdict
import app.nightbrief.weather.ForecastStatus
import kotlin.math.roundToInt

@Composable
fun NightDetail(
    report: NightReport,
    alternative: SiteAlternative?,
    onOpenAlternative: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusBanners(report)
        alternative?.let { alt ->
            val why = SiteComparison.joinReasons(alt.reasons)
            Banner(
                text = "${alt.report.scoreValue} at ${alt.report.site.name} (+${alt.delta})" + if (why.isNotEmpty()) " — $why" else "",
                color = NightColors.Excellent,
                action = {
                    androidx.compose.material3.TextButton(onClick = { onOpenAlternative(alt.report.site.id) }) { Text("View") }
                },
            )
        }
        HeroCard(report)
        SkyCard(report)
        MilkyWayCard(report)
        report.score?.let { BreakdownCard(report) }
        TimelineCard(report)
        if (report.suggestions.isNotEmpty()) {
            SectionCard("Suggested targets") {
                report.suggestions.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SuggestionRow(s, report)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusBanners(report: NightReport) {
    when {
        report.forecastStatus == ForecastStatus.STALE ->
            Banner("Offline — showing the last saved forecast", NightColors.Fair)
        report.coverage == ForecastCoverage.NONE && report.forecastStatus == null ->
            Banner(report.warnings.firstOrNull() ?: "Forecast unavailable", NightColors.Poor)
        report.coverage == ForecastCoverage.NONE ->
            Banner("This night is beyond the forecast horizon — showing sky data only", NightColors.TextMuted)
        report.coverage == ForecastCoverage.PARTIAL ->
            Banner("Forecast covers only part of this night", NightColors.TextMuted)
    }
}

@Composable
private fun HeroCard(report: NightReport) {
    val score = report.score
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreGauge(score?.score)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(report.site.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${Format.dayName(report.date)}, ${Format.dayMonth(report.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (score != null) {
                    val color = NightColors.forScore(score.score)
                    AssistChip(
                        onClick = {},
                        label = { Text("${score.verdict.label} · ${score.band.label}", fontWeight = FontWeight.SemiBold) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = color),
                        border = AssistChipDefaults.assistChipBorder(true, borderColor = color.copy(alpha = 0.5f)),
                    )
                    score.bestWindow?.takeIf { score.verdict != Verdict.NO_GO || score.bestWindowScore >= 50 }?.let {
                        Text(
                            "Best ${Format.window(it, report.site.zone)} (${score.bestWindowScore})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text("No score yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SkyCard(report: NightReport) {
    val e = report.ephemeris
    val zone = report.site.zone
    SectionCard("Sky") {
        Row(Modifier.fillMaxWidth()) {
            LabeledValue("Sunset", Format.time(e.sunset, zone), Modifier.weight(1f))
            LabeledValue("Dark from", Format.time(e.darkWindow?.start, zone), Modifier.weight(1f))
            LabeledValue("Dark until", Format.time(e.darkWindow?.end, zone), Modifier.weight(1f))
            LabeledValue("Sunrise", Format.time(e.sunrise, zone), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(DigestComposer.moonLine(e) { Format.time(it, zone) }, style = MaterialTheme.typography.bodyMedium)
        if (e.darkness != Darkness.ASTRONOMICAL) {
            Text(e.darkness.label, style = MaterialTheme.typography.bodyMedium, color = NightColors.Fair)
        }
        Text(
            "Moon-free darkness: ${Format.duration(e.moonFreeDarkDuration)} · Bortle ${report.site.bortle ?: "${report.site.effectiveBortle} (assumed)"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MilkyWayCard(report: NightReport) {
    val mw = report.ephemeris.milkyWay
    val zone = report.site.zone
    SectionCard("Milky Way core") {
        if (mw == null) {
            Text(
                "The galactic core doesn't clear 10° during darkness tonight.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                LabeledValue("Visible", Format.window(mw.window, zone), Modifier.weight(1.4f))
                LabeledValue("Peak", "${Format.degrees(mw.peakAltitudeDeg)} at ${Format.time(mw.peakTime, zone)}", Modifier.weight(1.4f))
                LabeledValue("Direction", DigestComposer.compass(mw.peakAzimuthDeg), Modifier.weight(0.8f))
            }
            Spacer(Modifier.height(8.dp))
            val free = mw.moonFreeDuration
            Text(
                if (free == mw.window.duration) "Moon-free for the whole window"
                else if (free.isZero) "Moon is up for the whole window"
                else "Moon-free for ${Format.duration(free)} of ${Format.duration(mw.window.duration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BreakdownCard(report: NightReport) {
    val score = report.score ?: return
    SectionCard("Score breakdown") {
        score.factors.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    f.factor.label + if (f.estimated) " (est.)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(150.dp),
                )
                LinearProgressIndicator(
                    progress = { (f.points / f.maxPoints).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = NightColors.forScore((f.points / f.maxPoints * 100).roundToInt()),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {},
                )
                Text(
                    "${f.points.roundToInt()}/${f.maxPoints}",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        if (score.factors.any { it.estimated }) {
            Text(
                "Estimated factors use humidity and jet-stream wind where 7Timer has no data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TimelineRow(val label: String, val value: (TimelineHour) -> String, val color: (TimelineHour) -> Color?)

@Composable
private fun TimelineCard(report: NightReport) {
    val zone = report.site.zone
    val rows = listOf(
        TimelineRow("Score", { it.score.toString() }, { NightColors.forScore(it.score) }),
        TimelineRow("Cloud", { h -> h.cloudCover?.let { "$it%" } ?: "–" }, { h -> h.cloudCover?.let { NightColors.forScore(100 - it) } }),
        TimelineRow("Moon", { h -> if (h.moonAltitudeDeg > 0) Format.degrees(h.moonAltitudeDeg) else "↓" }, { h ->
            if (h.moonAltitudeDeg > 0 && h.moonIllumination > 0.05) NightColors.Fair else null
        }),
        TimelineRow("MW core", { h -> if (h.galacticCenterAltitudeDeg > 0) Format.degrees(h.galacticCenterAltitudeDeg) else "↓" }, { h ->
            if (h.isDark && h.galacticCenterAltitudeDeg >= 10) NightColors.Primary else null
        }),
        TimelineRow("Seeing", { h -> h.seeingIndex?.let { "$it/8" } ?: "–" }, { h -> h.seeingIndex?.let { NightColors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow("Transp.", { h -> h.transparencyIndex?.let { "$it/8" } ?: "–" }, { h -> h.transparencyIndex?.let { NightColors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow("Wind", { h -> h.windKmh?.roundToInt()?.toString() ?: "–" }, { h -> h.windKmh?.let { NightColors.forScore((100 - it * 2.5).roundToInt()) } }),
    )
    SectionCard("Hour by hour") {
        Row {
            Column {
                Text("", style = MaterialTheme.typography.labelMedium, modifier = Modifier.height(24.dp))
                rows.forEach {
                    Box(Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
                        Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                report.timeline.forEach { h ->
                    Column(
                        Modifier
                            .width(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (h.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                            Text(Format.hour(h.time, zone), style = MaterialTheme.typography.labelMedium)
                        }
                        rows.forEach { row ->
                            Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    row.value(h),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = row.color(h) ?: MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (row.label == "Score") FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Shaded columns are full darkness. Seeing and transparency use the 7Timer scale (1 best, 8 worst); wind in km/h.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionRow(s: TargetSuggestion, report: NightReport) {
    val zone = report.site.zone
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.target.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${s.target.kind.label} · ${Format.window(s.window, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.degrees(s.peakAltitudeDeg), style = MaterialTheme.typography.titleMedium, color = NightColors.Primary)
                Text(
                    "${DigestComposer.compass(s.peakAzimuthDeg)} at ${Format.time(s.bestTime, zone)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(s.reason, style = MaterialTheme.typography.bodyMedium)
        s.exposure?.let { ex ->
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(10.dp),
            ) {
                Column {
                    Text(ex.summary, style = MaterialTheme.typography.labelLarge, color = NightColors.Amber)
                    Text(
                        "${ex.lens.name} on ${ex.body.name} · NPF ${"%.0f".format(ex.npfSeconds)}s · 500-rule ${"%.0f".format(ex.rule500Seconds)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!ex.reachesTarget) {
                        Text(
                            "Needs ~${s.target.minFocalMm}mm+ (full-frame equiv.) to frame well",
                            style = MaterialTheme.typography.bodySmall,
                            color = NightColors.Marginal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(s.target.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}