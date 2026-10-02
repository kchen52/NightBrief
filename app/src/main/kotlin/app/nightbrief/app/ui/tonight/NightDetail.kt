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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.LabeledValue
import app.nightbrief.app.ui.common.ScoreGauge
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPass
import app.nightbrief.score.AuroraChance
import app.nightbrief.score.AuroraCopy
import app.nightbrief.score.AuroraOutlook
import app.nightbrief.score.DewCopy
import app.nightbrief.score.DewOutlook
import app.nightbrief.score.DigestComposer
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.MeteorAdvisor
import app.nightbrief.score.MeteorOutlook
import app.nightbrief.score.NightReport
import app.nightbrief.score.SiteAlternative
import app.nightbrief.score.SiteComparison
import app.nightbrief.score.TargetSuggestion
import app.nightbrief.score.TimelineHour
import app.nightbrief.score.Verdict
import app.nightbrief.weather.ForecastStatus
import java.time.ZoneId
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
            val score = alt.report.scoreValue ?: return@let
            val why = SiteComparison.joinReasons(alt.reasons)
            Banner(
                text = if (why.isEmpty()) {
                    stringResource(R.string.alternative_banner, score, alt.report.site.name, alt.delta)
                } else {
                    stringResource(R.string.alternative_banner_why, score, alt.report.site.name, alt.delta, why)
                },
                color = NightColors.Excellent,
                action = {
                    androidx.compose.material3.TextButton(onClick = { onOpenAlternative(alt.report.site.id) }) {
                        Text(stringResource(R.string.view))
                    }
                },
            )
        }
        HeroCard(report)
        SkyCard(report)
        report.dew?.let { DewCard(it, report.site.zone) }
        report.aurora?.let { AuroraCard(it) }
        report.meteor?.takeIf { it.worthWatching }?.let { MeteorCard(it, report) }
        if (report.issPasses.isNotEmpty()) IssCard(report)
        MilkyWayCard(report)
        report.score?.let { BreakdownCard(report) }
        TimelineCard(report)
        if (report.suggestions.isNotEmpty()) {
            SectionCard(stringResource(R.string.section_targets)) {
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
            Banner(stringResource(R.string.banner_stale), NightColors.Fair)
        report.coverage == ForecastCoverage.NONE && report.forecastStatus == null ->
            Banner(report.warnings.firstOrNull() ?: stringResource(R.string.banner_forecast_unavailable), NightColors.Poor)
        report.coverage == ForecastCoverage.NONE ->
            Banner(stringResource(R.string.banner_beyond_horizon), NightColors.TextMuted)
        report.coverage == ForecastCoverage.PARTIAL ->
            Banner(stringResource(R.string.banner_partial), NightColors.TextMuted)
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
                            stringResource(R.string.best_window, Format.window(it, report.site.zone), score.bestWindowScore),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text(stringResource(R.string.no_score_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SkyCard(report: NightReport) {
    val e = report.ephemeris
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_sky)) {
        Row(Modifier.fillMaxWidth()) {
            LabeledValue(stringResource(R.string.sunset), Format.time(e.sunset, zone), Modifier.weight(1f))
            LabeledValue(stringResource(R.string.dark_from), Format.time(e.darkWindow?.start, zone), Modifier.weight(1f))
            LabeledValue(stringResource(R.string.dark_until), Format.time(e.darkWindow?.end, zone), Modifier.weight(1f))
            LabeledValue(stringResource(R.string.sunrise), Format.time(e.sunrise, zone), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(DigestComposer.moonLine(e) { Format.time(it, zone) }, style = MaterialTheme.typography.bodyMedium)
        if (e.darkness != Darkness.ASTRONOMICAL) {
            Text(e.darkness.label, style = MaterialTheme.typography.bodyMedium, color = NightColors.Fair)
        }
        val bortle = report.site.bortle?.toString()
            ?: stringResource(R.string.bortle_assumed, report.site.effectiveBortle)
        Text(
            stringResource(R.string.moon_free_darkness, Format.duration(e.moonFreeDarkDuration), bortle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DewCard(dew: DewOutlook, zone: ZoneId) {
    val title = when {
        dew.frostFrom != null -> R.string.section_frost
        dew.from != null -> R.string.section_dew
        else -> R.string.section_overnight
    }
    val color = if (dew.frostFrom != null) NightColors.Poor else NightColors.Amber
    SectionCard(stringResource(title)) {
        DewCopy.riskLine(dew) { Format.time(it, zone) }?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            DewCopy.lowLine(dew),
            style = if (dew.risk) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = if (dew.risk) FontWeight.Normal else FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            DewCopy.detail(dew),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuroraCard(aurora: AuroraOutlook) {
    val alert = aurora.prominent
    val color = when (aurora.chance) {
        AuroraChance.LIKELY -> NightColors.Excellent
        AuroraChance.POSSIBLE -> NightColors.Amber
        AuroraChance.UNLIKELY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SectionCard(stringResource(R.string.section_aurora)) {
        Text(
            AuroraCopy.digestLine(aurora),
            style = if (alert) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (alert) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            AuroraCopy.detail(aurora),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeteorCard(meteor: MeteorOutlook, report: NightReport) {
    SectionCard(stringResource(R.string.section_meteors)) {
        Text(MeteorAdvisor.digestLine(meteor), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            MeteorAdvisor.detail(meteor),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(
                R.string.meteor_radiant,
                Format.azimuth(meteor.peakRadiantAzimuthDeg),
                Format.time(meteor.peakRadiantTime, report.site.zone),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssCard(report: NightReport) {
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_iss)) {
        report.issPasses.forEachIndexed { i, pass ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            IssPassRow(pass, zone)
        }
    }
}

@Composable
private fun IssPassRow(pass: IssPass, zone: ZoneId) {
    val rise = pass.rise
    val set = pass.set
    val span = when {
        rise != null && set != null -> stringResource(R.string.iss_span, Format.time(rise, zone), Format.time(set, zone))
        rise != null -> stringResource(R.string.iss_from, Format.time(rise, zone))
        set != null -> stringResource(R.string.iss_until, Format.time(set, zone))
        else -> stringResource(R.string.iss_peak_only, Format.time(pass.peak, zone))
    }
    Text(span, style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(
            R.string.iss_peak,
            Format.degrees(pass.peakAltitudeDeg),
            DigestComposer.compass(pass.peakAzimuthDeg),
            Format.time(pass.peak, zone),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MilkyWayCard(report: NightReport) {
    val mw = report.ephemeris.milkyWay
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_milky_way)) {
        if (mw == null) {
            Text(
                stringResource(R.string.milky_way_down),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                LabeledValue(stringResource(R.string.visible), Format.window(mw.window, zone), Modifier.weight(1.4f))
                LabeledValue(
                    stringResource(R.string.peak),
                    stringResource(R.string.peak_altitude_at, Format.degrees(mw.peakAltitudeDeg), Format.time(mw.peakTime, zone)),
                    Modifier.weight(1.4f),
                )
                LabeledValue(stringResource(R.string.direction), DigestComposer.compass(mw.peakAzimuthDeg), Modifier.weight(0.8f))
            }
            Spacer(Modifier.height(8.dp))
            val free = mw.moonFreeDuration
            Text(
                if (free == mw.window.duration) stringResource(R.string.moon_free_whole)
                else if (free.isZero) stringResource(R.string.moon_up_whole)
                else stringResource(R.string.moon_free_part, Format.duration(free), Format.duration(mw.window.duration)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BreakdownCard(report: NightReport) {
    val score = report.score ?: return
    SectionCard(stringResource(R.string.section_breakdown)) {
        score.factors.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    if (f.estimated) stringResource(R.string.factor_estimated, f.factor.label) else f.factor.label,
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
                stringResource(R.string.estimated_factors),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TimelineRow(
    val label: String,
    val value: (TimelineHour) -> String,
    val color: (TimelineHour) -> Color?,
    val emphasize: Boolean = false,
)

@Composable
private fun TimelineCard(report: NightReport) {
    val zone = report.site.zone
    val rows = listOf(
        TimelineRow(stringResource(R.string.timeline_score), { it.score.toString() }, { NightColors.forScore(it.score) }, emphasize = true),
        TimelineRow(stringResource(R.string.timeline_cloud), { h -> h.cloudCover?.let { "$it%" } ?: "–" }, { h -> h.cloudCover?.let { NightColors.forScore(100 - it) } }),
        TimelineRow(stringResource(R.string.timeline_moon), { h -> if (h.moonAltitudeDeg > 0) Format.degrees(h.moonAltitudeDeg) else "↓" }, { h ->
            if (h.moonAltitudeDeg > 0 && h.moonIllumination > 0.05) NightColors.Fair else null
        }),
        TimelineRow(stringResource(R.string.timeline_mw), { h -> if (h.galacticCenterAltitudeDeg > 0) Format.degrees(h.galacticCenterAltitudeDeg) else "↓" }, { h ->
            if (h.isDark && h.galacticCenterAltitudeDeg >= 10) NightColors.Primary else null
        }),
        TimelineRow(stringResource(R.string.timeline_seeing), { h -> h.seeingIndex?.let { "$it/8" } ?: "–" }, { h -> h.seeingIndex?.let { NightColors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow(stringResource(R.string.timeline_transp), { h -> h.transparencyIndex?.let { "$it/8" } ?: "–" }, { h -> h.transparencyIndex?.let { NightColors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow(stringResource(R.string.timeline_wind), { h -> h.windKmh?.roundToInt()?.toString() ?: "–" }, { h -> h.windKmh?.let { NightColors.forScore((100 - it * 2.5).roundToInt()) } }),
    )
    SectionCard(stringResource(R.string.section_timeline)) {
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
                                    fontWeight = if (row.emphasize) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.timeline_footnote),
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
                    stringResource(R.string.target_kind_window, s.target.kind.label, Format.window(s.window, zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.degrees(s.peakAltitudeDeg), style = MaterialTheme.typography.titleMedium, color = NightColors.Primary)
                Text(
                    stringResource(R.string.compass_at, DigestComposer.compass(s.peakAzimuthDeg), Format.time(s.bestTime, zone)),
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
                        stringResource(
                            R.string.exposure_rule,
                            ex.lens.name,
                            ex.body.name,
                            "%.0f".format(ex.npfSeconds),
                            "%.0f".format(ex.rule500Seconds),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!ex.reachesTarget) {
                        Text(
                            stringResource(R.string.needs_focal, s.target.minFocalMm),
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