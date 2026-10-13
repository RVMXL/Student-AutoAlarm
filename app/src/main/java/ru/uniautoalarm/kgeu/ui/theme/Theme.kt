package ru.uniautoalarm.kgeu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.ThemeMode

/**
 * Тема приложения.
 *
 * Логика:
 *  1) dynamicColor (Material You) — если включён в настройках и API 31+;
 *  2) иначе акцентная палитра из [ACCENT_PALETTES] по Settings.accentIndex
 *     (светлая/тёмная вариация по ThemeMode);
 *  3) settings == null (настройки ещё грузятся) — нейтральная тёплая схема.
 *
 * Тёмная тема: ThemeMode.SYSTEM → isSystemInDarkTheme(), LIGHT/DARK — явно.
 */
@Composable
fun AutoAlarmTheme(settings: Settings?, content: @Composable () -> Unit) {
    val darkTheme = when (settings?.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    var colorScheme = when {
        settings != null && settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        settings == null ->
            if (darkTheme) NeutralWarmDark else NeutralWarmLight

        else -> {
            val palette = ACCENT_PALETTES[settings.accentIndex.coerceIn(0, ACCENT_PALETTES.lastIndex)]
            if (darkTheme) palette.toDarkScheme() else palette.toLightScheme()
        }
    }

    // Эффект стекла: все поверхности/карточки становятся полупрозрачными, под ними
    // просвечивает фон (окно или пользовательская картинка). Интенсивность — ползунок
    // в настройках (0..1): чем больше, тем прозрачнее. Минимум 0.15 — текст читается.
    if (settings?.glassEnabled == true) {
        val glassAlpha = (1f - settings.glassIntensity * 0.85f).coerceIn(0.15f, 1f)
        colorScheme = colorScheme.copy(
            background = colorScheme.background.copy(alpha = glassAlpha),
            surface = colorScheme.surface.copy(alpha = glassAlpha),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = glassAlpha),
            surfaceContainerLowest = colorScheme.surfaceContainerLowest.copy(alpha = glassAlpha),
            surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = glassAlpha),
            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = glassAlpha),
            surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = glassAlpha),
            surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = glassAlpha),
            surfaceDim = colorScheme.surfaceDim.copy(alpha = glassAlpha),
            surfaceBright = colorScheme.surfaceBright.copy(alpha = glassAlpha)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutoAlarmTypography,
        content = content
    )
}

// --- Сборка схем из акцентной палитры ---

private fun AccentPalette.toLightScheme(): ColorScheme = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = WarmBackgroundLight,
    onBackground = WarmOnBackgroundLight,
    surface = WarmBackgroundLight,
    onSurface = WarmOnBackgroundLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmOnSurfaceVariantLight,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight
)

private fun AccentPalette.toDarkScheme(): ColorScheme = darkColorScheme(
    primary = Color(primaryDark),
    onPrimary = Color(onPrimaryDark),
    primaryContainer = Color(primaryContainerDark),
    onPrimaryContainer = Color(onPrimaryContainerDark),
    secondary = Color(secondaryDark),
    onSecondary = Color(onSecondaryDark),
    secondaryContainer = Color(secondaryContainerDark),
    onSecondaryContainer = Color(onSecondaryContainerDark),
    tertiary = Color(tertiaryDark),
    onTertiary = Color(onTertiaryDark),
    tertiaryContainer = Color(tertiaryContainerDark),
    onTertiaryContainer = Color(onTertiaryContainerDark),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = WarmBackgroundDark,
    onBackground = WarmOnBackgroundDark,
    surface = WarmBackgroundDark,
    onSurface = WarmOnBackgroundDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnSurfaceVariantDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark
)

// --- Тёплые нейтральные фоны/поверхности: общие для всех палитр,
//     чтобы приложение оставалось «тёплым» при любом акценте ---

private val WarmBackgroundLight = Color(0xFFFFF8F1)
private val WarmOnBackgroundLight = Color(0xFF2C2620)
private val WarmSurfaceVariantLight = Color(0xFFF0E0CF)
private val WarmOnSurfaceVariantLight = Color(0xFF4F443A)
private val WarmOutlineLight = Color(0xFF817467)
private val WarmOutlineVariantLight = Color(0xFFD3C4B4)

private val WarmBackgroundDark = Color(0xFF1A1611)
private val WarmOnBackgroundDark = Color(0xFFECE0D4)
private val WarmSurfaceVariantDark = Color(0xFF4F443A)
private val WarmOnSurfaceVariantDark = Color(0xFFD3C4B4)
private val WarmOutlineDark = Color(0xFF998D7E)
private val WarmOutlineVariantDark = Color(0xFF4F443A)

// --- Нейтральная тёплая схема для периода, пока настройки ещё не загружены (settings == null) ---

private val NeutralWarmLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF7F5A33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DFC4),
    onPrimaryContainer = Color(0xFF33250F),
    secondary = Color(0xFF6F5B44),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF9DFBB),
    onSecondaryContainer = Color(0xFF281804),
    tertiary = Color(0xFF67642F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEECA6),
    onTertiaryContainer = Color(0xFF201F00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = WarmBackgroundLight,
    onBackground = WarmOnBackgroundLight,
    surface = WarmBackgroundLight,
    onSurface = WarmOnBackgroundLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmOnSurfaceVariantLight,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight
)

private val NeutralWarmDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFE3C3A1),
    onPrimary = Color(0xFF412D15),
    primaryContainer = Color(0xFF5A432A),
    onPrimaryContainer = Color(0xFFF6DFC4),
    secondary = Color(0xFFDCC3A4),
    onSecondary = Color(0xFF3E2C16),
    secondaryContainer = Color(0xFF57422B),
    onSecondaryContainer = Color(0xFFF9DFBB),
    tertiary = Color(0xFFD1CD8D),
    onTertiary = Color(0xFF343300),
    tertiaryContainer = Color(0xFF4B4A16),
    onTertiaryContainer = Color(0xFFEEECA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = WarmBackgroundDark,
    onBackground = WarmOnBackgroundDark,
    surface = WarmBackgroundDark,
    onSurface = WarmOnBackgroundDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmOnSurfaceVariantDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark
)
