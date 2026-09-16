package com.agon.app.ui.components.app

// 卡片容器与弱化文字：两主题的圆角/色板不同，这里做一次映射，屏幕层只写一份。
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出；
// 同日第 3 对（详情页）补 AppStatusCard，并把 AppHintText 改成走 AppText 的档位表（取值不变）。

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表行/信息块的卡片底：MD3 走 `shapes.large` + `surfaceContainer`，
 * Miuix 走 16dp 圆角 + 同名色。不传 `contentColor`，与合并前两版的用法一致。
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}

/**
 * 带库默认内衬的卡片：MD3 侧与 [AppCard] **完全相同**（`shapes.large` + `surfaceContainer`），
 * Miuix 侧用官方 `Card` 而不是 `Surface`。
 *
 * 为什么两种卡片都要留着（上游 v0.9.4-rc01 `Card.kt:50` 已核对）：Miuix `Card` 的圆角默认值
 * `CardDefaults.CornerRadius` 也是 16dp，与 `AppCard` 的 Miuix 分支一致，但它还会把内容套进
 * `Column(Modifier.padding(CardDefaults.InsideMargin))`，且 content 接收者是 `ColumnScope`。
 * 合并前归档行与详情页两张信息卡用的都是 `Card`（= 内容自带 padding 之外**再多一层内衬**），
 * 消耗记录行用的是 `Surface`（无内衬）。这层差别真机上看得到，所以按原样分成两个组件，
 * 谁用哪个由「合并前那一版用的是什么」决定，不许顺手统一。
 */
@Composable
fun AppPaddedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixCard(modifier = modifier) { content() }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}

/**
 * 状态色大卡：详情页顶部那块（底色 = 状态语义色 `ui.container`）。
 * MD3 用 `shapes.extraLarge`、Miuix 用 24dp 圆角，都比 [AppCard] 更圆 —— 合并前两版就是这样，照抄。
 */
@Composable
fun AppStatusCard(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = color,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = color,
        ) {
            content()
        }
    }
}

/**
 * 弱化说明文字：列表上方的提示语、行内副标题、卡片提示都用它。
 * 现在是 [AppText] 的 Hint 档位 + 弱化色的一层薄封装（取值与封装前逐字相同：
 * MD3 `bodySmall` + `onSurfaceVariant` / Miuix `footnote2` + `onSurfaceVariantSummary`）。
 */
@Composable
fun AppHintText(text: String, modifier: Modifier = Modifier) {
    AppText(text, AppTextScale.Hint, modifier = modifier, color = appMutedColor())
}
