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
        state.clearSelection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = {
                        Text("已选 ${state.selectedIds.size} 项", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { state.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        // 全选 / 取消全选
                        IconButton(onClick = {
                            if (state.selectedIds.size == state.filtered.size) state.clearSelection()
                            else state.selectAll()
                        }) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = if (state.selectedIds.size == state.filtered.size) "取消全选" else "全选",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text("食品列表", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onOpenArchive) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = "归档历史",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { state.setQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索食品（含归档）…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                )
                Spacer(Modifier.width(10.dp))
                FilterToggle(
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
                    FilterSectionLabel("状态")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(FoodStatusFilter.entries.toList()) { f ->
                            FilterChip(
                                selected = state.statusFilter == f,
                                onClick = { state.setStatusFilter(f) },
                                label = { Text(f.label) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    FilterSectionLabel("分类")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.categories, key = { it.id }) { c ->
                            FilterChip(
                                selected = state.categoryFilter == c.id,
                                onClick = { state.setCategoryFilter(if (state.categoryFilter == c.id) null else c.id) },
                                label = { Text("${c.emoji} ${c.label}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                    if (state.usedLocations.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FilterSectionLabel("位置")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.usedLocations, key = { it }) { loc ->
                                FilterChip(
                                    selected = state.locationFilter == loc,
                                    onClick = { state.setLocationFilter(if (state.locationFilter == loc) null else loc) },
                                    label = { Text(loc) },
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

            if (state.filtered.isEmpty() && state.archivedMatches.isEmpty()) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .animateItem(),
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "归档中找到 ${state.archivedMatches.size} 条",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.archivedMatches, key = { "arch_${it.item.id}" }) { entry ->
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FoodAvatar(entry.item, state.categories.byId(entry.item.category).emoji, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${entry.reason.emoji} ${entry.reason.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { state.restoreArchivedWithUndo(entry) }) {
                                        Icon(
                                            Icons.Rounded.RestartAlt,
                                            contentDescription = "恢复 ${entry.item.name}",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { state.deleteArchived(entry.item.id) }) {
                                        Icon(
                                            Icons.Rounded.DeleteForever,
                                            contentDescription = "彻底删除 ${entry.item.name}",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun FilterToggle(
    expanded: Boolean,
    activeCount: Int,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MotionSpring.expand<Float>(),
        label = "filterArrow",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.FilterList,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
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
                Text(
                    if (count > 0) "筛选($count)" else "筛选",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (count > 0) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = if (activeCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
