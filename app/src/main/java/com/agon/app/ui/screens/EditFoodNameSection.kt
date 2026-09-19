package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.CategoryDef
import com.agon.app.data.HistoryEntry
import com.agon.app.data.byId
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit

/**
 * 编辑页「食品名称 + 历史联想」区块（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 无状态。⚠️ 点联想条会一次回填 9 个表单字段，那段逻辑**留在入口**（`onPickSuggestion`）：
 * 抽到这里要收十几个参数，而它写的字段横跨封面 / 分类 / 数量 / 位置 / 保质期 / 备注 / 阈值
 * 七个区块。输入时的三件事（写 name、清 nameError、展开联想）同理合并进 `onNameChange`。
 *
 * 联想面板的可见条件与进出场动画（filterPanelEnter / filterPanelExit）逐字照搬。
 */
@Composable
internal fun EditFoodNameSection(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    isEdit: Boolean,
    suggestions: List<HistoryEntry>,
    categories: List<CategoryDef>,
    showSuggestions: Boolean,
    onPickSuggestion: (HistoryEntry) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("食品名称") },
            placeholder = { Text("例如：草莓夹心饼干") },
            isError = nameError,
            supportingText = if (nameError) {
                { Text("请输入食品名称") }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        AnimatedVisibility(
            visible = !isEdit && suggestions.isNotEmpty() && (showSuggestions || name.isBlank()),
            enter = filterPanelEnter(),
            exit = filterPanelExit(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (name.isBlank()) "最近录入过，点击一键填入全部信息" else "匹配到历史记录，点击一键填入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        AssistChip(
                            onClick = { onPickSuggestion(s) },
                            label = {
                                Text(
                                    "${s.coverText.ifBlank { categories.byId(s.category).emoji }} ${s.name}"
                                )
                            },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }
        }
    }
}
