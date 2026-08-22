package com.agon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.byId
import com.agon.app.data.statusForAt
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.FoodCard
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 食品列表页的 Miuix（HyperOS）实现（v2.8 阶段二）。
 *
 * 与 [FoodListScreen]（Material 3 实现）逻辑对等。结构性组件（Scaffold/TopAppBar/搜索框/
 * 批量归档栏/Snackbar）使用 Miuix 组件；FoodCard/筛选 Chip 复用现有实现（经根级桥接的
 * MaterialTheme 取 Miuix 配色），属渐进迁移的预期中间态。
 */
@Composable
fun MiuixFoodListScreen(
    viewModel: AppViewModel,
    initialFilter: String?,
    onOpenItem: (String) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val state = rememberFoodListUiState(viewModel, initialFilter)

    // 系统返回键：多选时只退出多选，不切页（在 NavHost 内部，优先级高于导航返回）
    BackHandler(enabled = state.selectionMode) {
        state.onClearSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (state.selectionMode) "已选择 ${state.selectedIds.size} 项" else "全部食品",
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = { state.onClearSelection() }) {
                            Icon(MiuixIcons.Close, contentDescription = "取消选择")
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        val allSelected = state.filtered.isNotEmpty() && state.selectedIds.size == state.filtered.size
                        IconButton(onClick = {
                            if (allSelected) state.onClearSelection() else state.onSelectAll()
                        }) {
                            Icon(
                                if (allSelected) MiuixIcons.Close else MiuixIcons.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选",
                            )
                        }
                    } else {
                        MiuixFilterToggle(
                            activeCount = state.activeFilterCount,
                            expanded = state.filtersExpanded,
                            onToggle = { state.onFiltersExpandedChange(!state.filtersExpanded) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // ---- Miuix InputField ----
            InputField(
                query = state.query,
                onQueryChange = state.onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = "搜索食品名称…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            // ---- Expandable Filter Panel ----
            AnimatedVisibility(
                visible = state.filtersExpanded,
                enter = filterPanelEnter(),
                exit = filterPanelExit(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Status filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(FoodStatusFilter.entries) { f ->
                            FilterChip(
                                selected = state.statusFilter == f,
                                onClick = { state.onStatusFilterChange(f) },
                                label = { Text(f.label, style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MiuixTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }

                    // Category filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.categoryFilter == null,
                                onClick = { state.onCategoryFilterChange(null) },
                                label = { Text("全部分类", style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                            )
                        }
                        items(state.categories, key = { it.id }) { c ->
                            FilterChip(
                                selected = state.categoryFilter == c.id,
                                onClick = { state.onCategoryFilterChange(if (state.categoryFilter == c.id) null else c.id) },
                                label = { Text("${c.emoji} ${c.label}", style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }

                    // Location filter chips
                    if (state.usedLocations.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = state.locationFilter == null,
                                    onClick = { state.onLocationFilterChange(null) },
                                    label = { Text("全部位置", style = MiuixTheme.textStyles.body2) },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                            items(state.usedLocations, key = { it }) { loc ->
                                FilterChip(
                                    selected = state.locationFilter == loc,
                                    onClick = { state.onLocationFilterChange(if (state.locationFilter == loc) null else loc) },
                                    label = { Text("📍 $loc", style = MiuixTheme.textStyles.body2) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Content list ----
            if (state.items.isEmpty()) {
                EmptyState(
                    emoji = "🥫",
                    title = "零食柜还是空的",
                    subtitle = "点击右下角的“+”添加第一件食品吧",
                )
            } else if (state.filtered.isEmpty()) {
                EmptyState(
                    emoji = "🔍",
                    title = "没有找到匹配的食品",
                    subtitle = "试试清除筛选条件或换个关键词",
                    actionLabel = "清除筛选",
                    onAction = state.onResetFilters,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.filtered, key = { it.id }) { item ->
                        val selected = item.id in state.selectedIds
                        FoodCard(
                            item = item,
                            category = state.categories.byId(item.category),
                            status = item.statusForAt(state.today, state.thresholds),
                            onClick = {
                                if (state.selectionMode) state.onToggleSelection(item.id)
                                else onOpenItem(item.id)
                            },
                            onLongClick = {
                                state.onToggleSelection(item.id)
                            },
                            selected = selected,
                            selectionMode = state.selectionMode,
                            onQuantityChange = { delta ->
                                state.onChangeQuantity(item.id, delta)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    // 列表底部入口：归档历史快速直达
                    if (state.archived.isNotEmpty()) {
                        item {
                            MiuixRecentArchivedEntry(
                                count = state.archived.size,
                                latestName = state.archived.firstOrNull()?.item?.name,
                                latestEmoji = state.archived.firstOrNull()?.let {
                                    state.categories.byId(it.item.category).emoji
                                } ?: "🧺",
                                onClick = onOpenArchive,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixFilterToggle(
    activeCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = top.yukonga.miuix.kmp.anim.folmeSpring<Float>(
            damping = 0.95f,
            response = if (expanded) 0.2f else 0.3f,
        ),
        label = "miuixFilterArrow",
    )
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = if (activeCount > 0) MiuixTheme.colorScheme.primaryContainer
        else MiuixTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                MiuixIcons.Filter,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (activeCount > 0) MiuixTheme.colorScheme.onPrimaryContainer
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            AnimatedContent(
                targetState = activeCount,
                transitionSpec = {
                    (fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(150)))
                        .togetherWith(fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150)))
                },
                label = "miuixFilterCount",
            ) { count ->
                if (count > 0) {
                    Text(
                        "$count",
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        "筛选",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Icon(
                MiuixIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = if (activeCount > 0) MiuixTheme.colorScheme.onPrimaryContainer
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun MiuixRecentArchivedEntry(
    count: Int,
    latestName: String?,
    latestEmoji: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MiuixIcons.Recent,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "归档历史（$count 条记录）",
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                )
                if (latestName != null) {
                    Text(
                        "最近归档：$latestEmoji $latestName · 点击查看/恢复",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "查看",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
