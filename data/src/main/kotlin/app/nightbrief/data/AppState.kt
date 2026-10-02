package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.SiteComparison
import app.nightbrief.sites.SiteBook
import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
data class AppState(
    val onboardingComplete: Boolean = false,
    val sites: SiteBook = SiteBook(),
    val gear: GearKit = GearCatalog.exampleKit,
    /** Global digest time for the primary site ("HH:mm"). */
    val digestTime: String = "08:00",
    val digestEnabled: Boolean = true,
    /** Post a notification when a site's tonight score reaches [bigNightThreshold]. */
    val bigNightAlertsEnabled: Boolean = true,
    /** Score that counts as a Big Night. Default is Excellent (85); the settings slider is 70–95. */
    val bigNightThreshold: Int = BigNightAlerts.threshold,
    /** Site id -> ISO local night date (yyyy-MM-dd) already alerted. */
    val lastBigNightAlerts: Map<String, String> = emptyMap(),
    /** Minimum score lead before the digest mentions another site. */
    val alternativeThreshold: Int = SiteComparison.DEFAULT_THRESHOLD,
    /**
     * Stable notification slot per site id. Slots are positive and never reused, so one site's
     * digest cannot replace another's. Not part of a library export.
     */
    val notificationSlots: Map<String, Int> = emptyMap(),
    /** Next slot to assign. Stays ahead of every slot ever handed out, including deleted sites. */
    val notificationSlotNext: Int = 1,
) {
    val digestLocalTime: LocalTime get() = LocalTime.parse(digestTime)

    /** Gives every current site a unique positive slot without reusing a retired one. */
    fun ensureNotificationSlots(): AppState {
        val assigned = LinkedHashMap<String, Int>()
        val used = HashSet<Int>()
        for (site in sites.sites) {
            val existing = notificationSlots[site.id]
            if (existing != null && existing > 0 && existing !in used) {
                assigned[site.id] = existing
                used += existing
            }
        }
        var next = notificationSlotNext.coerceAtLeast(1)
        for (site in sites.sites) {
            if (site.id in assigned) continue
            while (next in used) next++
            assigned[site.id] = next
            used += next
            next++
        }
        val highWater = maxOf(notificationSlotNext.coerceAtLeast(1), next)
        if (assigned == notificationSlots && highWater == notificationSlotNext) return this
        return copy(notificationSlots = assigned, notificationSlotNext = highWater)
    }
}
