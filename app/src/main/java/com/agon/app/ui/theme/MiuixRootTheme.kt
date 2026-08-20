package com.agon.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 由 darkMode（0 跟随 / 1 浅色 / 2 深色）与 dynamicColor 推导 Miuix [ThemeController]，
 * 语义与 Material 3 侧（Theme.kt 的 AgonAppTheme）保持一致：
 * 动态取色开启 → Monet（keyColor 为 null，跟随系统壁纸），否则按 darkMode 映射 System/Light/Dark。
 */
@Composable
fun rememberMiuixController(darkMode: Int, dynamicColor: Boolean): ThemeController =
    remember(darkMode, dynamicColor) {
        when {
            dynamicColor && darkMode == 1 -> ThemeController(ColorSchemeMode.MonetLight)
            dynamicColor && darkMode == 2 -> ThemeController(ColorSchemeMode.MonetDark)
            dynamicColor -> ThemeController(ColorSchemeMode.MonetSystem)
            darkMode == 1 -> ThemeController(ColorSchemeMode.Light)
            darkMode == 2 -> ThemeController(ColorSchemeMode.Dark)
            else -> ThemeController(ColorSchemeMode.System)
        }
    }

/**
 * 把 Miuix [Colors] 桥接为 MD3 [ColorScheme]。
 *
 * 用途：尚未迁移到 Miuix 组件的页面与 `ui/components/Common.kt` 复用组件仍通过
 * `MaterialTheme.colorScheme` 取色；桥接后它们在 MIUIX 模式下也能取到协调的 Miuix 配色，
 * 属于渐进迁移的过渡层。
 *
 * Miuix 无 tertiary/inverse*/surfaceTint 等独立角色，用最接近的角色近似；
 * 缺失角色不影响核心可读性（primary/surface/error 系列均已精确映射）。
 */
@Composable
fun miuixColorsToMd3ColorScheme(c: Colors): ColorScheme = ColorScheme(
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    inversePrimary = c.primaryContainer,
    secondary = c.secondary,
    onSecondary = c.onSecondary,
    secondaryContainer = c.secondaryContainer,
    onSecondaryContainer = c.onSecondaryContainer,
    tertiary = c.tertiaryContainer,
    onTertiary = c.onTertiaryContainer,
    tertiaryContainer = c.tertiaryContainer,
    onTertiaryContainer = c.onTertiaryContainer,
    background = c.background,
    onBackground = c.onBackground,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariantSummary,
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

/**
 * MIUIX 模式的根主题：包一层 [MiuixTheme]（提供 Miuix 组件所需的 colorScheme/textStyles），
 * 再在其内桥接一个 [MaterialTheme]，让未迁移的 MD3 页面与复用组件仍可正常取色。
 *
 * 页面内使用 Miuix 组件时读 `MiuixTheme.colorScheme`；使用 MD3 组件/Common.kt 时读
 * `MaterialTheme.colorScheme`（已桥接为 Miuix 配色），两者视觉一致。
 */
@Composable
fun MiuixRootTheme(
    darkMode: Int,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = rememberMiuixController(darkMode, dynamicColor)
    MiuixTheme(controller = controller) {
        MaterialTheme(
            colorScheme = miuixColorsToMd3ColorScheme(MiuixTheme.colorScheme),
            shapes = AppShapes,
            content = content,
        )
    }
}
