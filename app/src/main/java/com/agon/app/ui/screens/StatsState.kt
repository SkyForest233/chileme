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
    val wastedTotal = archived.count { it.reason == ArchiveReason.EXPIRED }

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
