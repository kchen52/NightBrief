package app.nightbrief.app.ui.tonight.whimsy

import androidx.compose.ui.graphics.Color
import app.nightbrief.app.ui.theme.NightPalette
import app.nightbrief.score.SkyTwilight

internal data class SkyPaint(
    val top: Color,
    val bottom: Color,
    val star: Color,
    val milkyWay: Color,
    val moonLight: Color,
    val moonShadow: Color,
)

internal data class OwlPaint(
    val body: Color,
    val belly: Color,
    val ink: Color,
    val eye: Color,
    val pupil: Color,
    val beak: Color,
    val telescope: Color,
    val lens: Color,
    val cloud: Color,
    val blanket: Color,
    val zzz: Color,
    val scarf: Color,
    val umbrella: Color,
    val aurora: Color,
)

/**
 * Night-vision swatches are taken from [NightPalette.NightVision], which is red on black.
 * Standard twilight may use blue; the aurora ripple is green only on that palette.
 */
internal fun skyPaint(palette: NightPalette, twilight: SkyTwilight): SkyPaint {
    if (palette === NightPalette.NightVision) {
        val (top, bottom) = when (twilight) {
            SkyTwilight.DAY -> palette.primaryContainer to palette.surfaceHigh
            SkyTwilight.CIVIL -> palette.outline to palette.primaryContainer
            SkyTwilight.NAUTICAL -> palette.surfaceHigh to palette.surface
            SkyTwilight.ASTRO -> palette.surface to palette.background
        }
        return SkyPaint(
            top = top,
            bottom = bottom,
            star = palette.onBackground,
            milkyWay = palette.good,
            moonLight = palette.onBackground,
            moonShadow = palette.surfaceHigh,
        )
    }
    val (top, bottom) = when (twilight) {
        SkyTwilight.DAY -> Color(0xFF2E4C73) to Color(0xFF1A3358)
        SkyTwilight.CIVIL -> Color(0xFF2C2040) to Color(0xFF4A3038)
        SkyTwilight.NAUTICAL -> Color(0xFF141A30) to Color(0xFF1E2C4A)
        SkyTwilight.ASTRO -> Color(0xFF080C16) to Color(0xFF141A2C)
    }
    return SkyPaint(
        top = top,
        bottom = bottom,
        star = Color(0xFFF7F4EA),
        milkyWay = Color(0xFFC9D6FF),
        moonLight = Color(0xFFF4F0E4),
        moonShadow = Color(0xFF2C3348),
    )
}

internal fun owlPaint(palette: NightPalette): OwlPaint {
    if (palette === NightPalette.NightVision) {
        return OwlPaint(
            body = palette.primaryContainer,
            belly = palette.outline,
            ink = palette.onPrimary,
            eye = palette.onBackground,
            pupil = palette.onPrimary,
            beak = palette.amber,
            telescope = palette.outline,
            lens = palette.good,
            cloud = palette.textMuted,
            blanket = palette.surfaceHigh,
            zzz = palette.excellent,
            scarf = palette.primary,
            umbrella = palette.marginal,
            aurora = palette.excellent,
        )
    }
    return OwlPaint(
        body = Color(0xFFC4A574),
        belly = Color(0xFFF3E2C8),
        ink = Color(0xFF2A2118),
        eye = Color(0xFFFFF8EE),
        pupil = Color(0xFF1C140E),
        beak = Color(0xFFE39A34),
        telescope = Color(0xFF3E4A5C),
        lens = Color(0xFF8EB4D4),
        cloud = Color(0xFFD5DCEB),
        blanket = Color(0xFF2A3A62),
        zzz = Color(0xFF8AB4FF),
        scarf = Color(0xFFD24B4B),
        umbrella = Color(0xFF3C7A9A),
        aurora = Color(0xFF4ADE80),
    )
}

internal fun starAlpha(twilight: SkyTwilight): Float = when (twilight) {
    SkyTwilight.DAY -> 0f
    SkyTwilight.CIVIL -> 0.28f
    SkyTwilight.NAUTICAL -> 0.6f
    SkyTwilight.ASTRO -> 0.9f
}
