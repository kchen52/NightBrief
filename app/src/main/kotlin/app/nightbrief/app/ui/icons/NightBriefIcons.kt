package app.nightbrief.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCalendar
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCamera
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorChartBar
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorChartLine
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCloud
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCloudFog
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCompass
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorCrosshair
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorDrop
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorMapPin
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorMoon
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorMoonStars
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorMountains
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorPlanet
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorShootingStar
import app.nightbrief.app.ui.icons.vendor.phosphor.PhosphorSparkle
import app.nightbrief.app.ui.icons.vendor.tabler.TablerArrowLeft
import app.nightbrief.app.ui.icons.vendor.tabler.TablerBulb
import app.nightbrief.app.ui.icons.vendor.tabler.TablerChevronDown
import app.nightbrief.app.ui.icons.vendor.tabler.TablerChevronLeft
import app.nightbrief.app.ui.icons.vendor.tabler.TablerChevronRight
import app.nightbrief.app.ui.icons.vendor.tabler.TablerChevronUp
import app.nightbrief.app.ui.icons.vendor.tabler.TablerClock
import app.nightbrief.app.ui.icons.vendor.tabler.TablerCloud
import app.nightbrief.app.ui.icons.vendor.tabler.TablerCompass
import app.nightbrief.app.ui.icons.vendor.tabler.TablerCurrentLocation
import app.nightbrief.app.ui.icons.vendor.tabler.TablerEye
import app.nightbrief.app.ui.icons.vendor.tabler.TablerGalaxy
import app.nightbrief.app.ui.icons.vendor.tabler.TablerMap
import app.nightbrief.app.ui.icons.vendor.tabler.TablerMoon
import app.nightbrief.app.ui.icons.vendor.tabler.TablerPencil
import app.nightbrief.app.ui.icons.vendor.tabler.TablerPlus
import app.nightbrief.app.ui.icons.vendor.tabler.TablerRefresh
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSatellite
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSearch
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSettings
import app.nightbrief.app.ui.icons.vendor.tabler.TablerShare
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSnowflake
import app.nightbrief.app.ui.icons.vendor.tabler.TablerStar
import app.nightbrief.app.ui.icons.vendor.tabler.TablerStarFilled
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSunrise
import app.nightbrief.app.ui.icons.vendor.tabler.TablerSunset2
import app.nightbrief.app.ui.icons.vendor.tabler.TablerTemperature
import app.nightbrief.app.ui.icons.vendor.tabler.TablerTrash
import app.nightbrief.app.ui.icons.vendor.tabler.TablerWorld
import app.nightbrief.app.ui.icons.vendor.tabler.TablerX

/**
 * NightBrief icon set: Phosphor Duotone for tabs, section headers, and target kinds;
 * Tabler outline (plus a filled star) for chrome and inline actions.
 *
 * ISS and Milky Way section headers use Tabler — Phosphor has no satellite/galaxy glyph.
 */
object NightBriefIcons {
    // —— Tabs / brand (Phosphor Duotone) ——
    val Tonight: ImageVector get() = PhosphorMoonStars
    val Week: ImageVector get() = PhosphorCalendar
    val Sites: ImageVector get() = PhosphorMapPin
    val Gear: ImageVector get() = PhosphorCamera

    // —— Section headers (Phosphor Duotone, Tabler where Phosphor lacks a match) ——
    val Sky: ImageVector get() = PhosphorMoon
    val Targets: ImageVector get() = PhosphorCrosshair
    val Aurora: ImageVector get() = PhosphorSparkle
    val Meteors: ImageVector get() = PhosphorShootingStar
    val Comet: ImageVector get() = PhosphorPlanet
    val Iss: ImageVector get() = TablerSatellite
    val MilkyWay: ImageVector get() = TablerGalaxy
    val Breakdown: ImageVector get() = PhosphorChartBar
    val Timeline: ImageVector get() = PhosphorChartLine
    val Smoke: ImageVector get() = PhosphorCloudFog
    val Dew: ImageVector get() = PhosphorDrop
    val Explore: ImageVector get() = PhosphorCompass
    val Confidence: ImageVector get() = PhosphorCloud

    // —— Target kinds ——
    val TargetMilkyWay: ImageVector get() = TablerGalaxy
    val TargetGalaxy: ImageVector get() = TablerWorld
    val TargetEmissionNebula: ImageVector get() = PhosphorCloud
    val TargetReflectionNebula: ImageVector get() = PhosphorCloudFog
    val TargetStarCluster: ImageVector get() = PhosphorShootingStar
    val TargetConstellation: ImageVector get() = PhosphorSparkle
    val TargetMoon: ImageVector get() = PhosphorMoon
    val TargetMoonlit: ImageVector get() = PhosphorMountains

    // —— Chrome / actions (Tabler) ——
    val Back: ImageVector get() = TablerArrowLeft
    val Previous: ImageVector get() = TablerChevronLeft
    val Next: ImageVector get() = TablerChevronRight
    val ExpandMore: ImageVector get() = TablerChevronDown
    val ExpandLess: ImageVector get() = TablerChevronUp
    val MoveUp: ImageVector get() = TablerChevronUp
    val MoveDown: ImageVector get() = TablerChevronDown
    val Add: ImageVector get() = TablerPlus
    val Delete: ImageVector get() = TablerTrash
    val Edit: ImageVector get() = TablerPencil
    val Close: ImageVector get() = TablerX
    val Search: ImageVector get() = TablerSearch
    val Settings: ImageVector get() = TablerSettings
    val Refresh: ImageVector get() = TablerRefresh
    val Share: ImageVector get() = TablerShare
    val Visibility: ImageVector get() = TablerEye
    val Star: ImageVector get() = TablerStarFilled
    val StarOutline: ImageVector get() = TablerStar
    val Map: ImageVector get() = TablerMap
    val MyLocation: ImageVector get() = TablerCurrentLocation
    val Schedule: ImageVector get() = TablerClock
    val Lightbulb: ImageVector get() = TablerBulb
    val Sunset: ImageVector get() = TablerSunset2
    val Sunrise: ImageVector get() = TablerSunrise
    val Moon: ImageVector get() = TablerMoon
    val Frost: ImageVector get() = TablerSnowflake
    val Thermostat: ImageVector get() = TablerTemperature
    val Compass: ImageVector get() = TablerCompass
    val InlineCloud: ImageVector get() = TablerCloud
}
