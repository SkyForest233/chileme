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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.data.CategoryDef
import com.agon.app.data.HistoryEntry
import com.agon.app.data.byId
import com.agon.app.ui.components.app.AppMutedText
import com.agon.app.ui.components.app.AppTextScale
import com.agon.app.ui.components.app.appMutedColor
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.ui.theme.filterPanelEnter
import com.agon.app.ui.theme.filterPanelExit
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Recent

/**
 * 编辑页「食品名称 + 历史联想」区块（#10e 从 `EditFoodScreen` 抽出）。
 *
 * 无状态。点联想条会一次回填 9 个表单字段，那段逻辑留在入口（`onPickSuggestion`）；
 * 输入时写 name、清 nameError、展开联想也仍由入口负责。
 * 联想面板的可见条件与进出场动画（filterPanelEnter / filterPanelExit）保持原样。
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
        EditFoodTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = "食品名称",
            placeholder = "例如：草莓夹心饼干",
            isError = nameError,
            errorText = "请输入食品名称",
            singleLine = true,
        )
        AnimatedVisibility(
            visible = !isEdit && suggestions.isNotEmpty() && (showSuggestions || name.isBlank()),
            enter = filterPanelEnter(),
            exit = filterPanelExit(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixIcon(
                            MiuixIcons.Recent,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = appMutedColor(),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = appMutedColor(),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    AppMutedText(
                        if (name.isBlank()) "最近录入过，点击一键填入全部信息" else "匹配到历史记录，点击一键填入",
                        AppTextScale.Tag,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s ->
                        EditFoodSuggestionChip(
                            label = "${s.coverText.ifBlank { categories.byId(s.category).emoji }} ${s.name}",
                            onClick = { onPickSuggestion(s) },
                        )
                    }
                }
            }
        }
    }
}
