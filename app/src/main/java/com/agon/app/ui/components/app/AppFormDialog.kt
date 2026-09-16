package com.agon.app.ui.components.app

// 带输入框的弹窗：管理页的「添加/编辑分类」（两个字段）与「添加存放位置」（一个字段）。
// 设置页的坚果云账号 / 应用密码弹窗是同一形态，第 8 对合并时再决定要不要并进来（不提前抽象）。
//
// ⚠️ MD3 分支的 `DialogProperties(decorFitsSystemWindows = false)` + `Modifier.imePadding()` 是
// `ImeHandlingTest` 第 3 条**按文件点名**的位置：这个弹窗再搬家，测试清单要跟着改
// （本文件就是 2026-09-16 从 `ManageScreens.kt` 搬来的，同批已把清单里那一行改到这里）。

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * 弹窗里一个输入框的规格。
 *
 * @param maxLength 最多接受多少字符（分类名 8 / emoji 4 / 位置名 12，合并前两版一致）。
 *   **两主题的截断时机不同**，见 [AppFormDialog] 的 KDoc。
 * @param placeholder **只在 MD3 侧生效**：Miuix 的 `TextField` 合并前传的是
 *   `useLabelAsPlaceholder = true`（拿 label 当占位符），没有独立占位符形参 —— 按
 *   `docs/ARCHITECTURE.md` 的约束 ⑥ 点名哪边不生效，不替 Miuix 造一个它没有的槽。
 */
data class AppFormFieldSpec(
    val label: String,
    val maxLength: Int,
    val initial: String = "",
    val placeholder: String = "",
)

/**
 * 带输入框的确认弹窗：标题 + N 个输入框 + 「取消 / 确认」两个等宽按钮。
 *
 * **两主题的输入机制根本不同，这里各走各的、不做桥接**：
 * - MD3 用 `OutlinedTextField(value, onValueChange)`，**边打字边截断**（`take(maxLength)`），
 *   所以用户根本打不进第 9 个字符；
 * - Miuix 用 `TextField(state: TextFieldState)`，**允许超长输入，点确认时才 `take(maxLength)`**。
 *
 * 把两者塞进同一个「value + onValueChange」外壳，需要在 Miuix 侧用 `LaunchedEffect` 把截断后的文本
 * 回写 `TextFieldState` —— 那会动到光标位置与状态重建时机，是只有真机看得出来的行为改动。
 * 所以本组件保留两套机制，只保证**对外读到的文本一致**：`onConfirm` 拿到的永远是截断后的值，
 * 确认按钮的可用条件也永远是「第一个字段非空白」（两版合并前都是这个口径）。
 *
 * 弹窗落位遵守 [AppConfirmDialog] 那条库约束：Miuix 侧**无条件调用** + `show` 控制，
 * MD3 侧 `show` 为 false 时不组合 `AlertDialog`（与合并前 MD3 的 `if (show) { … }` 等价）。
 *
 * @param fields 用 `data class` 是为了拿它当 `remember` 的 key：调用方每次都新建一个 List，
 *   按引用比较会在每次重组时把用户已经打进去的字清空（合并前 Miuix 版 key 的是
 *   `remember(show, initialLabel)`，同理）。
 */
@Composable
fun AppFormDialog(
    title: String,
    show: Boolean,
    fields: List<AppFormFieldSpec>,
    confirmText: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val states = remember(show, fields) { fields.map { TextFieldState(it.initial) } }
        MiuixDialog(
            show = show,
            onDismissRequest = onDismiss,
            title = title,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                states.forEachIndexed { index, fieldState ->
                    MiuixTextField(
                        state = fieldState,
                        label = fields[index].label,
                        useLabelAsPlaceholder = true,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm(
                            states.mapIndexed { index, fieldState ->
                                fieldState.text.toString().take(fields[index].maxLength)
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = states.firstOrNull()?.text?.isNotBlank() == true,
                )
            }
        }
    } else if (show) {
        val values = remember(show, fields) { fields.map { it.initial }.toMutableStateList() }
        AlertDialog(
            onDismissRequest = onDismiss,
            // 键盘避让（2026-09-15 起）：MD3 弹窗是独立浮动窗口，必须关掉 decorFitsSystemWindows
            // IME inset 才传得进来，否则底部「确定 / 取消」会被键盘盖住。见 SettingsScreen 坚果云弹窗处的说明。
            properties = DialogProperties(decorFitsSystemWindows = false),
            modifier = Modifier.imePadding(),
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fields.forEachIndexed { index, spec ->
                        OutlinedTextField(
                            value = values[index],
                            onValueChange = { values[index] = it.take(spec.maxLength) },
                            label = { Text(spec.label) },
                            placeholder = { Text(spec.placeholder) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(values.toList()) },
                    enabled = values.firstOrNull()?.isNotBlank() == true,
                ) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            },
        )
    }
}
