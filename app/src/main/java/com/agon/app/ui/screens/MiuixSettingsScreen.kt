package com.agon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.agon.app.data.CLOUD_BACKUP_KEEP
import com.agon.app.data.CloudBackup
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的 Miuix（HyperOS）实现（v2.8）。
 *
 * 与 [SettingsScreen]（Material 3 实现）功能对等：外观（主题风格/深浅/动态取色/配色/悬浮导航）、
 * 物品管理入口、备份与数据（导出/导入 JSON、坚果云同步、自动同步、清空库存）、关于。
 *
 * 主题风格说明：
 * - MiuixTheme 由根级 `MiuixRootTheme` 统一提供，本页不再自行包裹。
 * - 坚果云「应用密码」输入框保留 MD3 OutlinedTextField：Miuix TextField 无 visualTransformation
 *   参数，无法做密码遮蔽，故该处为必要妥协（其余组件均 Miuix）。
 */
@Composable
fun MiuixSettingsScreen(
    viewModel: AppViewModel,
    onOpenArchive: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
) {
    val state = rememberSettingsUiState(viewModel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Backup export (SAF create document) ----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val jsonText = state.buildBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    } ?: error("stream null")
                }
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "备份导出成功 ✅" },
                        onFailure = { it.message?.takeIf { m -> m != "stream null" } ?: "导出失败，请重试" },
                    )
                )
            }
        }
    }

    // ---- Backup import (SAF open document) ----
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
                val ok = raw != null && state.importBackupJson(raw)
                snackbarHostState.showSnackbar(
                    if (ok) "导入成功，数据已恢复 ✅" else "导入失败：文件格式不正确"
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "设置") },
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 84.dp))
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item(key = "appearance") {
                SmallTitle(text = "外观")
                Card(modifier = Modifier.padding(12.dp)) {
                    RadioButtonPreference(
                        title = ThemeStyle.MATERIAL3.label,
                        summary = "当前 MD3 风格（默认）",
                        selected = state.themeStyleName == ThemeStyle.MATERIAL3.name,
                        onClick = { state.onSetThemeStyle(ThemeStyle.MATERIAL3.name) },
                    )
                    RadioButtonPreference(
                        title = ThemeStyle.MIUIX.label,
                        summary = "小米 HyperOS 风格",
                        selected = state.themeStyleName == ThemeStyle.MIUIX.name,
                        onClick = { state.onSetThemeStyle(ThemeStyle.MIUIX.name) },
                    )
                    OverlayDropdownPreference(
                        title = "深色模式",
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = state.darkMode.coerceIn(0, 2),
                        onSelectedIndexChange = { state.onSetDarkMode(it) },
                    )
                    SwitchPreference(
                        title = "动态取色 (Material You)",
                        summary = "需要 Android 12 及以上，优先于配色方案",
                        checked = state.dynamicColor,
                        onCheckedChange = { state.onSetDynamicColor(it) },
                    )
                    SwitchPreference(
                        title = "悬浮导航",
                        summary = "关闭后底部导航改为全宽常驻底栏",
                        checked = state.floatingNav,
                        onCheckedChange = { state.onSetFloatingNav(it) },
                    )
                }
            }

            item(key = "inventory") {
                SmallTitle(text = "物品管理")
                Card(modifier = Modifier.padding(12.dp)) {
                    ArrowPreference(
                        title = "临期提醒阈值",
                        summary = "各分类到期前多少天提醒",
                        onClick = onOpenThresholds,
                    )
                    ArrowPreference(
                        title = "分类管理",
                        summary = "共 ${state.categories.size} 个分类",
                        onClick = onOpenCategories,
                    )
                    ArrowPreference(
                        title = "存放位置管理",
                        summary = "共 ${state.locations.size} 个位置预设",
                        onClick = onOpenLocations,
                    )
                    ArrowPreference(
                        title = "归档历史",
                        summary = "已归档 ${state.archived.size} 条，可恢复或彻底删除",
                        onClick = onOpenArchive,
                    )
                }
            }

            item(key = "backup") {
                SmallTitle(text = "备份与数据")
                Card(modifier = Modifier.padding(12.dp)) {
                    ArrowPreference(
                        title = "导出备份",
                        summary = "导出为 JSON 文件，含库存、归档、消耗记录与设置",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.UploadCloud,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { exportLauncher.launch("吃了么备份_${LocalDate.now()}.json") },
                    )
                    ArrowPreference(
                        title = "导入备份",
                        summary = "从 JSON 文件整体恢复数据",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.FileDownloads,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    ArrowPreference(
                        title = "坚果云同步",
                        summary = when {
                            state.nutstoreAccount.isBlank() -> "未配置，点击设置 WebDAV 账号"
                            state.credentialBroken -> "应用密码已失效，请重新填写"
                            state.lastSync.isNotBlank() -> state.lastSync
                            else -> "已配置，尚未同步"
                        },
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.CloudFill,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { state.onShowNutstoreDialogChange(true) },
                    )
                    if (state.nutstoreAccount.isNotBlank()) {
                        ArrowPreference(
                            title = "上传到云端",
                            summary = if (state.syncing) "正在上传…" else "立即手动上传当前数据",
                            startAction = {
                                MiuixIcon(
                                    MiuixIcons.UploadCloud,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            },
                            enabled = !state.syncing,
                            onClick = {
                                state.onSyncUpload { _, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                        )
                        ArrowPreference(
                            title = "从云端恢复",
                            summary = "选择备份版本（云端保留最近 $CLOUD_BACKUP_KEEP 次）",
                            startAction = {
                                MiuixIcon(
                                    MiuixIcons.Download,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            },
                            enabled = !state.syncing && !state.loadingBackups,
                            onClick = {
                                state.onShowBackupPickerChange(true)
                                state.onLoadCloudBackups { ok, msg ->
                                    if (!ok) {
                                        state.onShowBackupPickerChange(false)
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
                        )
                    }
                    OverlayDropdownPreference(
                        title = "自动同步",
                        items = listOf("关闭", "每天", "3 天", "每周"),
                        selectedIndex = when (state.autoSyncDays) {
                            1 -> 1
                            3 -> 2
                            7 -> 3
                            else -> 0
                        },
                        onSelectedIndexChange = { idx ->
                            state.onSetAutoSyncDays(listOf(0, 1, 3, 7)[idx])
                        },
                    )
                    ArrowPreference(
                        title = "清空库存记录",
                        summary = "当前共 ${state.items.size} 条食品记录（不影响归档）",
                        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                        onClick = { state.onShowClearDialogChange(true) },
                    )
                }
            }

            item(key = "about") {
                SmallTitle(text = "关于")
                Card(modifier = Modifier.padding(12.dp)) {
                    ArrowPreference(
                        title = "吃了么 v1.0",
                        summary = "记录家中零食库存，提醒临期食品，减少食物浪费 🌱",
                    )
                }
            }
        }

        // ---- 清空库存确认 ----
        OverlayDialog(
            title = "清空库存记录",
            summary = "确定要删除全部 ${state.items.size} 条食品记录吗？建议先导出备份。",
            show = state.showClearDialog,
            onDismissRequest = { state.onShowClearDialogChange(false) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onShowClearDialogChange(false) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "清空",
                    onClick = {
                        state.onShowClearDialogChange(false)
                        state.onClearAll()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }

        // ---- 坚果云账号配置 ----
        OverlayDialog(
            title = "坚果云账号",
            show = state.showNutstoreDialog,
            onDismissRequest = { state.onShowNutstoreDialogChange(false) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中生成应用密码（不是登录密码）。备份存放于云端 ChiLeMe 文件夹。密码使用系统 Keystore 加密存储。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                OutlinedTextField(
                    value = state.accountInput,
                    onValueChange = state.onAccountInputChange,
                    label = { Text("账号（邮箱）") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = state.onPasswordInputChange,
                    label = { Text("应用密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 按钮区与表单区之间额外留白，避免紧贴密码框
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { state.onShowNutstoreDialogChange(false) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "保存",
                        onClick = {
                            state.onSaveNutstoreCredentials(state.accountInput, state.passwordInput)
                            state.onShowNutstoreDialogChange(false)
                            scope.launch { snackbarHostState.showSnackbar("坚果云账号已保存") }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- 云端备份选择（恢复哪一份） ----
        OverlayDialog(
            title = "选择要恢复的备份",
            show = state.showBackupPicker,
            onDismissRequest = { if (!state.loadingBackups) state.onShowBackupPickerChange(false) },
        ) {
            if (state.loadingBackups) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("正在获取云端备份列表…", style = MiuixTheme.textStyles.body2)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "云端共 ${state.cloudBackups.size} 份备份，新的在前：",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    state.cloudBackups.forEachIndexed { index, backup ->
                        Surface(
                            onClick = {
                                state.onShowBackupPickerChange(false)
                                state.onRestoreCandidateChange(backup)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (index == 0) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    backup.displayTime,
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    (if (index == 0 && !backup.isLegacy) "最新 · " else "") + backup.displaySize,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (index == 0) MiuixTheme.colorScheme.onPrimaryContainer
                                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onShowBackupPickerChange(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- 恢复二次确认 ----
        OverlayDialog(
            title = "确认恢复",
            summary = state.restoreCandidate?.let {
                "将恢复备份：\n${it.displayTime}\n\n此操作会整体替换本机全部数据（库存、归档、消耗记录和设置）。确定继续吗？"
            },
            show = state.restoreCandidate != null,
            onDismissRequest = { state.onRestoreCandidateChange(null) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { state.onRestoreCandidateChange(null) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "恢复这一份",
                    onClick = {
                        val fileName = state.restoreCandidate?.fileName
                        state.onRestoreCandidateChange(null)
                        if (fileName != null) {
                            state.onSyncDownload(fileName) { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }
    }
}
