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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
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
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("该食品已归档或移除", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack, shape = RoundedCornerShape(50)) { Text("返回") }
            }
        }
        return
    }

    val ui = rememberStatusUi(state.status)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("食品详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(item.id) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { state.onShowDeleteDialogChange(true) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                shape = MaterialTheme.shapes.extraLarge,
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
                            background = MaterialTheme.colorScheme.surface,
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
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(state.status)
                    Spacer(Modifier.height(16.dp))
                    // 正相关进度：时间过去多少走多少
                    LinearProgressIndicator(
                        progress = { item.elapsedRatioAt(state.today) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = ui.content,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.remainingTextAt(state.today),
                        style = MaterialTheme.typography.titleMedium,
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
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    if (state.burstCount > 1) "连击打卡 ×${state.burstCount} 😋" else "吃掉一份！😋",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // ---- Quantity & stepper ----
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "当前库存",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    QuantityStepper(
                        quantity = item.quantity,
                        unit = item.unit,
                        onDecrease = {
                            state.onChangeQuantity(-1) { onBack() }
                        },
                        onIncrease = {
                            state.onChangeQuantity(1) {}
                        },
                    )
                }
            }

            // ---- Detail metadata ----
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow("分类", "${state.categoryDef.emoji} ${state.categoryDef.label}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("存放位置", item.location.ifBlank { "未设置" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("生产日期", item.productionDate.cn())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("保质期", "${item.shelfLifeDays} 天")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow("过期日期", item.expiryDate.cn())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DetailRow(
                        "临期提醒阈值",
                        if (item.expiringThresholdDays != null) "${item.expiringThresholdDays} 天（单品覆盖）"
                        else "${item.effectiveThreshold(emptyMap())} 天（使用分类默认）",
                    )
                    if (item.note.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DetailRow("备注", item.note)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onEdit(item.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑")
                }
                OutlinedButton(
                    onClick = { state.onShowDeleteDialogChange(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { state.onShowDeleteDialogChange(false) },
            title = { Text("移入归档") },
            text = { Text("确定要将“${item.name}”移入归档吗？（可在设置页的归档历史中查看或恢复）") },
            confirmButton = {
                TextButton(onClick = {
                    state.onShowDeleteDialogChange(false)
                    state.onDeleteItem()
                    onBack()
                }) {
                    Text("归档", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.onShowDeleteDialogChange(false) }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
