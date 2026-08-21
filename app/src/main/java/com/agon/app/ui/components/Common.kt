package com.agon.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.cn
import com.agon.app.data.daysLeft
import com.agon.app.data.elapsedRatio
import com.agon.app.data.expiryDate
import com.agon.app.data.effectiveThreshold
import com.agon.app.data.productionDate
import com.agon.app.data.remainingText
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.agon.app.ui.theme.DangerContainerDark
import com.agon.app.ui.theme.DangerContainerLight
import com.agon.app.ui.theme.DangerContentDark
import com.agon.app.ui.theme.DangerContentLight
import com.agon.app.ui.theme.SafeContainerDark
import com.agon.app.ui.theme.SafeContainerLight
import com.agon.app.ui.theme.SafeContentDark
import com.agon.app.ui.theme.SafeContentLight
import com.agon.app.ui.theme.SafeDotDark
import com.agon.app.ui.theme.UrgentDotDark
import com.agon.app.ui.theme.UrgentDotLight
import com.agon.app.ui.theme.SafeDotLight
import com.agon.app.ui.theme.WarnDotDark
import com.agon.app.ui.theme.WarnDotLight
import com.agon.app.ui.theme.DangerDotDark
import com.agon.app.ui.theme.DangerDotLight
import com.agon.app.ui.theme.WarnContainerDark
import com.agon.app.ui.theme.WarnContainerLight
import com.agon.app.ui.theme.WarnContentDark
import com.agon.app.ui.theme.WarnContentLight
import java.io.File

data class StatusUi(
    val container: Color,
    val content: Color,
    val label: String,
    val icon: ImageVector,
    /**
     * 高饱和圆点色：content 色是为文字设计的深色调，在 6~7dp 小圆点上
     * 红/棕/绿几乎不可辨；日历圆点、图例等小面积色块必须用本色。
     */
    val dot: Color,
)

@Composable
fun rememberStatusUi(status: FoodStatus): StatusUi {
    // 用当前主题背景亮度判断深浅色，而非 isSystemInDarkTheme()：
    // App 支持在设置中强制浅色/深色，两者不一致时会取错色套。
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    return when (status) {
        FoodStatus.SAFE -> StatusUi(
            container = if (dark) SafeContainerDark else SafeContainerLight,
            content = if (dark) SafeContentDark else SafeContentLight,
            label = "安全",
            icon = if (isMiuix) MiuixIcons.Ok else Icons.Rounded.CheckCircle,
            dot = if (dark) SafeDotDark else SafeDotLight,
        )
        FoodStatus.EXPIRING -> StatusUi(
            container = if (dark) WarnContainerDark else WarnContainerLight,
            content = if (dark) WarnContentDark else WarnContentLight,
            label = "临期",
            icon = if (isMiuix) MiuixIcons.Timer else Icons.Rounded.Schedule,
            dot = if (dark) WarnDotDark else WarnDotLight,
        )
        FoodStatus.EXPIRED -> StatusUi(
            container = if (dark) DangerContainerDark else DangerContainerLight,
            content = if (dark) DangerContentDark else DangerContentLight,
            label = "已过期",
            icon = if (isMiuix) MiuixIcons.Report else Icons.Rounded.ErrorOutline,
            dot = if (dark) DangerDotDark else DangerDotLight,
        )
    }
}

/**
 * 到期紧急度四档（专供日历圆点、图例等小面积标记使用）：
 * 三态 status 在日历场景下区分度不足——阈值内的日期全是"临期"一片黄。
 * 圆点按剩余天数分梯度：红(已过期) → 深橙(≤3天) → 琥珀黄(阈值内) → 绿(安全)。
 */
enum class ExpiryUrgency(val label: String) {
    EXPIRED("已过期"),
    URGENT("3天内"),
    SOON("临期"),
    SAFE("安全"),
}

fun FoodItem.urgencyFor(thresholds: Map<String, Int>): ExpiryUrgency = when {
    daysLeft < 0 -> ExpiryUrgency.EXPIRED
    daysLeft <= 3 -> ExpiryUrgency.URGENT
    daysLeft <= effectiveThreshold(thresholds) -> ExpiryUrgency.SOON
    else -> ExpiryUrgency.SAFE
}

@Composable
fun urgencyDotColor(urgency: ExpiryUrgency): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (urgency) {
        ExpiryUrgency.EXPIRED -> if (dark) DangerDotDark else DangerDotLight
        ExpiryUrgency.URGENT -> if (dark) UrgentDotDark else UrgentDotLight
        ExpiryUrgency.SOON -> if (dark) WarnDotDark else WarnDotLight
        ExpiryUrgency.SAFE -> if (dark) SafeDotDark else SafeDotLight
    }
}

@Composable
fun StatusBadge(status: FoodStatus, modifier: Modifier = Modifier) {
    val ui = rememberStatusUi(status)
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            color = ui.container,
            contentColor = ui.content,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(ui.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                MiuixText(ui.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Surface(
            modifier = modifier,
            color = ui.container,
            contentColor = ui.content,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ui.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(ui.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 食品头像优先级：照片 > 自定义 emoji/文字封面 > 分类 emoji。
 */
@Composable
fun FoodAvatar(
    item: FoodItem,
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    if (item.photoPath.isNotBlank() && File(item.photoPath).exists()) {
        AsyncImage(
            model = File(item.photoPath),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3)),
        )
    } else {
        EmojiAvatar(item.coverText.ifBlank { emoji }, modifier = modifier, size = size, background = background)
    }
}

@Composable
fun EmojiAvatar(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    // 自定义文字可能是 2-3 个汉字，按长度缩小字号避免溢出
    val fontScale = when {
        emoji.length <= 2 -> 0.48f
        emoji.length == 3 -> 0.3f
        else -> 0.24f
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emoji,
            fontSize = (size.value * fontScale).sp,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LocationTag(location: String, modifier: Modifier = Modifier) {
    if (location.isBlank()) return
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = bg,
            contentColor = fg,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    MiuixIcons.Location,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = fg,
                )
                Spacer(Modifier.width(3.dp))
                MiuixText(location, fontSize = 11.sp, color = fg)
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Place,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun QuantityStepper(
    quantity: Int,
    unit: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = MaterialTheme.colorScheme.onSurface
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = bg,
            contentColor = fg,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIconButton(
                    onClick = { onChange(-1) },
                    enabled = quantity > 0,
                ) {
                    // Miuix 无「减号」图标（Remove 是「移除/退出」形状），减号回退 material
                    MiuixIcon(Icons.Rounded.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp), tint = fg)
                }
                AnimatedContent(targetState = quantity, label = "qty") { q ->
                    MiuixText(
                        "$q $unit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                MiuixIconButton(
                    onClick = { onChange(1) },
                ) {
                    MiuixIcon(MiuixIcons.Add, contentDescription = "增加", modifier = Modifier.size(18.dp), tint = fg)
                }
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onChange(-1) },
                    enabled = quantity > 0,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp))
                }
                AnimatedContent(targetState = quantity, label = "qty") { q ->
                    Text(
                        "$q $unit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                IconButton(
                    onClick = { onChange(1) },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "增加", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiuixText(
                                item.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            LocationTag(item.location)
                        }
                        Spacer(Modifier.height(2.dp))
                        MiuixText(
                            "${category.label} · 生产 ${item.productionDate.cn()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MiuixText(
                            "到期 ${item.expiryDate.cn()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 正相关进度：时间过去多少，进度条就走多少
                    MiuixLinearProgressIndicator(
                        progress = item.elapsedRatio,
                        modifier = Modifier.weight(1f),
                        height = 6.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = ui.content,
                            backgroundColor = ui.container,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    MiuixText(
                        item.remainingText,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            LocationTag(item.location)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${category.label} · 生产 ${item.productionDate.cn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "到期 ${item.expiryDate.cn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 正相关进度：时间过去多少，进度条就走多少
                    LinearProgressIndicator(
                        progress = { item.elapsedRatio },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = ui.content,
                        trackColor = ui.container,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.remainingText,
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

/** 多选模式下的圆形勾选指示器 */
@Composable
fun SelectIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(200, easing = MotionEasing.Standard),
        label = "selBg",
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                MiuixIcon(
                    MiuixIcons.Ok,
                    contentDescription = "已选中",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选中",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * 打勾/打叉样式开关（参考 Focus 类 App 设计截图）：
 * ON = primary 胶囊轨道 + 白色圆形滑块内打勾；OFF = 灰色轨道 + 深灰滑块内打叉。
 * 项目内所有开关统一使用本组件，禁止使用 material3 Switch。
 */
@Composable
fun CheckSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val padding = 4.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - padding else padding,
        animationSpec = tween(220, easing = MotionEasing.Standard),
        label = "thumbOffset",
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(220, easing = MotionEasing.Standard),
        label = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outlineVariant
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(220, easing = MotionEasing.Standard),
        label = "thumbColor",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(220, easing = MotionEasing.Standard),
        label = "iconTint",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = checked, label = "switchIcon") { on ->
                Icon(
                    if (on) Icons.Rounded.Check else Icons.Rounded.Close,
                    // 状态由 toggleable 的 Role.Switch 语义播报，图标仅为装饰
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
            MiuixText(emoji, fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            MiuixText(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            MiuixText(
                subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(emoji, fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
