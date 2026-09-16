package com.agon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.byId
import com.agon.app.data.statusForAt
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.FoodCard
import com.agon.app.ui.components.app.AppActionRow
import com.agon.app.ui.components.app.AppArchiveAction
import com.agon.app.ui.components.app.AppCardTone
import com.agon.app.ui.components.app.AppChipTone
import com.agon.app.ui.components.app.AppFilterChip
import com.agon.app.ui.components.app.AppFilterSectionLabel
import com.agon.app.ui.components.app.AppFilterToggle
import com.agon.app.ui.components.app.AppHistoryNote
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSearchField
import com.agon.app.ui.components.app.AppSelectAllAction
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import com.agon.app.viewmodel.AppViewModel

/**
 * 食品列表页：搜索框 + 「筛选」胶囊 + 三排筛选 chip + 库存卡片（长按进多选）+ 搜索命中的归档条目。
 *
 * 2026-09-16 由 `FoodListScreen`(419) + `MiuixFoodListScreen`(411) 合并为单文件双主题（第三批 #3 第 5 对）。
 * 两版 830 行里只有 166 行不同，且差异**全在叶子上**：顶栏两态、搜索框、筛选胶囊与 chip 的取色/字形/弹簧、
 * 归档匹配行的外壳。库存卡片本来就是共用的 `FoodCard`，业务结构（BackHandler、筛选面板的
 * `AnimatedVisibility`、空态 `Crossfade`、`animateItem` 的 tween 规格）两版逐字相同。
 *
 * 几处照抄而非统一的地方：
 * - **顶栏多选态**：MD3 恒用 `SelectAll` 字形只换 contentDescription，Miuix 已全选时换成 `Close`
 *   字形（见 [AppSelectAllAction]）；MD3 多选态底色转 `surfaceContainer`，Miuix 两态同色。
 * - **筛选胶囊的箭头弹簧**：MD3 `MotionSpring.expand()`、Miuix 库的 `folmeSpring(0.95f, …)`，两套手感。
 * - **chip 选中态**：状态排显式指定文字色，分类/位置排只指定容器色、文字色留库默认（见 [AppFilterChip]）。
 * - **归档匹配行的底色**：MD3 用 `surfaceContainerLow`（比归档页的行低一档），故传
 *   [AppCardTone.ContainerLow]；Miuix 侧两版都是库 `Card` 默认底色，不受这个参数影响。
 * - `Crossfade` 的 `label` 保留 MD3 版的 `"foodListEmptyCrossfade"`（Miuix 版原来加了 `miuix` 前缀
 *   以区分双胞胎文件）—— 它只是转场调试标签，不影响渲染。
 */
@Composable
fun FoodListScreen(
    viewModel: AppViewModel,
    initialFilter: String?,
    onOpenItem: (String) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val state = rememberFoodListUiState(viewModel, initialFilter)

    // 系统返回键：多选时只退出多选，不切页（在 NavHost 内部，优先级高于导航返回）
    BackHandler(enabled = state.selectionMode) {
        state.clearSelection()
    }

    AppScaffold(
        title = if (state.selectionMode) "已选 ${state.selectedIds.size} 项" else "食品列表",
        onClose = if (state.selectionMode) state::clearSelection else null,
        selectionMode = state.selectionMode,
        // 键盘避让：搜索框聚焦后列表可视区止于键盘顶边，末尾条目才滚得出来
        //（原先底部留白只算了导航栏 + 96dp，不含 IME）。两版原来都写在各自 Scaffold 的 modifier 上；
        // 合并后这一行就是 ImeHandlingTest 第 1 条要看的证据（一个条目覆盖两套主题）。
        modifier = Modifier.imePadding(),
        actions = {
            if (state.selectionMode) {
                // 全选 / 取消全选
                AppSelectAllAction(
                    allSelected = state.selectedIds.size == state.filtered.size,
                    onClick = {
                        if (state.selectedIds.size == state.filtered.size) {
                            state.clearSelection()
                        } else {
                            state.selectAll()
                        }
                    },
                )
            } else {
                AppArchiveAction(onClick = onOpenArchive)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSearchField(
                    value = state.query,
                    onValueChange = { state.setQuery(it) },
                    label = "搜索食品…",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                AppFilterToggle(
                    expanded = state.filtersExpanded,
                    activeCount = state.activeFilterCount,
                    onClick = { state.setFiltersExpanded(!state.filtersExpanded) },
                )
            }

            AnimatedVisibility(
                visible = state.filtersExpanded,
                enter = filterPanelEnter(),
                exit = filterPanelExit(),
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    AppFilterSectionLabel("状态")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(FoodStatusFilter.entries.toList()) { f ->
                            AppFilterChip(
                                selected = state.statusFilter == f,
                                onClick = { state.setStatusFilter(f) },
                                label = f.label,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AppFilterSectionLabel("分类")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.categories, key = { it.id }) { c ->
                            AppFilterChip(
                                selected = state.categoryFilter == c.id,
                                onClick = { state.setCategoryFilter(if (state.categoryFilter == c.id) null else c.id) },
                                label = "${c.emoji} ${c.label}",
                                tone = AppChipTone.Secondary,
                            )
                        }
                    }
                    if (state.usedLocations.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        AppFilterSectionLabel("位置")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.usedLocations, key = { it }) { loc ->
                                AppFilterChip(
                                    selected = state.locationFilter == loc,
                                    onClick = { state.setLocationFilter(if (state.locationFilter == loc) null else loc) },
                                    label = loc,
                                    tone = AppChipTone.Tertiary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Crossfade(
                targetState = state.filtered.isEmpty() && state.archivedMatches.isEmpty(),
                label = "foodListEmptyCrossfade",
                modifier = Modifier.fillMaxSize(),
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyState(
                        emoji = if (state.items.isEmpty()) "🧺" else "🔍",
                        title = if (state.items.isEmpty()) "零食柜还是空的" else "没有符合条件的食品",
                        subtitle = if (state.items.isEmpty()) "点击下方“添加”开始记录吧" else "换个关键词或筛选条件试试",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.filtered, key = { it.id }) { item ->
                            FoodCard(
                                item = item,
                                category = state.categories.byId(item.category),
                                status = item.statusForAt(state.today, state.thresholds),
                                selectionMode = state.selectionMode,
                                selected = item.id in state.selectedIds,
                                onClick = {
                                    if (state.selectionMode) {
                                        state.toggleSelection(item.id)
                                    } else {
                                        onOpenItem(item.id)
                                    }
                                },
                                onLongClick = {
                                    state.toggleSelection(item.id)
                                },
                                onQuantityChange = { delta -> state.changeQuantity(item.id, delta) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate),
                                    fadeOutSpec = tween(200, easing = MotionEasing.EmphasizedAccelerate),
                                ),
                            )
                        }

                        // 归档中的搜索结果
                        if (state.archivedMatches.isNotEmpty()) {
                            item(key = "archive_header") {
                                AppHistoryNote(
                                    text = "归档中找到 ${state.archivedMatches.size} 条",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .animateItem(),
                                )
                            }
                            items(state.archivedMatches, key = { "arch_${it.item.id}" }) { entry ->
                                AppActionRow(
                                    title = entry.item.name,
                                    subtitle = "${entry.reason.emoji} ${entry.reason.label}",
                                    tone = AppCardTone.ContainerLow,
                                    modifier = Modifier.animateItem(),
                                    leading = {
                                        FoodAvatar(
                                            entry.item,
                                            state.categories.byId(entry.item.category).emoji,
                                            size = 40.dp,
                                        )
                                    },
                                    onRestore = { state.restoreArchivedWithUndo(entry) },
                                    restoreDescription = "恢复 ${entry.item.name}",
                                    onDelete = { state.deleteArchived(entry.item.id) },
                                    deleteDescription = "彻底删除 ${entry.item.name}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
