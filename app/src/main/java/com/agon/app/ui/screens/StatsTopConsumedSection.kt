/**
 * 统计页的「消耗排行榜」区块（09-19 #11d ② 从 `StatsScreen` 的装配体里搬出来）。
 *
 * 为什么单独一个文件：排行榜行带「点得进详情才可点」的判断（`findItemIdByName`），且用了 `by` 委托 ⇒ getValue 要跟着走。
 * 与 #10e 的 `EditFood*Section.kt` 同形：同包 `internal` + 参数表按入口局部审计的结果给，
 * 所以调用点的 import 一处不改（判据②预测 0 = 实测 0）。
 */
package com.agon.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.byId
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.app.AppEmojiText
import com.agon.app.ui.components.app.AppSection
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appPrimaryColor
import com.agon.app.ui.components.app.appPrimaryContainerColor
import com.agon.app.ui.theme.MotionEasing

// ---- 消耗排行榜 ----
@Composable
internal fun StatsTopConsumedSection(
    state: StatsUiState,
    onOpenItem: (String) -> Unit,
) {
    AppSection(
        cardTitle = "消耗排行榜",
        sectionTitle = "消耗排行",
        titleSpacing = 12.dp,
    ) {
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
                    // 点击进入对应食品详情（可编辑）；找不到对应食品就不可点
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
                        AppText(
                            "${index + 1}",
                            AppTextScale.Heading,
                            modifier = Modifier.width(20.dp),
                            fontWeight = FontWeight.Bold,
                            color = appPrimaryColor(),
                        )
                        AppEmojiText(state.categories.byId(cat).emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            AppText(
                                name,
                                AppTextScale.Meta,
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
                                    .background(appPrimaryContainerColor()),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        AppText(
                            "×$amount",
                            AppTextScale.Action,
                            fontWeight = FontWeight.Bold,
                            color = appPrimaryColor(),
                        )
                    }
                }
            }
        }
    }
}
