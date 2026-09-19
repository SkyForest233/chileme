package com.agon.app.ui.components.app

// 语义字号档位：屏幕层写一次文字，两主题各自的 TextStyle 由这里映射。
//
// 为什么用档位枚举、而不是逐个包一个组件：详情页一页就用到 5 种档位（主标题 / 强调 / 小标题 /
// 正文 / 元信息），逐个包装会让 components/app/ 长出一排只差字号的函数，改一次要改五处；
// 一张映射表反而好核对（下面每个档位都标了合并前两版各自的取值）。
//
// 两主题的档位不是一一对应：Miuix 的 body1 同时承接 MD3 的 titleMedium 与 bodyLarge。这不是偷懒，
// 合并前 MiuixFoodDetailScreen 本来就这么用（剩余天数 / DetailRow 的值 / 按钮文案全是 body1），照抄。
//
// 默认值与两主题的 Text 完全对齐（已核对上游 v0.9.4-rc01 的 Text.kt 与 material3 的 Text）：
// color = Color.Unspecified（两边都表示「用默认内容色」）、fontWeight = null、
// fontSize = TextUnit.Unspecified、maxLines = Int.MAX_VALUE、overflow = TextOverflow.Clip。
//
// 2026-09-16 由 FoodDetailScreen + MiuixFoodDetailScreen 合并时抽出。

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 屏幕层用的语义字号档位。每项后面是合并前两版各自的取值。
 *
 * **这张表是登记表，不是换算表**：同一个 MD3 style 可以对应两个不同档位（`Heading` 与 `ItemTitle`
 * 都是 `titleSmall`，`Emphasis` 与 `SectionTitle` 都是 `titleMedium`），同一个 Miuix style 也可以
 * 被两个档位共用（`Emphasis` 与 `Body` 都是 `body1`，`Meta` 与 `Link` 都是 `body2`）。因为记的是
 * 「合并前那一处两版各自写的什么」，交叉恰恰是原版差异所在 —— 按字号推导去统一它们才是改行为。
 */
enum class AppTextScale {
    /** 页面主标题（详情页的食品名）：MD3 `headlineSmall` / Miuix `title2` */
    Hero,

    /** 强调文本（剩余天数、空态提示语）：MD3 `titleMedium` / Miuix `body1` */
    Emphasis,

    /** 卡片小标题（「库存数量」）：MD3 `titleSmall` / Miuix `subtitle` */
    Heading,

    /** 正文（详情行的值）：MD3 `bodyLarge` / Miuix `body1` */
    Body,

    /** 元信息（详情行的标签、行尾强调数字）：MD3 `bodyMedium` / Miuix `body2` */
    Meta,

    /** 弱化说明（列表副标题、卡片提示语）：MD3 `bodySmall` / Miuix `footnote2` */
    Hint,

    /** 大数字（首页统计卡的数值）：MD3 `headlineMedium` / Miuix `title2`（比 [Hero] 在 MD3 侧大一级） */
    Value,

    /** 小标签（统计卡标签、卡片内小标题「今日提醒」）：MD3 `labelMedium` / Miuix `footnote2` */
    Label,

    /** 分区标题（首页「需要处理」）：MD3 `titleMedium` / Miuix `title4` */
    SectionTitle,

    /**
     * 可点文字 / 控件文案（首页「全部食品」链接、列表页筛选切换按钮的「筛选(N)」）：
     * MD3 `labelLarge` / Miuix `body2`。第 4 对时叫 `Link`，第 5 对发现筛选按钮文案是同一映射，
     * 改成不限于链接的名字。
     */
    Action,

    /** 条目名称（首页「需要处理」列表里的食品名）：MD3 `titleSmall` / Miuix `body2` */
    ItemTitle,

    /**
     * 更小的标注（列表行的来源标注「月度合计」、筛选面板的分组小标题「状态/分类/位置」）：
     * MD3 `labelSmall` / Miuix `footnote2`。比 [Hint] 在 MD3 侧再小一档 —— 两处原本都这么写，
     * 第 5 对凑齐两个调用点后从 `AppListRow` 的私有实现提到表里。
     */
    Tag,

    /**
     * 弹窗选项行的标题（设置页「JSON 完整备份」「CSV 数据表格」「从本地历史快照恢复」这类）：
     * MD3 `bodyMedium` / Miuix `body1`。
     *
     * **表里第一个两侧不同级别的档位**：MD3 侧与 [Meta] 同级、Miuix 侧与 [Body] 同级。合并前两版就是这么写的，
     * 第 8 对凑齐四个调用点（导出格式 2 + 恢复来源 2）后提到表里 —— 不为了「表看着整齐」把任何一边挪一级，
     * 那是只有真机看得出来的字号改动。
     */
    OptionTitle,
}

/**
 * 双主题文字。字号档位查 [AppTextScale]，其余参数（颜色/字重/字号/行数/省略）默认值与两主题的
 * `Text` 一致，所以「不传 = 和合并前一样」。
 */
@Composable
fun AppText(
    text: String,
    scale: AppTextScale,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(
            text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            style = when (scale) {
                AppTextScale.Hero -> MiuixTheme.textStyles.title2
                AppTextScale.Emphasis -> MiuixTheme.textStyles.body1
                AppTextScale.Heading -> MiuixTheme.textStyles.subtitle
                AppTextScale.Body -> MiuixTheme.textStyles.body1
                AppTextScale.Meta -> MiuixTheme.textStyles.body2
                AppTextScale.Hint -> MiuixTheme.textStyles.footnote2
                AppTextScale.Value -> MiuixTheme.textStyles.title2
                AppTextScale.Label -> MiuixTheme.textStyles.footnote2
                AppTextScale.SectionTitle -> MiuixTheme.textStyles.title4
                AppTextScale.Action -> MiuixTheme.textStyles.body2
                AppTextScale.ItemTitle -> MiuixTheme.textStyles.body2
                AppTextScale.Tag -> MiuixTheme.textStyles.footnote2
                AppTextScale.OptionTitle -> MiuixTheme.textStyles.body1
            },
        )
    } else {
        Text(
            text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            style = when (scale) {
                AppTextScale.Hero -> MaterialTheme.typography.headlineSmall
                AppTextScale.Emphasis -> MaterialTheme.typography.titleMedium
                AppTextScale.Heading -> MaterialTheme.typography.titleSmall
                AppTextScale.Body -> MaterialTheme.typography.bodyLarge
                AppTextScale.Meta -> MaterialTheme.typography.bodyMedium
                AppTextScale.Hint -> MaterialTheme.typography.bodySmall
                AppTextScale.Value -> MaterialTheme.typography.headlineMedium
                AppTextScale.Label -> MaterialTheme.typography.labelMedium
                AppTextScale.SectionTitle -> MaterialTheme.typography.titleMedium
                AppTextScale.Action -> MaterialTheme.typography.labelLarge
                AppTextScale.ItemTitle -> MaterialTheme.typography.titleSmall
                AppTextScale.Tag -> MaterialTheme.typography.labelSmall
                AppTextScale.OptionTitle -> MaterialTheme.typography.bodyMedium
            },
        )
    }
}

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
 * 纯 emoji 文本：**不指定 style**。
 *
 * 两主题「不传 style」时的默认正文样式并不相同（MD3 取 `LocalTextStyle`，Miuix 取
 * `LocalTextStyles.current.main`），所以这里刻意不套 [AppTextScale] —— 套任何一档都等于
 * 悄悄改掉其中一边的原样。详情页那颗飘起来的「😋」在用（合并前两版都只传 `fontSize = 28.sp`）。
 * 列表行的 emoji 不走这里：Miuix 侧原本显式用了 `title3`，仍由 `AppListRow` 内部的 RowEmoji 处理。
 */
@Composable
fun AppEmojiText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixText(text, modifier = modifier, fontSize = fontSize)
    } else {
        Text(text, modifier = modifier, fontSize = fontSize)
    }
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
 * 弱化文字（任意档位）：MD3 `onSurfaceVariant` / Miuix `onSurfaceVariantSummary`。
 *
 * [AppHintText] 是它的 Hint 档特例（第 1 对就有，保留以免动已真机验证的调用点）；
 * 要别的档位就用这个 —— 统计页的图例走 Meta 档、环图中心的小字与柱状图的日期走 Tag 档。
 * **别在屏幕里自己取色再拼一个 `Text`**，否则两主题的色板角色又要各写一遍。
 */
@Composable
fun AppMutedText(
    text: String,
    scale: AppTextScale,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    AppText(
        text,
        scale,
        modifier = modifier,
        color = appMutedColor(),
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
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
