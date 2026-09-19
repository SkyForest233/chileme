package com.agon.app.ui.components

// 数量步进器（胶囊容器 + 触感反馈 + 仅数字做 AnimatedContent 竖直滑动，单位固定不动）。
//
// quantityChangeTransition 是它的私有转场，必须与本组件同文件（private 作用域）。
// 触摸目标规则见 docs/DESIGN_SPEC.md §6：IconButton 不得用 Modifier.size 缩小容器。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

private fun AnimatedContentTransitionScope<Int>.quantityChangeTransition(): ContentTransform {
    val up = targetState > initialState
    val enterSpec = tween<IntOffset>(180, easing = MotionEasing.EmphasizedDecelerate)
    val exitSpec = tween<IntOffset>(140, easing = MotionEasing.EmphasizedAccelerate)
    val fadeInSpec = tween<Float>(180, easing = MotionEasing.EmphasizedDecelerate)
    val fadeOutSpec = tween<Float>(140, easing = MotionEasing.EmphasizedAccelerate)
    return if (up) {
        (slideInVertically(enterSpec) { it / 2 } + fadeIn(fadeInSpec)) togetherWith
            (slideOutVertically(exitSpec) { -it / 2 } + fadeOut(fadeOutSpec))
    } else {
        (slideInVertically(enterSpec) { -it / 2 } + fadeIn(fadeInSpec)) togetherWith
            (slideOutVertically(exitSpec) { it / 2 } + fadeOut(fadeOutSpec))
    }
}

@Composable
fun QuantityStepper(
    quantity: Int,
    unit: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(-1)
                    },
                    enabled = quantity > 0,
                ) {
                    // Miuix 无「减号」图标（Remove 是「移除/退出」形状），减号回退 material
                    MiuixIcon(Icons.Rounded.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp), tint = fg)
                }
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = quantity,
                        transitionSpec = { quantityChangeTransition() },
                        label = "qty",
                    ) { q ->
                        MiuixText(
                            "$q",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    MiuixText(
                        unit,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                    )
                }
                MiuixIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(1)
                    },
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(-1)
                    },
                    enabled = quantity > 0,
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp))
                }
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = quantity,
                        transitionSpec = { quantityChangeTransition() },
                        label = "qty",
                    ) { q ->
                        Text(
                            "$q",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(1)
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "增加", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
