package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.components.ExpiryCalendarCard
import com.agon.app.ui.components.app.AppArchiveAction
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppStatCard
import com.agon.app.ui.components.app.AppStatTone
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appChartColors
import com.agon.app.ui.components.app.appStatsListMetrics
import com.agon.app.viewmodel.AppViewModel

/**
 * 统计页：三张 mini 统计卡（本周消耗 / 本月消耗 / 过期浪费）+ 到期日历 + 近 7 天消耗趋势柱状图
 * + 库存分类占比环图（带图例）+ 消耗排行榜。
 *
 * 2026-09-16 由 `StatsScreen.kt`(455) + `MiuixStatsScreen.kt`(445) 合并为单文件双主题（第三批 #3 第 7 对）。
 * 两版去掉 package/import/注释后是 383 / 362 行代码，其中 **271 行逐字相同**（把 `MaterialTheme`↔`MiuixTheme`、
 * `.typography.`↔`.textStyles.`、`Miuix` 前缀归一化后是 294 行）—— **图表是 `Canvas` 与 `layout` 自绘的，本来就与主题无关**，
 * 差异只在文字档位、取色、以及「节标题放哪儿」这三类。**本文件只管装配**（状态取一次、区块按顺序排、
 * `LazyColumn` 的度量与卡片宽度在这里给）；画法在同包三个区块文件里 —— `StatsTrendSection.kt`（柱状图）、
 * `StatsCategorySection.kt`（环图 + 图例）、`StatsTopConsumedSection.kt`（排行榜），#11d ① 又把 `DonutChart` /
 * `LegendRow` 这两个图表件下沉到 `ui/components/StatsCharts.kt`（合并后两份变一份）。位置判据：`StatsSectionLocationTest`。
 *
 * **业务量一律走已测状态层**（这条是 2026-09-15 的修复，合并前 Miuix 版的文件头注释就是它）：
 * 此前 Miuix 那一版把 `StatsState` 的计算手抄了一遍，于是 `StatsStateTest` 测的是「MIUIX 主题下
 * 根本不会执行」的那份代码 —— 两份实现、一份被测，是静默分叉的典型温床。现在本页只负责外壳与装配
 * （图表自绘在 `ui/components/StatsCharts.kt`，屏幕层不许自绘由 `StatsChartsLocationTest` 守着），数据一律来自 [rememberStatsUiState]，业务计算禁止在本文件内重写（`ScreenParityTest` 静态拦截）。
 *
 * 几处照抄而非统一的地方（细节在各组件的 KDoc 里）：
 * - **节标题两边是不同控件**：MD3 在卡片内（`titleMedium` + Bold + 间隔），Miuix 是卡片外的库
 *   `SmallTitle`、卡片内无标题；**连文案都不同**（「近 7 天消耗趋势」vs「消耗趋势」等）→ [AppSection] 两个参数。
 * - **列表度量不同**：MD3 用 `contentPadding` 给左右 20dp、项间距 16dp；Miuix `contentPadding` 左右 0、
 *   项间距 12dp、非卡片 item 自己加 20dp、卡片自己加 12dp → [appStatsListMetrics] 三个值一起给。
 * - **图表调色板 8 个角色两边不同**：Miuix 色板没有 `tertiary` / `inversePrimary`，合并前就用容器色与
 *   容器前景色替代 → [appChartColors] 照抄两份清单。
 * - **mini 统计卡**：与首页三张统计卡同构不同参（emoji 20sp vs 22sp、间隔 6dp vs 8dp、数值 Hero 档 vs
 *   Value 档、不可点 vs 可点），第 4 对留的悬案这次判了「给 [AppStatCard] 加参数」，理由见它的 KDoc。
 *
 * 两处非等价改动（都是收敛到更好的一边，刻意）：
 * ① 排行榜的点击目标用 `state.findItemIdByName(name)`（MD3 版的写法）—— Miuix 版在 UI 里内联了
 *   `items.firstOrNull { it.name == name }?.id`，属业务查找漏进渲染层，语义相同。
 * ② 柱高比例用 MD3 版的 `if (state.maxDaily > 0) … else 0f` 有守卫写法（这段画法现在在 `StatsTrendSection.kt`）。
 *   Miuix 版直接除；
 *   实际上 `StatsState` 里 `maxDaily` 已 `.coerceAtLeast(1)`，两版结果相同（**不存在 NaN**），
 *   取有守卫的那份只是口径统一，不是修 bug。
 * `animateFloatAsState` 的 `label` 统一用 MD3 版的（`"bar"` / `"topRankBar"`，Miuix 版带 `miuix` 前缀
 * 只为区分双胞胎文件）—— 与第 5 对空态 `Crossfade` 的 `label` 同一处理，只是转场调试标识。
 */
@Composable
fun StatsScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit = {},
    onOpenConsumption: () -> Unit = {},
) {
    val state = rememberStatsUiState(viewModel)
    val metrics = appStatsListMetrics()
    val chartColors = appChartColors()

    AppScaffold(
        title = "统计",
        actions = {
            AppArchiveAction(onClick = onOpenConsumption, contentDescription = "消耗记录")
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = metrics.contentPaddingHorizontal,
                end = metrics.contentPaddingHorizontal,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = metrics.itemPaddingHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppStatCard(
                        emoji = "😋",
                        value = state.consumedThisWeek,
                        label = "本周消耗",
                        tone = AppStatTone.Primary,
                        modifier = Modifier.weight(1f),
                        emojiSize = 20.sp,
                        valueSpacing = 6.dp,
                        valueScale = AppTextScale.Hero,
                    )
                    AppStatCard(
                        emoji = "📅",
                        value = state.consumedThisMonth,
                        label = "本月消耗",
                        tone = AppStatTone.Secondary,
                        modifier = Modifier.weight(1f),
                        emojiSize = 20.sp,
                        valueSpacing = 6.dp,
                        valueScale = AppTextScale.Hero,
                    )
                    AppStatCard(
                        emoji = "🗑️",
                        value = state.wastedTotal,
                        label = "过期浪费",
                        tone = AppStatTone.Error,
                        modifier = Modifier.weight(1f),
                        emojiSize = 20.sp,
                        valueSpacing = 6.dp,
                        valueScale = AppTextScale.Hero,
                    )
                }
            }

            // ---- 到期日历（带紧急度彩色圆点 + 左右滑动切换月份）----
            item {
                ExpiryCalendarCard(
                    items = state.items,
                    thresholds = state.thresholds,
                    categories = state.categories,
                    onOpenItem = onOpenItem,
                    modifier = Modifier.padding(horizontal = metrics.itemPaddingHorizontal),
                )
            }

            // 近 7 天趋势：柱状图的画法在同包 `StatsTrendSection.kt`（本文件只管装配）
            item { StatsTrendSection(state = state) }

            // 库存分类占比：环图与图例的画法在同包 `StatsCategorySection.kt`（本文件只管装配）
            item { StatsCategorySection(state = state, chartColors = chartColors) }

            // 消耗排行榜：排行条与数量的画法在同包 `StatsTopConsumedSection.kt`（本文件只管装配）
            item { StatsTopConsumedSection(state = state, onOpenItem = onOpenItem) }
        }
    }
}
