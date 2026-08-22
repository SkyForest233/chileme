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

@Composable
fun rememberStatsUiState(viewModel: AppViewModel): StatsUiState {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    val todayDate = LocalToday.current
    val today = todayDate.toEpochDay()
    val weekAgo = today - 6
    val monthStart = todayDate.withDayOfMonth(1).toEpochDay()

    val consumedThisWeek = remember(consumption, weekAgo) {
        consumption.filter { it.epochDay >= weekAgo }.sumOf { it.amount }
    }
    val consumedThisMonth = remember(consumption, monthStart) {
        consumption.filter { it.epochDay >= monthStart }.sumOf { it.amount }
    }
    val wastedTotal = archived.count { it.reason == ArchiveReason.EXPIRED }

    val dailyTrend = remember(consumption, today) {
        (0..6).map { offset ->
            val day = today - (6 - offset)
            val amount = consumption.filter { it.epochDay == day }.sumOf { it.amount }
            LocalDate.ofEpochDay(day) to amount
        }
    }
    val maxDaily = (dailyTrend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    val categoryShare = remember(items) {
        items.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }
    val totalQty = categoryShare.sumOf { it.second }

    val topConsumed = remember(consumption) {
        consumption.groupBy { it.name }
            .map { (name, records) ->
                Triple(name, records.first().category, records.sumOf { it.amount })
            }
            .sortedByDescending { it.third }
            .take(5)
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
