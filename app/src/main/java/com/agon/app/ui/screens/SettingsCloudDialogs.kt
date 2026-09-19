package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.agon.app.ui.components.MiuixDialog
import com.agon.app.ui.components.app.AppConfirmDialog
import com.agon.app.ui.components.app.stickyImePadding
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的坚果云弹窗（坚果云账号（含密码框）/ 云端备份选择 / 恢复二次确认）。
 *
 * **#10a-1 从 `SettingsScreen.kt` 的弹窗区逐字搬来**（原行号 522–828，只整体左移 4 空格），
 * 内容与合并史见原文件头 KDoc 的「照抄而非统一的地方」：Miuix 与 MD3 两套排版**刻意保留**
 * `isMiuix` 分支，2026-09-16 已评估过并判定不硬并（理由逐条写在那里），本次不做去重。
 */
@Composable
internal fun SettingsCloudDialogs(
    state: SettingsUiState,
    isMiuix: Boolean,
    saveNutstore: () -> Unit,
    confirmRestore: () -> Unit
) {
    // ---- 坚果云账号配置（保留分支：MD3 AlertDialog + decorFitsSystemWindows=false / Miuix MiuixDialog）----
    if (isMiuix) {
        MiuixDialog(
            title = "坚果云账号",
            show = state.showNutstoreDialog,
            onDismissRequest = { state.setShowNutstoreDialog(false) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(
                    "在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中生成应用密码（不是登录密码）。备份存放于云端 ChiLeMe 文件夹。密码使用系统 Keystore 加密存储。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                OutlinedTextField(
                    value = state.accountInput,
                    onValueChange = { state.setAccountInput(it) },
                    label = { MiuixText("账号（邮箱）") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = { state.setPasswordInput(it) },
                    label = { MiuixText("应用密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 按钮区与表单区之间额外留白，避免紧贴密码框
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextButton(
                        text = "取消",
                        onClick = { state.setShowNutstoreDialog(false) },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = "保存",
                        onClick = saveNutstore,
                        modifier = Modifier.weight(1f),
                        // 主要动作用蓝底白字胶囊：库的 TextButton 默认容器色是 secondaryVariant（浅灰），
                        // 不传 colors 就和「取消」同色。写法依据见 AppConfirmDialog.MiuixConfirmButton 的 KDoc。
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    } else if (state.showNutstoreDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowNutstoreDialog(false) },
            // 键盘避让（2026-09-15）：MD3 弹窗是独立浮动窗口，默认 DialogProperties
            // （decorFitsSystemWindows = true）不会把 IME inset 透给内容，底部按钮会被键盘盖住。
            // 关掉 decorFits 拿到 inset，再由粘性避让让弹窗整体上移到键盘之上。
            // 2026-09-17 起用 stickyImePadding()：账号 → 密码切换时输入法会重启、IME inset 瞬时归零，
            // 居中弹窗跟着上下坠一下（用户实机报告的问题 ①，机制与取舍见 ui/components/app/AppIme.kt）。
            properties = DialogProperties(decorFitsSystemWindows = false),
            modifier = stickyImePadding(),
            title = { Text("坚果云账号") },
            text = {
                Column {
                    Text(
                        "在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中生成应用密码（不是登录密码）。备份存放于云端 ChiLeMe 文件夹。密码使用系统 Keystore 加密存储。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.accountInput,
                        onValueChange = { state.setAccountInput(it) },
                        label = { Text("账号（邮箱）") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.passwordInput,
                        onValueChange = { state.setPasswordInput(it) },
                        label = { Text("应用密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = saveNutstore) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { state.setShowNutstoreDialog(false) }) { Text("取消") }
            },
        )
    }

    // ---- 云端备份选择（恢复哪一份；保留分支，理由见文件头 KDoc）----
    if (isMiuix) {
        MiuixDialog(
            title = "选择要恢复的备份",
            show = state.showBackupPicker,
            onDismissRequest = { if (!state.loadingBackups) state.setShowBackupPicker(false) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.loadingBackups) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiuixText(
                            "正在获取云端备份列表…",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiuixText(
                            "云端共 ${state.cloudBackups.size} 份备份，点击选择恢复：",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        state.cloudBackups.forEachIndexed { index, backup ->
                            val isLatest = index == 0 && !backup.isLegacy
                            MiuixSurface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isLatest) MiuixTheme.colorScheme.surfaceContainerHighest
                                else MiuixTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        state.setShowBackupPicker(false)
                                        state.setRestoreCandidate(backup)
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
                                            .background(
                                                if (isLatest) MiuixTheme.colorScheme.primaryContainer
                                                else MiuixTheme.colorScheme.secondaryContainer
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MiuixIcon(
                                            if (isLatest) MiuixIcons.CloudFill else MiuixIcons.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (isLatest) MiuixTheme.colorScheme.onPrimaryContainer
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        MiuixText(
                                            backup.displayTime,
                                            style = MiuixTheme.textStyles.body1,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (isLatest) {
                                                MiuixSurface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                ) {
                                                    MiuixText(
                                                        "最新",
                                                        style = MiuixTheme.textStyles.footnote2,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MiuixTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                            MiuixText(
                                                backup.displaySize,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
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

                // 底部取消按钮，与上方列表保持 16dp 间距，不重叠
                MiuixTextButton(
                    text = "取消",
                    onClick = { state.setShowBackupPicker(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else if (state.showBackupPicker) {
        AlertDialog(
            onDismissRequest = { if (!state.loadingBackups) state.setShowBackupPicker(false) },
            title = { Text("选择要恢复的备份") },
            text = {
                if (state.loadingBackups) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在获取云端备份列表…")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "云端共 ${state.cloudBackups.size} 份备份，新的在前：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.cloudBackups.forEachIndexed { index, backup ->
                            Surface(
                                onClick = {
                                    state.setShowBackupPicker(false)
                                    state.setRestoreCandidate(backup)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (index == 0) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            backup.displayTime,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            (if (index == 0 && !backup.isLegacy) "最新 · " else "") + backup.displaySize,
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
                TextButton(onClick = { state.setShowBackupPicker(false) }) { Text("取消") }
            },
        )
    }

    // ---- 恢复二次确认（两版共用 AppConfirmDialog）----
    AppConfirmDialog(
        show = state.restoreCandidate != null,
        title = "确认恢复",
        message = state.restoreCandidate?.let { candidate ->
            "将恢复备份：\n${candidate.displayTime}\n\n" +
                "此操作会整体替换本机全部数据（库存、归档、消耗记录和设置）。确定继续吗？"
        } ?: "",
        confirmText = "恢复这一份",
        destructive = true,
        onConfirm = confirmRestore,
        onDismiss = { state.setRestoreCandidate(null) },
        contentTopPadding = 8.dp,
    )
}
