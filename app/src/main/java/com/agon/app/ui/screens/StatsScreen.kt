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
import com.agon.app.data.byId
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.ExpiryCalendarCard
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate

// 参考设计：单色系深浅绿阶梯 + 少量蓝色点缀，保持整体薄荷绿氛围
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

            // ---- 到期日历卡片 ----
            item {
                ExpiryCalendarCard(
                    items = state.items,
                    thresholds = state.thresholds,
                    categories = state.categories,
                    onOpenItem = onOpenItem,
                )
            }

            // ---- 7-Day bar chart ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            state.dailyTrend.forEach { (date, amount) ->
                                val heightRatio = if (state.maxDaily > 0) amount.toFloat() / state.maxDaily else 0f
                                val isToday = date.toEpochDay() == state.todayDate.toEpochDay()
                                val barTargetHeight = if (amount > 0) (80 * heightRatio).coerceAtLeast(8f) else 4f
                                val barHeightAnim by animateFloatAsState(
                                    targetValue = barTargetHeight,
                                    animationSpec = tween(400, easing = MotionEasing.Emphasized),
                                    label = "barHeight",
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (amount > 0) {
                                        Text(
                                            "$amount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .layout { measurable, constraints ->
                                                val hPx = barHeightAnim.dp.roundToPx()
                                                val placeable = measurable.measure(
                                                    constraints.copy(minHeight = hPx, maxHeight = hPx)
                                                )
                                                layout(placeable.width, placeable.height) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            }
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                if (amount == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                                else if (isToday) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            ),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${date.dayOfMonth}日",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Category distribution ring chart ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val animProgress by animateFloatAsState(
                                    targetValue = 1f,
                                    animationSpec = tween(600, easing = MotionEasing.Emphasized),
                                    label = "ringChart",
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(130.dp),
                                ) {
                                    Canvas(Modifier.size(130.dp)) {
                                        val strokeWidth = 22.dp.toPx()
                                        val arcSize = size.width - strokeWidth
                                        var startAngle = -90f
                                        state.categoryShare.forEachIndexed { i, (_, qty) ->
                                            val sweep = (qty.toFloat() / state.totalQty) * 360f * animProgress
                                            drawArc(
                                                color = chartColors[i % chartColors.size],
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                                size = Size(arcSize, arcSize),
                                                style = Stroke(width = strokeWidth),
                                            )
                                            startAngle += sweep
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${state.totalQty}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "总件数",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(20.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    state.categoryShare.forEachIndexed { i, (catId, qty) ->
                                        val def = state.categories.byId(catId)
                                        val pct = (qty * 100f / state.totalQty).toInt()
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(chartColors[i % chartColors.size]),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${def.emoji} ${def.label}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "$qty 件 · $pct%",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Top 5 consumed foods ----
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "消耗排行 TOP 5",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.topConsumed.isEmpty()) {
                            Text(
                                "还没有消耗记录，多吃点零食吧 😋",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.topConsumed.forEachIndexed { rank, (name, catId, totalAmount) ->
                                val def = state.categories.byId(catId)
                                val rankColor = when (rank) {
                                    0 -> MaterialTheme.colorScheme.primary
                                    1 -> MaterialTheme.colorScheme.tertiary
                                    2 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val itemId = state.findItemIdByName(name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (itemId != null) Modifier.clickable { onOpenItem(itemId) }
                                            else Modifier
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "#${rank + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = rankColor,
                                        modifier = Modifier.width(32.dp),
                                    )
                                    Text(def.emoji, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "共消耗 $totalAmount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (rank < state.topConsumed.size - 1) {
                                    Spacer(Modifier.height(4.dp))
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
    emoji: String,
    value: String,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}
