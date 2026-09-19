package com.agon.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.ArchiveReason
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.app.AppActionRow
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.AppDestructiveAction
import com.agon.app.ui.components.app.AppFilterChip
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSearchField
import com.agon.app.ui.components.app.rememberAppSnackbarHostState
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 归档历史页（双主题）：搜索 + 按归档原因筛选 + 单条恢复 / 彻底删除 + 清空归档。
 *
 * 2026-09-16 由 `ArchiveScreen`（MD3，284 行）+ `MiuixArchiveScreen`（282 行）合并：
 * 两版逐段同构（连 `padding.calculateTopPadding()`、`bottom + 32.dp` 这种细节都一样），
 * 差别全在控件 API 上，故外壳下沉到 `ui/components/app/`：
 * 搜索框 → `AppSearchField`、筛选 chip → `AppFilterChip`、归档行 → `AppActionRow`、
 * 两个弹窗 → `AppConfirmDialog`（原来两版各写 2 个，共 4 份）、顶栏清空入口 → `AppDestructiveAction`。
 */
@Composable
fun ArchiveScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val state = rememberArchiveUiState(viewModel)
    val snackbar = rememberAppSnackbarHostState()
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = "归档历史",
        onBack = onBack,
        // 键盘避让：同 FoodListScreen（搜索框在顶部，列表末尾此前够不到键盘之上）。
        // 两版原来都写在各自 Scaffold 的 modifier 上；合并后仍由屏幕自己声明，
        // 这一行就是 ImeHandlingTest 第 1 条要看的证据（合并后一个条目覆盖两套主题）。
        modifier = Modifier.imePadding(),
        snackbar = snackbar,
        actions = {
            if (state.archived.isNotEmpty()) {
                AppDestructiveAction(
                    onClick = { state.setShowClearDialog(true) },
                    contentDescription = "清空归档",
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            AppSearchField(
                value = state.query,
                onValueChange = { state.setQuery(it) },
                label = "搜索归档食品…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf<ArchiveReason?>(null) + ArchiveReason.entries.toList()) { r ->
                    AppFilterChip(
                        selected = state.reasonFilter == r,
                        onClick = { state.setReasonFilter(r) },
                        label = r?.let { "${it.emoji} ${it.label}" } ?: "全部",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // 动画标签沿用 MD3 版的 "archiveCrossfade"（Miuix 版原为 "miuixArchiveCrossfade"）：
            // 它只是 Crossfade 的标识，合并后两套主题共用一个。
            Crossfade(
                targetState = state.filtered.isEmpty(),
                label = "archiveCrossfade",
                modifier = Modifier.fillMaxSize(),
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyState(
                        emoji = if (state.query.isNotBlank()) "🔍" else "📚",
                        title = if (state.archived.isEmpty()) "归档是空的" else "没有符合条件的记录",
                        subtitle = if (state.query.isNotBlank()) "换个关键词试试" else "删除、清理过期的食品会保存在这里，可随时恢复",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.filtered, key = { it.item.id }) { entry ->
                            AppActionRow(
                                title = entry.item.name,
                                subtitle = "${entry.reason.emoji} ${entry.reason.label} · " +
                                    LocalDate.ofEpochDay(entry.archivedEpochDay).cn(),
                                modifier = Modifier.animateItem(),
                                leading = {
                                    FoodAvatar(
                                        entry.item,
                                        state.categories.byId(entry.item.category).emoji,
                                        size = 44.dp,
                                    )
                                },
                                onRestore = {
                                    state.restoreEntry(entry.item.id) { merged ->
                                        scope.launch {
                                            val msg = if (merged) {
                                                "库存中已有同批次「${entry.item.name}」，已合并数量"
                                            } else {
                                                "已恢复「${entry.item.name}」到零食柜"
                                            }
                                            if (snackbar.showUndoSnackbar(msg)) {
                                                state.archiveBatch(setOf(entry.item.id), entry.reason)
                                            }
                                        }
                                    }
                                },
                                onDelete = { state.requestDelete(entry) },
                            )
                        }
                    }
                }
            }
        }

        // 两个弹窗都放在 content lambda 内：Miuix 的 WindowDialog 必须无条件调用、靠 show 控制显隐，
        // 否则不显示（库的硬约束，见 MiuixDialog.kt 的 KDoc）；MD3 的 AlertDialog 在内部 if (show)，
        // 放里放外都是独立窗口、渲染无差别，所以统一按 Miuix 的要求落位。
        AppConfirmDialog(
            show = state.pendingDelete != null,
            title = "彻底删除这条归档？",
            message = state.pendingDelete?.let { target ->
                "「${target.item.name}」将被永久删除，无法恢复，也不会回到库存。" +
                    "想留作记录的话，请改用「恢复到库存」。"
            } ?: "",
            confirmText = "彻底删除",
            destructive = true,
            onConfirm = { state.confirmDelete() },
            onDismiss = { state.cancelDelete() },
        )
        AppConfirmDialog(
            show = state.showClearDialog,
            title = "清空归档",
            message = "确定要彻底删除全部 ${state.archived.size} 条归档记录吗？此操作无法撤销。",
            confirmText = "清空",
            destructive = true,
            onConfirm = {
                state.setShowClearDialog(false)
                state.clearArchive()
            },
            onDismiss = { state.setShowClearDialog(false) },
        )
    }
}
