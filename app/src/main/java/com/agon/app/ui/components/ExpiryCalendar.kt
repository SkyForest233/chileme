package com.agon.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.data.expiryDate
import com.agon.app.data.remainingText
import com.agon.app.data.statusFor
import java.time.LocalDate
import java.time.YearMonth

/**
 * 到期日历卡片（嵌入统计页）：
 * - 月网格，日期格下方用「紧急度彩色圆点」标记当天到期食品，四档梯度：
 *   红 = 已过期、深橙 = 3 天内、琥75黄 = 临期、绿 = 安全（同一天多档并排，最多 3 点）
 * - 支持左右滑动切换月份（也保留箭头按钮），带方向感知滑动动画
 * - 点选日期在卡片下方列出当天到期食品，可跳转详情
 */
@Composable
fun ExpiryCalendarCard(
    items: List<FoodItem>,
    thresholds: Map<String, Int>,
    categories: List<CategoryDef>,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = remember(monthValue) { YearMonth.parse(monthValue) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = remember(selectedDay) { LocalDate.parse(selectedDay) }
    // 月份切换方向：1 = 下一月（内容从右滑入），-1 = 上一月
    var direction by remember { mutableIntStateOf(1) }
    // 手势累计位移
    var dragTotal by remember { mutableFloatStateOf(0f) }

    val byExpiry = remember(items) { items.groupBy { it.expiryDate } }
    val selectedItems = remember(byExpiry, selected) {
        (byExpiry[selected] ?: emptyList()).sortedBy { it.name }
    }

    fun goMonth(delta: Long) {
        direction = if (delta > 0) 1 else -1
        monthValue = month.plusMonths(delta).toString()
    }

    Column(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                // 标题 + 月份切换头
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "到期日历",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (month != YearMonth.from(today) || selected != today) {
                        TextButton(onClick = {
                            direction = if (YearMonth.from(today) > month) 1 else -1
                            monthValue = YearMonth.now().toString()
                            selectedDay = today.toString()
                        }) { Text("今天") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { goMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Text(
                        "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { goMonth(1) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
                Spacer(Modifier.height(2.dp))
                // 星期表头（周一开始）
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                        Text(
                            d,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 月份网格：左右滑动切换 + 方向滑动动画
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 * direction } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 * direction } + fadeOut())
                    },
                    label = "monthGrid",
                    modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragTotal < -60f -> goMonth(1)   // 左滑 → 下个月
                                    dragTotal > 60f -> goMonth(-1)   // 右滑 → 上个月
                                }
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f },
                        ) { _, dragAmount -> dragTotal += dragAmount }
                    },
                ) { m ->
                    MonthGrid(
                        month = m,
                        today = today,
                        selected = selected,
                        byExpiry = byExpiry,
                        thresholds = thresholds,
                        onSelect = { selectedDay = it.toString() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 紧急度图例（四档）
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ExpiryUrgency.entries.forEachIndexed { index, urgency ->
                        if (index > 0) Spacer(Modifier.width(14.dp))
                        LegendDot(urgency)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${selected.cn()} 到期",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (selectedItems.isEmpty()) {
            Text(
                "这一天没有食品到期 🌿",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedItems.forEach { item ->
                    CalendarItemRow(
                        item = item,
                        emoji = categories.byId(item.category).emoji,
                        status = item.statusFor(thresholds),
                        onClick = { onOpenItem(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(urgency: ExpiryUrgency) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(urgencyDotColor(urgency)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            urgency.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byExpiry: Map<LocalDate, List<FoodItem>>,
    thresholds: Map<String, Int>,
    onSelect: (LocalDate) -> Unit,
) {
    // 周一 = 1 ... 周日 = 7；前导空格数
    val leading = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    Column {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNum = index - leading + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            dayItems = byExpiry[date] ?: emptyList(),
                            thresholds = thresholds,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    dayItems: List<FoodItem>,
    thresholds: Map<String, Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 当天到期食品包含的紧急度（去重），按紧急程度排序（enum 定义即此顺序），最多 3 点
    val dotUrgencies = remember(dayItems, thresholds) {
        dayItems.map { it.urgencyFor(thresholds) }
            .distinct()
            .sorted()
            .take(3)
    }

    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (dotUrgencies.isEmpty()) {
                // 占位保持行高一致
                Spacer(Modifier.size(6.dp))
            } else {
                dotUrgencies.forEach { urgency ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(urgencyDotColor(urgency))
                            // 选中格底色为 primary 时加浅描边保证圆点可辨
                            .then(
                                if (isSelected) Modifier.border(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    CircleShape,
                                ) else Modifier
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarItemRow(
    item: FoodItem,
    emoji: String,
    status: FoodStatus,
    onClick: () -> Unit,
) {
    val ui = rememberStatusUi(status)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodAvatar(item, emoji = emoji, size = 44.dp, background = ui.container)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${item.remainingText} · ${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ui.content,
                )
            }
            StatusBadge(status)
        }
    }
}
