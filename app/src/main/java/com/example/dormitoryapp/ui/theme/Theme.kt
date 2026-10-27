package com.example.dormitoryapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Фирменная цветовая схема приложения общежития:
 * - Основной (Primary): Красно-пурпурный #9F003D (PANTONE 207)
 * - Темно-серый (Dark Gray): #333333 для четкого контрастного текста
 * - Белый (White): #FFFFFF для карточек и поверхностей
 * - Пастельный бордовый #FCE8EF для верхней шапки и выделенных блоков
 */
private val DormBrandColorScheme = lightColorScheme(
    primary = BrandBurgundy,                 // #9F003D (кнопки, активные вкладки, акценты)
    onPrimary = Color.White,                 // Белый текст на бордовых кнопках
    primaryContainer = BrandBurgundyContainer, // #FCE8EF (пастельная подложка шапки и сводки)
    onPrimaryContainer = BrandOnBurgundyContainer, // #4A0019 (глубокий контрастный цвет текста на шапке)

    secondary = BrandBurgundy,
    onSecondary = Color.White,
    secondaryContainer = BrandBurgundyContainer,
    onSecondaryContainer = BrandOnBurgundyContainer,

    tertiary = Color(0xFF6B1D34),
    onTertiary = Color.White,

    background = BrandBackground,            // #F7F7F8 (светлый чистый фон экранов)
    onBackground = BrandDarkGray,            // #333333 (основной темный текст по брендбуку)

    surface = BrandSurface,                  // #FFFFFF (чистые белые карточки)
    onSurface = BrandDarkGray,               // #333333 (темно-серый текст внутри карточек)

    surfaceVariant = BrandSurfaceVariant,    // #F0F0F2 (фон вспомогательных плашек и чипов)
    onSurfaceVariant = BrandDarkGray,        // #333333 (темный текст на вспомогательных элементах)

    outline = BrandOutline                   // #CCCCCC (четкие границы полей)
)

@Composable
fun DormitoryAppTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = DormBrandColorScheme,
        typography = Typography,
        content = content
    )
}