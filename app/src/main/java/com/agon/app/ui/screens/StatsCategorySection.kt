/**
 * 统计页的「库存分类占比（环图 + 图例）」区块（09-19 #11d ② 从 `StatsScreen` 的装配体里搬出来）。
 *
 * 为什么单独一个文件：环图与图例共用同一份 `chartColors` 取色（下标同源才不会错行）⇒ 调色板必须一起当参数传。
 * 与 #10e 的 `EditFood*Section.kt` 同形：同包 `internal` + 参数表按入口局部审计的结果给，
 * 所以调用点的 import 一处不改（判据②预测 0 = 实测 0）。
 */
package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agon.app.data.byId
import com.agon.app.ui.components.DonutChart
import com.agon.app.ui.components.LegendRow
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppSection
import com.agon.app.ui.components.app.AppTextScale

// ---- 库存分类占比（环图 + 图例）----
@Composable
internal fun StatsCategorySection(
    state: StatsUiState,
    chartColors: List<Color>,
) {
    AppSection(cardTitle = "库存分类占比", sectionTitle = "库存分类") {
        if (state.categoryShare.isEmpty()) {
            AppMutedText("暂无库存数据", AppTextScale.Meta)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DonutChart(
                    data = state.categoryShare.map { it.second.toFloat() },
                    colors = state.categoryShare.mapIndexed { i, _ -> chartColors[i % chartColors.size] },
                    centerLabel = "${state.totalQty}",
                    centerSub = "总件数",
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.categoryShare.forEachIndexed { i, (catId, qty) ->
                        LegendRow(
                            color = chartColors[i % chartColors.size],
                            category = state.categories.byId(catId),
                            qty = qty,
                            percent = if (state.totalQty > 0) qty * 100 / state.totalQty else 0,
                        )
                    }
                }
            }
        }
    }
}
