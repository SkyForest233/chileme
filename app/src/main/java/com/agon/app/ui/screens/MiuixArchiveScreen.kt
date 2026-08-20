package com.agon.app.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 归档页的 Miuix（HyperOS）实现（v2.8 阶段二 P0）。
 *
 * 与 [ArchiveScreen]（Material 3 实现）逻辑对等。结构性组件（Scaffold/TopAppBar/搜索框/
 * OverlayDialog/Snackbar）使用 Miuix；归档行 Surface / 筛选 Chip 复用现有实现（桥接取色）。
 */
@Composable
fun MiuixArchiveScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var reasonFilter by rememberSaveable { mutableStateOf<ArchiveReason?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filtered = remember(archived, reasonFilter, query) {
        archived
            .filter { reasonFilter == null || it.reason == reasonFilter }
            .filter { query.isBlank() || it.item.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = "归档历史",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (archived.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = "清空归档",
                                tint = MiuixTheme.colorScheme.error,
                            )
                        }
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
            InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
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
                    FilterChip(
                        selected = reasonFilter == r,
                        onClick = { reasonFilter = r },
                        label = { Text(r?.let { "${it.emoji} ${it.label}" } ?: "全部", style = MiuixTheme.textStyles.body2) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                EmptyState(
                    emoji = if (query.isNotBlank()) "🔍" else "📚",
                    title = if (archived.isEmpty()) "归档是空的" else "没有符合条件的记录",
                    subtitle = if (query.isNotBlank()) "换个关键词试试" else "删除、清理过期的食品会保存在这里，可随时恢复",
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
                    items(filtered, key = { it.item.id }) { entry ->
                        MiuixArchiveRow(
                            entry = entry,
                            emoji = categories.byId(entry.item.category).emoji,
                            onRestore = {
                                viewModel.restoreArchivedSmart(entry.item.id) { merged ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (merged) "库存中已有同批次“${entry.item.name}”，已合并数量"
                                            else "已恢复“${entry.item.name}”到零食柜"
                                        )
                                    }
                                }
                            },
                            onDelete = { viewModel.deleteArchived(entry.item.id) },
                        )
                    }
                }
            }
        }

        OverlayDialog(
            title = "清空归档",
            summary = "确定要彻底删除全部 ${archived.size} 条归档记录吗？此操作无法撤销。",
            show = showClearDialog,
            onDismissRequest = { showClearDialog = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showClearDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "清空",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearArchive()
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
private fun MiuixArchiveRow(
    entry: ArchivedItem,
    emoji: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodAvatar(entry.item, emoji, size = 44.dp)
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
                    "${entry.reason.emoji} ${entry.reason.label} · ${LocalDate.ofEpochDay(entry.archivedEpochDay).cn()}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = "恢复",
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "彻底删除",
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}
