package com.agon.app.ui.components

// 通用交互控件：SelectIndicator（多选勾选指示）、CheckSwitch（项目特色打勾/打叉开关）、
// EmptyState（空态）。
//
// CheckSwitch 是硬约定：全项目所有布尔开关一律用它，禁止 material3 Switch
// （CLAUDE.md §5）。它刻意保留 MD3 自绘 + 颜色桥接，不做 Miuix 版本——打勾/打叉是项目特色，
// 且已带 toggleable(Role.Switch) 语义与 minimumInteractiveComponentSize（≥48dp 触摸目标）。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok

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
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
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
    val appear = remember(emoji, title) { MutableTransitionState(false) }
    LaunchedEffect(appear) { appear.targetState = true }
    AnimatedVisibility(
        visibleState = appear,
        enter = fadeIn(tween(280, easing = MotionEasing.EmphasizedDecelerate)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
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
}
