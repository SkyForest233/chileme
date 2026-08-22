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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.cn
import com.agon.app.data.effectiveThreshold
import com.agon.app.data.expiryDate
import com.agon.app.data.elapsedRatioAt
import com.agon.app.data.productionDate
import com.agon.app.data.remainingTextAt
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.QuantityStepper
import com.agon.app.ui.components.StatusBadge
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 食品详情页的 Miuix（HyperOS）实现（v2.8 阶段二 P0）。
 *
 * 与 [FoodDetailScreen]（Material 3 实现）逻辑对等。结构性组件（Scaffold/TopAppBar/Button/
 * TextButton/OverlayDialog/Snackbar）使用 Miuix；状态卡/详情卡/进度条/分隔线复用现有实现
 * （经根级桥接的 MaterialTheme 取 Miuix 配色）。
 */
@Composable
fun MiuixFoodDetailScreen(
    viewModel: AppViewModel,
    itemId: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state = rememberFoodDetailUiState(viewModel, itemId)
    val item = state.item
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (item == null) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("该食品已归档或移除", style = MiuixTheme.textStyles.body1)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) {
                    Text("返回", color = MiuixTheme.colorScheme.onSecondaryVariant)
                }
            }
        }
        return
    }

    val ui = rememberStatusUi(state.status)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "食品详情",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(item.id) }) {
                        Icon(MiuixIcons.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { state.onShowDeleteDialogChange(true) }) {
                        Icon(
                            MiuixIcons.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                },
            )
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
            Surface(
                shape = RoundedCornerShape(24.dp),
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
                            background = MiuixTheme.colorScheme.surface,
                            modifier = Modifier.graphicsLayer {
                                val s = state.bounceScale.value
                                scaleX = s
                                scaleY = s
                            },
                        )
                        Text(
                            "😋",
                            fontSize = 28.sp,
                            modifier = Modifier.graphicsLayer {
                                translationY = with(density) { state.floatOffset.value.dp.toPx() }
                                alpha = state.floatAlpha.value
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.name,
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(state.status)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = item.elapsedRatioAt(state.today),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = ui.content,
                        trackColor = MiuixTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.remainingTextAt(state.today),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        color = ui.content,
                    )
                }
            }

            // ---- Eat one CTA button ----
            Button(
                onClick = {
                    state.playEatAnimation()
                    state.onConsumeOne(
                        onAutoArchived = {
                            scope.launch {
                                delay(600)
                                onBack()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text(
                    if (state.burstCount > 1) "连击打卡 ×${state.burstCount} 😋" else "吃掉一份！😋",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            }

            // ---- Quantity & stepper ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "当前库存",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            "${item.quantity} ${item.unit}",
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    QuantityStepper(
                        quantity = item.quantity,
                        unit = item.unit,
                        onChange = { delta ->
                            state.onChangeQuantity(delta) { if (delta < 0) onBack() }
                        },
                    )
                }
            }

            // ---- Detail metadata ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixDetailRow("分类", "${state.categoryDef.emoji} ${state.categoryDef.label}")
                    HorizontalDivider()
                    MiuixDetailRow("存放位置", item.location.ifBlank { "未设置" })
                    HorizontalDivider()
                    MiuixDetailRow("生产日期", item.productionDate.cn())
                    HorizontalDivider()
                    MiuixDetailRow("保质期", "${item.shelfLifeDays} 天")
                    HorizontalDivider()
                    MiuixDetailRow("过期日期", item.expiryDate.cn())
                    HorizontalDivider()
                    MiuixDetailRow(
                        "临期提醒",
                        if (item.expiringThresholdDays != null) "${item.expiringThresholdDays} 天（单品覆盖）"
                        else "提前 ${item.effectiveThreshold(state.thresholds)} 天（分类默认）",
                    )
                    if (item.note.isNotBlank()) {
                        HorizontalDivider()
                        MiuixDetailRow("备注", item.note)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "编辑",
                    onClick = { onEdit(item.id) },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                )
                TextButton(
                    text = "删除",
                    onClick = { state.onShowDeleteDialogChange(true) },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        OverlayDialog(
            title = "移入归档",
            summary = "确定要将“${item.name}”移入归档吗？（可在设置页的归档历史中查看或恢复）",
            show = state.showDeleteDialog,
            onDismissRequest = { state.onShowDeleteDialogChange(false) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onShowDeleteDialogChange(false) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "归档",
                    onClick = {
                        state.onShowDeleteDialogChange(false)
                        state.onDeleteItem()
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MiuixDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
    }
}
