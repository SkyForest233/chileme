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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.CategoryDef
import com.agon.app.data.byId
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.ExpiryCalendarCard
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate
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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    val today = LocalDate.now().toEpochDay()
    val weekAgo = today - 6
    val monthStart = LocalDate.now().withDayOfMonth(1).toEpochDay()

    val consumedThisWeek = consumption.filter { it.epochDay >= weekAgo }.sumOf { it.amount }
    val consumedThisMonth = consumption.filter { it.epochDay >= monthStart }.sumOf { it.amount }
    val wastedTotal = archived.count { it.reason == ArchiveReason.EXPIRED }

    val dailyTrend = remember(consumption) {
        (0..6).map { offset ->
            val day = today - (6 - offset)
            val amount = consumption.filter { it.epochDay == day }.sumOf { it.amount }
            LocalDate.ofEpochDay(day) to amount
        }
    }
    val maxDaily = (dailyTrend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val categoryShare = remember(items) {
        items.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }
    val totalQty = categoryShare.sumOf { it.second }

    val topConsumed = remember(consumption) {
        consumption.groupBy { it.name }
            .map { (name, records) ->
                Triple(name, records.first().category, records.sumOf { it.amount })
            }
            .sortedByDescending { it.third }
            .take(5)
    }

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
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    MiuixMiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "📅",
                        value = "$consumedThisMonth",
                        label = "本月消耗",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    MiuixMiniStat(
                        modifier = Modifier.weight(1f),
                        emoji = "🗑️",
                        value = "$wastedTotal",
                        label = "过期浪费",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
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
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    val ratio = amount.toFloat() / maxDaily
                                    val animRatio by animateFloatAsState(
                                        targetValue = ratio,
                                        animationSpec = tween(600, easing = MotionEasing.EmphasizedDecelerate),
                                        label = "bar",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.62f)
                                            .height((84 * animRatio).dp.coerceAtLeast(if (amount > 0) 14.dp else 8.dp))
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (amount > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${date.monthValue}/${date.dayOfMonth}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            color = MaterialTheme.colorScheme.primary,
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
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(amount.toFloat() / maxAmount)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "×$amount",
                                            style = MiuixTheme.textStyles.body2,
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
private fun rememberChartColorsMiuix(): List<Color> {
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
    val sweep by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = MotionEasing.EmphasizedDecelerate),
        label = "donut",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = Stroke(width = 30f)
            var startAngle = -90f
            data.forEachIndexed { i, value ->
                val angle = value / total * 360f * sweep
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
