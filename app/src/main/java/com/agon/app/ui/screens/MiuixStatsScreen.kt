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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.CategoryDef
import com.agon.app.data.byId
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.ExpiryCalendarCard
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统计页的 Miuix（HyperOS）实现（v2.8）。
 *
 * 与 [StatsScreen] 逻辑对等；外壳用 Miuix `Scaffold/TopAppBar/SmallTitle/Card` 分组，
 * 图表为 Canvas/Box 自绘（无 MD3 结构性组件），复用桥接 MaterialTheme 取 Miuix 配色。
 *
 * 编辑功能：消耗排行榜项点击 → 进入对应食品详情（可编辑）。
 */
@Composable
fun MiuixStatsScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit = {},
    onOpenConsumption: () -> Unit = {},
) {
    // 业务量一律走已测状态层（2026-09-15 修复）。
    //
    // 此前本页把 StatsState 的计算手抄了一遍，于是 StatsStateTest 测的是
    // 「MIUIX 主题下根本不会执行」的那份代码 —— 两份实现、一份被测，
    // 是静默分叉的典型温床（改统计口径只需改一处就会两套主题不一致）。
    // 现在本页只负责**外壳**（Miuix Scaffold/Card/SmallTitle）与图表自绘，
    // 数据一律来自 [rememberStatsUiState]；业务计算禁止在本文件内重写。
    val state = rememberStatsUiState(viewModel)
    val items = state.items
    val categories = state.categories
    val thresholds = state.thresholds

    val consumedThisWeek = state.consumedThisWeek
    val consumedThisMonth = state.consumedThisMonth
    val wastedTotal = state.wastedTotal
    val dailyTrend = state.dailyTrend
    val maxDaily = state.maxDaily
    val categoryShare = state.categoryShare
    val totalQty = state.totalQty
    val topConsumed = state.topConsumed

    val chartColors = rememberChartColorsMiuix()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "统计",
                actions = {
                    IconButton(onClick = onOpenConsumption) {
                        Icon(
                            MiuixIcons.Recent,
                            contentDescription = "消耗记录",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixMiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "😋",
                        value = "$consumedThisWeek",
                        label = "本周消耗",
                        container = MiuixTheme.colorScheme.primaryContainer,
                        content = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                    MiuixMiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "📅",
                        value = "$consumedThisMonth",
                        label = "本月消耗",
                        container = MiuixTheme.colorScheme.secondaryContainer,
                        content = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                    MiuixMiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "🗑️",
                        value = "$wastedTotal",
                        label = "过期浪费",
                        container = MiuixTheme.colorScheme.errorContainer,
                        content = MiuixTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            item {
                ExpiryCalendarCard(
                    items = items,
                    thresholds = thresholds,
                    categories = categories,
                    onOpenItem = onOpenItem,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            item {
                SmallTitle(text = "消耗趋势")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            dailyTrend.forEach { (date, amount) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (amount > 0) {
                                        Text(
                                            "$amount",
                                            style = MiuixTheme.textStyles.footnote2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    val ratio = amount.toFloat() / maxDaily
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
                                                if (amount > 0) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.surfaceContainerHighest
                                            ),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${date.monthValue}/${date.dayOfMonth}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "库存分类")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        if (categoryShare.isEmpty()) {
                            Text(
                                "暂无库存数据",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MiuixDonutChart(
                                    data = categoryShare.map { it.second.toFloat() },
                                    colors = categoryShare.mapIndexed { i, _ -> chartColors[i % chartColors.size] },
                                    centerLabel = "$totalQty",
                                    centerSub = "总件数",
                                )
                                Spacer(Modifier.height(16.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    categoryShare.forEachIndexed { i, (catId, qty) ->
                                        MiuixLegendRow(
                                            color = chartColors[i % chartColors.size],
                                            category = categories.byId(catId),
                                            qty = qty,
                                            percent = if (totalQty > 0) qty * 100 / totalQty else 0,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SmallTitle(text = "消耗排行")
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        if (topConsumed.isEmpty()) {
                            EmptyState(
                                emoji = "🍽️",
                                title = "还没有消耗记录",
                                subtitle = "在详情页点“吃掉一份”或减少库存后这里会有数据",
                            )
                        } else {
                            val maxAmount = topConsumed.first().third.coerceAtLeast(1)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                topConsumed.forEachIndexed { index, (name, cat, amount) ->
                                    // 点击进入对应食品详情（可编辑）
                                    val targetId = items.firstOrNull { it.name == name }?.id
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
                                        Text(
                                            "${index + 1}",
                                            style = MiuixTheme.textStyles.subtitle,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.width(20.dp),
                                        )
                                        Text(categories.byId(cat).emoji, fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                name,
                                                style = MiuixTheme.textStyles.body2,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            val animFraction by animateFloatAsState(
                                                targetValue = (amount.toFloat() / maxAmount).coerceIn(0.04f, 1f),
                                                animationSpec = tween(600, easing = MotionEasing.EmphasizedDecelerate),
                                                label = "miuixTopRankBar",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(animFraction)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MiuixTheme.colorScheme.primaryContainer),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "×$amount",
                                            style = MiuixTheme.textStyles.body2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
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
}

@Composable
private fun rememberChartColorsMiuix(): List<Color> {
    // Miuix Colors 无 tertiary / inversePrimary 字段，用其容器色/前景色替代，保持图表多色可辨。
    val cs = MiuixTheme.colorScheme
    return remember(cs) {
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
}

@Composable
private fun MiuixMiniStat(
    modifier: Modifier,
    emoji: String,
    value: String,
    label: String,
    container: Color,
    content: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = container, contentColor = content),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MiuixTheme.textStyles.footnote2)
        }
    }
}

@Composable
private fun MiuixDonutChart(
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
            Text(centerLabel, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.ExtraBold)
            Text(
                centerSub,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun MiuixLegendRow(color: Color, category: CategoryDef, qty: Int, percent: Int) {
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
        Text(
            "${category.emoji} ${category.label}",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$qty 件 · $percent%",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}
