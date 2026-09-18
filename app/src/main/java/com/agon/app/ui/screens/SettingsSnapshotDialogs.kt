package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.components.app.AppConfirmDialog
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的本地快照弹窗（本地历史快照列表（含还原确认））。
 *
 * **#10a-1 从 `SettingsScreen.kt` 的弹窗区逐字搬来**（原行号 830–986，只整体左移 4 空格），
 * 内容与合并史见原文件头 KDoc 的「照抄而非统一的地方」：Miuix 与 MD3 两套排版**刻意保留**
 * `isMiuix` 分支，2026-09-16 已评估过并判定不硬并（理由逐条写在那里），本次不做去重。
 */
@Composable
internal fun SettingsSnapshotDialogs(
    state: SettingsUiState,
    isMiuix: Boolean,
    confirmSnapshotRestore: () -> Unit
) {
    // ---- 本地历史快照列表（保留分支，理由见文件头 KDoc）----
    if (isMiuix) {
        MiuixDialog(
            title = "本地历史快照",
            summary = "系统自动滚动保留最近 3 份冷备快照，点击可还原：",
            show = state.showSnapshotPicker,
            onDismissRequest = { state.setShowSnapshotPicker(false) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.localSnapshots.isEmpty()) {
                    MiuixText(
                        "暂无本地快照，系统会在每天首次启动时自动备份。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.localSnapshots.forEach { snapshot ->
                            MiuixSurface(
                                shape = RoundedCornerShape(16.dp),
                                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        state.setRestoreSnapshotCandidate(snapshot)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MiuixIcon(
                                            MiuixIcons.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MiuixTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        MiuixText(
                                            snapshot.displayTime,
                                            style = MiuixTheme.textStyles.body1,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        MiuixText(
                                            "包含 ${snapshot.itemCount} 项资产 · ${snapshot.displaySize}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    MiuixIcon(
                                        MiuixIcons.Forward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }

                MiuixTextButton(
                    text = "关闭",
                    onClick = { state.setShowSnapshotPicker(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else if (state.showSnapshotPicker) {
        AlertDialog(
            onDismissRequest = { state.setShowSnapshotPicker(false) },
            title = { Text("本地历史快照") },
            text = {
                if (state.localSnapshots.isEmpty()) {
                    Text("暂无本地历史快照，系统会在每天首次启动时自动备份。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "系统自动滚动保留最近 3 份本地快照，点击可还原：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.localSnapshots.forEach { snapshot ->
                            Surface(
                                onClick = {
                                    state.setRestoreSnapshotCandidate(snapshot)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            snapshot.displayTime,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "包含 ${snapshot.itemCount} 项资产 · ${snapshot.displaySize}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.setShowSnapshotPicker(false) }) { Text("关闭") }
            },
        )
    }

    // ---- 本地快照还原二次确认（两版共用 AppConfirmDialog）----
    AppConfirmDialog(
        show = state.restoreSnapshotCandidate != null,
        title = "确认从快照还原",
        message = state.restoreSnapshotCandidate?.let { snapshot ->
            "将从本地快照还原数据：\n${snapshot.displayTime}\n\n" +
                "此操作会整体替换当前全部数据（库存、归档、消耗记录与设置）。确定继续吗？"
        } ?: "",
        confirmText = "确定还原",
        destructive = true,
        onConfirm = confirmSnapshotRestore,
        onDismiss = { state.setRestoreSnapshotCandidate(null) },
        contentTopPadding = 8.dp,
    )
}
