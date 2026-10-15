package app.nightbrief.score

import app.nightbrief.astro.MeteorShowers
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * One stamp on the sky passport. [earnedOn] is the night that completed the rule, null when it is still locked.
 * Stamps are derived from the session log. They are not stored, and a missed night does not revoke one.
 */
data class StampStatus(
    val id: String,
    val name: String,
    val hint: String,
    val earnedOn: LocalDate?,
) {
    val earned: Boolean get() = earnedOn != null
}

/**
 * Local rewards derived from [SessionEntry]. No backend.
 *
 * Go is a score of at least 70 and No-go is below 50, the same lines as [Verdict].
 * A synodic month is [SYNODIC_MONTH_DAYS] civil days: a span of 29 nights counts and a span of 30 does not.
 * Peak nights come from [MeteorShowers] (the peak date and one day either side) and need no stored field.
 * [SessionEntry.bortle], [SessionEntry.issVisible], and [SessionEntry.overnightLowC] stay null when unknown,
 * and the stamp that needs that fact cannot be earned from the entry.
 */
object Passport {
    /** Mean synodic month, in civil days. */
    const val SYNODIC_MONTH_DAYS = 29.530588853

    private val chronological =
        compareBy<SessionEntry>({ it.date }, { it.createdAtEpochSec }, { it.siteId })

    private data class Rule(
        val id: String,
        val name: String,
        val hint: String,
        val earnedOn: (List<SessionEntry>) -> LocalDate?,
    )

    private val rules = listOf(
        Rule("first-go", "First Go", "Log a night that scored 70 or more") { sessions ->
            earliest(sessions) { entry ->
                val score = entry.score ?: return@earliest false
                Verdict.of(score) == Verdict.GO
            }
        },
        Rule("dark-sky", "Dark sky pilgrim", "Log a session at Bortle 1–3") { sessions ->
            earliest(sessions) { entry ->
                val bortle = entry.bortle ?: return@earliest false
                bortle in 1..3
            }
        },
        Rule("peak-hunter", "Peak hunter", "Log a session within a day of a shower peak") { sessions ->
            earliest(sessions) { onShowerPeak(it.date) }
        },
        Rule("station-spotter", "Station spotter", "Log a night with an ISS pass in the dark window") { sessions ->
            earliest(sessions) { it.issVisible == true }
        },
        Rule("frostbitten", "Frostbitten", "Log a night whose overnight low was below 0 °C") { sessions ->
            earliest(sessions) { entry ->
                val low = entry.overnightLowC ?: return@earliest false
                low < 0.0
            }
        },
        Rule("lunar-regular", "Lunar regular", "Log 5 sessions within one lunar cycle") { sessions ->
            lunarRegular(sessions)
        },
        Rule("night-owl", "Night owl", "Log 10 sessions") { sessions ->
            sessions.sortedWith(chronological).getOrNull(9)?.date
        },
        Rule("cloud-chaser", "Cloud chaser", "Rate a No-go night 4 stars or more") { sessions ->
            earliest(sessions) { entry ->
                val score = entry.score ?: return@earliest false
                entry.rating >= 4 && Verdict.of(score) == Verdict.NO_GO
            }
        },
        Rule("site-hopper", "Site hopper", "Log sessions at 3 different sites") { sessions ->
            siteHopper(sessions)
        },
    )

    fun evaluate(sessions: List<SessionEntry>): List<StampStatus> =
        rules.map { rule ->
            StampStatus(
                id = rule.id,
                name = rule.name,
                hint = rule.hint,
                earnedOn = rule.earnedOn(sessions),
            )
        }

    /** True when any annual shower peaks on [date] or the day before or after it. */
    fun onShowerPeak(date: LocalDate): Boolean =
        MeteorShowers.activeOn(date).any { shower -> abs(MeteorShowers.daysFromPeak(shower, date)) <= 1 }

    private fun earliest(sessions: List<SessionEntry>, predicate: (SessionEntry) -> Boolean): LocalDate? =
        sessions.filter(predicate).minWithOrNull(chronological)?.date

    private fun lunarRegular(sessions: List<SessionEntry>): LocalDate? {
        if (sessions.size < 5) return null
        val ordered = sessions.sortedWith(chronological)
        for (end in ordered.indices) {
            val endDate = ordered[end].date
            var count = 0
            for (start in 0..end) {
                val span = ChronoUnit.DAYS.between(ordered[start].date, endDate)
                if (span <= SYNODIC_MONTH_DAYS) count++
            }
            if (count >= 5) return endDate
        }
        return null
    }

    private fun siteHopper(sessions: List<SessionEntry>): LocalDate? {
        if (sessions.size < 3) return null
        val seen = HashSet<String>()
        for (entry in sessions.sortedWith(chronological)) {
            seen += entry.siteId
            if (seen.size >= 3) return entry.date
        }
        return null
    }
}
