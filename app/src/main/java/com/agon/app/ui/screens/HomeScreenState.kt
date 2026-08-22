package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.CategoryDef
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import com.agon.app.data.daysLeftAt
import com.agon.app.data.statusForAt
import com.agon.app.ui.theme.LocalToday
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate

/**
 * 首页 Dashboard 跨主题共享状态容器。
 */
class HomeUiState(
    val items: List<FoodItem>,
    val corruptedKeys: Set<String>,
    val categories: List<CategoryDef>,
    val thresholds: Map<String, Int>,
    val today: LocalDate,
    val total: Int,
    val expiring: Int,
    val expired: Int,
    val urgent: List<FoodItem>,
    val autoSyncMessage: String?,
    val onConsumeAutoSyncMessage: () -> Unit,
    val onCleanExpired: () -> Unit,
)

@Composable
fun rememberHomeUiState(viewModel: AppViewModel): HomeUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val corruptedKeys by viewModel.corruptedKeys.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val autoSyncMessage by viewModel.autoSyncMessage.collectAsStateWithLifecycle()

    val today = LocalToday.current
    val total = items.size
    val expiring = items.count { it.statusForAt(today, thresholds) == FoodStatus.EXPIRING }
    val expired = items.count { it.statusForAt(today, thresholds) == FoodStatus.EXPIRED }
    val urgent = remember(items, thresholds, today) {
        items.filter { it.statusForAt(today, thresholds) != FoodStatus.SAFE }.sortedBy { it.daysLeftAt(today) }
    }

    return remember(
        items,
        corruptedKeys,
        categories,
        thresholds,
        today,
        total,
        expiring,
        expired,
        urgent,
        autoSyncMessage,
    ) {
        HomeUiState(
            items = items,
            corruptedKeys = corruptedKeys,
            categories = categories,
            thresholds = thresholds,
            today = today,
            total = total,
            expiring = expiring,
            expired = expired,
            urgent = urgent,
            autoSyncMessage = autoSyncMessage,
            onConsumeAutoSyncMessage = { viewModel.consumeAutoSyncMessage() },
            onCleanExpired = { viewModel.cleanExpired() },
        )
    }
}
