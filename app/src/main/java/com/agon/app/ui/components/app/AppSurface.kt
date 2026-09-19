package com.agon.app.ui.components.app

// 卡片容器与弱化文字：两主题的圆角/色板不同，这里做一次映射，屏幕层只写一份。
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出；
// 同日第 3 对（详情页）补 AppStatusCard，并把 AppHintText 改成走 AppText 的档位表（取值不变）；
// 第 4 对（首页）补 AppStatCard，并给 AppPaddedCard 加可点形态（首页紧急行在用）。
// 第 7 对（统计页）补 AppSection / AppStatsListMetrics，并给 AppStatCard 加可空 onClick 与三个度量参数
//（emojiSize / valueSpacing / valueScale）—— 默认值是首页那一版的，首页三个调用点一字未动。

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表行/信息块的卡片底：MD3 走 `shapes.large` + `surfaceContainer`，
 * Miuix 走 16dp 圆角 + 同名色。不传 `contentColor`，与合并前两版的用法一致。
 *
 * @param miuixCornerRadius **只影响 Miuix 侧的圆角**（MD3 侧恒走 `shapes.large` 这个 token，
 *   按 `docs/ARCHITECTURE.md` 的约束 ⑥ 点名哪边不生效）。默认 16dp = Miuix 库 `Surface` 的默认圆角，
 *   也是合并前消耗记录行等调用点的取值。管理页三行（阈值 / 分类 / 位置）合并前显式写了
 *   `RoundedCornerShape(24.dp)`，与 MD3 侧 `shapes.large`（= 24dp）同值，故那边传 `24.dp`。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    miuixCornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(miuixCornerRadius),
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
/**
 * 卡片底色的语义档。
 *
 * ⚠️ **只影响 MD3 侧**：Miuix 的 `Card` 两版都用库默认底色（归档页的行与列表页「归档中找到」的行
 * 都是默认值），所以 [ContainerLow] 在 Miuix 分支没有可见效果 —— 不是静默忽略参数，是原版 Miuix
 * 两态本来就同色。MD3 侧 `surfaceContainer` 与 `surfaceContainerLow` 是两个相邻但**不同**的
 * surface 角色（本项目色板由 seed 动态生成，两者不同值），合并前两版分别用过，不许统一。
 */
enum class AppCardTone { Container, ContainerLow }

@Composable
fun AppPaddedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tone: AppCardTone = AppCardTone.Container,
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
            colors = CardDefaults.cardColors(containerColor = md3CardColor(tone)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = md3CardColor(tone),
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
 * 统计页的 `MiniStat` 与它同构但不同参（emoji 20sp、间隔 6dp、数值走 Hero 档、不可点）。
 * **第 7 对两边都看得见之后的决定是「加参数」而不是「各留一份」**：两者的结构逐字同构
 * （同一个 `Column(padding 14dp)` + emoji + 间隔 + 数值 + 标签），差别全是**值**，
 * 且三套容器色正好就是 [AppStatTone] 的三档 —— 各留一份等于把同一段结构抄两遍。
 * 三个新参数的默认值 = 首页原值，**已真机验证的首页调用点一行未改**；`value` 也不用改成 String，
 * 统计页那三个值本来就是 Int（`consumedThisWeek` / `consumedThisMonth` / `wastedTotal`）。
 *
 * @param onClick 传 null = 不可点。此时 **MD3 侧走 `Surface` 而不是 `Card(onClick)`**
 *   （合并前统计页的 `MiniStat` 正是 `Surface`：无涟漪、无 button 语义），与 [AppPaddedCard] 同一处理。
 * @param emojiSize / @param valueSpacing / @param valueScale 首页 22sp / 8dp / Value 档，
 *   统计页 20sp / 6dp / Hero 档（两版各自的原值，不统一）。
 */
@Composable
fun AppStatCard(
    emoji: String,
    value: Int,
    label: String,
    tone: AppStatTone,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    emojiSize: TextUnit = 22.sp,
    valueSpacing: Dp = 8.dp,
    valueScale: AppTextScale = AppTextScale.Value,
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
        val colors = MiuixCardDefaults.defaultColors(color = container, contentColor = content)
        if (onClick != null) {
            MiuixCard(onClick = onClick, modifier = modifier, colors = colors) {
                StatCardBody(emoji, value, label, emojiSize, valueSpacing, valueScale)
            }
        } else {
            MiuixCard(modifier = modifier, colors = colors) {
                StatCardBody(emoji, value, label, emojiSize, valueSpacing, valueScale)
            }
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
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                StatCardBody(emoji, value, label, emojiSize, valueSpacing, valueScale)
            }
        } else {
            Surface(
                modifier = modifier,
                shape = MaterialTheme.shapes.large,
                color = container,
                contentColor = content,
            ) {
                StatCardBody(emoji, value, label, emojiSize, valueSpacing, valueScale)
            }
        }
    }
}

/** 统计卡内容：两主题完全一致，所以只写一份。 */
@Composable
private fun StatCardBody(
    emoji: String,
    value: Int,
    label: String,
    emojiSize: TextUnit,
    valueSpacing: Dp,
    valueScale: AppTextScale,
) {
    Column(Modifier.padding(14.dp)) {
        AppEmojiText(emoji, fontSize = emojiSize)
        Spacer(Modifier.height(valueSpacing))
        AppText("$value", valueScale, fontWeight = FontWeight.ExtraBold)
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
    AppMutedText(text, AppTextScale.Hint, modifier = modifier)
}

/** MD3 侧的卡片底色：按 [AppCardTone] 在两个相邻的 surface 角色之间选（Miuix 侧不需要，见枚举 KDoc）。 */
@Composable
private fun md3CardColor(tone: AppCardTone): Color =
    when (tone) {
        AppCardTone.Container -> MaterialTheme.colorScheme.surfaceContainer
        AppCardTone.ContainerLow -> MaterialTheme.colorScheme.surfaceContainerLow
    }

/**
 * 「一节 = 标题 + 卡片 + 内容」的外壳（统计页三节：消耗趋势 / 库存分类 / 消耗排行）。
 *
 * **两主题的节标题是不同控件，不是同一样式的两种映射**：
 * - MD3：标题在卡片**里面**（`titleMedium` + Bold = [AppTextScale.SectionTitle] 档在 MD3 侧的取值），
 *   下面接 [titleSpacing] 的间隔再接内容；卡片是 `Surface(shapes.large, surfaceContainer)` → 走 [AppCard]。
 * - Miuix：标题是库的 `SmallTitle`，在卡片**外面**，卡片内既无标题也无间隔；
 *   卡片是官方 `Card`（带库默认内衬）→ 走 [AppPaddedCard]，且自己带 12dp 水平边距。
 *
 * 「谁用 [AppCard] 谁用 [AppPaddedCard]，由合并前那一版用的是什么决定」那条规矩，在这一节里
 * 两种同时用上了 —— MD3 分支只走 `AppCard` 的 MD3 路径、Miuix 分支只走 `AppPaddedCard` 的 Miuix 路径。
 *
 * 两版的标题**文案也不同**（「近 7 天消耗趋势」vs「消耗趋势」、「库存分类占比」vs「库存分类」、
 * 「消耗排行榜」vs「消耗排行」），所以是两个参数而不是一个：这不是冗余，是合并前两版各自的措辞，
 * 统一任何一边都是改文案。
 *
 * @param titleSpacing **只在 MD3 侧生效**（Miuix 侧卡片内没有标题，自然没有这段间隔）—— 按约束 ⑥ 点名。
 *   合并前 MD3 三节分别是 16dp / 16dp / 12dp，故留参数不统一。
 * @param modifier MD3 侧作用于卡片（内部补 `fillMaxWidth()`）；Miuix 侧作用于「SmallTitle + 卡片」这一整列。
 */
@Composable
fun AppSection(
    cardTitle: String,
    sectionTitle: String,
    modifier: Modifier = Modifier,
    titleSpacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        Column(modifier) {
            MiuixSmallTitle(text = sectionTitle)
            AppPaddedCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    content()
                }
            }
        }
    } else {
        AppCard(modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                AppText(cardTitle, AppTextScale.SectionTitle, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(titleSpacing))
                content()
            }
        }
    }
}

/**
 * 统计页列表的度量。**两版这里真的不一样，糊在一起就会改其中一边的版式**：
 * - MD3：`LazyColumn` 的 `contentPadding` 给左右 20dp、项间距 16dp，各 item 自己不再加水平边距；
 * - Miuix：`contentPadding` 左右 0、项间距 12dp，**非卡片的 item 自己加 20dp**（统计卡那一行、到期日历），
 *   卡片那 12dp 由 [AppSection] 内部处理，不在这个结构里。
 *
 * 卡片最终的水平内缩两版本来就不同（MD3 20dp / Miuix 12dp），非卡片 item 则都是 20dp。
 * 三个值一起给，别让屏幕自己拼 —— 拼错任何一个是只有真机看得出来的版式改动。
 */
class AppStatsListMetrics(
    val contentPaddingHorizontal: Dp,
    val itemSpacing: Dp,
    val itemPaddingHorizontal: Dp,
)

@Composable
fun appStatsListMetrics(): AppStatsListMetrics =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        AppStatsListMetrics(0.dp, 12.dp, 20.dp)
    } else {
        AppStatsListMetrics(20.dp, 16.dp, 0.dp)
    }
