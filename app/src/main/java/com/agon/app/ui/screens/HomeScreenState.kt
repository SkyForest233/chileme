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
import com.agon.app.viewmodel.cleanExpired
import com.agon.app.viewmodel.discardCorruptData
import com.agon.app.viewmodel.restoreArchivedBatch
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
    /**
     * 临期食品的**件数**（`quantity` 求和），同上：首页新鲜度横幅说的是「有 N 件食品即将到期」，
     * 用 [expiring]（记录条数）会在「一条 6 瓶」时显示成 1 件。
     */
    val expiringQuantity: Int,
    val urgent: List<FoodItem>,
    private val viewModel: AppViewModel,
) {
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

/**
 * 指定状态食品的**件数**（一条记录可能是多件）。
 *
 * 2026-09-15：首页多处文案写「件」，此前却传记录条数（`items.count{...}`）——
 * 一条「牛奶 ×6」会显示成 1 件，与本轮统一的按件口径矛盾。抽成纯函数顺带可测。
 */
fun quantityOfStatus(
    items: List<FoodItem>,
    thresholds: Map<String, Int>,
    today: LocalDate,
    status: FoodStatus,
): Int = items.filter { it.statusForAt(today, thresholds) == status }.sumOf { it.quantity }

@Composable
fun rememberHomeUiState(viewModel: AppViewModel): HomeUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val corruptedKeys by viewModel.corruptedKeys.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val today = LocalToday.current
    val total = items.size
    val expiring = items.count { it.statusForAt(today, thresholds) == FoodStatus.EXPIRING }
    val expired = items.count { it.statusForAt(today, thresholds) == FoodStatus.EXPIRED }
    val expiredQuantity = quantityOfStatus(items, thresholds, today, FoodStatus.EXPIRED)
    val expiringQuantity = quantityOfStatus(items, thresholds, today, FoodStatus.EXPIRING)
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
        expiringQuantity,
        urgent,
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
            expiringQuantity = expiringQuantity,
            urgent = urgent,
            viewModel = viewModel,
        )
    }
}
