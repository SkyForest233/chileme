package com.agon.app.ui.components.app

// 确认弹窗（标题 + 正文 + 取消/确认两个按钮）。两主题的结构差异最大：
// MD3 是 AlertDialog 的 confirmButton/dismissButton 槽位；Miuix 是 WindowDialog + 自己排一行
// 两个等宽 TextButton。收在这里，屏幕层只写一次文案与回调。
//
// 2026-09-16 由 ArchiveScreen + MiuixArchiveScreen 合并时抽出（原来两版各写两个弹窗，共 4 份）。

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * @param show 是否显示。**Miuix 分支必须无条件调用 `MiuixDialog` 并靠 show 控制**（库的硬约束，
 *   见 `MiuixDialog.kt` 的 KDoc 与 `docs/MIUIX_UPGRADE.md` §2.3），所以这个参数不能省成
 *   「调用方自己 if」；MD3 分支在内部 `if (show)`，因为 `AlertDialog` 一组合就是独立窗口。
 *   也因此：**本组件必须放在 `AppScaffold` 的 content lambda 里面调用**。
 * @param destructive 确认按钮是否用 error 色（彻底删除、清空这类不可撤销操作）。
 *
 * IME：本组件不含输入框，所以不需要 `DialogProperties(decorFitsSystemWindows = false)`
 * （Miuix 的 `WindowDialog` 由库自理 IME）。将来若要加带输入框的弹窗，MD3 分支必须补这个属性，
 * 并把宿主文件加进 `ImeHandlingTest` 第 3 条的清单。
 */
@Composable
fun AppConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    destructive: Boolean = false,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixDialog(
            show = show,
            onDismissRequest = onDismiss,
            title = title,
            summary = message,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixTextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                MiuixConfirmButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    destructive = destructive,
                )
            }
        }
    } else if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    if (destructive) {
                        Text(confirmText, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(confirmText)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            },
        )
    }
}

/**
 * Miuix 侧的确认按钮：危险操作显式传 error 色，非危险操作**不传 colors**（用库默认值）。
 * 不写 `textButtonColors()` 去猜默认色 —— 合并前两版都只有危险操作这一种用法，
 * 非危险分支照库默认走最稳。
 */
@Composable
private fun MiuixConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    if (destructive) {
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            colors = MiuixButtonDefaults.textButtonColors(
                textColor = MiuixTheme.colorScheme.error,
            ),
        )
    } else {
        MiuixTextButton(text = text, onClick = onClick, modifier = modifier)
    }
}
