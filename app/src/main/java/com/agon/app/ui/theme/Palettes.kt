package com.agon.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色方案：每个方案一个 MD3 种子色，由 MaterialKolor 生成完整 ColorScheme。
 * 命名贴合食物主题；种子色均选中深色调，保证生成的浅/深两套方案对比度与易读性。
 */
enum class AppPalette(val label: String, val emoji: String, val seed: Color) {
    MINT("薄荷", "🌿", Color(0xFF1F5C46)),
    MATCHA("抹茶", "🍵", Color(0xFF5C7C2E)),
    CITRUS("蜜橘", "🍊", Color(0xFFB35310)),
    PEACH("蜜桃", "🍑", Color(0xFFB0455B)),
    BLUEBERRY("蓝莓", "🫐", Color(0xFF2F5DA8)),
    TARO("香芋", "🍠", Color(0xFF6B549E)),
    COCOA("可可", "🍫", Color(0xFF6E4A33)),
    STRAWBERRY("草莓", "🍓", Color(0xFFA93238)),
    HONEY("蜂蜜", "🍯", Color(0xFF8A6C00)),
    OLIVE("橄榄", "🫒", Color(0xFF57652F)),
    LAVENDER("薰衣草", "💜", Color(0xFF7D5CB0)),
    SODA("苏打", "🥤", Color(0xFF00696E)),
    SALMON("鲑鱼", "🍣", Color(0xFFB3543F)),
    GRAPE("葡萄", "🍇", Color(0xFF5F3D8C)),
    SESAME("黑芝麻", "⚫", Color(0xFF474A45));

    companion object {
        fun fromName(name: String?): AppPalette =
            entries.find { it.name == name } ?: MINT
    }
}
