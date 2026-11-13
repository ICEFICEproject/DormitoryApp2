package com.example.dormitoryapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * CompositionLocal для проверки активности тёмной темы в любых компонентах Compose.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

fun buildLightColorScheme(palette: AppColorPalette) = lightColorScheme(
    primary = palette.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = palette.lightPrimaryContainer,
    onPrimaryContainer = palette.lightOnPrimaryContainer,

    secondary = palette.lightPrimary,
    onSecondary = Color.White,
    secondaryContainer = palette.lightPrimaryContainer,
    onSecondaryContainer = palette.lightOnPrimaryContainer,

    tertiary = palette.lightPrimary,
    onTertiary = Color.White,

    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF222222),

    surface = Color.White,
    onSurface = Color(0xFF222222),

    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF333333),

    outline = Color(0xFFCCCCCC)
)

fun buildDarkColorScheme(palette: AppColorPalette) = darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = Color(0xFF141414),
    primaryContainer = palette.darkPrimaryContainer,
    onPrimaryContainer = palette.darkOnPrimaryContainer,

    secondary = palette.darkPrimary,
    onSecondary = Color(0xFF141414),
    secondaryContainer = palette.darkPrimaryContainer,
    onSecondaryContainer = palette.darkOnPrimaryContainer,

    tertiary = palette.darkPrimary,
    onTertiary = Color(0xFF141414),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),

    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFCCCCCC),

    outline = Color(0xFF555555)
)

@Composable
fun DormitoryAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.BURGUNDY,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) {
        buildDarkColorScheme(colorPalette)
    } else {
        buildLightColorScheme(colorPalette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
