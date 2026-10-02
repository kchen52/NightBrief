package app.nightbrief.app.ui.tonight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import app.nightbrief.app.ui.common.Banner
import app.nightbrief.app.ui.common.EmphasizedText
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.LabeledValue
import app.nightbrief.app.ui.common.ScoreGauge
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.common.emphasize
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPass
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.TargetKind
import app.nightbrief.score.AuroraChance
import app.nightbrief.score.AuroraCopy
import app.nightbrief.score.AuroraOutlook
import app.nightbrief.score.CloudReason
import app.nightbrief.score.DewCopy
import app.nightbrief.score.DewOutlook
import app.nightbrief.score.DigestComposer
import app.nightbrief.score.ForecastCoverage
import app.nightbrief.score.MeteorAdvisor
import app.nightbrief.score.MeteorOutlook
import app.nightbrief.score.NightReport
import app.nightbrief.score.NightSummary
import app.nightbrief.score.SavedForecast
import app.nightbrief.score.SiteAlternative
import app.nightbrief.score.SiteComparison
import app.nightbrief.score.TargetSuggestion
import app.nightbrief.score.TimelineHour
import app.nightbrief.score.UnitSystem
import app.nightbrief.score.Verdict
import app.nightbrief.score.WidgetCopy
import app.nightbrief.weather.ForecastStatus
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun NightDetail(
    report: NightReport,
    alternative: SiteAlternative?,
    onOpenAlternative: (String) -> Unit,
    modifier: Modifier = Modifier,
    units: UnitSystem = UnitSystem.METRIC,
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
        report.dew?.let { DewCard(it, report.site.zone, units) }
        report.aurora?.let { AuroraCard(it) }
        report.meteor?.takeIf { it.worthWatching }?.let { MeteorCard(it, report) }
        if (report.issPasses.isNotEmpty()) IssCard(report)
        MilkyWayCard(report)
        report.score?.let { BreakdownCard(report) }
        TimelineCard(report, units)
        if (report.suggestions.isNotEmpty()) {
            SectionCard(stringResource(R.string.section_targets), icon = Icons.Filled.FilterCenterFocus) {
                report.suggestions.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SuggestionRow(s, report)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun StaleForecastBanner(report: NightReport, modifier: Modifier = Modifier) {
    val savedAt = report.forecastFetchedAt
    Banner(
        if (savedAt != null) {
            stringResource(R.string.banner_stale, SavedForecast.clock(savedAt, report.site.zone))
        } else {
            stringResource(R.string.banner_stale_untimed)
        },
        NightColors.Fair,
        modifier,
    )
}

@Composable
private fun StatusBanners(report: NightReport) {
    if (report.forecastStatus == ForecastStatus.STALE) {
        StaleForecastBanner(report)
    }
    when {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = color,
                            )
                            Spacer(Modifier.width(6.dp))
                            EmphasizedText(
                                stringResource(R.string.best_window, Format.window(it, report.site.zone), score.bestWindowScore),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.no_score_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (score != null) {
            NightSummary.whyGood(score, report.ephemeris)?.let { why ->
                Spacer(Modifier.height(8.dp))
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SkyCard(report: NightReport) {
    val e = report.ephemeris
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_sky), icon = Icons.Filled.DarkMode) {
        Row(Modifier.fillMaxWidth()) {
            SkyMoment(Icons.Filled.WbTwilight, stringResource(R.string.sunset), Format.time(e.sunset, zone), Modifier.weight(1f))
            SkyMoment(Icons.Filled.WbSunny, stringResource(R.string.sunrise), Format.time(e.sunrise, zone), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.dark),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                emphasize(Format.window(e.darkWindow, zone)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        MoonLine(e, zone)
        if (e.darkness != Darkness.ASTRONOMICAL) {
            Spacer(Modifier.height(6.dp))
            Text(e.darkness.label, style = MaterialTheme.typography.bodyMedium, color = NightColors.Fair, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val free = e.moonFreeDarkDuration
            StatChip(
                icon = Icons.Filled.Bedtime,
                value = Format.duration(free),
                label = stringResource(R.string.moon_free_label),
                valueColor = if (free.isZero) NightColors.Fair else NightColors.Primary,
                modifier = Modifier.weight(1f),
            )
            val assumed = report.site.bortle == null
            StatChip(
                icon = Icons.Filled.Lightbulb,
                value = report.site.effectiveBortle.toString(),
                label = stringResource(if (assumed) R.string.bortle_assumed_label else R.string.bortle_label),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SkyMoment(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoonLine(e: NightEphemeris, zone: ZoneId) {
    val timing = moonTiming(e, zone)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Nightlight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = NightColors.Amber,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(e.moonPhaseName.label) }
                append("  ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Format.percent(e.moonIllumination)) }
                if (timing != null) {
                    append("  ·  ")
                    append(emphasize(timing))
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun moonTiming(e: NightEphemeris, zone: ZoneId): String? {
    val dark = e.darkWindow ?: return null
    if (e.moonIllumination < NightEphemeris.NEGLIGIBLE_MOON) return null
    val free = e.moonFreeDarkDuration
    val set = e.moonset
    val rise = e.moonrise
    return when {
        free >= dark.duration.minusMinutes(5) -> stringResource(R.string.moon_down)
        free.isZero -> stringResource(R.string.moon_up)
        set != null && set in dark -> stringResource(R.string.moon_sets, Format.time(set, zone))
        rise != null && rise in dark -> stringResource(R.string.moon_rises, Format.time(rise, zone))
        else -> null
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NightColors.Primary,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = valueColor)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DewCard(dew: DewOutlook, zone: ZoneId, units: UnitSystem) {
    val titleRes = when {
        dew.frostFrom != null -> R.string.section_frost
        dew.from != null -> R.string.section_dew
        else -> R.string.section_overnight
    }
    val icon = when {
        dew.frostFrom != null -> Icons.Filled.AcUnit
        dew.from != null -> Icons.Filled.WaterDrop
        else -> Icons.Filled.Thermostat
    }
    val color = if (dew.frostFrom != null) NightColors.Poor else NightColors.Amber
    SectionCard(stringResource(titleRes), icon = icon) {
        DewCopy.riskLine(dew) { Format.time(it, zone) }?.let { line ->
            EmphasizedText(
                line,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
        }
        EmphasizedText(
            DewCopy.lowLine(dew, units),
            style = if (dew.risk) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = if (dew.risk) FontWeight.Normal else FontWeight.SemiBold,
        )
        WhyBlock(stringResource(titleRes), DewCopy.detail(dew, units))
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
    val title = stringResource(R.string.section_aurora)
    SectionCard(title, icon = Icons.Filled.AutoAwesome) {
        EmphasizedText(
            AuroraCopy.digestLine(aurora),
            style = if (alert) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (alert) FontWeight.SemiBold else FontWeight.Normal,
        )
        WhyBlock(title, AuroraCopy.detail(aurora))
    }
}

@Composable
private fun MeteorCard(meteor: MeteorOutlook, report: NightReport) {
    val title = stringResource(R.string.section_meteors)
    SectionCard(title, icon = Icons.Filled.Stars) {
        EmphasizedText(MeteorAdvisor.digestLine(meteor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Explore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            EmphasizedText(
                stringResource(
                    R.string.meteor_radiant,
                    Format.azimuth(meteor.peakRadiantAzimuthDeg),
                    Format.time(meteor.peakRadiantTime, report.site.zone),
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WhyBlock(title, MeteorAdvisor.detail(meteor))
    }
}

/**
 * The long explanation starts hidden. [title] names the control for TalkBack
 * ("Show Dew details"); the visible label is just Why / Hide.
 */
@Composable
private fun WhyBlock(title: String, detail: String) {
    var open by remember(title) { mutableStateOf(false) }
    if (open) {
        Spacer(Modifier.height(6.dp))
        EmphasizedText(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val action = stringResource(if (open) R.string.hide_section_details else R.string.show_section_details, title)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(role = Role.Button) { open = !open },
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (open) R.string.hide else R.string.why),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = action,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssCard(report: NightReport) {
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_iss), icon = Icons.Filled.SatelliteAlt) {
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
    EmphasizedText(span, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    EmphasizedText(
        stringResource(
            R.string.iss_peak,
            Format.degrees(pass.peakAltitudeDeg),
            DigestComposer.compass(pass.peakAzimuthDeg),
            Format.time(pass.peak, zone),
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MilkyWayCard(report: NightReport) {
    val mw = report.ephemeris.milkyWay
    val zone = report.site.zone
    SectionCard(stringResource(R.string.section_milky_way), icon = Icons.Filled.NightsStay) {
        if (mw == null) {
            Text(
                if (report.ephemeris.milkyWayBlockedByHorizon) {
                    WidgetCopy.BEHIND_TREELINE
                } else {
                    stringResource(R.string.milky_way_down)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                LabeledValue(stringResource(R.string.visible), Format.window(mw.window, zone), Modifier.weight(1.5f))
                LabeledValue(
                    stringResource(R.string.peak),
                    stringResource(R.string.peak_altitude_at, Format.degrees(mw.peakAltitudeDeg), Format.time(mw.peakTime, zone)),
                    Modifier.weight(1.3f),
                )
                LabeledValue(stringResource(R.string.direction), DigestComposer.compass(mw.peakAzimuthDeg), Modifier.weight(0.7f))
            }
            mw.clearsHorizonAt?.let { clears ->
                Spacer(Modifier.height(8.dp))
                Text(
                    WidgetCopy.clearsTreeline(clears, zone),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            val free = mw.moonFreeDuration
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                EmphasizedText(
                    if (free == mw.window.duration) stringResource(R.string.moon_free_whole)
                    else if (free.isZero) stringResource(R.string.moon_up_whole)
                    else stringResource(R.string.moon_free_part, Format.duration(free), Format.duration(mw.window.duration)),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BreakdownCard(report: NightReport) {
    val score = report.score ?: return
    SectionCard(stringResource(R.string.section_breakdown), icon = Icons.Filled.BarChart) {
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
                    fontWeight = FontWeight.Bold,
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
private fun TimelineCard(report: NightReport, units: UnitSystem) {
    val zone = report.site.zone
    val colors = NightColors.palette
    fun layer(percent: Int?): String = percent?.let { "$it%" } ?: "–"
    fun layerColor(percent: Int?): Color? = percent?.let { colors.forScore(100 - it) }
    val rows = listOf(
        TimelineRow(stringResource(R.string.timeline_score), { it.score.toString() }, { colors.forScore(it.score) }, emphasize = true),
        TimelineRow(stringResource(R.string.timeline_cloud), { h -> layer(h.cloudCover) }, { h -> layerColor(h.cloudCover) }),
        TimelineRow(stringResource(R.string.timeline_cloud_low), { h -> layer(h.cloudLow) }, { h -> layerColor(h.cloudLow) }),
        TimelineRow(stringResource(R.string.timeline_cloud_mid), { h -> layer(h.cloudMid) }, { h -> layerColor(h.cloudMid) }),
        TimelineRow(stringResource(R.string.timeline_cloud_high), { h -> layer(h.cloudHigh) }, { h -> layerColor(h.cloudHigh) }),
        TimelineRow(stringResource(R.string.timeline_moon), { h -> if (h.moonAltitudeDeg > 0) Format.degrees(h.moonAltitudeDeg) else "↓" }, { h ->
            if (h.moonAltitudeDeg > 0 && h.moonIllumination > 0.05) colors.fair else null
        }),
        TimelineRow(stringResource(R.string.timeline_mw), { h -> if (h.galacticCenterAltitudeDeg > 0) Format.degrees(h.galacticCenterAltitudeDeg) else "↓" }, { h ->
            if (h.isDark && h.galacticCenterAltitudeDeg >= 10) colors.primary else null
        }),
        TimelineRow(stringResource(R.string.timeline_seeing), { h -> h.seeingIndex?.let { "$it/8" } ?: "–" }, { h -> h.seeingIndex?.let { colors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow(stringResource(R.string.timeline_transp), { h -> h.transparencyIndex?.let { "$it/8" } ?: "–" }, { h -> h.transparencyIndex?.let { colors.forScore(((8 - it) * 100) / 7) } }),
        TimelineRow(
            stringResource(R.string.timeline_wind),
            { h -> h.windKmh?.let(units::formatWind) ?: "–" },
            // Colour stays on km/h so a unit change does not repaint the same wind.
            { h -> h.windKmh?.let { colors.forScore((100 - it * 2.5).roundToInt()) } },
        ),
    )
    SectionCard(stringResource(R.string.section_timeline), icon = Icons.Filled.Timeline) {
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
        if (report.cloudReason == CloudReason.HIGH_THIN) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp), tint = NightColors.Fair)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.cloud_high_thin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightColors.Fair,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EmphasizedText(
            stringResource(R.string.timeline_footnote, units.windUnit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Name, window, and peak stay visible. Reason, exposure, and the tip start hidden. */
@Composable
private fun SuggestionRow(s: TargetSuggestion, report: NightReport) {
    val zone = report.site.zone
    var expanded by rememberSaveable(s.target.id) { mutableStateOf(false) }
    val action = stringResource(
        if (expanded) R.string.hide_target_details else R.string.show_target_details,
        s.target.name,
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = action) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                kindIcon(s.target.kind),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = NightColors.Primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.target.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.target_kind_window, s.target.kind.label, Format.window(s.window, zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Format.degrees(s.peakAltitudeDeg),
                    style = MaterialTheme.typography.titleMedium,
                    color = NightColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.compass_at, DigestComposer.compass(s.peakAzimuthDeg), Format.time(s.bestTime, zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            EmphasizedText(s.reason)
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
                        Text(ex.summary, style = MaterialTheme.typography.labelLarge, color = NightColors.Amber, fontWeight = FontWeight.Bold)
                        EmphasizedText(
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
                            EmphasizedText(
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
}

private fun kindIcon(kind: TargetKind): ImageVector = when (kind) {
    TargetKind.MILKY_WAY -> Icons.Filled.NightsStay
    TargetKind.GALAXY -> Icons.Filled.Public
    TargetKind.EMISSION_NEBULA -> Icons.Filled.Cloud
    TargetKind.REFLECTION_NEBULA -> Icons.Filled.FilterDrama
    TargetKind.STAR_CLUSTER -> Icons.Filled.Stars
    TargetKind.CONSTELLATION -> Icons.Filled.AutoAwesome
    TargetKind.MOON -> Icons.Filled.Nightlight
    TargetKind.MOONLIT -> Icons.Filled.Landscape
}
