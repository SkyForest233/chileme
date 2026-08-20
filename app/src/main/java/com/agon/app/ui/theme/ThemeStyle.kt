package com.agon.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 主题风格（v2.8）：Material 3（默认，现状）与 MIUIX（HyperOS 风格，Miuix 组件）。
 * 这是「风格」级别的切换，决定使用哪一套组件/主题渲染；与配色方案（AppPalette）、
 * 深浅模式、动态取色正交。
 */
enum class ThemeStyle(val label: String) {
    MATERIAL3("Material 3"),
    MIUIX("MIUIX");

    companion object {
        fun fromName(name: String?): ThemeStyle =
            entries.find { it.name == name } ?: MATERIAL3
    }
}

/**
 * 当前主题风格。MainActivity 依据 DataStore 状态提供；屏幕据此选择
 * Material 3 或 Miuix 的实现（渐进迁移期间仅部分页面存在 Miuix 实现）。
 */
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.MATERIAL3 }
