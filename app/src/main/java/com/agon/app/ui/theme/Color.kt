package com.agon.app.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================
// 状态语义色（安全 / 临期 / 过期）
// 不随主题种子色/动态取色变化 —— 语义色必须稳定可辨
// 主题色由 MaterialKolor 从 AppPalette 种子色生成（见 Theme.kt）
// =====================================================

val SafeContainerLight = Color(0xFFBDEBD1)
val SafeContentLight = Color(0xFF12503A)
val WarnContainerLight = Color(0xFFFFE1B3)
val WarnContentLight = Color(0xFF7A4A00)
val DangerContainerLight = Color(0xFFFFD9D4)
val DangerContentLight = Color(0xFF8C1D18)

val SafeContainerDark = Color(0xFF1C4A38)
val SafeContentDark = Color(0xFFA5E8C6)
val WarnContainerDark = Color(0xFF5A4218)
val WarnContentDark = Color(0xFFFFDDA8)
val DangerContainerDark = Color(0xFF5C2320)
val DangerContentDark = Color(0xFFFFB4AB)

// ---- 小圆点专用高饱和色（日历标记、图例等小面积色块）----
// content 色是为文字可读性设计的深/浅色调，在 6dp 圆点上红棕绿难以区分，
// 圆点必须使用高饱和、色相差异大的专用色；深浅模式各一套保证对比度。
val SafeDotLight = Color(0xFF2E9E5B)      // 鲜绿
val WarnDotLight = Color(0xFFF59E0B)      // 琥珀黄
val UrgentDotLight = Color(0xFFF4511E)    // 深橙（3 天内紧急档）
val DangerDotLight = Color(0xFFE5484D)    // 鲜红

val SafeDotDark = Color(0xFF4ADE80)
val WarnDotDark = Color(0xFFFBBF24)
val UrgentDotDark = Color(0xFFFF8A5C)
val DangerDotDark = Color(0xFFFF6B6B)
