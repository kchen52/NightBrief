package app.nightbrief.score

import app.nightbrief.astro.MoonPhase
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEventKind
import app.nightbrief.astro.SkyEvents
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * A short title for a special night, or null for an ordinary one.
 *
 * Eclipse and opposition titles come from [SkyEvents] and win over a shower, a full moon,
 * and a Big Night. A shower name needs the shower within a day of peak and [MeteorOutlook.worthWatching].
 * "Ghost Moon" needs a full moon and measured clear cloud: an estimated cloud factor is not called clear.
 * "The Big One" uses the caller's Big Night threshold, clamped the same way as [BigNightAlerts].
 */
object NightNames {
    const val NEAR_PEAK_DAYS = 1
    private const val CLEAR_CLOUD = 0.90

    fun title(report: NightReport, bigNightThreshold: Int): String? = title(
        date = report.date,
        score = report.scoreValue,
        fullMoon = report.ephemeris.moonPhaseName == MoonPhase.FULL,
        clearSkies = clearSkies(report),
        showerLabel = showerLabel(report.meteor),
        skyEventTitle = eventTitle(SkyEvents.between(report.date, report.date, report.site.zone)),
        bigNightThreshold = bigNightThreshold,
    )

    fun title(night: OutlookNight, bigNightThreshold: Int): String? = title(
        date = night.date,
        score = night.score,
        fullMoon = night.fullMoon,
        clearSkies = night.clearSkies,
        showerLabel = night.showerLabel,
        skyEventTitle = night.skyEventTitle,
        bigNightThreshold = bigNightThreshold,
    )

    fun title(
        date: LocalDate,
        score: Int?,
        fullMoon: Boolean,
        clearSkies: Boolean,
        showerLabel: String?,
        skyEventTitle: String?,
        bigNightThreshold: Int,
    ): String? {
        if (!skyEventTitle.isNullOrBlank()) return skyEventTitle
        if (!showerLabel.isNullOrBlank()) return "$showerLabel ${dayName(date)}"
        if (fullMoon && clearSkies) return "Ghost Moon ${dayName(date)}"
        if (score != null && score >= BigNightAlerts.clamp(bigNightThreshold)) return "The Big One"
        return null
    }

    /** True when cloud cover was measured and the night is clear. A proxy stays false. */
    fun clearSkies(report: NightReport): Boolean {
        val cloud = report.score?.factor(Factor.CLOUD) ?: return false
        return !cloud.estimated && cloud.quality >= CLEAR_CLOUD
    }

    /**
     * Shower adjective, such as "Perseid", when [meteor] is within [NEAR_PEAK_DAYS] of peak
     * and worth watching. Null otherwise.
     */
    fun showerLabel(meteor: MeteorOutlook?): String? {
        val outlook = meteor ?: return null
        if (!outlook.worthWatching) return null
        if (abs(outlook.daysFromPeak) > NEAR_PEAK_DAYS) return null
        return adjective(outlook.shower.name)
    }

    /** Eclipse title if [events] has one, otherwise an opposition title. Other kinds are ignored. */
    fun eventTitle(events: List<SkyEvent>): String? {
        events.firstOrNull { it.kind == SkyEventKind.LUNAR_ECLIPSE || it.kind == SkyEventKind.SOLAR_ECLIPSE }
            ?.let { return it.title }
        return events.firstOrNull { it.kind == SkyEventKind.OPPOSITION }?.title
    }

    /** "Perseids" becomes "Perseid". A name that does not end in "ids" is kept. */
    fun adjective(name: String): String = if (name.endsWith("ids")) name.dropLast(1) else name

    private fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ROOT)
}
