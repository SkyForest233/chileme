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
    private val viewModel: AppViewModel,
) {
    fun getThreshold(categoryId: String): Int =
        thresholds[categoryId] ?: DEFAULT_EXPIRING_THRESHOLD

    fun setThreshold(categoryId: String, days: Int) {
        viewModel.setCategoryThreshold(categoryId, days)
    }
}

@Composable
fun rememberThresholdManageUiState(viewModel: AppViewModel): ThresholdManageUiState {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    return remember(categories, thresholds) {
        ThresholdManageUiState(
            categories = categories,
            thresholds = thresholds,
            viewModel = viewModel,
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
    val editing: CategoryDef?,
    val deleting: CategoryDef?,
    private val viewModel: AppViewModel,
    private val onShowAddChanged: (Boolean) -> Unit,
    private val onEditingChanged: (CategoryDef?) -> Unit,
    private val onDeletingChanged: (CategoryDef?) -> Unit,
) {
    fun getInUseCount(categoryId: String): Int =
        items.count { it.category == categoryId }

    fun setShowAdd(show: Boolean) = onShowAddChanged(show)
    fun setEditing(cat: CategoryDef?) = onEditingChanged(cat)
    fun setDeleting(cat: CategoryDef?) = onDeletingChanged(cat)

    fun addCategory(label: String, emoji: String) {
        viewModel.addCategory(label, emoji)
    }

    fun updateCategory(def: CategoryDef, label: String, emoji: String) {
        viewModel.updateCategory(
            def.copy(
                label = label.trim(),
                emoji = emoji.trim().ifBlank { def.emoji },
            )
        )
    }

    fun deleteCategory(id: String) {
        viewModel.deleteCategory(id)
    }
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
            editing = editing,
            deleting = deleting,
            viewModel = viewModel,
            onShowAddChanged = { showAdd = it },
            onEditingChanged = { editing = it },
            onDeletingChanged = { deleting = it },
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
    private val viewModel: AppViewModel,
    private val onShowAddChanged: (Boolean) -> Unit,
) {
    fun getInUseCount(location: String): Int =
        items.count { it.location == location }

    fun setShowAdd(show: Boolean) = onShowAddChanged(show)

    fun addLocation(name: String) {
        viewModel.addLocation(name)
    }

    fun deleteLocation(name: String) {
        viewModel.deleteLocation(name)
    }
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
            viewModel = viewModel,
            onShowAddChanged = { showAdd = it },
        )
    }
}
