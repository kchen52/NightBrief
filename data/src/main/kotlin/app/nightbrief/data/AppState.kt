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
) {
    val digestLocalTime: LocalTime get() = LocalTime.parse(digestTime)
}
