package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * MD3 版设置页「备份与数据」节（#10a-2 从 [Md3SettingsBody] 抽出：内容逐字未改，只整体左移 4 空格）。
 *
 * 为什么单独抽这一节：`Md3SettingsBody` 连 KDoc 共 384 行，加文件头必然超 #10 的 400 行判据；
 * 这一节是四节里最大的（171 行，含「坚果云云同步」与「自动同步间隔」两个子块），
 * 且与其余三节不共享任何局部量（实测引用面只有 [state] / [onUpload] / [onCloudRestore]）⇒ 抽它一刀最省。
 * ⚠️ 这是 10a-2 **唯一新增的组合边界**：其余搬动都是「整个函数换个文件」，边界不变。
 * 节内无 `remember`（整个 MD3 body 只有根 `Column` 的 `rememberScrollState()`）⇒ 没有状态跨边界搬家。
 */
@Composable
internal fun Md3BackupSection(
    state: SettingsUiState,
    onUpload: () -> Unit,
    onCloudRestore: () -> Unit
) {
    // ==================== 备份与数据 ====================
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "备份与数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "管理本地与云端数据，定期备份防止意外丢失",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { state.setShowExportFormatDialog(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出数据")
                }
                OutlinedButton(
                    onClick = { state.setShowRestoreSourceDialog(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("恢复数据")
                }
            }

            // ---- 坚果云云同步 ----
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "坚果云同步",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        when {
                            state.credentialBroken -> "应用密码已失效，请重新填写"
                            state.plaintextFallback -> "⚠️ 系统 Keystore 不可用，密码以未加密形式保存"
                            state.lastSync.isBlank() -> "通过 WebDAV 备份到坚果云"
                            else -> state.lastSync
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.credentialBroken || state.plaintextFallback) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(onClick = { state.setShowNutstoreDialog(true) }) {
                    Text(if (state.nutstoreAccount.isBlank()) "配置" else "修改账号")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onUpload,
                    enabled = !state.syncing && state.nutstoreAccount.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("上传云端")
                }
                OutlinedButton(
                    onClick = onCloudRestore,
                    enabled = !state.syncing && !state.loadingBackups && state.nutstoreAccount.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    if (state.loadingBackups) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("云端恢复")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "云端自动保留最近 $CLOUD_BACKUP_KEEP 次备份，恢复时可选择任意一份",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- 自动同步间隔 ----
            Spacer(Modifier.height(12.dp))
            Text(
                "自动同步",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (state.autoSyncDays == 0) "已关闭；选择间隔后，每次打开应用时若超过间隔会自动上传"
                else "每 ${state.autoSyncDays} 天自动上传一次（在打开应用时触发）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "关闭", 1 to "每天", 3 to "3 天", 7 to "每周").forEach { (days, label) ->
                    FilterChip(
                        selected = state.autoSyncDays == days,
                        onClick = { state.setAutoSyncDays(days) },
                        enabled = state.nutstoreAccount.isNotBlank() || days == 0,
                        label = { Text(label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "清空库存记录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "当前共 ${state.items.size} 条食品记录（不影响归档）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { state.setShowClearDialog(true) },
                    enabled = state.items.isNotEmpty(),
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
