package com.agon.app.ui.components

// 状态语义层：三态 StatusUi（安全/临期/过期）与四档到期紧急度 ExpiryUrgency。
//
// 这一层只负责「状态 → 颜色/文案/图标」的映射，不含任何布局，是 StatusBadge、FoodCard、
// ExpiryCalendar 圆点与统计页图例的共同色源。规则见 docs/DESIGN_SPEC.md §1：
// 状态色一律经 rememberStatusUi() / urgencyDotColor() 取，屏幕代码禁止写死色值。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.daysLeftAt
import com.agon.app.data.effectiveThreshold
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Timer
import com.agon.app.ui.theme.DangerContainerDark
import com.agon.app.ui.theme.DangerContainerLight
import com.agon.app.ui.theme.DangerContentDark
import com.agon.app.ui.theme.DangerContentLight
import com.agon.app.ui.theme.SafeContainerDark
import com.agon.app.ui.theme.SafeContainerLight
import com.agon.app.ui.theme.SafeContentDark
import com.agon.app.ui.theme.SafeContentLight
import com.agon.app.ui.theme.SafeDotDark
import com.agon.app.ui.theme.UrgentDotDark
import com.agon.app.ui.theme.UrgentDotLight
import com.agon.app.ui.theme.SafeDotLight
import com.agon.app.ui.theme.WarnDotDark
import com.agon.app.ui.theme.WarnDotLight
import com.agon.app.ui.theme.DangerDotDark
import com.agon.app.ui.theme.DangerDotLight
import com.agon.app.ui.theme.WarnContainerDark
import com.agon.app.ui.theme.WarnContainerLight
import com.agon.app.ui.theme.WarnContentDark
import com.agon.app.ui.theme.WarnContentLight
import java.time.LocalDate

data class StatusUi(
    val container: Color,
    val content: Color,
    val label: String,
    val icon: ImageVector,
    /**
     * 高饱和圆点色：content 色是为文字设计的深色调，在 6~7dp 小圆点上
     * 红/棕/绿几乎不可辨；日历圆点、图例等小面积色块必须用本色。
     */
    val dot: Color,
)

@Composable
fun rememberStatusUi(status: FoodStatus): StatusUi {
    // 用当前主题背景亮度判断深浅色，而非 isSystemInDarkTheme()：
    // App 支持在设置中强制浅色/深色，两者不一致时会取错色套。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    return remember(status, dark, isMiuix) {
        when (status) {
            FoodStatus.SAFE -> StatusUi(
                container = if (dark) SafeContainerDark else SafeContainerLight,
                content = if (dark) SafeContentDark else SafeContentLight,
                label = "安全",
                icon = if (isMiuix) MiuixIcons.Ok else Icons.Rounded.CheckCircle,
                dot = if (dark) SafeDotDark else SafeDotLight,
            )
            FoodStatus.EXPIRING -> StatusUi(
                container = if (dark) WarnContainerDark else WarnContainerLight,
                content = if (dark) WarnContentDark else WarnContentLight,
                label = "临期",
                icon = if (isMiuix) MiuixIcons.Timer else Icons.Rounded.Schedule,
                dot = if (dark) WarnDotDark else WarnDotLight,
            )
            FoodStatus.EXPIRED -> StatusUi(
                container = if (dark) DangerContainerDark else DangerContainerLight,
                content = if (dark) DangerContentDark else DangerContentLight,
                label = "已过期",
                icon = if (isMiuix) MiuixIcons.Report else Icons.Rounded.ErrorOutline,
                dot = if (dark) DangerDotDark else DangerDotLight,
            )
        }
    }
}

/**
 * 到期紧急度四档（专供日历圆点、图例等小面积标记使用）：
 * 三态 status 在日历场景下区分度不足——阈值内的日期全是"临期"一片黄。
 * 圆点按剩余天数分梯度：红(已过期) → 深橙(≤3天) → 琥珀黄(阈值内) → 绿(安全)。
 */
enum class ExpiryUrgency(val label: String) {
    EXPIRED("已过期"),
    URGENT("3天内"),
    SOON("临期"),
    SAFE("安全"),
}

/** 可注入 today 的纯函数（供跨零点刷新与单测使用）。 */
fun FoodItem.urgencyForAt(today: LocalDate, thresholds: Map<String, Int>): ExpiryUrgency = when {
    daysLeftAt(today) < 0 -> ExpiryUrgency.EXPIRED
    daysLeftAt(today) <= 3 -> ExpiryUrgency.URGENT
    daysLeftAt(today) <= effectiveThreshold(thresholds) -> ExpiryUrgency.SOON
    else -> ExpiryUrgency.SAFE
}

@Composable
fun urgencyDotColor(urgency: ExpiryUrgency): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (urgency) {
        ExpiryUrgency.EXPIRED -> if (dark) DangerDotDark else DangerDotLight
        ExpiryUrgency.URGENT -> if (dark) UrgentDotDark else UrgentDotLight
        ExpiryUrgency.SOON -> if (dark) WarnDotDark else WarnDotLight
        ExpiryUrgency.SAFE -> if (dark) SafeDotDark else SafeDotLight
    }
}
