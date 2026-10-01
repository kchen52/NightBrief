package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.gear.GearKit
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
    /** Minimum score lead before the digest mentions another site. */
    val alternativeThreshold: Int = SiteComparison.DEFAULT_THRESHOLD,
) {
    val digestLocalTime: LocalTime get() = LocalTime.parse(digestTime)
}
