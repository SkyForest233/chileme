package com.agon.app.ui.screens

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.data.BACKUP_VERSION
import com.agon.app.data.cn
import com.agon.app.data.fileStamp
import com.agon.app.data.itemQuantity
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.AppOptionDialog
import com.agon.app.ui.components.app.AppOptionSpec
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 设置页的备份与导入弹窗（导出格式选择 / 恢复来源选择 / 导入前预览与二次确认 / 清空库存确认）。
 *
 * **#10a-1 从 `SettingsScreen.kt` 的弹窗区逐字搬来**（原行号 352–520，只整体左移 4 空格），
 * 内容与合并史见原文件头 KDoc 的「照抄而非统一的地方」：Miuix 与 MD3 两套排版**刻意保留**
 * `isMiuix` 分支，2026-09-16 已评估过并判定不硬并（理由逐条写在那里），本次不做去重。
 */
@Composable
internal fun SettingsBackupDialogs(
    state: SettingsUiState,
    isMiuix: Boolean,
    pendingImportState: MutableState<PendingImport?>,
    exportLauncher: ActivityResultLauncher<String>,
    importLauncher: ActivityResultLauncher<Array<String>>,
    csvExportLauncher: ActivityResultLauncher<String>,
    confirmImport: (PendingImport) -> Unit
) {
    var pendingImport by pendingImportState
    // ---- 导出格式选择（两版共用 AppOptionDialog）----
    AppOptionDialog(
        show = state.showExportFormatDialog,
        title = "选择导出格式",
        onDismissRequest = { state.setShowExportFormatDialog(false) },
        options = listOf(
            AppOptionSpec(
                md3Icon = Icons.Rounded.FileUpload,
                miuixIcon = MiuixIcons.UploadCloud,
                title = "JSON 完整备份",
                summary = "包含库存、归档、消耗记录与全部设置，适合换机与数据迁移",
                onClick = {
                    state.setShowExportFormatDialog(false)
                    exportLauncher.launch("吃了么备份_${LocalDateTime.now().fileStamp()}.json")
                },
            ),
            AppOptionSpec(
                md3Icon = Icons.Rounded.TableChart,
                miuixIcon = MiuixIcons.FileDownloads,
                title = "CSV 数据表格",
                summary = "表格文件，自带 UTF-8 BOM，支持 Excel、WPS 直接打开查看",
                onClick = {
                    state.setShowExportFormatDialog(false)
                    csvExportLauncher.launch("吃了么库存_${LocalDateTime.now().fileStamp()}.csv")
                },
            ),
        ),
    )

    // ---- 恢复来源选择（两版共用 AppOptionDialog）----
    AppOptionDialog(
        show = state.showRestoreSourceDialog,
        title = "选择恢复来源",
        onDismissRequest = { state.setShowRestoreSourceDialog(false) },
        options = listOf(
            AppOptionSpec(
                md3Icon = Icons.Rounded.FileDownload,
                miuixIcon = MiuixIcons.FileDownloads,
                title = "从 JSON 文件导入",
                summary = "从手机存储选取 .json 备份文件进行整体恢复",
                onClick = {
                    state.setShowRestoreSourceDialog(false)
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
            ),
            AppOptionSpec(
                md3Icon = Icons.Rounded.Restore,
                miuixIcon = MiuixIcons.Download,
                title = "从本地历史快照恢复",
                summary = "系统自动滚动保留的最近 3 份本地冷备快照",
                onClick = {
                    state.setShowRestoreSourceDialog(false)
                    state.loadLocalSnapshots()
                    state.setShowSnapshotPicker(true)
                },
            ),
        ),
    )

    // ---- 导入前预览与二次确认（2026-09-15）----
    // 同一份预览信息，两版排法不同：Miuix 用 buildString 拼一段 summary（MiuixDialog 只吃字符串），
    // MD3 用 AlertDialog 的 text 槽摆一列 Text（好给「版本过新」那行单独上 error 色）。两边各留一份。
    // Miuix 侧必须无条件调用 + show 控制（库约束，见 KDoc ①）。
    if (isMiuix) {
        MiuixDialog(
            title = "导入备份",
            summary = pendingImport?.let { pending ->
                val preview = pending.preview
                buildString {
                    append("备份导出日期：")
                    append(LocalDate.ofEpochDay(preview.exportedEpochDay).cn())
                    append("\n库存 ")
                    append(preview.itemQuantity)
                    append(" 件 · 归档 ")
                    append(preview.archived.size)
                    append(" 条 · 消耗 ")
                    append(preview.consumption.size)
                    append(" 条 · 历史 ")
                    append(preview.history.size)
                    append(" 条")
                    if (preview.version > BACKUP_VERSION) {
                        append("\n该备份来自更新的版本（v${preview.version}），部分字段可能无法识别。")
                    }
                    append("\n\n导入会整体替换当前全部数据，不可撤销。")
                    append("导入前会自动保存一份本地快照，可在「从本地历史快照恢复」里回退。")
                }
            }.orEmpty(),
            show = pendingImport != null,
            onDismissRequest = { pendingImport = null },
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixTextButton(
                    text = "取消",
                    onClick = { pendingImport = null },
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = "覆盖导入",
                    onClick = { pendingImport?.let { confirmImport(it) } },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }
    } else {
        pendingImport?.let { pending ->
            val preview = pending.preview
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("导入备份") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "备份导出日期：${LocalDate.ofEpochDay(preview.exportedEpochDay).cn()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "库存 ${preview.itemQuantity} 件 · 归档 ${preview.archived.size} 条 · " +
                                "消耗 ${preview.consumption.size} 条 · 历史 ${preview.history.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (preview.version > BACKUP_VERSION) {
                            Text(
                                "该备份来自更新的版本（v${preview.version}），部分字段可能无法识别。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "导入会整体替换当前全部数据（库存、归档、消耗记录、历史与阈值设置），" +
                                "不可撤销。导入前会自动保存一份本地快照，可在「从本地历史快照恢复」里回退。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { confirmImport(pending) }) {
                        Text("覆盖导入", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) { Text("取消") }
                },
            )
        }
    }

    // ---- 清空库存确认（两版共用 AppConfirmDialog）----
    AppConfirmDialog(
        show = state.showClearDialog,
        title = "清空库存记录",
        message = "确定要删除全部 ${state.items.size} 条食品记录吗？建议先导出备份。",
        confirmText = "清空",
        destructive = true,
        onConfirm = {
            state.setShowClearDialog(false)
            state.clearAll()
        },
        onDismiss = { state.setShowClearDialog(false) },
        // Miuix 侧按钮行与摘要之间原来就留了 8dp（MD3 槽位间距由库决定，此参数只影响 Miuix）
        contentTopPadding = 8.dp,
    )
}
