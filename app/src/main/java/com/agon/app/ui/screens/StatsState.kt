package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.FoodItem
import com.agon.app.ui.theme.LocalToday
import com.agon.app.viewmodel.AppViewModel
import java.time.LocalDate

/**
 * 统计页跨主题共享状态容器。
 */
class StatsUiState(
    val items: List<FoodItem>,
    val consumption: List<ConsumptionRecord>,
    val archived: List<ArchivedItem>,
    val categories: List<CategoryDef>,
    val thresholds: Map<String, Int>,
    val todayDate: LocalDate,
    val consumedThisWeek: Int,
    val consumedThisMonth: Int,
    val wastedTotal: Int,
    val dailyTrend: List<Pair<LocalDate, Int>>,
    val maxDaily: Int,
    val categoryShare: List<Pair<String, Int>>,
    val totalQty: Int,
    val topConsumed: List<Triple<String, String, Int>>,
) {
    fun findItemIdByName(name: String): String? =
        items.find { it.name == name }?.id
}

/**
 * 统计纯计算函数集（无 Compose 依赖，便于 JVM 单元测试）。
 */
fun calculateConsumedThisWeek(consumption: List<ConsumptionRecord>, todayDate: LocalDate): Int {
    val weekAgo = todayDate.toEpochDay() - 6
    return consumption.filter { it.epochDay >= weekAgo }.sumOf { it.amount }
}

fun calculateConsumedThisMonth(consumption: List<ConsumptionRecord>, todayDate: LocalDate): Int {
    val monthStart = todayDate.withDayOfMonth(1).toEpochDay()
    return consumption.filter { it.epochDay >= monthStart }.sumOf { it.amount }
}

fun calculateDailyTrend(consumption: List<ConsumptionRecord>, todayDate: LocalDate): List<Pair<LocalDate, Int>> {
    val today = todayDate.toEpochDay()
    return (0..6).map { offset ->
        val day = today - (6 - offset)
        val amount = consumption.filter { it.epochDay == day }.sumOf { it.amount }
        LocalDate.ofEpochDay(day) to amount
    }
}

/**
 * 过期浪费总量（**按件数**，不是按归档条目数）。
 *
 * `FoodRepository.archiveItems` 归档时保留原 `quantity`（只有「吃完自动归档」才 copy(quantity = 0)，
 * 那类归属 CONSUMED 不在此列）。所以「冰箱里 6 瓶冰红茶过期」应当是 6，不是 1；
 * 同屏的「本周消耗」也是按件求和（`sumOf { it.amount }`），两者口径必须一致。
 *
 * 注意：本指标仍受**归档保留上限**影响（常量 `ARCHIVE_RETENTION`，定义在 `FoodArchive.kt`）——
 * 超出上限被淘汰的旧归档不再计入。上限从 200 提到 1000（M1-3，2026-09-19）后这个偏差小了一个量级、
 * 但没消除；被挤掉的条数由 `archive_overflow_total` 记账，见 `FoodRepository.archiveOverflowFlow`。
 * 若要长期不失真，需要独立的单调计数器（见 docs/audits/2026-09-15-code-review.md §1.8）。
 */
fun calculateWastedTotal(archived: List<ArchivedItem>): Int =
    archived.filter { it.reason == ArchiveReason.EXPIRED }.sumOf { it.item.quantity }

fun calculateCategoryShare(items: List<FoodItem>): List<Pair<String, Int>> {
    return items.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.quantity } }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }
}

fun calculateTopConsumed(consumption: List<ConsumptionRecord>, limit: Int = 5): List<Triple<String, String, Int>> {
    return consumption.groupBy { it.name }
        .map { (name, records) ->
            Triple(name, records.first().category, records.sumOf { it.amount })
        }
        .sortedByDescending { it.third }
        .take(limit)
}

@Composable
fun rememberStatsUiState(viewModel: AppViewModel): StatsUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    val todayDate = LocalToday.current

    val consumedThisWeek = remember(consumption, todayDate) {
        calculateConsumedThisWeek(consumption, todayDate)
    }
    val consumedThisMonth = remember(consumption, todayDate) {
        calculateConsumedThisMonth(consumption, todayDate)
    }
    val wastedTotal = remember(archived) { calculateWastedTotal(archived) }

    val dailyTrend = remember(consumption, todayDate) {
        calculateDailyTrend(consumption, todayDate)
    }
    val maxDaily = (dailyTrend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val categoryShare = remember(items) {
        calculateCategoryShare(items)
    }
    val totalQty = categoryShare.sumOf { it.second }

    val topConsumed = remember(consumption) {
        calculateTopConsumed(consumption, 5)
    }

    return remember(
        items,
        consumption,
        archived,
        categories,
        thresholds,
        todayDate,
        consumedThisWeek,
        consumedThisMonth,
        wastedTotal,
        dailyTrend,
        maxDaily,
        categoryShare,
        totalQty,
        topConsumed,
    ) {
        StatsUiState(
            items = items,
            consumption = consumption,
            archived = archived,
            categories = categories,
            thresholds = thresholds,
            todayDate = todayDate,
            consumedThisWeek = consumedThisWeek,
            consumedThisMonth = consumedThisMonth,
            wastedTotal = wastedTotal,
            dailyTrend = dailyTrend,
            maxDaily = maxDaily,
            categoryShare = categoryShare,
            totalQty = totalQty,
            topConsumed = topConsumed,
        )
    }
}
