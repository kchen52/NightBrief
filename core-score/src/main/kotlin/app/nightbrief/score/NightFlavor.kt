package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.weather.ForecastStatus

/**
 * One playful sentence for a scored night, or null when nothing on the report earns one.
 *
 * Cloud, dew, and frost wording is skipped when that input was estimated or the forecast
 * is a saved file, so a proxy or an old file is never stated as tonight's measurement.
 * Moon, meteors, the ISS, and the Milky Way window are computed on device and can still be named.
 * When a fact has more than one wording, the choice is stable for the same site id and night date.
 */
object NightFlavor {
    fun line(report: NightReport): String? {
        if (report.ephemeris.darkness == Darkness.NONE) return null
        clearsTreeline(report)?.let { return it }
        aurora(report)?.let { return it }
        meteor(report)?.let { return it }
        iss(report)?.let { return it }
        if (cloudMeasured(report)) {
            highThin(report)?.let { return it }
            overcast(report)?.let { return it }
            clearMoonless(report)?.let { return it }
        }
        if (tempsMeasured(report)) {
            frostOrDew(report)?.let { return it }
        }
        moon(report)?.let { return it }
        if (report.score != null && report.forecastStatus == ForecastStatus.STALE) {
            return pick(report, STALE)
        }
        if (report.score?.factors?.any { it.estimated } == true) {
            return pick(report, ESTIMATED)
        }
        return null
    }

    private fun clearsTreeline(report: NightReport): String? {
        val at = report.ephemeris.milkyWay?.clearsHorizonAt ?: return null
        return WidgetCopy.clearsTreeline(at, report.site.zone) + ". Fashionably late."
    }

    private fun aurora(report: NightReport): String? = when (report.aurora?.chance) {
        AuroraChance.LIKELY -> pick(report, AURORA_LIKELY)
        AuroraChance.POSSIBLE -> pick(report, AURORA_POSSIBLE)
        AuroraChance.UNLIKELY, null -> null
    }

    private fun meteor(report: NightReport): String? {
        val shower = report.meteor?.takeIf { it.worthWatching } ?: return null
        val name = shower.shower.name
        return pick(
            report,
            listOf(
                "The $name are visiting. Make a wish or a frame.",
                "The $name are dropping by tonight.",
            ),
        )
    }

    private fun iss(report: NightReport): String? {
        if (report.issPasses.isEmpty()) return null
        return pick(report, ISS)
    }

    private fun highThin(report: NightReport): String? {
        if (report.cloudReason != CloudReason.HIGH_THIN) return null
        return pick(report, VEIL)
    }

    private fun overcast(report: NightReport): String? {
        val cloud = report.score?.factor(Factor.CLOUD) ?: return null
        if (cloud.quality > OVERCAST_QUALITY) return null
        return pick(report, OVERCAST)
    }

    private fun clearMoonless(report: NightReport): String? {
        val score = report.score ?: return null
        if (score.verdict != Verdict.GO) return null
        if (score.factor(Factor.CLOUD).quality < CLEAR_QUALITY) return null
        if (report.ephemeris.darkness != Darkness.ASTRONOMICAL) return null
        if (!moonless(report.ephemeris)) return null
        return pick(report, CLEAR)
    }

    private fun frostOrDew(report: NightReport): String? {
        val dew = report.dew ?: return null
        return when {
            dew.frost -> pick(report, FROST)
            dew.risk -> pick(report, DEW)
            else -> null
        }
    }

    private fun moon(report: NightReport): String? {
        val eph = report.ephemeris
        if (eph.moonIllumination < BRIGHT_MOON) return null
        val dark = eph.darkWindow ?: return null
        val freeMinutes = eph.moonFreeDarkDuration.toMinutes()
        val darkMinutes = dark.duration.toMinutes()
        if (darkMinutes <= 0) return null
        if (freeMinutes * 10 > darkMinutes * 3) return null
        return pick(report, MOON)
    }

    private fun moonless(eph: NightEphemeris): Boolean {
        if (eph.moonIllumination < NightEphemeris.NEGLIGIBLE_MOON) return true
        val dark = eph.darkWindow ?: return false
        return eph.moonFreeDarkDuration >= dark.duration.minusMinutes(5)
    }

    /** True when total cloud was measured on this fetch, not proxied and not read from a saved file. */
    private fun cloudMeasured(report: NightReport): Boolean {
        if (report.forecastStatus == ForecastStatus.STALE) return false
        val cloud = report.score?.factor(Factor.CLOUD) ?: return false
        return !cloud.estimated
    }

    /** True when a dark hour actually reported temperature and dew point on this fetch. */
    private fun tempsMeasured(report: NightReport): Boolean {
        if (report.forecastStatus == ForecastStatus.STALE) return false
        val dew = report.dew ?: return false
        return dew.spreadKnown && dew.risk
    }

    private fun pick(report: NightReport, options: List<String>): String {
        val key = report.site.id + "|" + report.date
        return options[key.hashCode().mod(options.size)]
    }

    private const val OVERCAST_QUALITY = 0.25
    private const val CLEAR_QUALITY = 0.90
    private const val BRIGHT_MOON = 0.70

    private val OVERCAST = listOf(
        "Overcast. Even the owls are staying in.",
        "Overcast. Even the owls stayed home.",
    )
    private val CLEAR = listOf(
        "Clear, dark, and moonless. The universe is showing off.",
        "Clear, dark, and moonless. The universe is in a generous mood.",
    )
    private val MOON = listOf(
        "The Moon is hogging the stage tonight.",
        "The Moon is hogging the stage and will not share.",
    )
    private val VEIL = listOf(
        "Thin high cloud: the sky's wearing a veil.",
        "High thin cloud. The sky's wearing a veil.",
    )
    private val FROST = listOf(
        "Frost is likely. The grass will crunch.",
        "Frost is invited. Dress like you mean it.",
    )
    private val DEW = listOf(
        "Dew is settling in. A heater earns its place.",
        "Dew is likely. Lenses prefer a heater tonight.",
    )
    private val ISS = listOf(
        "The ISS is dropping by. Wave if you're out.",
        "A bright guest is crossing the sky. That's the ISS.",
    )
    private val AURORA_LIKELY = listOf(
        "Aurora is likely. The sky may throw its own show.",
        "Aurora is likely. Look toward the pole.",
    )
    private val AURORA_POSSIBLE = listOf(
        "Aurora is possible. Worth a glance toward the pole.",
        "The pole might glow. Aurora is possible.",
    )
    private val STALE = listOf(
        "This is the forecast we saved. The sky may have rewritten it.",
        "Numbers from the drawer. The sky may have changed its mind.",
    )
    private val ESTIMATED = listOf(
        "Some of this score is a stand-in, not a measurement.",
        "Part of the weather is a stand-in, not a measurement.",
    )
}
