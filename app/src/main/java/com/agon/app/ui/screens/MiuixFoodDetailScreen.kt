package com.agon.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.data.effectiveThreshold
import com.agon.app.data.expiryDate
import com.agon.app.data.elapsedRatio
import com.agon.app.data.productionDate
import com.agon.app.data.remainingText
import com.agon.app.data.statusFor
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.QuantityStepper
import com.agon.app.ui.components.StatusBadge
import com.agon.app.ui.components.rememberStatusUi
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val item = items.find { it.id == itemId }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val bounceScale = remember { Animatable(1f) }
    val floatOffset = remember { Animatable(0f) }
    val floatAlpha = remember { Animatable(0f) }
    var burstCount by remember { mutableIntStateOf(0) }

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
                Button(onClick = onBack, cornerRadius = 50.dp) {
                    Text("返回", color = MiuixTheme.colorScheme.onSecondaryVariant)
                }
            }
        }
        return
    }

    val status = item.statusFor(thresholds)
    val ui = rememberStatusUi(status)
    val categoryDef = categories.byId(item.category)

    fun playEatAnimation() {
        burstCount++
        scope.launch {
            bounceScale.snapTo(1f)
            bounceScale.animateTo(1.25f, tween(120, easing = MotionEasing.EmphasizedDecelerate))
            bounceScale.animateTo(1f, tween(220, easing = MotionEasing.Emphasized))
        }
        scope.launch {
            floatOffset.snapTo(0f)
            floatAlpha.snapTo(1f)
            launch { floatOffset.animateTo(-72f, tween(700, easing = MotionEasing.EmphasizedDecelerate)) }
            floatAlpha.animateTo(0f, tween(700, easing = MotionEasing.Standard))
        }
    }

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
                    IconButton(onClick = { showDeleteDialog = true }) {
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
                shape = RoundedCornerShape(28.dp),
                color = ui.container,
                contentColor = ui.content,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FoodAvatar(
                            item,
                            categoryDef.emoji,
                            size = 80.dp,
                            background = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.scale(bounceScale.value),
                        )
                        Text(
                            "😋",
                            fontSize = 28.sp,
                            modifier = Modifier
                                .offset(y = floatOffset.value.dp)
                                .alpha(floatAlpha.value),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.name,
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(status)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = item.elapsedRatio,
                        height = 8.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = ui.content,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.remainingText,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = ui.content,
                    )
                }
            }

            Button(
                onClick = {
                    if (item.quantity > 0) {
                        val isLast = item.quantity == 1
                        if (isLast) {
                            playEatAnimation()
                            scope.launch {
                                kotlinx.coroutines.delay(750)
                                viewModel.consumeOne(item.id)
                                onBack()
                            }
                        } else {
                            viewModel.consumeOne(item.id)
                            playEatAnimation()
                        }
                    }
                },
                enabled = item.quantity > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                cornerRadius = 50.dp,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("😋", fontSize = 22.sp, color = MiuixTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (item.quantity > 0) "吃掉一份！" else "已经吃光啦",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
                if (burstCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "×$burstCount",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    DetailRow("分类", "${categoryDef.emoji} ${categoryDef.label}")
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    DetailRow("存放位置", item.location.ifBlank { "未设置" })
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    DetailRow("生产日期", item.productionDate.cn())
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    DetailRow("保质期", "${item.shelfLifeDays} 天")
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    DetailRow("预计过期", item.expiryDate.cn())
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    DetailRow(
                        "临期提醒",
                        "提前 ${item.effectiveThreshold(thresholds)} 天" +
                            if (item.expiringThresholdDays != null) "（单独设置）" else "（分类默认）",
                    )
                    if (item.note.isNotBlank()) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        DetailRow("备注", item.note)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "库存数量",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "减少会计入消耗统计",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    QuantityStepper(
                        quantity = item.quantity,
                        unit = item.unit,
                        onChange = { delta -> viewModel.changeQuantity(item.id, delta) },
                    )
                }
            }

            Button(
                onClick = { onEdit(item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                cornerRadius = 50.dp,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                ),
            ) {
                Icon(
                    MiuixIcons.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSecondaryVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text("编辑食品信息", color = MiuixTheme.colorScheme.onSecondaryVariant)
            }

            Spacer(Modifier.height(24.dp))
        }

        OverlayDialog(
            title = "移入归档",
            summary = "确定要将“${item.name}”移入归档吗？可在“归档历史”中恢复。",
            show = showDeleteDialog,
            onDismissRequest = { showDeleteDialog = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "移入归档",
                    onClick = {
                        showDeleteDialog = false
                        viewModel.archive(item.id, ArchiveReason.DELETED)
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
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
    }
}
