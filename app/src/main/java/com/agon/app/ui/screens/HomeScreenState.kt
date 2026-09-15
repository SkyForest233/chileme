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
    /**
     * 过期食品的**件数**（`quantity` 求和），用于「一键清理 N 件」这类面向用户的文案。
     * [expired] 是记录条数，两者在一条记录数量 > 1 时不同 —— 按钮一次会把整条记录
     * 连同它的数量一起归档，所以显示件数才与「一键清理」的实际效果一致。
     */
    val expiredQuantity: Int,
    val urgent: List<FoodItem>,
    val autoSyncMessage: String?,
    private val viewModel: AppViewModel,
) {
    fun consumeAutoSyncMessage() {
        viewModel.consumeAutoSyncMessage()
    }

    fun cleanExpired(onDone: ((Set<String>) -> Unit)? = null) {
        viewModel.cleanExpired(onDone)
    }

    /** 放弃处于损坏态的数据（调用方需先做二次确认）。 */
    fun discardCorruptData() {
        viewModel.discardCorruptData()
    }

    fun restoreArchivedBatch(ids: Set<String>) {
        viewModel.restoreArchivedBatch(ids)
    }
}

/**
 * 首页紧迫待处理项计算纯函数（无 Compose 依赖，便于 JVM 单元测试）。
 */
fun calculateUrgentItems(
    items: List<FoodItem>,
    thresholds: Map<String, Int>,
    today: LocalDate,
): List<FoodItem> {
    return items
        .filter { it.statusForAt(today, thresholds) != FoodStatus.SAFE }
        .sortedBy { it.daysLeftAt(today) }
}

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
    val expiredQuantity = items
        .filter { it.statusForAt(today, thresholds) == FoodStatus.EXPIRED }
        .sumOf { it.quantity }
    val urgent = remember(items, thresholds, today) {
        calculateUrgentItems(items, thresholds, today)
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
        expiredQuantity,
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
            expiredQuantity = expiredQuantity,
            urgent = urgent,
            autoSyncMessage = autoSyncMessage,
            viewModel = viewModel,
        )
    }
}
