package com.agon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.FoodStatus
import com.agon.app.data.byId
import com.agon.app.data.daysLeft
import com.agon.app.data.statusFor
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.FoodCard
import com.agon.app.viewmodel.AppViewModel

private enum class StatusFilter(val label: String) {
    ALL("全部"), SAFE("安全"), EXPIRING("临期"), EXPIRED("已过期")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodListScreen(
    viewModel: AppViewModel,
    initialFilter: String?,
    onOpenItem: (String) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable(initialFilter) {
        mutableStateOf(
            when (initialFilter) {
                "expiring" -> StatusFilter.EXPIRING
                "expired" -> StatusFilter.EXPIRED
                else -> StatusFilter.ALL
            }
        )
    }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var locationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersExpanded by rememberSaveable(initialFilter) {
        mutableStateOf(initialFilter != null)
    }
    // ---- 长按多选（批量归档，选中状态提升到 ViewModel，供 MainActivity 批量操作栏共用）----
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionMode = selectedIds.isNotEmpty()

    // 系统返回键：多选时只退出多选，不切页（在 NavHost 内部，优先级高于导航返回）
    BackHandler(enabled = selectionMode) {
        viewModel.clearSelection()
    }

    val usedLocations = remember(items) {
        items.map { it.location }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount =
        (if (statusFilter != StatusFilter.ALL) 1 else 0) +
            (if (categoryFilter != null) 1 else 0) +
            (if (locationFilter != null) 1 else 0)

    val filtered = remember(items, thresholds, query, statusFilter, categoryFilter, locationFilter) {
        items
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .filter {
                when (statusFilter) {
                    StatusFilter.ALL -> true
                    StatusFilter.SAFE -> it.statusFor(thresholds) == FoodStatus.SAFE
                    StatusFilter.EXPIRING -> it.statusFor(thresholds) == FoodStatus.EXPIRING
                    StatusFilter.EXPIRED -> it.statusFor(thresholds) == FoodStatus.EXPIRED
                }
            }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { locationFilter == null || it.location == locationFilter }
            // 吃完（数量 0）的自动沉底，其余按剩余天数升序
            .sortedWith(compareBy({ it.quantity == 0 }, { it.daysLeft }))
    }

    // 搜索时同时命中归档记录
    val archivedMatches = remember(archived, query) {
        if (query.isBlank()) emptyList()
        else archived.filter { it.item.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text("已选 ${selectedIds.size} 项", fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        // 全选 / 取消全选
                        IconButton(onClick = {
                            viewModel.setSelection(
                                if (selectedIds.size == filtered.size) emptySet()
                                else filtered.map { it.id }.toSet()
                            )
                        }) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = if (selectedIds.size == filtered.size) "取消全选" else "全选",
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
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索食品（含归档）…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                )
                Spacer(Modifier.width(10.dp))
                FilterToggle(
                    expanded = filtersExpanded,
                    activeCount = activeFilterCount,
                    onClick = { filtersExpanded = !filtersExpanded },
                )
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = expandVertically(tween(250, easing = MotionEasing.EmphasizedDecelerate)) +
                    fadeIn(tween(250, easing = MotionEasing.EmphasizedDecelerate)),
                exit = shrinkVertically(tween(200, easing = MotionEasing.EmphasizedAccelerate)) +
                    fadeOut(tween(200, easing = MotionEasing.EmphasizedAccelerate)),
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    FilterSectionLabel("状态")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(StatusFilter.entries.toList()) { f ->
                            FilterChip(
                                selected = statusFilter == f,
                                onClick = { statusFilter = f },
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
                        items(categories, key = { it.id }) { c ->
                            FilterChip(
                                selected = categoryFilter == c.id,
                                onClick = { categoryFilter = if (categoryFilter == c.id) null else c.id },
                                label = { Text("${c.emoji} ${c.label}") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                    if (usedLocations.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FilterSectionLabel("位置")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(usedLocations) { loc ->
                                FilterChip(
                                    selected = locationFilter == loc,
                                    onClick = { locationFilter = if (locationFilter == loc) null else loc },
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

            if (filtered.isEmpty() && archivedMatches.isEmpty()) {
                EmptyState(
                    emoji = if (items.isEmpty()) "🧺" else "🔍",
                    title = if (items.isEmpty()) "零食柜还是空的" else "没有符合条件的食品",
                    subtitle = if (items.isEmpty()) "点击下方“添加”开始记录吧" else "换个关键词或筛选条件试试",
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
                    items(filtered, key = { it.id }) { item ->
                        FoodCard(
                            item = item,
                            category = categories.byId(item.category),
                            status = item.statusFor(thresholds),
                            selectionMode = selectionMode,
                            selected = item.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    onOpenItem(item.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(item.id)
                            },
                            onQuantityChange = { delta -> viewModel.changeQuantity(item.id, delta) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate),
                                fadeOutSpec = tween(200, easing = MotionEasing.EmphasizedAccelerate),
                            ),
                        )
                    }

                    // 归档中的搜索结果
                    if (archivedMatches.isNotEmpty()) {
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
                                    "归档中找到 ${archivedMatches.size} 条",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(archivedMatches, key = { "arch_${it.item.id}" }) { entry ->
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
                                    FoodAvatar(entry.item, categories.byId(entry.item.category).emoji, size = 40.dp)
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
                                    IconButton(onClick = { viewModel.restoreArchived(entry.item.id) }) {
                                        Icon(
                                            Icons.Rounded.RestartAlt,
                                            contentDescription = "恢复 ${entry.item.name}",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteArchived(entry.item.id) }) {
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
        modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterToggle(
    expanded: Boolean,
    activeCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (activeCount > 0) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FilterList, contentDescription = "筛选", modifier = Modifier.size(18.dp))
            if (activeCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "$activeCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

