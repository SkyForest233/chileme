package com.agon.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.agon.app.data.CLOUD_BACKUP_KEEP
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 84.dp))
        },
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ==================== 外观 ====================
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "外观",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("深色模式", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = state.darkMode == index,
                                onClick = { state.setDarkMode(index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("主题风格", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (state.themeStyleName == ThemeStyle.MIUIX.name) "MIUIX：设置页使用小米 HyperOS 组件渲染"
                        else "Material 3：默认风格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = state.themeStyleName == style.name,
                                onClick = { state.setThemeStyle(style.name) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeStyle.entries.size,
                                ),
                            ) { Text(style.label) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("主题配色", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (state.dynamicColor) "已开启动态取色，主题跟随壁纸；关闭后生效" else "基于 MD3 种子色生成完整主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(AppPalette.entries.toList()) { p ->
                            PaletteSwatch(
                                palette = p,
                                selected = state.paletteName == p.name && !state.dynamicColor,
                                enabled = !state.dynamicColor,
                                onClick = { state.setPalette(p.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "动态取色 (Material You)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    "跟随壁纸颜色，优先于上方配色方案"
                                else
                                    "需要 Android 12 及以上",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CheckSwitch(
                            checked = state.dynamicColor,
                            onCheckedChange = { state.setDynamicColor(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "悬浮导航",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "关闭后底部导航改为全宽常驻底栏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CheckSwitch(
                            checked = state.floatingNav,
                            onCheckedChange = { state.setFloatingNav(it) },
                        )
                    }
                }
            }

            // ==================== 物品管理（统一入口，全部二级页面） ====================
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "物品管理",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                    SettingsNavRow(
                        icon = Icons.Rounded.Schedule,
                        title = "临期提醒阈值",
                        subtitle = "各分类到期前多少天提醒",
                        onClick = onOpenThresholds,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    SettingsNavRow(
                        icon = Icons.Rounded.Category,
                        title = "分类管理",
                        subtitle = "共 ${state.categories.size} 个分类",
                        onClick = onOpenCategories,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    SettingsNavRow(
                        icon = Icons.Rounded.Place,
                        title = "存放位置管理",
                        subtitle = "共 ${state.locations.size} 个位置预设",
                        onClick = onOpenLocations,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    SettingsNavRow(
                        icon = Icons.Rounded.History,
                        title = "归档历史",
                        subtitle = "已归档 ${state.archived.size} 条，可恢复或彻底删除",
                        onClick = onOpenArchive,
                    )
                }
            }

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
                                    state.lastSync.isBlank() -> "通过 WebDAV 备份到坚果云"
                                    else -> state.lastSync
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.credentialBroken) {
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
                            onClick = {
                                state.syncUpload { _, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
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
                            onClick = {
                                state.setShowBackupPicker(true)
                                state.loadCloudBackups { ok, msg ->
                                    if (!ok) {
                                        state.setShowBackupPicker(false)
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
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
                                state.loadCloudBackups { ok, msg ->
                                    if (!ok) {
                                        state.setShowBackupPicker(false)
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
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
                            Text("从云端恢复")
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

            // ==================== 关于 ====================
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "关于",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "吃了么 v1.0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "记录家中零食库存，提醒临期食品，减少食物浪费 🌱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ---- 导出格式选择弹窗 ----
    if (state.showExportFormatDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowExportFormatDialog(false) },
            title = { Text("选择导出格式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            state.setShowExportFormatDialog(false)
                            exportLauncher.launch("吃了么备份_${LocalDate.now()}.json")
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("JSON 完整备份", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("包含库存、归档、消耗记录与全部设置，适合换机与数据迁移", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            state.setShowExportFormatDialog(false)
                            csvExportLauncher.launch("吃了么库存_${LocalDate.now()}.csv")
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CSV 数据表格", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("表格文件，自带 UTF-8 BOM，支持 Excel、WPS 直接打开查看", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.setShowExportFormatDialog(false) }) { Text("取消") }
            },
        )
    }

    // ---- 恢复来源选择弹窗 ----
    if (state.showRestoreSourceDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowRestoreSourceDialog(false) },
            title = { Text("选择恢复来源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            state.setShowRestoreSourceDialog(false)
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("从 JSON 文件导入", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("从手机存储选取 .json 备份文件进行整体恢复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            state.setShowRestoreSourceDialog(false)
                            state.loadLocalSnapshots()
                            state.setShowSnapshotPicker(true)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("从本地历史快照恢复", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("系统自动滚动保留的最近 3 份本地冷备快照", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.setShowRestoreSourceDialog(false) }) { Text("取消") }
            },
        )
    }

    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowClearDialog(false) },
            title = { Text("清空库存记录") },
            text = { Text("确定要删除全部 ${state.items.size} 条食品记录吗？建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    state.setShowClearDialog(false)
                    state.clearAll()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.setShowClearDialog(false) }) { Text("取消") }
            },
        )
    }

    // ---- 坚果云账号配置对话框 ----
    if (state.showNutstoreDialog) {
        AlertDialog(
            onDismissRequest = { state.setShowNutstoreDialog(false) },
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
                TextButton(onClick = {
                    state.saveNutstoreCredentials(state.accountInput, state.passwordInput)
                    state.setShowNutstoreDialog(false)
                    scope.launch { snackbarHostState.showSnackbar("坚果云账号已保存") }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { state.setShowNutstoreDialog(false) }) { Text("取消") }
            },
        )
    }

    // ---- 云端备份选择（恢复哪一份） ----
    if (state.showBackupPicker) {
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

    // ---- 恢复二次确认（针对选中的备份） ----
    state.restoreCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { state.setRestoreCandidate(null) },
            title = { Text("确认恢复") },
            text = {
                Text(
                    "将恢复备份：\n${candidate.displayTime}\n\n" +
                        "此操作会整体替换本机全部数据（库存、归档、消耗记录和设置）。确定继续吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val fileName = candidate.fileName
                    state.setRestoreCandidate(null)
                    state.syncDownload(fileName) { _, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }) {
                    Text("恢复这一份", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.setRestoreCandidate(null) }) { Text("取消") }
            },
        )
    }

    // ---- 本地历史快照列表 ----
    if (state.showSnapshotPicker) {
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

    // ---- 本地快照还原二次确认 ----
    state.restoreSnapshotCandidate?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { state.setRestoreSnapshotCandidate(null) },
            title = { Text("确认从快照还原") },
            text = {
                Text(
                    "将从本地快照还原数据：\n${snapshot.displayTime}\n\n" +
                        "此操作会整体替换当前全部数据（库存、归档、消耗记录与设置）。确定继续吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val fileName = snapshot.fileName
                    state.setRestoreSnapshotCandidate(null)
                    state.setShowSnapshotPicker(false)
                    state.restoreLocalSnapshot(fileName) { _, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }) {
                    Text("确定还原", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.setRestoreSnapshotCandidate(null) }) { Text("取消") }
            },
        )
    }
}

/** 设置页导航行：图标 + 标题/副标题 + 尾部箭头，点击进入二级页面 */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 主题色预览按钮：用该方案种子色实时生成 MD3 色板，
 * 展示 primary / primaryContainer / tertiaryContainer 三色拼盘 + 名称，
 * 选中态外圈描边 + 打勾角标。
 */
@Composable
private fun PaletteSwatch(
    palette: AppPalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val preview = rememberDynamicColorScheme(
        seedColor = palette.seed,
        isDark = dark,
        style = PaletteStyle.TonalSpot,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onClick() }
            .padding(6.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.4f else 0.15f),
                        shape = CircleShape,
                    ),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (enabled) preview.primary else preview.primary.copy(alpha = 0.35f)),
                    )
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(
                                    if (enabled) preview.primaryContainer
                                    else preview.primaryContainer.copy(alpha = 0.35f)
                                ),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(
                                    if (enabled) preview.tertiaryContainer
                                    else preview.tertiaryContainer.copy(alpha = 0.35f)
                                ),
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "已选中",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${palette.emoji} ${palette.label}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}
