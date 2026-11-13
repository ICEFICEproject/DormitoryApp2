package com.example.dormitoryapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Режимы освещения интерфейса:
 * - SYSTEM: Системная (автоматически следует за настройками Android)
 * - LIGHT: Светлая тема
 * - DARK: Тёмная тема
 */
enum class ThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

/**
 * Цветовые палитры оформления интерфейса:
 * 1. BURGUNDY — Красно-пурпурная / бордовая (фирменная по брендбуку #9F003D)
 * 2. SAPPHIRE — Сапфировая / синяя (#1565C0)
 * 3. EMERALD — Изумрудная / зелёная (#2E7D32)
 * 4. AMETHYST — Аметистовая / фиолетовая (#6A1B9A)
 * 5. GRAPHITE — Графитовая / монохром (#37474F)
 */
enum class AppColorPalette(
    val title: String,
    val previewColor: Color,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightPrimaryContainer: Color,
    val darkPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color
) {
    BURGUNDY(
        title = "Бордовая (фирменная)",
        previewColor = Color(0xFF9F003D),
        lightPrimary = Color(0xFF9F003D),
        darkPrimary = Color(0xFFFFB1C4),
        lightPrimaryContainer = Color(0xFFFCE8EF),
        darkPrimaryContainer = Color(0xFF5D0022),
        lightOnPrimaryContainer = Color(0xFF4A0019),
        darkOnPrimaryContainer = Color(0xFFFFD9E2)
    ),
    SAPPHIRE(
        title = "Сапфировая (синяя)",
        previewColor = Color(0xFF1565C0),
        lightPrimary = Color(0xFF1565C0),
        darkPrimary = Color(0xFF90CAF9),
        lightPrimaryContainer = Color(0xFFE3F2FD),
        darkPrimaryContainer = Color(0xFF0D47A1),
        lightOnPrimaryContainer = Color(0xFF0A386D),
        darkOnPrimaryContainer = Color(0xFFD1E4FF)
    ),
    EMERALD(
        title = "Изумрудная (зелёная)",
        previewColor = Color(0xFF2E7D32),
        lightPrimary = Color(0xFF2E7D32),
        darkPrimary = Color(0xFFA5D6A7),
        lightPrimaryContainer = Color(0xFFE8F5E9),
        darkPrimaryContainer = Color(0xFF1B5E20),
        lightOnPrimaryContainer = Color(0xFF134D16),
        darkOnPrimaryContainer = Color(0xFFC8E6C9)
    ),
    AMETHYST(
        title = "Аметистовая (фиолетовая)",
        previewColor = Color(0xFF6A1B9A),
        lightPrimary = Color(0xFF6A1B9A),
        darkPrimary = Color(0xFFCE93D8),
        lightPrimaryContainer = Color(0xFFF3E5F5),
        darkPrimaryContainer = Color(0xFF4A148C),
        lightOnPrimaryContainer = Color(0xFF380854),
        darkOnPrimaryContainer = Color(0xFFF0D6F7)
    ),
    GRAPHITE(
        title = "Графитовая (монохром)",
        previewColor = Color(0xFF37474F),
        lightPrimary = Color(0xFF37474F),
        darkPrimary = Color(0xFFCFD8DC),
        lightPrimaryContainer = Color(0xFFECEFF1),
        darkPrimaryContainer = Color(0xFF263238),
        lightOnPrimaryContainer = Color(0xFF1D262B),
        darkOnPrimaryContainer = Color(0xFFECEFF1)
    )
}

// Фирменная базовая палитра (для обратной совместимости)
val BrandBurgundy = Color(0xFF9F003D)
val BrandDarkGray = Color(0xFF333333)
val BrandWhite = Color(0xFFFFFFFF)
val BrandBurgundyContainer = Color(0xFFFCE8EF)
val BrandOnBurgundyContainer = Color(0xFF4A0019)
val BrandBackground = Color(0xFFF7F7F8)
val BrandSurface = Color(0xFFFFFFFF)
val BrandSurfaceVariant = Color(0xFFF0F0F2)
val BrandOutline = Color(0xFFCCCCCC)
