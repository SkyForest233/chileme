package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.CategoryDef
import com.agon.app.data.DEFAULT_EXPIRING_THRESHOLD
import com.agon.app.data.FoodItem
import com.agon.app.viewmodel.AppViewModel

/**
 * 临期阈值管理跨主题共享状态。
 */
class ThresholdManageUiState(
    val categories: List<CategoryDef>,
    val thresholds: Map<String, Int>,
    val onSetThreshold: (categoryId: String, days: Int) -> Unit,
) {
    fun getThreshold(categoryId: String): Int =
        thresholds[categoryId] ?: DEFAULT_EXPIRING_THRESHOLD
}

@Composable
fun rememberThresholdManageUiState(viewModel: AppViewModel): ThresholdManageUiState {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    return remember(categories, thresholds) {
        ThresholdManageUiState(
            categories = categories,
            thresholds = thresholds,
            onSetThreshold = { id, days -> viewModel.setCategoryThreshold(id, days) },
        )
    }
}

/**
 * 分类管理跨主题共享状态。
 */
class CategoryManageUiState(
    val categories: List<CategoryDef>,
    val items: List<FoodItem>,
    val showAdd: Boolean,
    val onShowAddChange: (Boolean) -> Unit,
    val editing: CategoryDef?,
    val onEditingChange: (CategoryDef?) -> Unit,
    val deleting: CategoryDef?,
    val onDeletingChange: (CategoryDef?) -> Unit,
    val onAddCategory: (label: String, emoji: String) -> Unit,
    val onUpdateCategory: (def: CategoryDef, label: String, emoji: String) -> Unit,
    val onDeleteCategory: (id: String) -> Unit,
) {
    fun getInUseCount(categoryId: String): Int =
        items.count { it.category == categoryId }
}

@Composable
fun rememberCategoryManageUiState(viewModel: AppViewModel): CategoryManageUiState {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }

    return remember(categories, items, showAdd, editing, deleting) {
        CategoryManageUiState(
            categories = categories,
            items = items,
            showAdd = showAdd,
            onShowAddChange = { showAdd = it },
            editing = editing,
            onEditingChange = { editing = it },
            deleting = deleting,
            onDeletingChange = { deleting = it },
            onAddCategory = { label, emoji -> viewModel.addCategory(label, emoji) },
            onUpdateCategory = { def, label, emoji ->
                viewModel.updateCategory(
                    def.copy(
                        label = label.trim(),
                        emoji = emoji.trim().ifBlank { def.emoji },
                    )
                )
            },
            onDeleteCategory = { id -> viewModel.deleteCategory(id) },
        )
    }
}

/**
 * 存放位置管理跨主题共享状态。
 */
class LocationManageUiState(
    val locations: List<String>,
    val items: List<FoodItem>,
    val showAdd: Boolean,
    val onShowAddChange: (Boolean) -> Unit,
    val onAddLocation: (name: String) -> Unit,
    val onDeleteLocation: (name: String) -> Unit,
) {
    fun getInUseCount(location: String): Int =
        items.count { it.location == location }
}

@Composable
fun rememberLocationManageUiState(viewModel: AppViewModel): LocationManageUiState {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    return remember(locations, items, showAdd) {
        LocationManageUiState(
            locations = locations,
            items = items,
            showAdd = showAdd,
            onShowAddChange = { showAdd = it },
            onAddLocation = { name -> viewModel.addLocation(name) },
            onDeleteLocation = { name -> viewModel.deleteLocation(name) },
        )
    }
}
