package ir.mums.stufood.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

val LocalPersianFontFamily = staticCompositionLocalOf<FontFamily> { GanjnamehFamily }
val LocalPersianFontScale = staticCompositionLocalOf { 1f }

internal val LightColors = lightColorScheme(
    primary = md_light_primary, onPrimary = md_light_onPrimary, primaryContainer = md_light_primaryContainer, onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary, onSecondary = md_light_onSecondary, secondaryContainer = md_light_secondaryContainer, onSecondaryContainer = md_light_onSecondaryContainer,
    tertiary = md_light_tertiary, onTertiary = md_light_onTertiary, tertiaryContainer = md_light_tertiaryContainer, onTertiaryContainer = md_light_onTertiaryContainer,
    error = md_light_error, onError = md_light_onError, errorContainer = md_light_errorContainer, onErrorContainer = md_light_onErrorContainer,
    background = md_light_background, onBackground = md_light_onBackground, surface = md_light_surface, onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant, onSurfaceVariant = md_light_onSurfaceVariant, outline = md_light_outline,
    inverseSurface = md_light_inverseSurface, inverseOnSurface = md_light_inverseOnSurface, inversePrimary = md_light_inversePrimary,
    surfaceContainerLowest = md_light_surfaceContainerLowest,
    surfaceContainerLow = md_light_surfaceContainerLow,
    surfaceContainer = md_light_surfaceContainer,
    surfaceContainerHigh = md_light_surfaceContainerHigh,
    surfaceContainerHighest = md_light_surfaceContainerHighest,
)

internal val DarkColors = darkColorScheme(
    primary = md_dark_primary, onPrimary = md_dark_onPrimary, primaryContainer = md_dark_primaryContainer, onPrimaryContainer = md_dark_onPrimaryContainer,
    secondary = md_dark_secondary, onSecondary = md_dark_onSecondary, secondaryContainer = md_dark_secondaryContainer, onSecondaryContainer = md_dark_onSecondaryContainer,
    tertiary = md_dark_tertiary, onTertiary = md_dark_onTertiary, tertiaryContainer = md_dark_tertiaryContainer, onTertiaryContainer = md_dark_onTertiaryContainer,
    error = md_dark_error, onError = md_dark_onError, errorContainer = md_dark_errorContainer, onErrorContainer = md_dark_onErrorContainer,
    background = md_dark_background, onBackground = md_dark_onBackground, surface = md_dark_surface, onSurface = md_dark_onSurface,
    surfaceVariant = md_dark_surfaceVariant, onSurfaceVariant = md_dark_onSurfaceVariant, outline = md_dark_outline,
    inverseSurface = md_dark_inverseSurface, inverseOnSurface = md_dark_inverseOnSurface, inversePrimary = md_dark_inversePrimary,
    surfaceContainerLowest = md_dark_surfaceContainerLowest,
    surfaceContainerLow = md_dark_surfaceContainerLow,
    surfaceContainer = md_dark_surfaceContainer,
    surfaceContainerHigh = md_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_dark_surfaceContainerHighest,
)

internal val LaneLightColors = lightColorScheme(
    primary = lane_light_primary, onPrimary = lane_light_onPrimary, primaryContainer = lane_light_primaryContainer, onPrimaryContainer = lane_light_onPrimaryContainer,
    secondary = lane_light_secondary, onSecondary = lane_light_onSecondary, secondaryContainer = lane_light_secondaryContainer, onSecondaryContainer = lane_light_onSecondaryContainer,
    tertiary = lane_light_tertiary, onTertiary = lane_light_onTertiary, tertiaryContainer = lane_light_tertiaryContainer, onTertiaryContainer = lane_light_onTertiaryContainer,
    error = md_light_error, onError = md_light_onError, errorContainer = md_light_errorContainer, onErrorContainer = md_light_onErrorContainer,
    background = lane_light_background, onBackground = lane_light_onBackground, surface = lane_light_surface, onSurface = lane_light_onSurface,
    surfaceVariant = lane_light_surfaceVariant, onSurfaceVariant = lane_light_onSurfaceVariant, outline = lane_light_outline,
    inverseSurface = lane_light_inverseSurface, inverseOnSurface = lane_light_inverseOnSurface, inversePrimary = lane_light_inversePrimary,
    surfaceContainerLowest = lane_light_surfaceContainerLowest,
    surfaceContainerLow = lane_light_surfaceContainerLow,
    surfaceContainer = lane_light_surfaceContainer,
    surfaceContainerHigh = lane_light_surfaceContainerHigh,
    surfaceContainerHighest = lane_light_surfaceContainerHighest,
)

internal val LaneDarkColors = darkColorScheme(
    primary = lane_dark_primary, onPrimary = lane_dark_onPrimary, primaryContainer = lane_dark_primaryContainer, onPrimaryContainer = lane_dark_onPrimaryContainer,
    secondary = lane_dark_secondary, onSecondary = lane_dark_onSecondary, secondaryContainer = lane_dark_secondaryContainer, onSecondaryContainer = lane_dark_onSecondaryContainer,
    tertiary = lane_dark_tertiary, onTertiary = lane_dark_onTertiary, tertiaryContainer = lane_dark_tertiaryContainer, onTertiaryContainer = lane_dark_onTertiaryContainer,
    error = md_dark_error, onError = md_dark_onError, errorContainer = md_dark_errorContainer, onErrorContainer = md_dark_onErrorContainer,
    background = lane_dark_background, onBackground = lane_dark_onBackground, surface = lane_dark_surface, onSurface = lane_dark_onSurface,
    surfaceVariant = lane_dark_surfaceVariant, onSurfaceVariant = lane_dark_onSurfaceVariant, outline = lane_dark_outline,
    inverseSurface = lane_dark_inverseSurface, inverseOnSurface = lane_dark_inverseOnSurface, inversePrimary = lane_dark_inversePrimary,
    surfaceContainerLowest = lane_dark_surfaceContainerLowest,
    surfaceContainerLow = lane_dark_surfaceContainerLow,
    surfaceContainer = lane_dark_surfaceContainer,
    surfaceContainerHigh = lane_dark_surfaceContainerHigh,
    surfaceContainerHighest = lane_dark_surfaceContainerHighest,
)

@Composable
fun BananiteTheme(
    themeMode: String = "system",       // "system", "light", "dark"
    pureBlack: Boolean = false,         // OLED pure black toggle
    colorSchemeType: String = "dynamic",// "dynamic" (Material You) or "custom" (Expressive/Warm Palette)
    persianFontId: String = DEFAULT_PERSIAN_FONT_ID,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }

    val baseColorScheme = when {
        colorSchemeType == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorSchemeType == "lane" -> if (darkTheme) LaneDarkColors else LaneLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Apply Pure Black overrides for OLED displays
    val finalColorScheme = if (darkTheme && pureBlack) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424)
        )
    } else {
        baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalPersianFontFamily provides persianFontFor(persianFontId),
        LocalPersianFontScale provides persianFontScaleFor(persianFontId)
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            content = content
        )
    }
}