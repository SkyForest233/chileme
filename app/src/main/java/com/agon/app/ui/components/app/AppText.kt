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
//
// 2026-09-19（#11a）：本文件里的 9 个跨主题**取色** helper 已抽到同包 `AppColors.kt` —— 那件事与「文字」无关，
// 只是当初一起住在这里。调用方的 import 路径**没变**（同包），新增取色口子请去那边加、别塞回来。

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
