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
    val onQueryChange: (String) -> Unit,
    val statusFilter: FoodStatusFilter,
    val onStatusFilterChange: (FoodStatusFilter) -> Unit,
    val categoryFilter: String?,
    val onCategoryFilterChange: (String?) -> Unit,
    val locationFilter: String?,
    val onLocationFilterChange: (String?) -> Unit,
    val filtersExpanded: Boolean,
    val onFiltersExpandedChange: (Boolean) -> Unit,
    val usedLocations: List<String>,
    val activeFilterCount: Int,
    val filtered: List<FoodItem>,
    val selectedIds: Set<String>,
    val selectionMode: Boolean,
    val onToggleSelection: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onResetFilters: () -> Unit,
    val onChangeQuantity: (id: String, delta: Int) -> Unit,
)

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

    fun onResetFilters() {
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
            onQueryChange = { query = it },
            statusFilter = statusFilter,
            onStatusFilterChange = { statusFilter = it },
            categoryFilter = categoryFilter,
            onCategoryFilterChange = { categoryFilter = it },
            locationFilter = locationFilter,
            onLocationFilterChange = { locationFilter = it },
            filtersExpanded = filtersExpanded,
            onFiltersExpandedChange = { filtersExpanded = it },
            usedLocations = usedLocations,
            activeFilterCount = activeFilterCount,
            filtered = filtered,
            selectedIds = selectedIds,
            selectionMode = selectionMode,
            onToggleSelection = { viewModel.toggleSelection(it) },
            onSelectAll = { viewModel.setSelection(filtered.map { it.id }.toSet()) },
            onClearSelection = { viewModel.clearSelection() },
            onResetFilters = { onResetFilters() },
            onChangeQuantity = { id, delta -> viewModel.changeQuantity(id, delta, withUndo = true) },
        )
    }
}
