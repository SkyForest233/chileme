package com.agon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.FoodCard
import com.agon.app.ui.theme.MotionEasing
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

private enum class MiuixStatusFilter(val label: String) {
    ALL("全部"), SAFE("安全"), EXPIRING("临期"), EXPIRED("已过期")
}

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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable(initialFilter) {
        mutableStateOf(
            when (initialFilter) {
                "expiring" -> MiuixStatusFilter.EXPIRING
                "expired" -> MiuixStatusFilter.EXPIRED
                else -> MiuixStatusFilter.ALL
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
        (if (statusFilter != MiuixStatusFilter.ALL) 1 else 0) +
            (if (categoryFilter != null) 1 else 0) +
            (if (locationFilter != null) 1 else 0)

    val filtered = remember(items, thresholds, query, statusFilter, categoryFilter, locationFilter) {
        items
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .filter {
                when (statusFilter) {
                    MiuixStatusFilter.ALL -> true
                    MiuixStatusFilter.SAFE -> it.statusFor(thresholds) == FoodStatus.SAFE
                    MiuixStatusFilter.EXPIRING -> it.statusFor(thresholds) == FoodStatus.EXPIRING
                    MiuixStatusFilter.EXPIRED -> it.statusFor(thresholds) == FoodStatus.EXPIRED
                }
            }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { locationFilter == null || it.location == locationFilter }
            .sortedWith(compareBy({ it.quantity == 0 }, { it.daysLeft }))
    }

    val archivedMatches = remember(archived, query) {
        if (query.isBlank()) emptyList()
        else archived.filter { it.item.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = "已选 ${selectedIds.size} 项",
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(MiuixIcons.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.setSelection(
                                if (selectedIds.size == filtered.size) emptySet()
                                else filtered.map { it.id }.toSet()
                            )
                        }) {
                            Icon(
                                MiuixIcons.SelectAll,
                                contentDescription = if (selectedIds.size == filtered.size) "取消全选" else "全选",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = "食品列表",
                    actions = {
                        IconButton(onClick = onOpenArchive) {
                            Icon(
                                MiuixIcons.Recent,
                                contentDescription = "归档历史",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    },
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
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = "搜索食品（含归档）…",
                    modifier = Modifier.weight(1f),
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
                        items(MiuixStatusFilter.entries.toList()) { f ->
                            FilterChip(
                                selected = statusFilter == f,
                                onClick = { statusFilter = f },
                                label = { Text(f.label, style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MiuixTheme.colorScheme.onPrimaryContainer,
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
                                label = { Text("${c.emoji} ${c.label}", style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
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
                                    label = { Text(loc, style = MiuixTheme.textStyles.body2) },
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
                            onQuantityChange = { delta -> viewModel.changeQuantity(item.id, delta, withUndo = delta < 0) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(280, easing = MotionEasing.EmphasizedDecelerate),
                                fadeOutSpec = tween(200, easing = MotionEasing.EmphasizedAccelerate),
                            ),
                        )
                    }

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
                                    MiuixIcons.Recent,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "归档中找到 ${archivedMatches.size} 条",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        items(archivedMatches, key = { "arch_${it.item.id}" }) { entry ->
                            Card(
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
                                            style = MiuixTheme.textStyles.subtitle,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${entry.reason.emoji} ${entry.reason.label}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.restoreArchived(entry.item.id) }) {
                                        Icon(
                                            MiuixIcons.Refresh,
                                            contentDescription = "恢复 ${entry.item.name}",
                                            tint = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteArchived(entry.item.id) }) {
                                        Icon(
                                            MiuixIcons.Delete,
                                            contentDescription = "彻底删除 ${entry.item.name}",
                                            modifier = Modifier.size(20.dp),
                                            tint = MiuixTheme.colorScheme.error,
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
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterToggle(
    expanded: Boolean,
    activeCount: Int,
    onClick: () -> Unit,
) {
    // Miuix Icon 读 Miuix 的 LocalContentColor，而此容器是 MD3 Surface（读 MD3 LocalContentColor），
    // 故图标显式 tint，避免取到错误默认色。
    val iconTint = if (activeCount > 0) MiuixTheme.colorScheme.onPrimaryContainer
    else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (activeCount > 0) MiuixTheme.colorScheme.primaryContainer
        else MiuixTheme.colorScheme.surfaceContainerHigh,
        contentColor = iconTint,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MiuixIcons.Filter,
                contentDescription = "筛选",
                modifier = Modifier.size(18.dp),
                tint = iconTint,
            )
            if (activeCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "$activeCount",
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                MiuixIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 180f else 0f),
                tint = iconTint,
            )
        }
    }
}
