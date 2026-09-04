package com.agon.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 颜色模式，对齐 KernelSU 的 7 档设计。
 * 0 SYSTEM 跟随系统
 * 1 LIGHT 强制浅色
 * 2 DARK 强制深色
 * 3 MONET_SYSTEM 动态取色+跟随系统
 * 4 MONET_LIGHT 动态取色+强制浅色
 * 5 MONET_DARK 动态取色+强制深色
 * 6 DARK_AMOLED 纯黑 AMOLED
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val palette: AppPalette,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
)

val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
            this == PaletteStyle.Neutral ||
            this == PaletteStyle.Vibrant ||
            this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

val LocalColorMode = staticCompositionLocalOf { 0 }
val LocalEnableBlur = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { true }
val LocalEnableNavigationBadge = staticCompositionLocalOf { true }
