package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.cn
import com.agon.app.data.effectiveThreshold
import com.agon.app.data.elapsedRatioAt
import com.agon.app.data.expiryDate
import com.agon.app.data.productionDate
import com.agon.app.data.remainingTextAt
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.QuantityStepper
import com.agon.app.ui.components.StatusBadge
import com.agon.app.ui.components.app.AppBigButton
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.AppDeleteAction
import com.agon.app.ui.components.app.AppDetailRow
import com.agon.app.ui.components.app.AppDivider
import com.agon.app.ui.components.app.AppEditAction
import com.agon.app.ui.components.app.AppEditButton
import com.agon.app.ui.components.app.AppEmojiText
import com.agon.app.ui.components.app.AppHintText
import com.agon.app.ui.components.app.AppLinearProgress
import com.agon.app.ui.components.app.AppMessageScreen
import com.agon.app.ui.components.app.AppPaddedCard
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppStatusCard
import com.agon.app.ui.components.app.AppText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appSurfaceColor
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 食品详情页（双主题）：状态卡（头像 + 名称 + 状态徽章 + 时间进度 + 剩余文案）、
 * 「吃掉一份」大按钮、信息卡、库存步进器、编辑入口、移入归档确认弹窗。
 *
 * 2026-09-16 由 `FoodDetailScreen`（MD3，354 行）+ `MiuixFoodDetailScreen`（343 行）合并。
 * 两版的结构与动画逐行相同（`bounceScale` / `floatOffset` / `floatAlpha` / `burstCount` 全部来自
 * 共用的 `FoodDetailState`），差别只在文字档位、按钮形态、卡片外壳与进度条 API 上，
 * 故外壳下沉到 `ui/components/app/`：状态卡 → `AppStatusCard`、进度条 → `AppLinearProgress`、
 * 大按钮 → `AppBigButton`、信息卡 → `AppPaddedCard` + `AppDetailRow` + `AppDivider`、
 * 库存卡标题 → `AppText(Heading)`、编辑入口 → `AppEditButton`、顶栏两个图标 → `AppEditAction` /
 * `AppDeleteAction`、弹窗 → `AppConfirmDialog`、食品不存在的兜底屏 → `AppMessageScreen`。
 *
 * **与合并前唯一的非等价改动**：不再创建 `SnackbarHostState` 与 `SnackbarHost`。两版都建了宿主却
 * 从未弹过任何一条（`showSnackbar` 全文件 0 处调用）—— 就是待办清单里「详情页『吃掉一份』无撤销」
 * 那个缺口的现场。空宿主渲染出来是零尺寸，删掉它不改变任何可见行为；将来补撤销时，
 * 传 `snackbar = rememberAppSnackbarHostState()` 给 `AppScaffold` 即可。
 */
@Composable
fun FoodDetailScreen(
    viewModel: AppViewModel,
    itemId: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state = rememberFoodDetailUiState(viewModel, itemId)
    val item = state.item
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    if (item == null) {
        AppMessageScreen(
            message = "该食品已归档或移除",
            actionLabel = "返回",
            onAction = onBack,
        )
        return
    }

    val ui = rememberStatusUi(state.status)
    val surface = appSurfaceColor()

    AppScaffold(
        title = "食品详情",
        onBack = onBack,
        actions = {
            AppEditAction(onClick = { onEdit(item.id) })
            AppDeleteAction(onClick = { state.setShowDeleteDialog(true) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppStatusCard(
                color = ui.container,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val density = LocalDensity.current
                        FoodAvatar(
                            item,
                            state.categoryDef.emoji,
                            size = 80.dp,
                            background = surface,
                            modifier = Modifier.graphicsLayer {
                                val s = state.bounceScale.value
                                scaleX = s
                                scaleY = s
                            },
                        )
                        AppEmojiText(
                            "😋",
                            fontSize = 28.sp,
                            modifier = Modifier.graphicsLayer {
                                translationY = with(density) { state.floatOffset.value.dp.toPx() }
                                alpha = state.floatAlpha.value
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    AppText(
                        item.name,
                        AppTextScale.Hero,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(state.status)
                    Spacer(Modifier.height(16.dp))
                    // 正相关进度：时间过去多少走多少
                    AppLinearProgress(
                        progress = item.elapsedRatioAt(state.today),
                        color = ui.content,
                        trackColor = surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        item.remainingTextAt(state.today),
                        AppTextScale.Emphasis,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                }
            }

            // “Eat one” big fun button
            AppBigButton(
                emoji = "😋",
                label = if (item.quantity > 0) "吃掉一份！" else "已经吃光啦",
                badge = if (state.burstCount > 0) "×${state.burstCount}" else null,
                enabled = item.quantity > 0,
                onClick = {
                    if (item.quantity > 0) {
                        val isLast = item.quantity == 1
                        if (isLast) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // 吃完 → 仓库层自动归档；先播动画再消耗，避免页面瞬间切换
                            state.playEatAnimation()
                            scope.launch {
                                delay(750)
                                state.consumeOne(item.id)
                                onBack()
                            }
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            state.consumeOne(item.id)
                            state.playEatAnimation()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )

            AppPaddedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    AppDetailRow("分类", "${state.categoryDef.emoji} ${state.categoryDef.label}")
                    AppDivider(Modifier.padding(vertical = 10.dp))
                    AppDetailRow("存放位置", item.location.ifBlank { "未设置" })
                    AppDivider(Modifier.padding(vertical = 10.dp))
                    AppDetailRow("生产日期", item.productionDate.cn())
                    AppDivider(Modifier.padding(vertical = 10.dp))
                    AppDetailRow("保质期", "${item.shelfLifeDays} 天")
                    AppDivider(Modifier.padding(vertical = 10.dp))
                    AppDetailRow("预计过期", item.expiryDate.cn())
                    AppDivider(Modifier.padding(vertical = 10.dp))
                    AppDetailRow(
                        "临期提醒",
                        "提前 ${item.effectiveThreshold(state.thresholds)} 天" +
                            if (item.expiringThresholdDays != null) "（单独设置）" else "（分类默认）",
                    )
                    if (item.note.isNotBlank()) {
                        AppDivider(Modifier.padding(vertical = 10.dp))
                        AppDetailRow("备注", item.note)
                    }
                }
            }

            AppPaddedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        AppText(
                            "库存数量",
                            AppTextScale.Heading,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AppHintText("减少会计入消耗统计")
                    }
                    QuantityStepper(
                        quantity = item.quantity,
                        unit = item.unit,
                        onChange = { delta -> state.changeQuantity(item.id, delta) },
                    )
                }
            }

            AppEditButton(
                label = "编辑食品信息",
                onClick = { onEdit(item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )

            Spacer(Modifier.height(24.dp))
        }

        // 弹窗放在 content lambda 内：Miuix 的 WindowDialog 必须无条件调用、靠 show 控制（库的硬约束）
        AppConfirmDialog(
            show = state.showDeleteDialog,
            title = "移入归档",
            message = "确定要将“${item.name}”移入归档吗？可在“归档历史”中恢复。",
            confirmText = "移入归档",
            destructive = true,
            onConfirm = {
                state.setShowDeleteDialog(false)
                state.deleteItem(item.id)
                onBack()
            },
            onDismiss = { state.setShowDeleteDialog(false) },
        )
    }
}
