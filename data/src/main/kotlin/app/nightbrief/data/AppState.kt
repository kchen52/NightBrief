package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.SessionEntry
import app.nightbrief.score.SiteComparison
import app.nightbrief.score.UnitSystem
import app.nightbrief.sites.SiteBook
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.util.Locale

@Serializable
data class AppState(
    val onboardingComplete: Boolean = false,
    /**
     * Epoch seconds when onboarding finished. Null on files written before this field,
     * which are not inside the first-session Big Night quiet window.
     */
    val onboardingCompletedAtEpochSec: Long? = null,
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
    /** Red-on-black theme for use at the telescope. Display only. */
    val nightVision: Boolean = false,
    /**
     * Wind and temperature display. Null follows the device locale
     * ([UnitSystem.fromLocale]) until the user picks one.
     */
    val units: UnitSystem? = null,
    /**
     * Stable notification slot per site id. Slots are positive and never reused, so one site's
     * digest cannot replace another's. Not part of a library export.
     */
    val notificationSlots: Map<String, Int> = emptyMap(),
    /** Next slot to assign. Stays ahead of every slot ever handed out, including deleted sites. */
    val notificationSlotNext: Int = 1,
    /**
     * Local shooting history. Oldest first is not guaranteed; UI sorts by night date.
     * Empty for older settings files.
     */
    val sessions: List<SessionEntry> = emptyList(),
    /** "siteId/yyyy-MM-dd" prompts the user dismissed. Never reused as a session key. */
    val dismissedSessionPrompts: Set<String> = emptySet(),
    /**
     * Passport stamp ids Tonight has already announced. Empty for older settings files,
     * so a stamp earned from existing sessions can show once.
     */
    val seenPassportStamps: Set<String> = emptySet(),
    /**
     * Show ISS passes on Tonight and the planner. Off until the user turns it on in Settings,
     * so an older settings file also starts without the card. Display only: the briefing still
     * carries the passes, and the digest, widget, and watch are not gated by this.
     */
    val issEnabled: Boolean = false,
    /**
     * Ids of Tonight cards the user has collapsed (`CardIds` in `:app`). One set for every site
     * and night. Unknown ids are harmless, so a card removed later leaves nothing to migrate.
     */
    val collapsedCards: Set<String> = emptySet(),
) {
    val digestLocalTime: LocalTime get() = LocalTime.parse(digestTime)

    fun resolvedUnits(locale: Locale = Locale.getDefault()): UnitSystem =
        units ?: UnitSystem.fromLocale(locale)

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
