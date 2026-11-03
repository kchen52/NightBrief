package app.nightbrief.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colours the night UI actually paints with. [Standard] is the blue night theme.
 * [NightVision] is deep red on black so a bright screen does not wipe out dark adaptation.
 * Every swatch has green and blue at zero. Score bands stay distinguishable by how bright the red is.
 */
data class NightPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val primary: Color,
    val amber: Color,
    val textMuted: Color,
    val excellent: Color,
    val good: Color,
    val fair: Color,
    val marginal: Color,
    val poor: Color,
    val onBackground: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
) {
    fun forScore(score: Int?): Color = when {
        score == null -> textMuted
        score >= 85 -> excellent
        score >= 70 -> good
        score >= 50 -> fair
        score >= 30 -> marginal
        else -> poor
    }

    fun colorScheme() = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = amber,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = surface,
        onSurfaceVariant = textMuted,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = outline,
        outlineVariant = outlineVariant,
        error = poor,
    )

    companion object {
        val Standard = NightPalette(
            background = Color(0xFF0B1020),
            surface = Color(0xFF141B2E),
            surfaceHigh = Color(0xFF1C2540),
            primary = Color(0xFF8AB4FF),
            amber = Color(0xFFFFC266),
            textMuted = Color(0xFF9AA4BF),
            excellent = Color(0xFF4ADE80),
            good = Color(0xFFA3E635),
            fair = Color(0xFFFBBF24),
            marginal = Color(0xFFFB923C),
            poor = Color(0xFFF87171),
            onBackground = Color(0xFFE8EEFF),
            onPrimary = Color(0xFF0A1A3A),
            primaryContainer = Color(0xFF243A6B),
            onPrimaryContainer = Color(0xFFDCE6FF),
            onSecondary = Color(0xFF2D1E00),
            secondaryContainer = Color(0xFF4A3510),
            onSecondaryContainer = Color(0xFFFFE2B3),
            outline = Color(0xFF3A4566),
            outlineVariant = Color(0xFF263052),
        )

        val NightVision = NightPalette(
            background = Color(0xFF000000),
            surface = Color(0xFF140000),
            surfaceHigh = Color(0xFF2A0000),
            primary = Color(0xFFFF0000),
            amber = Color(0xFFCC0000),
            textMuted = Color(0xFF990000),
            excellent = Color(0xFFFF0000),
            good = Color(0xFFDD0000),
            fair = Color(0xFFBB0000),
            marginal = Color(0xFF880000),
            poor = Color(0xFF550000),
            onBackground = Color(0xFFFF0000),
            onPrimary = Color(0xFF1A0000),
            primaryContainer = Color(0xFF3A0000),
            onPrimaryContainer = Color(0xFFFF0000),
            onSecondary = Color(0xFF1A0000),
            secondaryContainer = Color(0xFF2A0000),
            onSecondaryContainer = Color(0xFFFF0000),
            outline = Color(0xFF440000),
            outlineVariant = Color(0xFF220000),
        )
    }
}

val LocalNightPalette = staticCompositionLocalOf { NightPalette.Standard }

object NightColors {
    val palette: NightPalette
        @Composable get() = LocalNightPalette.current

    val Background: Color @Composable get() = palette.background
    val Surface: Color @Composable get() = palette.surface
    val SurfaceHigh: Color @Composable get() = palette.surfaceHigh
    val Primary: Color @Composable get() = palette.primary
    val Amber: Color @Composable get() = palette.amber
    val TextMuted: Color @Composable get() = palette.textMuted
    val Excellent: Color @Composable get() = palette.excellent
    val Good: Color @Composable get() = palette.good
    val Fair: Color @Composable get() = palette.fair
    val Marginal: Color @Composable get() = palette.marginal
    val Poor: Color @Composable get() = palette.poor

    @Composable
    fun forScore(score: Int?): Color = palette.forScore(score)
}

private val typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/**
 * Always dark: the app is used at night, often outdoors next to a camera.
 * [nightVision] swaps in the red-on-black palette. It does not change any score.
 */
@Composable
fun NightBriefTheme(nightVision: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (nightVision) NightPalette.NightVision else NightPalette.Standard
    CompositionLocalProvider(LocalNightPalette provides palette) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = typography, content = content)
    }
}
