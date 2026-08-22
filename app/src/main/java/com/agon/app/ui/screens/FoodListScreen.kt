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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.agon.app.ui.theme.MotionSpring
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import com.agon.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
        state.onClearSelection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectionMode) {
                        Text(
                            "已选择 ${state.selectedIds.size} 项",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else {
                        Text("全部食品", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = { state.onClearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "取消选择")
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        // 全选 / 取消全选
                        val allSelected = state.filtered.isNotEmpty() && state.selectedIds.size == state.filtered.size
                        IconButton(onClick = {
                            if (allSelected) state.onClearSelection() else state.onSelectAll()
                        }) {
                            Icon(
                                if (allSelected) Icons.Rounded.Close else Icons.Rounded.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选",
                            )
                        }
                    } else {
                        // 筛选折叠切换按钮
                        FilterToggle(
                            activeCount = state.activeFilterCount,
                            expanded = state.filtersExpanded,
                            onToggle = { state.onFiltersExpandedChange(!state.filtersExpanded) },
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
                .padding(top = padding.calculateTopPadding()),
        ) {
            // ---- Search box ----
            OutlinedTextField(
                value = state.query,
                onValueChange = state.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("搜索食品名称…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { state.onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
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
                                label = { Text(f.label) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                label = { Text("全部分类") },
                                shape = RoundedCornerShape(50),
                            )
                        }
                        items(state.categories, key = { it.id }) { c ->
                            FilterChip(
                                selected = state.categoryFilter == c.id,
                                onClick = { state.onCategoryFilterChange(if (state.categoryFilter == c.id) null else c.id) },
                                label = { Text("${c.emoji} ${c.label}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
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
                                    label = { Text("全部位置") },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                            items(state.usedLocations, key = { it }) { loc ->
                                FilterChip(
                                    selected = state.locationFilter == loc,
                                    onClick = { state.onLocationFilterChange(if (state.locationFilter == loc) null else loc) },
                                    label = { Text("📍 $loc") },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
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
                            categoryEmoji = state.categories.byId(item.category).emoji,
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
                            RecentArchivedEntry(
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
private fun FilterToggle(
    activeCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionSpring.expand<Float>(),
        label = "filterArrow",
    )
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.FilterList,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (activeCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = activeCount,
                transitionSpec = {
                    (fadeIn(tween(150)) + androidx.compose.animation.scaleIn(tween(150)))
                        .togetherWith(fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150)))
                },
                label = "filterCount",
            ) { count ->
                if (count > 0) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        "筛选",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = if (activeCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentArchivedEntry(
    count: Int,
    latestName: String?,
    latestEmoji: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "归档历史（$count 条记录）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (latestName != null) {
                    Text(
                        "最近归档：$latestEmoji $latestName · 点击查看/恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "查看",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
