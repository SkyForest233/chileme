package com.agon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Forward
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

    // ---- CSV Export (SAF create document) ----
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val csvText = state.buildCsvExport()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csvText.toByteArray(Charsets.UTF_8))
                    } ?: error("stream null")
                }
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "CSV 表格导出成功 📊" },
                        onFailure = { "导出 CSV 失败，请重试" },
                    )
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
                        onClick = { state.setThemeStyle(ThemeStyle.MATERIAL3.name) },
                    )
                    RadioButtonPreference(
                        title = ThemeStyle.MIUIX.label,
                        summary = "小米 HyperOS 风格",
                        selected = state.themeStyleName == ThemeStyle.MIUIX.name,
                        onClick = { state.setThemeStyle(ThemeStyle.MIUIX.name) },
                    )
                    OverlayDropdownPreference(
                        title = "深色模式",
                        items = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = state.darkMode.coerceIn(0, 2),
                        onSelectedIndexChange = { state.setDarkMode(it) },
                    )
                    SwitchPreference(
                        title = "动态取色 (Material You)",
                        summary = "需要 Android 12 及以上，优先于配色方案",
                        checked = state.dynamicColor,
                        onCheckedChange = { state.setDynamicColor(it) },
                    )
                    SwitchPreference(
                        title = "悬浮导航",
                        summary = "关闭后底部导航改为全宽常驻底栏",
                        checked = state.floatingNav,
                        onCheckedChange = { state.setFloatingNav(it) },
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
                        title = "导出数据",
                        summary = "支持导出为 JSON 完整备份或 Excel CSV 表格",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.UploadCloud,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { state.setShowExportFormatDialog(true) },
                    )
                    ArrowPreference(
                        title = "恢复数据",
                        summary = "支持从 JSON 备份文件或本地历史快照恢复",
                        startAction = {
                            MiuixIcon(
                                MiuixIcons.FileDownloads,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { state.setShowRestoreSourceDialog(true) },
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
                        onClick = { state.setShowNutstoreDialog(true) },
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
                                state.syncUpload { _, msg ->
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
                                state.setShowBackupPicker(true)
                                state.loadCloudBackups { ok, msg ->
                                    if (!ok) {
                                        state.setShowBackupPicker(false)
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
                            state.setAutoSyncDays(listOf(0, 1, 3, 7)[idx])
                        },
                    )
                    ArrowPreference(
                        title = "清空库存记录",
                        summary = "当前共 ${state.items.size} 条食品记录（不影响归档）",
                        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                        onClick = { state.setShowClearDialog(true) },
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
        WindowDialog(
            title = "清空库存记录",
            summary = "确定要删除全部 ${state.items.size} 条食品记录吗？建议先导出备份。",
            show = state.showClearDialog,
            onDismissRequest = { state.setShowClearDialog(false) },
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { state.setShowClearDialog(false) },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                )
                TextButton(
                    text = "清空",
                    onClick = {
                        state.setShowClearDialog(false)
                        state.clearAll()
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }

        // ---- 导出格式选择弹窗 ----
        WindowDialog(
            title = "选择导出格式",
            show = state.showExportFormatDialog,
            onDismissRequest = { state.setShowExportFormatDialog(false) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    onClick = {
                        state.setShowExportFormatDialog(false)
                        exportLauncher.launch("吃了么备份_${LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                MiuixIcons.UploadCloud,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("JSON 完整备份", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
                            Text("包含库存、归档、消耗记录与全部设置，适合换机与数据迁移", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    onClick = {
                        state.setShowExportFormatDialog(false)
                        csvExportLauncher.launch("吃了么库存_${LocalDate.now()}.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                MiuixIcons.FileDownloads,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CSV 数据表格", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
                            Text("表格文件，自带 UTF-8 BOM，支持 Excel、WPS 直接打开查看", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                TextButton(
                    text = "取消",
                    onClick = { state.setShowExportFormatDialog(false) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                )
            }
        }

        // ---- 恢复来源选择弹窗 ----
        WindowDialog(
            title = "选择恢复来源",
            show = state.showRestoreSourceDialog,
            onDismissRequest = { state.setShowRestoreSourceDialog(false) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    onClick = {
                        state.setShowRestoreSourceDialog(false)
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                MiuixIcons.FileDownloads,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("从 JSON 文件导入", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
                            Text("从手机存储选取 .json 备份文件进行整体恢复", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    onClick = {
                        state.setShowRestoreSourceDialog(false)
                        state.loadLocalSnapshots()
                        state.setShowSnapshotPicker(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                            Text("从本地历史快照恢复", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
                            Text("系统自动滚动保留的最近 3 份本地冷备快照", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                TextButton(
                    text = "取消",
                    onClick = { state.setShowRestoreSourceDialog(false) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                )
            }
        }

        // ---- 坚果云账号配置 ----
        WindowDialog(
            title = "坚果云账号",
            show = state.showNutstoreDialog,
            onDismissRequest = { state.setShowNutstoreDialog(false) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中生成应用密码（不是登录密码）。备份存放于云端 ChiLeMe 文件夹。密码使用系统 Keystore 加密存储。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                OutlinedTextField(
                    value = state.accountInput,
                    onValueChange = { state.setAccountInput(it) },
                    label = { Text("账号（邮箱）") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.passwordInput,
                    onValueChange = { state.setPasswordInput(it) },
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
                        onClick = { state.setShowNutstoreDialog(false) },
                        modifier = Modifier.weight(1f),
                        minHeight = 48.dp,
                    )
                    TextButton(
                        text = "保存",
                        onClick = {
                            state.saveNutstoreCredentials(state.accountInput, state.passwordInput)
                            state.setShowNutstoreDialog(false)
                            scope.launch { snackbarHostState.showSnackbar("坚果云账号已保存") }
                        },
                        modifier = Modifier.weight(1f),
                        minHeight = 48.dp,
                    )
                }
            }
        }

        // ---- 云端备份选择（恢复哪一份） ----
        WindowDialog(
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
                        Text(
                            "正在获取云端备份列表…",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "云端共 ${state.cloudBackups.size} 份备份，点击选择恢复：",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        state.cloudBackups.forEachIndexed { index, backup ->
                            val isLatest = index == 0 && !backup.isLegacy
                            Surface(
                                onClick = {
                                    state.setShowBackupPicker(false)
                                    state.setRestoreCandidate(backup)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isLatest) MiuixTheme.colorScheme.surfaceContainerHighest
                                else MiuixTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
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
                                        Text(
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
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                ) {
                                                    Text(
                                                        "最新",
                                                        style = MiuixTheme.textStyles.footnote2,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MiuixTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }
                                            Text(
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
                TextButton(
                    text = "取消",
                    onClick = { state.setShowBackupPicker(false) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                )
            }
        }

        // ---- 恢复二次确认 ----
        WindowDialog(
            title = "确认恢复",
            summary = state.restoreCandidate?.let {
                "将恢复备份：\n${it.displayTime}\n\n此操作会整体替换本机全部数据（库存、归档、消耗记录和设置）。确定继续吗？"
            }.orEmpty(),
            show = state.restoreCandidate != null,
            onDismissRequest = { state.setRestoreCandidate(null) },
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { state.setRestoreCandidate(null) },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                )
                TextButton(
                    text = "恢复这一份",
                    onClick = {
                        val fileName = state.restoreCandidate?.fileName
                        state.setRestoreCandidate(null)
                        if (fileName != null) {
                            state.syncDownload(fileName) { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }

        // ---- 本地快照列表弹窗 ----
        WindowDialog(
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
                    Text(
                        "暂无本地快照，系统会在每天首次启动时自动备份。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.localSnapshots.forEach { snapshot ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                onClick = {
                                    state.setRestoreSnapshotCandidate(snapshot)
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                                        Text(
                                            snapshot.displayTime,
                                            style = MiuixTheme.textStyles.body1,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
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

                TextButton(
                    text = "关闭",
                    onClick = { state.setShowSnapshotPicker(false) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                )
            }
        }

        // ---- 本地快照还原二次确认 ----
        WindowDialog(
            title = "确认从快照还原",
            summary = state.restoreSnapshotCandidate?.let {
                "将从本地快照还原数据：\n${it.displayTime}\n\n此操作会整体替换当前全部数据（库存、归档、消耗记录与设置）。确定继续吗？"
            }.orEmpty(),
            show = state.restoreSnapshotCandidate != null,
            onDismissRequest = { state.setRestoreSnapshotCandidate(null) },
        ) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { state.setRestoreSnapshotCandidate(null) },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                )
                TextButton(
                    text = "确定还原",
                    onClick = {
                        val fileName = state.restoreSnapshotCandidate?.fileName
                        state.setRestoreSnapshotCandidate(null)
                        state.setShowSnapshotPicker(false)
                        if (fileName != null) {
                            state.restoreLocalSnapshot(fileName) { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                )
            }
        }
    }
}
