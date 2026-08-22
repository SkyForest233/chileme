package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.CategoryDef
import com.agon.app.data.ConsumptionRecord
import com.agon.app.viewmodel.AppViewModel

/**
 * 消耗记录页跨主题共享状态容器。
 */
class ConsumptionLogUiState(
    val sortedRecords: List<ConsumptionRecord>,
    val categories: List<CategoryDef>,
    private val viewModel: AppViewModel,
) {
    fun deleteRecord(record: ConsumptionRecord) {
        viewModel.deleteConsumption(record)
    }
}

@Composable
fun rememberConsumptionLogUiState(viewModel: AppViewModel): ConsumptionLogUiState {
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val sorted = remember(consumption) {
        consumption.sortedByDescending { it.epochDay }
    }

    return remember(sorted, categories) {
        ConsumptionLogUiState(
            sortedRecords = sorted,
            categories = categories,
            viewModel = viewModel,
        )
    }
}
