package app.tidyphone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2468),
    onPrimaryContainer = Color(0xFFE2DDFF),
    secondary = Brand.Mint,
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF0E3B33),
    onSecondaryContainer = Color(0xFFB4F5E2),
    tertiary = Brand.Cyan,
    onTertiary = Color(0xFF00344A),
    error = Brand.Coral,
    onError = Color(0xFF3F0010),
    errorContainer = Color(0xFF4A1620),
    onErrorContainer = Color(0xFFFFDADF),
    background = Brand.Ink,
    onBackground = Color(0xFFE7E8F2),
    surface = Brand.Ink,
    onSurface = Color(0xFFE7E8F2),
    surfaceVariant = Brand.InkHigh,
    onSurfaceVariant = Color(0xFFA7ACC4),
    surfaceContainerLowest = Color(0xFF080A12),
    surfaceContainerLow = Color(0xFF10131F),
    surfaceContainer = Brand.InkRaised,
    surfaceContainerHigh = Brand.InkHigh,
    surfaceContainerHighest = Color(0xFF262B42),
    outline = Color(0xFF3A3F58),
    outlineVariant = Color(0xFF262A3E),
)

private val LightColors = lightColorScheme(
    primary = Brand.VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF1C1366),
    secondary = Color(0xFF00A784),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F7EA),
    onSecondaryContainer = Color(0xFF00382B),
    tertiary = Color(0xFF0091C7),
    error = Color(0xFFE5394D),
    errorContainer = Color(0xFFFFE1E5),
    onErrorContainer = Color(0xFF5B0614),
    background = Brand.Mist,
    onBackground = Color(0xFF151827),
    surface = Brand.Mist,
    onSurface = Color(0xFF151827),
    surfaceVariant = Color(0xFFE7E9F3),
    onSurfaceVariant = Color(0xFF5B6079),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF0F7),
    surfaceContainerHighest = Color(0xFFE6E8F2),
    outline = Color(0xFFC5C8D8),
    outlineVariant = Color(0xFFE2E4EE),
)

val TidyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun TidyTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TidyTypography,
        shapes = TidyShapes,
        content = content,
    )
}
