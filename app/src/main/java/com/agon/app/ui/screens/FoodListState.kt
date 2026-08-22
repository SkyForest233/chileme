package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.daysLeftAt
import com.agon.app.data.statusForAt
import com.agon.app.ui.theme.LocalToday
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate

enum class FoodStatusFilter(val label: String) {
    ALL("全部"), SAFE("安全"), EXPIRING("临期"), EXPIRED("已过期")
}

/**
 * 食品列表页跨主题共享状态容器。
 */
class FoodListUiState(
    val items: List<FoodItem>,
    val archived: List<ArchivedItem>,
    val categories: List<CategoryDef>,
    val thresholds: Map<String, Int>,
    val today: LocalDate,
    val query: String,
    val statusFilter: FoodStatusFilter,
    val categoryFilter: String?,
    val locationFilter: String?,
    val filtersExpanded: Boolean,
    val usedLocations: List<String>,
    val activeFilterCount: Int,
    val filtered: List<FoodItem>,
    val selectedIds: Set<String>,
    val selectionMode: Boolean,
    private val viewModel: AppViewModel,
    private val onQueryChanged: (String) -> Unit,
    private val onStatusFilterChanged: (FoodStatusFilter) -> Unit,
    private val onCategoryFilterChanged: (String?) -> Unit,
    private val onLocationFilterChanged: (String?) -> Unit,
    private val onFiltersExpandedChanged: (Boolean) -> Unit,
    private val onResetFiltersAction: () -> Unit,
) {
    fun setQuery(q: String) = onQueryChanged(q)
    fun setStatusFilter(f: FoodStatusFilter) = onStatusFilterChanged(f)
    fun setCategoryFilter(c: String?) = onCategoryFilterChanged(c)
    fun setLocationFilter(l: String?) = onLocationFilterChanged(l)
    fun setFiltersExpanded(e: Boolean) = onFiltersExpandedChanged(e)
    fun resetFilters() = onResetFiltersAction()

    fun toggleSelection(id: String) = viewModel.toggleSelection(id)
    fun selectAll() = viewModel.setSelection(filtered.map { it.id }.toSet())
    fun clearSelection() = viewModel.clearSelection()

    fun changeQuantity(id: String, delta: Int) =
        viewModel.changeQuantity(id, delta, withUndo = true)
}

@Composable
fun rememberFoodListUiState(
    viewModel: AppViewModel,
    initialFilter: String?,
): FoodListUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable(initialFilter) {
        mutableStateOf(
            when (initialFilter?.lowercase()) {
                "expiring" -> FoodStatusFilter.EXPIRING
                "expired" -> FoodStatusFilter.EXPIRED
                "safe" -> FoodStatusFilter.SAFE
                else -> FoodStatusFilter.ALL
            }
        )
    }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var locationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersExpanded by rememberSaveable(initialFilter) {
        mutableStateOf(initialFilter != null)
    }

    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionMode = selectedIds.isNotEmpty()

    val usedLocations = remember(items) {
        items.map { it.location }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount =
        (if (statusFilter != FoodStatusFilter.ALL) 1 else 0) +
            (if (categoryFilter != null) 1 else 0) +
            (if (locationFilter != null) 1 else 0)

    val today = LocalToday.current
    val filtered = remember(items, thresholds, query, statusFilter, categoryFilter, locationFilter, today) {
        items
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .filter {
                when (statusFilter) {
                    FoodStatusFilter.ALL -> true
                    FoodStatusFilter.SAFE -> it.statusForAt(today, thresholds) == FoodStatus.SAFE
                    FoodStatusFilter.EXPIRING -> it.statusForAt(today, thresholds) == FoodStatus.EXPIRING
                    FoodStatusFilter.EXPIRED -> it.statusForAt(today, thresholds) == FoodStatus.EXPIRED
                }
            }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { locationFilter == null || it.location == locationFilter }
            .sortedWith(compareBy({ it.quantity == 0 }, { it.daysLeftAt(today) }))
    }

    fun performReset() {
        query = ""
        statusFilter = FoodStatusFilter.ALL
        categoryFilter = null
        locationFilter = null
    }

    return remember(
        items,
        archived,
        categories,
        thresholds,
        today,
        query,
        statusFilter,
        categoryFilter,
        locationFilter,
        filtersExpanded,
        usedLocations,
        activeFilterCount,
        filtered,
        selectedIds,
        selectionMode,
    ) {
        FoodListUiState(
            items = items,
            archived = archived,
            categories = categories,
            thresholds = thresholds,
            today = today,
            query = query,
            statusFilter = statusFilter,
            categoryFilter = categoryFilter,
            locationFilter = locationFilter,
            filtersExpanded = filtersExpanded,
            usedLocations = usedLocations,
            activeFilterCount = activeFilterCount,
            filtered = filtered,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            viewModel = viewModel,
            onQueryChanged = { query = it },
            onStatusFilterChanged = { statusFilter = it },
            onCategoryFilterChanged = { categoryFilter = it },
            onLocationFilterChanged = { locationFilter = it },
            onFiltersExpandedChanged = { filtersExpanded = it },
            onResetFiltersAction = { performReset() },
        )
    }
}
