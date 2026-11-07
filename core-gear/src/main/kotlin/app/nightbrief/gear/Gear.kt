package app.nightbrief.gear

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.sqrt

@Serializable
data class CameraBody(
    val id: String,
    val name: String,
    val sensorWidthMm: Double,
    val sensorHeightMm: Double,
    val megapixels: Double,
    /** Personal name. Null or blank leaves the exposure line in its technical form. */
    val nickname: String? = null,
) {
    init {
        require(sensorWidthMm > 0 && sensorHeightMm > 0 && megapixels > 0) { "invalid sensor spec" }
    }

    /** Crop factor relative to a 36×24 mm full-frame diagonal. */
    val cropFactor: Double get() = FULL_FRAME_DIAGONAL / hypot(sensorWidthMm, sensorHeightMm)

    /** Pixel pitch in micrometres, assuming square pixels. */
    val pixelPitchUm: Double
        get() {
            val pixelsAcross = sqrt(megapixels * 1e6 * sensorWidthMm / sensorHeightMm)
            return sensorWidthMm * 1000.0 / pixelsAcross
        }

    val format: SensorFormat get() = SensorFormat.fromCrop(cropFactor)

    companion object {
        const val FULL_FRAME_DIAGONAL = 43.267
    }
}

enum class SensorFormat(val label: String) {
    FULL_FRAME("Full frame"),
    APS_C("APS-C"),
    MICRO_FOUR_THIRDS("Micro Four Thirds"),
    OTHER("Other");

    companion object {
        fun fromCrop(crop: Double): SensorFormat = when {
            crop < 1.2 -> FULL_FRAME
            crop < 1.75 -> APS_C
            crop < 2.2 -> MICRO_FOUR_THIRDS
            else -> OTHER
        }
    }
}

@Serializable
data class Lens(
    val id: String,
    val name: String,
    val minFocalMm: Double,
    val maxFocalMm: Double = minFocalMm,
    /** Widest aperture (f-number) at [minFocalMm]. */
    val maxAperture: Double,
    /** Widest aperture at [maxFocalMm] for variable-aperture zooms; equals [maxAperture] otherwise. */
    val maxApertureAtLongEnd: Double = maxAperture,
    /** Personal name. Null or blank leaves the exposure line in its technical form. */
    val nickname: String? = null,
    /**
     * Crop factor this lens is designed to cover. Null means unknown, so it is not treated as a
     * mismatch. A body with a smaller crop factor (a larger sensor) than this is a format mismatch.
     */
    val designedForCrop: Double? = null,
) {
    init {
        require(minFocalMm > 0 && maxFocalMm >= minFocalMm) { "invalid focal range" }
        require(maxAperture > 0 && maxApertureAtLongEnd >= maxAperture) { "invalid aperture" }
        require(designedForCrop == null || designedForCrop > 0) { "invalid designed crop" }
    }

    val isZoom: Boolean get() = maxFocalMm > minFocalMm

    fun covers(focalMm: Double): Boolean = focalMm in minFocalMm..maxFocalMm

    /** Widest aperture available at [focalMm], interpolated linearly for variable-aperture zooms. */
    fun maxApertureAt(focalMm: Double): Double {
        if (!isZoom) return maxAperture
        val f = focalMm.coerceIn(minFocalMm, maxFocalMm)
        val t = (f - minFocalMm) / (maxFocalMm - minFocalMm)
        return maxAperture + t * (maxApertureAtLongEnd - maxAperture)
    }

    val focalLabel: String
        get() = if (isZoom) "${minFocalMm.fmt()}–${maxFocalMm.fmt()}mm" else "${minFocalMm.fmt()}mm"

    val apertureLabel: String
        get() = if (maxApertureAtLongEnd > maxAperture) "f/${maxAperture.fmt()}–${maxApertureAtLongEnd.fmt()}"
        else "f/${maxAperture.fmt()}"
}

/**
 * Optional star tracker. Lets subs run past the 30 s untracked cap.
 *
 * [maxFocalMm] is the longest full-frame focal length the planner will set on a tracked target.
 * A deep-sky target whose minimum focal length is longer is not offered. Star trails are never
 * offered while a tracker is in the kit. The default keeps a kit saved before this field as a
 * portable mount; a non-positive value means the same.
 */
@Serializable
data class StarTracker(
    val id: String,
    val name: String,
    val maxFocalMm: Int = DEFAULT_FOCAL_MM,
) {
    init {
        require(id.isNotBlank()) { "tracker id required" }
        require(name.isNotBlank()) { "tracker name required" }
    }

    /** Focal length [maxFocalMm] after substituting [DEFAULT_FOCAL_MM] for a missing or invalid value. */
    val planningFocalMm: Int get() = maxFocalMm.takeIf { it > 0 } ?: DEFAULT_FOCAL_MM

    companion object {
        const val DEFAULT_FOCAL_MM = 300
    }
}

/** Filter family. Only dual-band changes which targets are offered; the rest only annotate the exposure. */
@Serializable
enum class FilterKind(val label: String) {
    DUAL_BAND("Dual-band"),
    LIGHT_POLLUTION("Light pollution"),
    DIFFUSION("Diffusion"),
}

/** Screw-in or clip-in filter in the kit. */
@Serializable
data class GearFilter(
    val id: String,
    val name: String,
    val kind: FilterKind,
) {
    init {
        require(id.isNotBlank()) { "filter id required" }
        require(name.isNotBlank()) { "filter name required" }
    }
}

/**
 * Coarse kinds of subject a user can ask for. `:core-score` maps each to catalog target kinds;
 * this lives here, with the kit, because `:core-gear` cannot see the target catalog.
 * Serialized by name, so never rename one.
 */
@Serializable
enum class TargetInterest(val label: String) {
    NEBULAE("Nebulae"),
    GALAXIES("Galaxies"),
    CLUSTERS("Star clusters"),
    MILKY_WAY("Milky Way"),
    CONSTELLATIONS("Constellations"),
    MOON("Moon & moonlit landscapes"),
}

/** The user's kit: any number of bodies and lenses, with one body used for suggestions. */
@Serializable
data class GearKit(
    val bodies: List<CameraBody> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val primaryBodyId: String? = null,
    /** Optional star tracker. Null means every exposure is untracked and capped at 30 s. */
    val tracker: StarTracker? = null,
    /** Optional screw-in / clip-in filters. Empty means no filter. */
    val filters: List<GearFilter> = emptyList(),
    /**
     * What the user wants to shoot. Empty means no preference: every kind is suggested. Kept on the
     * kit so Tonight, the digest, the widget and the watch, which all take a kit, agree.
     */
    val interests: Set<TargetInterest> = emptySet(),
) {
    val primaryBody: CameraBody? get() = bodies.firstOrNull { it.id == primaryBodyId } ?: bodies.firstOrNull()
    val isEmpty: Boolean get() = bodies.isEmpty() || lenses.isEmpty()
    /** True when a tracker is in the kit, so subs can run past the untracked 30 s cap. */
    val isTracked: Boolean get() = tracker != null
    /** True when the kit holds a dual-band filter, which helps emission nebulae under Moon and city glow. */
    val hasDualBandFilter: Boolean get() = filters.any { it.kind == FilterKind.DUAL_BAND }
    /** True when the kit holds a broad light-pollution filter. */
    val hasLightPollutionFilter: Boolean get() = filters.any { it.kind == FilterKind.LIGHT_POLLUTION }

    fun addBody(body: CameraBody) = copy(
        bodies = bodies.filterNot { it.id == body.id } + body,
        primaryBodyId = primaryBodyId ?: body.id,
    )

    fun removeBody(id: String): GearKit {
        val remaining = bodies.filterNot { it.id == id }
        return copy(
            bodies = remaining,
            primaryBodyId = if (primaryBodyId == id) remaining.firstOrNull()?.id else primaryBodyId,
        )
    }

    fun addLens(lens: Lens) = copy(lenses = lenses.filterNot { it.id == lens.id } + lens)
    fun removeLens(id: String) = copy(lenses = lenses.filterNot { it.id == id })

    fun withTracker(tracker: StarTracker?) = copy(tracker = tracker)
    fun addFilter(filter: GearFilter) = copy(filters = filters.filterNot { it.id == filter.id } + filter)
    fun removeFilter(id: String) = copy(filters = filters.filterNot { it.id == id })
    fun withInterests(interests: Set<TargetInterest>) = copy(interests = interests)
}

internal fun Double.fmt(): String =
    if (this == kotlin.math.floor(this)) toInt().toString() else "%.1f".format(java.util.Locale.ROOT, this)
