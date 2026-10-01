package app.nightbrief.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object NightColors {
    val Background = Color(0xFF0B1020)
    val Surface = Color(0xFF141B2E)
    val SurfaceHigh = Color(0xFF1C2540)
    val Primary = Color(0xFF8AB4FF)
    val Amber = Color(0xFFFFC266)
    val TextMuted = Color(0xFF9AA4BF)

    val Excellent = Color(0xFF4ADE80)
    val Good = Color(0xFFA3E635)
    val Fair = Color(0xFFFBBF24)
    val Marginal = Color(0xFFFB923C)
    val Poor = Color(0xFFF87171)

    fun forScore(score: Int?): Color = when {
        score == null -> TextMuted
        score >= 85 -> Excellent
        score >= 70 -> Good
        score >= 50 -> Fair
        score >= 30 -> Marginal
        else -> Poor
    }
}

private val scheme = darkColorScheme(
    primary = NightColors.Primary,
    onPrimary = Color(0xFF0A1A3A),
    primaryContainer = Color(0xFF243A6B),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = NightColors.Amber,
    onSecondary = Color(0xFF2D1E00),
    secondaryContainer = Color(0xFF4A3510),
    onSecondaryContainer = Color(0xFFFFE2B3),
    background = NightColors.Background,
    onBackground = Color(0xFFE8EEFF),
    surface = NightColors.Background,
    onSurface = Color(0xFFE8EEFF),
    surfaceVariant = NightColors.Surface,
    onSurfaceVariant = NightColors.TextMuted,
    surfaceContainer = NightColors.Surface,
    surfaceContainerLow = NightColors.Surface,
    surfaceContainerHigh = NightColors.SurfaceHigh,
    surfaceContainerHighest = NightColors.SurfaceHigh,
    outline = Color(0xFF3A4566),
    outlineVariant = Color(0xFF263052),
    error = NightColors.Poor,
)

private val typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

/** Always dark: the app is used at night, often outdoors next to a camera. */
@Composable
fun NightBriefTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
