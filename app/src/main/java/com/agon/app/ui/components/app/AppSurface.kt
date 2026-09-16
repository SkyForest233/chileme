package com.agon.app.ui.components.app

// 卡片容器与弱化文字：两主题的圆角/色板不同，这里做一次映射，屏幕层只写一份。
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出；
// 同日第 3 对（详情页）补 AppStatusCard，并把 AppHintText 改成走 AppText 的档位表（取值不变）；
// 第 4 对（首页）补 AppStatCard，并给 AppPaddedCard 加可点形态（首页紧急行在用）。

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
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
fun AppPaddedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        // Miuix Card 有可点/不可点两种调用形态（合并前两版都用到），别自己套 clickable：
        // 库的 onClick 才带正确的按压态与语义（role = button）。
        if (onClick != null) {
            MiuixCard(onClick = onClick, modifier = modifier) { content() }
        } else {
            MiuixCard(modifier = modifier) { content() }
        }
    } else if (onClick != null) {
        // MD3 侧可点时用 Card(onClick)（= 带 shape 裁剪涟漪的 Surface），配色与 elevation 照抄
        // 合并前首页 UrgentRow 的写法：surfaceContainer + 0dp 阴影，与不可点分支视觉一致。
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
 * 统计卡的语义配色。合并前两版都是「按语义挑容器色」（primaryContainer / secondaryContainer /
 * errorContainer），所以不让屏幕取好颜色再传进来 —— 那样屏幕里又要写一遍 `if (isMiuix)`。
 */
enum class AppStatTone { Primary, Secondary, Error }

/**
 * 首页三张统计卡：emoji + 大号数字 + 标签，整卡可点（点了带筛选条件跳列表页）。
 *
 * 外壳：MD3 = `Card(shape = shapes.large, elevation 0)`，Miuix = 官方 `Card`（带库默认内衬）——
 * 即 [AppPaddedCard] 的可点形态，配色按 [tone] 在内部取，两版逐字照抄。
 * 数字走 [AppTextScale.Value]（MD3 `headlineMedium` / Miuix `title2`）、标签走 [AppTextScale.Label]；
 * emoji 只给字号不套档位（[AppEmojiText]）。
 *
 * 统计页的 `MiniStat` 与它同构但不同参数（emoji 20sp、间隔 6dp、数值走 Hero 档、不可点、
 * value 是 String）。**现在不为那个还没合并的调用点预先抽象** —— 等第 7 对到手再决定
 * 是加参数还是各自保留，届时两边都看得见。
 */
@Composable
fun AppStatCard(
    emoji: String,
    value: Int,
    label: String,
    tone: AppStatTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val (container, content) = when (tone) {
            AppStatTone.Primary ->
                MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
            AppStatTone.Secondary ->
                MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onSecondaryContainer
            AppStatTone.Error ->
                MiuixTheme.colorScheme.errorContainer to MiuixTheme.colorScheme.onErrorContainer
        }
        MiuixCard(
            onClick = onClick,
            modifier = modifier,
            colors = MiuixCardDefaults.defaultColors(color = container, contentColor = content),
        ) {
            StatCardBody(emoji, value, label)
        }
    } else {
        val (container, content) = when (tone) {
            AppStatTone.Primary ->
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            AppStatTone.Secondary ->
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            AppStatTone.Error ->
                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            StatCardBody(emoji, value, label)
        }
    }
}

/** 统计卡内容：两主题完全一致，所以只写一份。 */
@Composable
private fun StatCardBody(emoji: String, value: Int, label: String) {
    Column(Modifier.padding(14.dp)) {
        AppEmojiText(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        AppText("$value", AppTextScale.Value, fontWeight = FontWeight.ExtraBold)
        AppText(label, AppTextScale.Label)
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
