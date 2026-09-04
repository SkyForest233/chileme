package com.agon.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun rememberMiuixController(
    colorMode: ColorMode,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
): ThemeController {
    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(paletteStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }
    val miuixColorSpec = if (colorSpec.effectiveFor(paletteStyle) == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    return remember(colorMode, paletteStyle, colorSpec) {
        ThemeController(
            when (colorMode) {
                ColorMode.SYSTEM -> ColorSchemeMode.System
                ColorMode.LIGHT -> ColorSchemeMode.Light
                ColorMode.DARK -> ColorSchemeMode.Dark
                ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
            },
            isDark = colorMode.isDark,
            paletteStyle = miuixPaletteStyle,
            colorSpec = miuixColorSpec,
        )
    }
}

// 兼容旧签名
@Composable
fun rememberMiuixController(darkMode: Int, dynamicColor: Boolean): ThemeController =
    remember(darkMode, dynamicColor) {
        val cm = when {
            dynamicColor && darkMode == 1 -> ColorMode.MONET_LIGHT
            dynamicColor && darkMode == 2 -> ColorMode.MONET_DARK
            dynamicColor -> ColorMode.MONET_SYSTEM
            darkMode == 1 -> ColorMode.LIGHT
            darkMode == 2 -> ColorMode.DARK
            else -> ColorMode.SYSTEM
        }
        ThemeController(
            when (cm) {
                ColorMode.SYSTEM -> ColorSchemeMode.System
                ColorMode.LIGHT -> ColorSchemeMode.Light
                ColorMode.DARK -> ColorSchemeMode.Dark
                ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
            },
        )
    }

@Composable
fun miuixColorsToMd3ColorScheme(c: Colors, isAmoled: Boolean = false): ColorScheme {
    val base = ColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        inversePrimary = c.primaryContainer,
        secondary = c.secondary,
        onSecondary = c.onSecondary,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onTertiaryContainer,
        tertiary = c.tertiaryContainer,
        onTertiary = c.onTertiaryContainer,
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.onTertiaryContainer,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceSecondary,
        surfaceTint = c.primary,
        inverseSurface = c.onSurface,
        inverseOnSurface = c.surface,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
        outline = c.outline,
        outlineVariant = c.dividerLine,
        scrim = c.windowDimming,
        surfaceBright = c.surfaceContainerHighest,
        surfaceDim = c.background,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHighest,
        surfaceContainerLow = c.surfaceContainer,
        surfaceContainerLowest = c.surface,
    )
    return base.amoledBackground(isAmoled)
}

@Composable
fun MiuixRootTheme(
    colorMode: ColorMode,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    content: @Composable () -> Unit,
) {
    val controller = rememberMiuixController(colorMode, paletteStyle, colorSpec)
    MiuixTheme(controller = controller) {
        MaterialTheme(
            colorScheme = miuixColorsToMd3ColorScheme(MiuixTheme.colorScheme, isAmoled = colorMode.isAmoled),
            shapes = AppShapes,
            content = content,
        )
    }
}

// 兼容旧签名
@Composable
fun MiuixRootTheme(
    darkMode: Int,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val cm = when {
        dynamicColor && darkMode == 1 -> ColorMode.MONET_LIGHT
        dynamicColor && darkMode == 2 -> ColorMode.MONET_DARK
        dynamicColor -> ColorMode.MONET_SYSTEM
        darkMode == 1 -> ColorMode.LIGHT
        darkMode == 2 -> ColorMode.DARK
        else -> ColorMode.SYSTEM
    }
    MiuixRootTheme(
        colorMode = cm,
        paletteStyle = PaletteStyle.TonalSpot,
        colorSpec = ColorSpec.SpecVersion.SPEC_2025,
        content = content,
    )
}
