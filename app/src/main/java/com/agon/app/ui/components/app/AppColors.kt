package com.agon.app.ui.components.app

/**
 * 跨主题的**取色口子**：一个语义角色一个函数，内部只做「MD3 色板的哪个角色 vs Miuix 色板的哪个角色」这一件事。
 *
 * 2026-09-19 由 `AppText.kt` 抽出（路线图 #11a）。之前这 9 个 helper 与文本组件同住一个文件，
 * 后果不是"文件长"，而是**全仓事实上的颜色层叫 AppText.kt**：屏幕要取个弱化色、危险色、图表调色板，
 * 第一反应是去文本组件里找，而新增一档颜色时也容易顺手塞回那里（守卫 `AppColorLocationTest` 就是钉这条）。
 *
 * 三条约定随函数搬过来，一个字没改：
 * 1. **只在"组件层没有对应东西"时才开口子**（自绘图表、头像底、进度条轨道）；屏幕要弱化文字就用
 *    [AppHintText] / [AppMutedText]，别自己取色再拼一个 `Text`，否则两主题的色板角色又要各写一遍；
 * 2. **两主题的色板角色不是一一对应**，每个函数标了各自取的那一格（`appMutedColor` 是
 *    MD3 `onSurfaceVariant` / Miuix `onSurfaceVariantSummary`，`appFaintColor` 是 `outlineVariant` /
 *    `dividerLine`）⇒ 按角色名去"统一"就是改视觉，只有真机看得出来；
 * 3. 函数名刻意不叫 `appOutlineVariantColor` 那种"照抄 MD3 角色名"的形状 —— Miuix 侧没有那个角色。
 */

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主题色：卡片底 / 头像底 / 进度条轨道用的那一层 surface
 * （MD3 `MaterialTheme.colorScheme.surface` / Miuix `MiuixTheme.colorScheme.surface`）。
 *
 * 只开口子给「屏幕层必须自己取色」的少数场合（详情页把它同时用作头像底色与进度条轨道色）；
 * 弱化文字色不在此列 —— 那是 [AppHintText] / [AppDetailRow] 内部的事，屏幕层不该关心。
 */
@Composable
fun appSurfaceColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface
    }

/**
 * 弱化文字色：MD3 `onSurfaceVariant` / Miuix `onSurfaceVariantSummary`。
 * 组件层内部用（`AppHintText` / `AppDetailRow`），不对屏幕层开口 —— 屏幕要弱化文字就用
 * [AppHintText]，别自己取色再拼一个 Text，否则两主题的色板角色又要各写一遍。
 */
@Composable
internal fun appMutedColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

/** 危险操作色：MD3 `colorScheme.error` / Miuix `colorScheme.error`（顶栏删除入口、行内删除按钮用）。 */
@Composable
internal fun appErrorColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.error
    }

/**
 * 主色容器底 / 其上的内容色：首页「今日提醒」那颗圆形图标在用（统计卡的配色走 [AppStatTone]，
 * 不需要屏幕自己取色）。两主题同名，但取值各自来自自己的色板。
 */
/** 主色容器底：首页「今日提醒」的圆形图标、统计页排行榜的横条在用（自绘，理由同 [appPrimaryColor]）。 */
@Composable
fun appPrimaryContainerColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

@Composable
internal fun appOnPrimaryContainerColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

/**
 * 强调色：MD3 `colorScheme.primary` / Miuix `colorScheme.primary`。
 * 组件层内部用（行尾数量、恢复按钮、文字链接、编辑按钮）；**统计页的自绘图表也要用**
 * （柱体、排行榜的序号与「×N」、排行条），那是 [appSurfaceColor] 那条 KDoc 里说的
 * 「屏幕层必须自己取色」的既定例外 —— 图表是 `Canvas` / `Modifier.background` 画的，
 * 没有组件能替它拿色。
 */
@Composable
fun appPrimaryColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary
    }

/**
 * 最弱的一档前景色：**禁用状态的图标**（分类只剩一个时那个点不动的删除按钮）。
 * MD3 `outlineVariant` / Miuix `dividerLine` —— 合并前两版各自就是这么取的。
 * 名字按「弱到接近分割线」这个共同语义起，不叫 `appOutlineVariantColor`，
 * 免得暗示 Miuix 侧也是那个角色（它没有 outlineVariant）。
 */
@Composable
internal fun appFaintColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.dividerLine
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

/**
 * 最高一档容器色：MD3 / Miuix 同名角色 `surfaceContainerHighest`。
 * 统计页柱状图里「当天没有消耗」那根空柱用它当底色（自绘，理由同 [appPrimaryColor]）；
 * 组件层内部则用 `AppStepperPill` 的胶囊底。
 */
@Composable
fun appHighestContainerColor(): Color =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

/**
 * 图表调色板：8 个语义角色，随主题种子色 / 动态取色 / 深浅色自动适配，不硬编码 hex。
 *
 * **两版的角色清单不同，这是原样照抄而不是没对齐**：Miuix 色板没有 `tertiary` / `inversePrimary`，
 * 合并前 Miuix 版就用容器色与容器前景色替代，原注释写着「用其容器色/前景色替代，保持图表多色可辨」。
 * 若按 MD3 的角色名去"统一"，改的是 Miuix 侧的图表配色 —— 只有真机看得出来。
 */
@Composable
fun appChartColors(): List<Color> =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val cs = MiuixTheme.colorScheme
        remember(cs) {
            listOf(
                cs.primary,
                cs.secondary,
                cs.primaryContainer,
                cs.secondaryContainer,
                cs.tertiaryContainer,
                cs.onPrimaryContainer,
                cs.onSecondaryContainer,
                cs.onTertiaryContainer,
            )
        }
    } else {
        val cs = MaterialTheme.colorScheme
        remember(cs) {
            listOf(
                cs.primary,
                cs.tertiary,
                cs.secondary,
                cs.inversePrimary,
                cs.primaryContainer,
                cs.tertiaryContainer,
                cs.secondaryContainer,
                cs.outline,
            )
        }
    }
