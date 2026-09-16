package com.agon.app.ui.components.app

// 输入与筛选控件：搜索框、筛选 chip。两主题的控件 API 完全不同（MD3 OutlinedTextField /
// Miuix InputField；chip 两版都用 material3 FilterChip，只是取色与标签字号走各自主题），
// 收在这里，屏幕层不再出现第二套调用。
//
// 2026-09-16 由 ArchiveScreen + MiuixArchiveScreen 合并时抽出。

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单行搜索框。
 *
 * 两主题差别很大，不是"换个皮"：MD3 是圆角 50 的 `OutlinedTextField` + 左侧放大镜 + placeholder；
 * Miuix 是库的 `InputField`，用 `label` 而不是 placeholder，也没有前导图标。
 * `label` 一个参数同时喂两边（MD3 当 placeholder 文案、Miuix 当 label）。
 *
 * Miuix 侧那三个空实现（`onSearch` / `expanded` / `onExpandedChange`）照抄合并前
 * `MiuixArchiveScreen` 的调用形态——那是仓库里已经编译通过、且真机验过的写法，
 * 不靠记忆补参数（`CLAUDE.md` 明令禁止臆造 Miuix 签名）。
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixInputField(
            query = value,
            onQueryChange = onValueChange,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            label = label,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )
    }
}

/**
 * 筛选用胶囊 chip（「全部 / 各归档原因」这一排）。
 *
 * 两版原来用的都是 **material3 `FilterChip`**（Miuix 侧靠 `MiuixRootTheme` 的 MaterialTheme 桥接取色），
 * 这里保持原样，只把「取哪套色板、标签用哪档字号」分流：
 * MD3 用 `MaterialTheme.colorScheme` + 默认标签样式；Miuix 用 `MiuixTheme.colorScheme` + `body2` 标签。
 */
@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    val miuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (miuix) {
                MiuixText(label, style = MiuixTheme.textStyles.body2)
            } else {
                Text(label)
            }
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (miuix) {
                MiuixTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            selectedLabelColor = if (miuix) {
                MiuixTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
    )
}
