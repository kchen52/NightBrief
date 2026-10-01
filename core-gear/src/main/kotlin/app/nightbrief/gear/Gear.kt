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
) {
    init {
        require(minFocalMm > 0 && maxFocalMm >= minFocalMm) { "invalid focal range" }
        require(maxAperture > 0 && maxApertureAtLongEnd >= maxAperture) { "invalid aperture" }
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

/** The user's kit: any number of bodies and lenses, with one body used for suggestions. */
@Serializable
data class GearKit(
    val bodies: List<CameraBody> = emptyList(),
    val lenses: List<Lens> = emptyList(),
    val primaryBodyId: String? = null,
) {
    val primaryBody: CameraBody? get() = bodies.firstOrNull { it.id == primaryBodyId } ?: bodies.firstOrNull()
    val isEmpty: Boolean get() = bodies.isEmpty() || lenses.isEmpty()

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
}

internal fun Double.fmt(): String =
    if (this == kotlin.math.floor(this)) toInt().toString() else "%.1f".format(java.util.Locale.ROOT, this)
