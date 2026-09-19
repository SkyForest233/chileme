package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agon.app.data.CategoryDef
import com.agon.app.data.cn
import java.time.LocalDate

// #10e：编辑页的六个字段区块（分类 / 数量+单位 / 存放位置 / 生产日期 / 保质期 / 备注）。
// 全部无状态（value + onValueChange）；逻辑与 12 个 rememberSaveable 都留在 EditFoodScreen。

// 两个候选值表：跟着唯一使用者（「数量+单位」与「保质期」区块）搬过来，仍是 private
// ⇒ 本轮可见性放宽 0 处。
private val unitOptions = listOf("件", "包", "袋", "盒", "瓶", "杯", "桶", "罐")
private val shelfLifePresets = listOf(7, 15, 30, 90, 180, 270, 365)

/**
 * 分类选择：候选来自 `viewModel.categories`，选中项以 id 回传。（#10e 抽出，无状态。）
 */
@Composable
internal fun EditFoodCategorySection(
    category: String,
    onCategoryChange: (String) -> Unit,
    categories: List<CategoryDef>,
) {
    Column {
        Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { c ->
                FilterChip(
                    selected = category == c.id,
                    onClick = { onCategoryChange(c.id) },
                    label = { Text("${c.emoji} ${c.label}") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * 数量 + 单位（#10e 抽出，无状态）。
 *
 * ⚠️ 这是一个 `Row` 带两个 `Modifier.weight` 孩子：weight 是 `RowScope` 的扩展，
 * 所以刀口必须落在整棵 Row 子树上 —— 只搬 Row 的一个孩子会编译不过。
 */
@Composable
internal fun EditFoodQuantityUnitSection(
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = quantityText,
            onValueChange = { onQuantityTextChange(it.filter { ch -> ch.isDigit() }.take(4)) },
            modifier = Modifier.weight(1f),
            label = { Text("数量") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Column(Modifier.weight(2f)) {
            Text("单位", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(unitOptions) { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { onUnitChange(u) },
                        label = { Text(u) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
    }
}

/**
 * 存放位置：输入框 + 预设位置 chip（再点一次同一个 = 清空）。（#10e 抽出，无状态。）
 */
@Composable
internal fun EditFoodLocationSection(
    location: String,
    onLocationChange: (String) -> Unit,
    locationPresets: List<String>,
) {
    Column {
        OutlinedTextField(
            value = location,
            onValueChange = { onLocationChange(it.take(12)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("存放位置（可选）") },
            placeholder = { Text("例如：零食柜") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(locationPresets) { loc ->
                FilterChip(
                    selected = location == loc,
                    onClick = { onLocationChange(if (location == loc) "" else loc) },
                    label = { Text(loc) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * 生产日期：只读按钮，点了由入口开弹窗（`production` 已在入口由 epochDay 算好）。
 */
@Composable
internal fun EditFoodProductionDateSection(
    production: LocalDate,
    onPickDate: () -> Unit,
) {
    Column {
        Text("生产日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(production.cn())
        }
    }
}

/**
 * 保质期（天）：输入框 + 常用天数 chip（30 的倍数显示成「N个月」）。（#10e 抽出，无状态。）
 */
@Composable
internal fun EditFoodShelfLifeSection(
    shelfLifeText: String,
    onShelfLifeTextChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = shelfLifeText,
            onValueChange = { onShelfLifeTextChange(it.filter { ch -> ch.isDigit() }.take(4)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("保质期（天）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shelfLifePresets) { d ->
                FilterChip(
                    selected = shelfLifeText == d.toString(),
                    onClick = { onShelfLifeTextChange(d.toString()) },
                    label = { Text(if (d % 30 == 0) "${d / 30}个月" else "${d}天") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                )
            }
        }
    }
}

/**
 * 备注（可选，多行）。（#10e 抽出，无状态。）
 */
@Composable
internal fun EditFoodNoteSection(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("备注（可选）") },
        placeholder = { Text("例如：放在客厅柜子第二层") },
        minLines = 2,
        shape = MaterialTheme.shapes.medium,
    )
}
