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
    val onReasonFilterChange: (ArchiveReason?) -> Unit,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val showClearDialog: Boolean,
    val onShowClearDialogChange: (Boolean) -> Unit,
    val onRestoreEntry: (id: String, onDone: (merged: Boolean) -> Unit) -> Unit,
    val onDeleteEntry: (id: String) -> Unit,
    val onClearArchive: () -> Unit,
)

@Composable
fun rememberArchiveUiState(viewModel: AppViewModel): ArchiveUiState {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var reasonFilter by rememberSaveable { mutableStateOf<ArchiveReason?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filtered = remember(archived, reasonFilter, query) {
        archived
            .filter { reasonFilter == null || it.reason == reasonFilter }
            .filter { query.isBlank() || it.item.name.contains(query.trim(), ignoreCase = true) }
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
            onReasonFilterChange = { reasonFilter = it },
            query = query,
            onQueryChange = { query = it },
            showClearDialog = showClearDialog,
            onShowClearDialogChange = { showClearDialog = it },
            onRestoreEntry = { id, onDone -> viewModel.restoreArchivedSmart(id, onDone) },
            onDeleteEntry = { id -> viewModel.deleteArchived(id) },
            onClearArchive = { viewModel.clearArchive() },
        )
    }
}
