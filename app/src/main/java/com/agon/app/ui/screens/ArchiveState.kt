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
    private val viewModel: AppViewModel,
    private val onReasonFilterChanged: (ArchiveReason?) -> Unit,
    private val onQueryChanged: (String) -> Unit,
    private val onShowClearDialogChanged: (Boolean) -> Unit,
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

    fun deleteEntry(id: String) {
        viewModel.deleteArchived(id)
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
    ) {
        ArchiveUiState(
            archived = archived,
            categories = categories,
            filtered = filtered,
            reasonFilter = reasonFilter,
            query = query,
            showClearDialog = showClearDialog,
            viewModel = viewModel,
            onReasonFilterChanged = { reasonFilter = it },
            onQueryChanged = { query = it },
            onShowClearDialogChanged = { showClearDialog = it },
        )
    }
}
