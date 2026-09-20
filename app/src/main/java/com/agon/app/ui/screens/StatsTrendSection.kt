/**
 * 统计页的「近 7 天消耗趋势（柱状图）」区块（09-19 #11d ② 从 `StatsScreen` 的装配体里搬出来）。
 *
 * 为什么单独一个文件：柱状图的「有守卫的除法」与 14dp/8dp 最小柱高都是这里独有的口径，搬走时一个字没动。
 * 与 #10e 的 `EditFood*Section.kt` 同形：同包 `internal` + 参数表按入口局部审计的结果给，
 * 所以调用点的 import 一处不改（判据②预测 0 = 实测 0）。
 */
package com.agon.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppSection
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appHighestContainerColor
import com.agon.app.ui.components.app.appPrimaryColor
import com.agon.app.ui.theme.MotionEasing

// ---- 近 7 天消耗趋势（柱状图）----
@Composable
internal fun StatsTrendSection(state: StatsUiState) {
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
