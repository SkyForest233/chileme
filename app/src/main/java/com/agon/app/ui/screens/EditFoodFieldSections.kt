package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.agon.app.data.CategoryDef
import com.agon.app.data.cn
import java.time.LocalDate

// #10e：编辑页的六个字段区块（分类 / 数量+单位 / 存放位置 / 生产日期 / 保质期 / 备注）。
// 全部无状态（value + onValueChange）；逻辑与 12 个 rememberSaveable 都留在 EditFoodScreen。

// 两个候选值表：跟着唯一使用者（「数量+单位」与「保质期」）搬过来，仍是 private。
private val unitOptions = listOf("件", "包", "袋", "盒", "瓶", "杯", "桶", "罐")
private val shelfLifePresets = listOf(7, 15, 30, 90, 180, 270, 365)

/** 分类选择：候选来自 `viewModel.categories`，选中项以 id 回传。（无状态。） */
@Composable
internal fun EditFoodCategorySection(
    category: String,
    onCategoryChange: (String) -> Unit,
    categories: List<CategoryDef>,
) {
    Column {
        EditFoodSectionTitle("分类")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { c ->
                EditFoodFilterChip(
                    selected = category == c.id,
                    onClick = { onCategoryChange(c.id) },
                    label = "${c.emoji} ${c.label}",
                    tone = EditFoodChipTone.Primary,
                )
            }
        }
    }
}

/**
 * 数量 + 单位（无状态）。数量字段与两个 weight 孩子仍留在整棵 Row 子树内。
 */
@Composable
internal fun EditFoodQuantityUnitSection(
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EditFoodTextField(
            value = quantityText,
            onValueChange = { onQuantityTextChange(it.filter { ch -> ch.isDigit() }.take(4)) },
            modifier = Modifier.weight(1f),
            label = "数量",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Column(Modifier.weight(2f)) {
            EditFoodSectionTitle("单位")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unitOptions) { u ->
                    EditFoodFilterChip(
                        selected = unit == u,
                        onClick = { onUnitChange(u) },
                        label = u,
                    )
                }
            }
        }
    }
}

/** 存放位置：输入框 + 预设位置 chip（再点一次同一个 = 清空）。 */
@Composable
internal fun EditFoodLocationSection(
    location: String,
    onLocationChange: (String) -> Unit,
    locationPresets: List<String>,
) {
    Column {
        EditFoodTextField(
            value = location,
            onValueChange = { onLocationChange(it.take(12)) },
            modifier = Modifier.fillMaxWidth(),
            label = "存放位置（可选）",
            placeholder = "例如：零食柜",
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(locationPresets) { loc ->
                EditFoodFilterChip(
                    selected = location == loc,
                    onClick = { onLocationChange(if (location == loc) "" else loc) },
                    label = loc,
                    tone = EditFoodChipTone.Tertiary,
                )
            }
        }
    }
}

/** 生产日期：只读按钮，点了由入口开弹窗（`production` 已在入口由 epochDay 算好）。 */
@Composable
internal fun EditFoodProductionDateSection(
    production: LocalDate,
    onPickDate: () -> Unit,
) {
    Column {
        EditFoodSectionTitle("生产日期")
        Spacer(Modifier.height(8.dp))
        EditFoodOutlinedActionButton(
            onClick = onPickDate,
            label = production.cn(),
            icon = Icons.Rounded.CalendarMonth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 保质期（天）：输入框 + 常用天数 chip（30 的倍数显示成「N个月」）。 */
@Composable
internal fun EditFoodShelfLifeSection(
    shelfLifeText: String,
    onShelfLifeTextChange: (String) -> Unit,
) {
    Column {
        EditFoodTextField(
            value = shelfLifeText,
            onValueChange = { onShelfLifeTextChange(it.filter { ch -> ch.isDigit() }.take(4)) },
            modifier = Modifier.fillMaxWidth(),
            label = "保质期（天）",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shelfLifePresets) { d ->
                EditFoodFilterChip(
                    selected = shelfLifeText == d.toString(),
                    onClick = { onShelfLifeTextChange(d.toString()) },
                    label = if (d % 30 == 0) "${d / 30}个月" else "${d}天",
                    tone = EditFoodChipTone.Tertiary,
                )
            }
        }
    }
}

/** 备注（可选，多行）。 */
@Composable
internal fun EditFoodNoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    EditFoodTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier.fillMaxWidth(),
        label = "备注（可选）",
        placeholder = "例如：放在客厅柜子第二层",
        minLines = 2,
    )
}
