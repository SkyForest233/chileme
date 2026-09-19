package com.agon.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.CategoryDef
import com.agon.app.data.byId
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.ExpiryCalendarCard
import com.agon.app.ui.components.app.AppArchiveAction
import com.agon.app.ui.components.app.AppEmojiText
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSection
import com.agon.app.ui.components.app.AppStatCard
import com.agon.app.ui.components.app.AppStatTone
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appChartColors
import com.agon.app.ui.components.app.appHighestContainerColor
import com.agon.app.ui.components.app.appPrimaryColor
import com.agon.app.ui.components.app.appPrimaryContainerColor
import com.agon.app.ui.components.app.appStatsListMetrics
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel

/**
 * 统计页：三张 mini 统计卡（本周消耗 / 本月消耗 / 过期浪费）+ 到期日历 + 近 7 天消耗趋势柱状图
 * + 库存分类占比环图（带图例）+ 消耗排行榜。
 *
 * 2026-09-16 由 `StatsScreen.kt`(455) + `MiuixStatsScreen.kt`(445) 合并为单文件双主题（第三批 #3 第 7 对）。
 * 两版去掉 package/import/注释后是 383 / 362 行代码，其中 **271 行逐字相同**（把 `MaterialTheme`↔`MiuixTheme`、
 * `.typography.`↔`.textStyles.`、`Miuix` 前缀归一化后是 294 行）—— **图表是 `Canvas` 与 `layout` 自绘的，本来就与主题无关**，
 * 差异只在文字档位、取色、以及「节标题放哪儿」这三类。合并后 `DonutChart` / `LegendRow` 两份变一份，
 * 柱状图与排行榜行的代码留在本文件（它们只此一处用，且已经不含任何主题分支）。
 *
 * **业务量一律走已测状态层**（这条是 2026-09-15 的修复，合并前 Miuix 版的文件头注释就是它）：
 * 此前 Miuix 那一版把 `StatsState` 的计算手抄了一遍，于是 `StatsStateTest` 测的是「MIUIX 主题下
 * 根本不会执行」的那份代码 —— 两份实现、一份被测，是静默分叉的典型温床。现在本页只负责外壳与
 * 图表自绘，数据一律来自 [rememberStatsUiState]，业务计算禁止在本文件内重写（`ScreenParityTest` 静态拦截）。
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
 * ② 柱高比例用 MD3 版的 `if (state.maxDaily > 0) … else 0f` 有守卫写法。Miuix 版直接除；
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

            // ---- 近 7 天消耗趋势（柱状图）----
            item {
                AppSection(cardTitle = "近 7 天消耗趋势", sectionTitle = "消耗趋势") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        state.dailyTrend.forEach { (date, amount) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (amount > 0) {
                                    AppText(
                                        "$amount",
                                        AppTextScale.Tag,
                                        fontWeight = FontWeight.Bold,
                                        color = appPrimaryColor(),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                val ratio = if (state.maxDaily > 0) amount.toFloat() / state.maxDaily else 0f
                                val animRatio = animateFloatAsState(
                                    targetValue = ratio,
                                    animationSpec = tween(600, easing = MotionEasing.EmphasizedDecelerate),
                                    label = "bar",
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.62f)
                                        .layout { measurable, constraints ->
                                            val minH = if (amount > 0) 14.dp.roundToPx() else 8.dp.roundToPx()
                                            val h = (84.dp.roundToPx() * animRatio.value).toInt().coerceAtLeast(minH)
                                            val placeable = measurable.measure(
                                                constraints.copy(minHeight = h, maxHeight = h),
                                            )
                                            layout(placeable.width, h) { placeable.placeRelative(0, 0) }
                                        }
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (amount > 0) appPrimaryColor() else appHighestContainerColor(),
                                        ),
                                )
                                Spacer(Modifier.height(6.dp))
                                AppMutedText(
                                    "${date.monthValue}/${date.dayOfMonth}",
                                    AppTextScale.Tag,
                                )
                            }
                        }
                    }
                }
            }

            // ---- 库存分类占比（环图 + 图例）----
            item {
                AppSection(cardTitle = "库存分类占比", sectionTitle = "库存分类") {
                    if (state.categoryShare.isEmpty()) {
                        AppMutedText("暂无库存数据", AppTextScale.Meta)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DonutChart(
                                data = state.categoryShare.map { it.second.toFloat() },
                                colors = state.categoryShare.mapIndexed { i, _ -> chartColors[i % chartColors.size] },
                                centerLabel = "${state.totalQty}",
                                centerSub = "总件数",
                            )
                            Spacer(Modifier.height(16.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                state.categoryShare.forEachIndexed { i, (catId, qty) ->
                                    LegendRow(
                                        color = chartColors[i % chartColors.size],
                                        category = state.categories.byId(catId),
                                        qty = qty,
                                        percent = if (state.totalQty > 0) qty * 100 / state.totalQty else 0,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- 消耗排行榜 ----
            item {
                AppSection(
                    cardTitle = "消耗排行榜",
                    sectionTitle = "消耗排行",
                    titleSpacing = 12.dp,
                ) {
                    if (state.topConsumed.isEmpty()) {
                        EmptyState(
                            emoji = "🍽️",
                            title = "还没有消耗记录",
                            subtitle = "在详情页点“吃掉一份”或减少库存后这里会有数据",
                        )
                    } else {
                        val maxAmount = state.topConsumed.first().third.coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.topConsumed.forEachIndexed { index, (name, cat, amount) ->
                                // 点击进入对应食品详情（可编辑）；找不到对应食品就不可点
                                val targetId = state.findItemIdByName(name)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = if (targetId != null) {
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onOpenItem(targetId) }
                                            .padding(vertical = 4.dp)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    },
                                ) {
                                    AppText(
                                        "${index + 1}",
                                        AppTextScale.Heading,
                                        modifier = Modifier.width(20.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = appPrimaryColor(),
                                    )
                                    AppEmojiText(state.categories.byId(cat).emoji, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        AppText(
                                            name,
                                            AppTextScale.Meta,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        val animFraction by animateFloatAsState(
                                            targetValue = (amount.toFloat() / maxAmount).coerceIn(0.04f, 1f),
                                            animationSpec = tween(600, easing = MotionEasing.EmphasizedDecelerate),
                                            label = "topRankBar",
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animFraction)
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(appPrimaryContainerColor()),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    AppText(
                                        "×$amount",
                                        AppTextScale.Action,
                                        fontWeight = FontWeight.Bold,
                                        color = appPrimaryColor(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 库存分类占比的环图：`Canvas` 自绘，800ms 扫出动画，中心叠总件数。
 *
 * 合并前两版**除了中心两行文字的样式以外逐字相同**（连 `Stroke(width = 30f)`、`topLeft = Offset(15f, 15f)`、
 * 每段之间留 3° 缝隙、最小 1° 的兜底都一样），所以两份并成一份：中心大字走 [AppTextScale.Hero]
 * （MD3 `headlineSmall` / Miuix `title2`）、小字走 [AppTextScale.Tag] + 弱化色
 * （MD3 `labelSmall` + `onSurfaceVariant` / Miuix `footnote2` + `onSurfaceVariantSummary`）。
 * 弧色由调用方传（[appChartColors] 的两份清单不同，见其 KDoc）。
 */
@Composable
private fun DonutChart(
    data: List<Float>,
    colors: List<Color>,
    centerLabel: String,
    centerSub: String,
) {
    val total = data.sum().coerceAtLeast(0.001f)
    val sweep = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = MotionEasing.EmphasizedDecelerate),
        label = "donut",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = Stroke(width = 30f)
            var startAngle = -90f
            val sweepValue = sweep.value
            data.forEachIndexed { i, value ->
                val angle = value / total * 360f * sweepValue
                drawArc(
                    color = colors[i],
                    startAngle = startAngle,
                    sweepAngle = (angle - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    style = stroke,
                    topLeft = Offset(15f, 15f),
                    size = Size(size.width - 30f, size.height - 30f),
                )
                startAngle += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(centerLabel, AppTextScale.Hero, fontWeight = FontWeight.ExtraBold)
            AppMutedText(centerSub, AppTextScale.Tag)
        }
    }
}

/**
 * 环图的图例一行：色点 + 「emoji 分类名」+「N 件 · P%」。
 * 两版只有文字样式不同（都是 MD3 `bodyMedium` / Miuix `body2` = [AppTextScale.Meta] 档），故并成一份。
 */
@Composable
private fun LegendRow(color: Color, category: CategoryDef, qty: Int, percent: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        AppText(
            "${category.emoji} ${category.label}",
            AppTextScale.Meta,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        AppMutedText("$qty 件 · $percent%", AppTextScale.Meta, maxLines = 1)
    }
}
