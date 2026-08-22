package com.agon.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 消耗记录管理页（Miuix）：列出全部消耗流水，可删除单条以修正统计。
 * 删除仅移除统计记录，不回滚库存数量（库存可自行在列表/详情调整）。
 */
@Composable
fun MiuixConsumptionLogScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val sorted = remember(consumption) {
        consumption.sortedByDescending { it.epochDay }
    }

    // 删除后的撤销提示（collect 模式避免 consume 改变 key 取消协程）
    LaunchedEffect(Unit) {
        viewModel.deletedConsumption.filterNotNull().collect { deleted ->
            viewModel.consumeDeletedConsumption()
            val result = snackbarHostState.showUndoSnackbar(
                "已删除「${deleted.record.name}」的消耗记录",
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteConsumption(deleted.record, deleted.index)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "消耗记录",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "删除记录仅修正统计，不会回滚库存数量",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (sorted.isEmpty()) {
                EmptyState(
                    emoji = "🍽️",
                    title = "还没有消耗记录",
                    subtitle = "在详情页点“吃掉一份”或减少库存后这里会有数据",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // key 用 index 兜底：旧数据 id=null，若同天同名同数量会出现 key 冲突崩溃
                    itemsIndexed(sorted, key = { index, record -> record.id ?: "idx-$index" }) { _, record ->
                        MiuixConsumptionRow(
                            record = record,
                            emoji = categories.byId(record.category).emoji,
                            onDelete = {
                                viewModel.deleteConsumption(record)
                            },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate),
                                fadeOutSpec = tween(200, easing = MotionEasing.EmphasizedAccelerate),
                                placementSpec = tween<IntOffset>(280, easing = MotionEasing.EmphasizedDecelerate),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixConsumptionRow(
    record: ConsumptionRecord,
    emoji: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    LocalDate.ofEpochDay(record.epochDay).cn(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                "×${record.amount} ${record.unit}",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    MiuixIcons.Delete,
                    contentDescription = "删除 ${record.name} 的消耗记录",
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}
