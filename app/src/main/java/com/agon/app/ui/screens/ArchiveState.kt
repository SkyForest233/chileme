package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.archiveBatch
import com.agon.app.viewmodel.clearArchive
import com.agon.app.viewmodel.deleteArchived
import com.agon.app.viewmodel.restoreArchivedSmart

/**
 * 归档页跨主题共享状态容器。
 */
class ArchiveUiState(
    val archived: List<ArchivedItem>,
    val categories: List<CategoryDef>,
    val filtered: List<ArchivedItem>,
    val reasonFilter: ArchiveReason?,
    val query: String,
    val showClearDialog: Boolean,
    /** 待确认「彻底删除」的那一条（null = 不显示确认弹窗）。 */
    val pendingDelete: ArchivedItem?,
    private val viewModel: AppViewModel,
    private val onReasonFilterChanged: (ArchiveReason?) -> Unit,
    private val onQueryChanged: (String) -> Unit,
    private val onShowClearDialogChanged: (Boolean) -> Unit,
    private val onPendingDeleteChanged: (ArchivedItem?) -> Unit,
) {
    fun setReasonFilter(reason: ArchiveReason?) = onReasonFilterChanged(reason)
    fun setQuery(newQuery: String) = onQueryChanged(newQuery)
    fun setShowClearDialog(show: Boolean) = onShowClearDialogChanged(show)

    fun restoreEntry(id: String, onDone: (merged: Boolean) -> Unit) {
        viewModel.restoreArchivedSmart(id, onDone)
    }

    fun archiveBatch(ids: Set<String>, reason: ArchiveReason) {
        viewModel.archiveBatch(ids, reason)
    }

    /**
     * 单条「彻底删除」**不能一键生效**（2026-09-15）：归档是误删食品的最后一道保险，
     * 点错一下就没有了，所以先弹确认；确认后才会真的删。
     */
    fun requestDelete(entry: ArchivedItem) = onPendingDeleteChanged(entry)

    fun cancelDelete() = onPendingDeleteChanged(null)

    fun confirmDelete() {
        pendingDelete?.let { viewModel.deleteArchived(it.item.id) }
        onPendingDeleteChanged(null)
    }

    fun clearArchive() {
        viewModel.clearArchive()
    }
}

/**
 * 归档过滤纯函数（无 Compose 依赖，便于 JVM 单元测试）。
 */
fun filterArchiveItems(
    archived: List<ArchivedItem>,
    reasonFilter: ArchiveReason? = null,
    query: String = "",
): List<ArchivedItem> {
    return archived
        .filter { reasonFilter == null || it.reason == reasonFilter }
        .filter { query.isBlank() || it.item.name.contains(query.trim(), ignoreCase = true) }
}

@Composable
fun rememberArchiveUiState(viewModel: AppViewModel): ArchiveUiState {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var reasonFilter by rememberSaveable { mutableStateOf<ArchiveReason?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ArchivedItem?>(null) }

    val filtered = remember(archived, reasonFilter, query) {
        filterArchiveItems(
            archived = archived,
            reasonFilter = reasonFilter,
            query = query,
        )
    }

    return remember(
        archived,
        categories,
        filtered,
        reasonFilter,
        query,
        showClearDialog,
        pendingDelete,
    ) {
        ArchiveUiState(
            archived = archived,
            categories = categories,
            filtered = filtered,
            reasonFilter = reasonFilter,
            query = query,
            showClearDialog = showClearDialog,
            pendingDelete = pendingDelete,
            viewModel = viewModel,
            onReasonFilterChanged = { reasonFilter = it },
            onQueryChanged = { query = it },
            onShowClearDialogChanged = { showClearDialog = it },
            onPendingDeleteChanged = { pendingDelete = it },
        )
    }
}
