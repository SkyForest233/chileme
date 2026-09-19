package com.agon.app.ui.components

// 列表主卡片：头像 + 名称 + 分类/生产/到期日期 + 状态徽章 + 位置标签 + 新鲜度进度条 + 数量步进器。
//
// 双主题各一份完整实现（Miuix 用 squircleBorder 描边 + PressFeedbackType.Sink，MD3 用
// combinedClickable + BorderStroke），共用 StatusUi / FoodAvatar / StatusBadge / LocationTag /
// QuantityStepper / SelectIndicator。这是本目录最大的组件，也是「App 级组件层」重构
// （docs/audits/2026-09-15-code-review.md §2.1）的首要目标。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.dot
import com.agon.app.data.elapsedRatioAt
import com.agon.app.data.expiryDate
import com.agon.app.data.productionDate
import com.agon.app.data.remainingTextAt
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.LocalToday
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 食品卡片。支持长按进入多选模式：
 * - selectionMode = false：点击进详情，长按触发 onLongClick（进入多选并选中自己）
 * - selectionMode = true：点击切换选中态，卡片左侧显示圆形勾选指示，选中时描边高亮
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodCard(
    item: FoodItem,
    category: CategoryDef,
    status: FoodStatus,
    onClick: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val ui = rememberStatusUi(status)
    val progress by animateFloatAsState(
        targetValue = item.elapsedRatioAt(LocalToday.current).coerceIn(0f, 1f),
        animationSpec = tween(400, easing = MotionEasing.Standard),
        label = "elapsed",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200, easing = MotionEasing.Standard),
        label = "cardBorder",
    )
    val containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainer

    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixCard(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        // squircle 描边：与 Miuix Card 本体的 squircle 圆角曲率一致，
                        // API 33+ 平滑贴合，低版本自动回退普通圆角。
                        Modifier.squircleBorder(
                            width = 2.dp,
                            color = borderColor,
                            cornerRadius = MiuixCardDefaults.CornerRadius,
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = MiuixCardDefaults.defaultColors(color = containerColor),
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = onClick,
            onLongPress = onLongClick,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    AnimatedVisibility(visible = selectionMode) {
                        Row {
                            SelectIndicator(selected = selected)
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    FoodAvatar(item, category.emoji, background = ui.container)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        MiuixText(
                            item.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        MiuixText(
                            "${category.label} · 生产 ${item.productionDate.dot()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MiuixText(
                            "到期 ${item.expiryDate.dot()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        StatusBadge(status)
                        if (item.location.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            LocationTag(item.location)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 正相关进度：时间过去多少，进度条就走多少
                    MiuixLinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.weight(1f),
                        height = 6.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = ui.content,
                            backgroundColor = ui.container,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    MiuixText(
                        item.remainingTextAt(LocalToday.current),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ui.content,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        "库存数量",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    QuantityStepper(quantity = item.quantity, unit = item.unit, onChange = onQuantityChange)
                }
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(2.dp, borderColor),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    AnimatedVisibility(visible = selectionMode) {
                        Row {
                            SelectIndicator(selected = selected)
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                    FoodAvatar(item, category.emoji, background = ui.container)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${category.label} · 生产 ${item.productionDate.dot()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "到期 ${item.expiryDate.dot()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        StatusBadge(status)
                        if (item.location.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            LocationTag(item.location)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 正相关进度：时间过去多少，进度条就走多少
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = ui.content,
                        trackColor = ui.container,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.remainingTextAt(LocalToday.current),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ui.content,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "库存数量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    QuantityStepper(quantity = item.quantity, unit = item.unit, onChange = onQuantityChange)
                }
            }
        }
    }
}
