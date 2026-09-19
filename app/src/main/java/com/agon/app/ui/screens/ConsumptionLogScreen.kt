package com.agon.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.data.isDeletable
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.app.AppHintText
import com.agon.app.ui.components.app.AppListRow
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.rememberAppSnackbarHostState
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.UiEvent
import com.agon.app.viewmodel.undoDeleteConsumption
import java.time.LocalDate

/**
 * 消耗记录管理页（双主题）：列出全部消耗流水，可删除单条以修正统计。
 * 删除仅移除统计记录，不回滚库存数量（库存可自行在列表/详情调整）。
 *
 * 2026-09-16 由 `ConsumptionLogScreen`（MD3）+ `MiuixConsumptionLogScreen` 合并而来：
 * 两份 396 行的文件里，业务结构逐行相同，只有外壳（Scaffold/顶栏/卡片/字号/图标字形）不同。
 * 外壳差异下沉到 `ui/components/app/`，本文件只剩一份结构，改口径不会再漏掉另一个主题。
 */
@Composable
fun ConsumptionLogScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val state = rememberConsumptionLogUiState(viewModel)
    val snackbar = rememberAppSnackbarHostState()

    // 删除后的撤销提示（#4a：改收 Channel，接收即出队，不再需要 consume）。
    // key 用 snackbar：切换主题会换一个新的宿主容器，旧协程必须停掉，
    // 否则撤销条会弹到已经卸载的那个宿主上（合并前两份文件天然分开，不存在这个问题）。
    // 换成 Channel 后这条更稳：主题切换期间发出的事件会在队列里等着，不会像
    // SharedFlow(replay=0) 那样直接丢掉。
    LaunchedEffect(snackbar) {
        viewModel.consumptionLogUiEvents.collect { event ->
            if (event is UiEvent.UndoDeleteConsumption) {
                val undone = snackbar.showUndoSnackbar("已删除「${event.record.name}」的消耗记录")
                if (undone) {
                    viewModel.undoDeleteConsumption(event.record, event.index)
                }
            }
        }
    }

    AppScaffold(title = "消耗记录", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppHintText(
                "删除记录仅修正统计，不会回滚库存数量",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (state.sortedRecords.isEmpty()) {
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
                    itemsIndexed(state.sortedRecords, key = { index, record -> record.id ?: "idx-$index" }) { _, record ->
                        // 月度聚合记录（一条 = 整月合计）：删掉它等于抹掉整月历史，所以不给删除按钮，
                        // 只标注数据来源让用户理解「为什么这一行是几十件」。
                        val deletable = record.isDeletable()
                        AppListRow(
                            emoji = state.categories.byId(record.category).emoji,
                            title = record.name,
                            subtitle = LocalDate.ofEpochDay(record.epochDay).cn(),
                            trailing = "×${record.amount} ${record.unit}",
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate),
                                fadeOutSpec = tween(200, easing = MotionEasing.EmphasizedAccelerate),
                                placementSpec = tween<IntOffset>(280, easing = MotionEasing.EmphasizedDecelerate),
                            ),
                            trailingTag = if (deletable) null else "月度合计",
                            onDelete = if (deletable) {
                                { state.deleteRecord(record) }
                            } else {
                                null
                            },
                            deleteContentDescription = "删除 ${record.name} 的消耗记录",
                        )
                    }
                }
            }
        }
    }
}
