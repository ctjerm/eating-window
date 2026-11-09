package dk.eatwindow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dk.eatwindow.R

// Bundled fonts (SIL OFL 1.1, see licenses/). They are static instances cut from the variable fonts in
// github.com/google/fonts with `fonttools varLib.instancer`, Fraunces at wght=400 opsz=9 SOFT=0 WONK=1,
// which is the instance Google Fonts serves. Static files, because Compose 1.7 drops a font's
// variation settings when the previous font loaded had the same ones.

/** Editorial serif for headlines. */
val Display = FontFamily(Font(R.font.fraunces_regular))

/** Geometric sans for UI text and numerals. */
val Sans = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
)

// Roles: primary = eating (amber), secondary = last call (coral), tertiary = fasting (sage),
// surfaceVariant = ring track, surfaceContainer = cards. Containers and the Low/Highest surfaces
// are set so the time picker and bottom sheet stay in the palette.
private val DarkColors = darkColorScheme(
    background = Color(0xFF0F0E0C),
    onBackground = Color(0xFFF4EFE6),
    surface = Color(0xFF0F0E0C),
    onSurface = Color(0xFFF4EFE6),
    onSurfaceVariant = Color(0xFFA39E93),
    surfaceVariant = Color(0xFF2A2823),
    surfaceContainerLow = Color(0xFF161512),
    surfaceContainer = Color(0xFF1A1916),
    surfaceContainerHigh = Color(0xFF24221E),
    surfaceContainerHighest = Color(0xFF2E2C27),
    outline = Color(0xFF4A463E),
    outlineVariant = Color(0xFF34312B),
    primary = Color(0xFFF2B35B),
    onPrimary = Color(0xFF2A1A05),
    primaryContainer = Color(0xFF4A3616),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFFF7A59),
    onSecondary = Color(0xFF2A0E06),
    tertiary = Color(0xFF8FB9A8),
    onTertiary = Color(0xFF0B2219),
    tertiaryContainer = Color(0xFF23352D),
    onTertiaryContainer = Color(0xFFCFE8DB),
)

private val LightColors = lightColorScheme(
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1C1A17),
    surface = Color(0xFFF7F3EC),
    onSurface = Color(0xFF1C1A17),
    onSurfaceVariant = Color(0xFF6F6A60),
    surfaceVariant = Color(0xFFE9E2D6),
    surfaceContainerLow = Color(0xFFFBF8F3),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEFE8DC),
    outline = Color(0xFFC9C0B1),
    outlineVariant = Color(0xFFE6DFD2),
    primary = Color(0xFFE0922F),
    onPrimary = Color(0xFF2A1A05),
    primaryContainer = Color(0xFFFBE3C0),
    onPrimaryContainer = Color(0xFF3D2604),
    secondary = Color(0xFFE2573A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4E8B74),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EADF),
    onTertiaryContainer = Color(0xFF10301F),
)

/** Small spaced capitals for labels. */
val Eyebrow = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.6.sp)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Display),
        displayMedium = displayMedium.copy(fontFamily = Display),
        displaySmall = displaySmall.copy(fontFamily = Display),
        headlineLarge = headlineLarge.copy(fontFamily = Display),
        headlineMedium = headlineMedium.copy(fontFamily = Display),
        headlineSmall = headlineSmall.copy(fontFamily = Display),
        titleLarge = titleLarge.copy(fontFamily = Sans),
        titleMedium = titleMedium.copy(fontFamily = Sans),
        titleSmall = titleSmall.copy(fontFamily = Sans),
        bodyLarge = bodyLarge.copy(fontFamily = Sans),
        bodyMedium = bodyMedium.copy(fontFamily = Sans),
        bodySmall = bodySmall.copy(fontFamily = Sans),
        labelLarge = labelLarge.copy(fontFamily = Sans),
        labelMedium = labelMedium.copy(fontFamily = Sans),
        labelSmall = labelSmall.copy(fontFamily = Sans),
    )
}

@Composable
fun EatingWindowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
