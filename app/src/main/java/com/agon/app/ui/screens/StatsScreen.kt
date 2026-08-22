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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/**
 * 图表调色板：全部取自 MaterialTheme.colorScheme 语义角色，
 * 随主题种子色 / 动态取色 / 深浅色自动适配，不硬编码 hex。
 */
@Composable
private fun rememberChartColors(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: AppViewModel,
    onOpenItem: (String) -> Unit = {},
    onOpenConsumption: () -> Unit = {},
) {
    val state = rememberStatsUiState(viewModel)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("统计", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenConsumption) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "消耗记录",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "😋",
                        value = "${state.consumedThisWeek}",
                        label = "本周消耗",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "📅",
                        value = "${state.consumedThisMonth}",
                        label = "本月消耗",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    MiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "🗑️",
                        value = "${state.wastedTotal}",
                        label = "过期浪费",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
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
                )
            }

            // ---- 7-day consumption bar chart ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "近 7 天消耗趋势",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
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
                                        Text(
                                            "$amount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
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
                                                if (amount > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${date.monthValue}/${date.dayOfMonth}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Category pie chart ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "库存分类占比",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (state.categoryShare.isEmpty()) {
                            Text(
                                "暂无库存数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val chartColors = rememberChartColors()
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
            }

            // ---- Top consumed ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "消耗排行榜",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
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
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(20.dp),
                                        )
                                        Text(state.categories.byId(cat).emoji, fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.bodyMedium,
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
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "×$amount",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
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
private fun MiniStat(
    modifier: Modifier,
    emoji: String,
    value: String,
    label: String,
    container: Color,
    content: Color,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

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
            Text(centerLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                centerSub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
        Text(
            "${category.emoji} ${category.label}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$qty 件 · $percent%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
