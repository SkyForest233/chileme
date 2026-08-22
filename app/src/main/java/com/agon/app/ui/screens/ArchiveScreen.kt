package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.byId
import com.agon.app.data.cn
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.FoodAvatar
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val state = rememberArchiveUiState(viewModel)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SwipeDismissSnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("归档历史", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.archived.isNotEmpty()) {
                        IconButton(onClick = { state.setShowClearDialog(true) }) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = "清空归档",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
            OutlinedTextField(
                value = state.query,
                onValueChange = { state.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("搜索归档食品…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf<ArchiveReason?>(null) + ArchiveReason.entries.toList()) { r ->
                    FilterChip(
                        selected = state.reasonFilter == r,
                        onClick = { state.setReasonFilter(r) },
                        label = { Text(r?.let { "${it.emoji} ${it.label}" } ?: "全部") },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.filtered.isEmpty()) {
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
                        ArchiveRow(
                            entry = entry,
                            emoji = state.categories.byId(entry.item.category).emoji,
                            onRestore = {
                                state.restoreEntry(entry.item.id) { merged ->
                                    scope.launch {
                                        val msg = if (merged) "库存中已有同批次「${entry.item.name}」，已合并数量"
                                                  else "已恢复「${entry.item.name}」到零食柜"
                                        val result = snackbarHostState.showUndoSnackbar(msg)
                                        if (result == SnackbarResult.ActionPerformed) {
                                            state.archiveBatch(setOf(entry.item.id), entry.reason)
                                        }
                                    }
                                }
                            },
                            onDelete = { state.deleteEntry(entry.item.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowClearDialog(false) },
            title = { Text("清空归档") },
            text = { Text("确定要彻底删除全部 ${state.archived.size} 条归档记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    state.setShowClearDialog(false)
                    state.clearArchive()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.setShowClearDialog(false) }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ArchiveRow(
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.reason.emoji} ${entry.reason.label} · ${LocalDate.ofEpochDay(entry.archivedEpochDay).cn()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = "恢复",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "彻底删除",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
