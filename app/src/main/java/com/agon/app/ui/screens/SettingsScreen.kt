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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.data.CLOUD_BACKUP_KEEP
import com.agon.app.data.readBackupText
import com.agon.app.ui.components.CheckSwitch
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSnackbarForm
import com.agon.app.ui.components.app.AppSnackbarPlacement
import com.agon.app.ui.components.app.rememberAppSnackbarHostState
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.DataOp
import com.agon.app.viewmodel.UiEvent
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页（双主题单文件）。外观（主题风格 / 深浅 / 动态取色 / 配色方案 / 悬浮导航）、
 * 物品管理入口（临期阈值 / 分类 / 存放位置 / 归档）、备份与数据（导出 JSON/CSV、导入、
 * 坚果云同步、云端与本地快照恢复、自动同步、清空库存）、关于。
 *
 * **#10a-1（2026-09-18）**：9 个弹窗的实现已按领域**逐字搬**到同包三个文件 ——
 * 备份与导入类 `SettingsBackupDialogs.kt`、坚果云与云端恢复类 `SettingsCloudDialogs.kt`、
 * 本地快照类 `SettingsSnapshotDialogs.kt`；本文件只剩入口装配（SAF 启动器、事件收集、共用动作）
 * 与两套 body。下面「弹窗」与「照抄而非统一的地方」两节讲的**就是那三个文件里的代码**，
 * 判定与理由原样有效（本次只搬不改，去重与否的结论一个都没动）。
 *
 * 2026-09-16 由 `SettingsScreen.kt`(1,142) + `MiuixSettingsScreen.kt`(958) 合并（第三批 #3 第 8 对）。
 * 两版去掉 package/import/注释后是 771 / 664 行代码，其中 **287 行逐字相同**（把 `MaterialTheme`↔`MiuixTheme`、
 * `.typography.`↔`.textStyles.`、`Miuix` 前缀归一化后是 311 行）—— **八对里最低的一对**（第 7 对统计页 271/294）。
 * 原因不是抄漏了，而是两版的排版习语根本不同：MD3 是「滚动 `Column` + `Surface` 分组卡片 + 手写行」，
 * Miuix 是「`LazyColumn` + 库的 Preference 组件（`SmallTitle`/`Card`/`RadioButtonPreference`/
 * `SwitchPreference`/`ArrowPreference`/`OverlayDropdownPreference`）」。所以 body 保留两套
 * （[Md3SettingsBody] / [MiuixSettingsBody]，都是逐字搬运），去重收益来自下面三处：
 *
 * 1. **SAF 启动器 + 本地状态**：两版 63 行逐字相同（导出 JSON / 导入预览 / 导出 CSV，含 `pendingImport`），只留一份。
 * 2. **弹窗**：合并前 9 个弹窗 × 2 套主题 = 18 份实现。其中 5 个弹窗（10 份）两版**文案与动作逐字相同**：
 *    - 清空库存、恢复二次确认、快照还原确认 → [AppConfirmDialog]（吸收 6 份）；
 *    - 导出格式选择、恢复来源选择 → [AppOptionDialog]（本对新增的 App 级组件，吸收 4 份：
 *      两版的标题/摘要/动作完全一致，只有图标分属两套图标库，故 `AppOptionSpec` 收 `md3Icon` + `miuixIcon`）；
 *    剩下 4 个弹窗（8 份）信息等价但排版各成体系 → **保留 `isMiuix` 分支**，理由见下面「照抄而非统一」。
 * 3. **骨架**：[AppScaffold] 顶掉两份 `Scaffold` + 两份顶栏 + 两份 snackbar 宿主。
 *
 * 照抄而非统一的地方：
 * - **坚果云账号弹窗**：MD3 是 `AlertDialog` 的 title/text/confirmButton 槽位 + `DialogProperties(decorFitsSystemWindows = false)`
 *   + `stickyImePadding`（2026-09-15 的键盘避让修复 + 2026-09-17 改粘性避让，`ImeHandlingTest` 第 3 条按文件点名），Miuix 是 `MiuixDialog` +
 *   `Column(spacedBy 12.dp)` + 自己排一行两个等宽按钮。**两版的输入框都是 MD3 `OutlinedTextField`**
 *   （Miuix 的 `TextField` 没有 `visualTransformation` 参数，做不了密码遮蔽，合并前就是刻意妥协），
 *   且值直接读写 `state.accountInput` / `state.passwordInput`。`AppFormDialog` 是「本地字段 + `onConfirm(values)`」
 *   的口径，硬套进来要改它的字段所有权并加密码/说明文槽位，风险大于收益 —— 判定不并（已回写 `AppFormDialog.kt` 头注释）。
 * - **导入预览弹窗**：正文是 `buildString` 拼的多行摘要 + 版本告警，MD3 放 `text` 槽、Miuix 放 `summary`，
 *   按钮文案同为「取消 / 覆盖导入」但排布不同（MD3 槽位 vs Miuix 一行两个等宽）。摘要文本只写一份（`M_sum` 那段）。
 * - **云端备份选择 / 本地快照列表**：都是「列表行 + 角标 + 时间/大小」，但 MD3 行是 `Surface(onClick)` + `Icon`，
 *   Miuix 行是 `Surface` + `clip` + `clickable` + 圆形容器徽章 + `MiuixIcon`，且 Miuix 的云端列表还多一个
 *   「最新 / 遗留格式」角标口径 —— 与 [AppOptionDialog] 的静态选项行不是一回事，不硬并。
 *
 * 已知非对等（合并前就存在，勿再声明「完全对等」）：本页**配色方案（`AppPalette`）入口只在 MD3 分支**——
 * `MiuixRootTheme` 只消费 darkMode + dynamicColor，Miuix 侧没有种子色通道，因此 MIUIX 风格下 15 套配色
 * 一个都选不了，MD3 下选好的配色切过来也会无提示地失效。补齐或明示「配色仅对 Material 3 风格生效」二选一，
 * 用户已指示暂缓（见 `devlog/INDEX.md`）。
 *
 * 三处非等价改动（都是收敛到更好的一边，刻意）：
 * ① Miuix 的「导入预览」由 `pendingImport?.let { MiuixDialog(show = true) }` 改成无条件调用 +
 *   `show = pendingImport != null`（库的硬约束，见 `MiuixDialog.kt` KDoc 与 `docs/MIUIX_UPGRADE.md` §2.3），
 *   关闭时才有淡出动画；摘要文本用 `?.let { … }.orEmpty()` 计算，内容逐字不变。
 * ② 「恢复二次确认」「快照还原确认」两版原本一份 `?.let { AlertDialog }`、一份无条件 `MiuixDialog`，
 *   统一走 [AppConfirmDialog]（MD3 分支内部 `if (show)`，行为不变），确认动作抽成 `confirmRestore` /
 *   `confirmSnapshotRestore`，取 Miuix 版的空安全写法（`?.fileName` + `if (fileName != null)`）——
 *   MD3 版靠 `?.let` 保证非空，两版结果相同。
 * ③ 两版备份节的「上传到云端」「从云端恢复」按钮体内联了同一段回调（含 snackbar 提示），抽成
 *   `onUpload` / `onCloudRestore` 供**两版 body 的备份节**共用，避免第 3 份拷贝。
 *   ⚠️ 本条原写「供 body 与「恢复来源」弹窗共用」—— 09-18 核查第 20 处：弹窗区里这两个名字
 *   **0 次出现**（恢复来源弹窗直接调 `state.setShowRestoreSourceDialog` / `state.loadLocalSnapshots()`），
 *   只有两版 body 的「上传云端」「云端恢复」两行在用（现 4 处）。
 */
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
    // 两版原来都是「官方 snackbar 宿主 + 底部 84dp 让开悬浮导航」，即 FloatingNav；
    // 但本页提示全都没有撤销动作，SwipeDismissSnackbarHost 会把文案截成一行并画倒计时环，
    // 所以形态取 Plain（该参数只影响 MD3 分支，Miuix 分支一直用库的官方宿主）。
    val snackbar = rememberAppSnackbarHostState()
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX

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
                snackbar.showMessage(
                    result.fold(
                        onSuccess = { "备份导出成功 ✅" },
                        onFailure = { it.message?.takeIf { m -> m != "stream null" } ?: "导出失败，请重试" },
                    )
                )
            }
        }
    }

    // ---- Backup import (SAF open document) ----
    // 2026-09-15：不再是「选完即覆盖」。先读（带 20 MB 上限）→ 解析出摘要 →
    // 弹二次确认（展示将覆盖的条数与导出日期）→ 导入前自动存一份本地快照。
    // #10a-1：弹窗抽到同包三个文件后，这里改成显式的 State 对象，好把它传给弹窗；
    // 委托写法与语义不变（读写的是同一个 MutableState），入口自己的赋值也照旧。
    val pendingImportState = remember { mutableStateOf<PendingImport?>(null) }
    var pendingImport by pendingImportState
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = readBackupText(context, uri)
                val preview = raw?.let { state.previewBackup(it) }
                when {
                    raw == null -> snackbar.showMessage("读取文件失败，或文件超过 20 MB")
                    preview == null -> snackbar.showMessage("导入失败：这不是本应用的备份文件")
                    else -> pendingImport = PendingImport(raw, preview)
                }
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
                snackbar.showMessage(
                    result.fold(
                        onSuccess = { "CSV 表格导出成功 📊" },
                        onFailure = { "导出 CSV 失败，请重试" },
                    )
                )
            }
        }
    }

    // 同步 / 还原 / 导入的成败提示（#4c：改收 Channel，不再用 `(Boolean, String)` 回调）。
    // key 用 snackbar：切主题会换一个新的宿主容器，旧协程必须停掉（与消耗记录页同理）。
    // 文案全在 VM 里（`AppViewModel`）—— 界面只负责"弹在哪、要不要顺手关掉选择器"。
    LaunchedEffect(snackbar) {
        viewModel.settingsUiEvents.collect { event ->
            when (event) {
                is UiEvent.Notice -> snackbar.showMessage(event.message)
                is UiEvent.CloudBackupsEmpty -> {
                    state.setShowBackupPicker(false)
                    snackbar.showMessage(event.message)
                }
                is UiEvent.OpFailed -> {
                    // 只有「拉云端备份列表」失败才关选择器 —— 与改造前逐字一致的行为：
                    // 那时只有 loadCloudBackups 的回调里写了 setShowBackupPicker(false)，
                    // 上传/下载/还原失败都不碰选择器。
                    if (event.op == DataOp.ListBackups) state.setShowBackupPicker(false)
                    snackbar.showMessage(event.failure.message)
                }
                // 另三类是撤销事件，走各自宿主的队列，不会流到这里；when 对 sealed 必须穷尽。
                is UiEvent.UndoConsumption, is UiEvent.UndoRestoreArchived,
                is UiEvent.UndoDeleteConsumption -> Unit
            }
        }
    }

    // ---- 两版共用的动作 ----
    // 合并前这些回调在两版里各写一遍（备份节两个按钮 + 三个确认弹窗），逻辑逐字相同；
    // 抽出来既让 body 与弹窗共享，也避免第 3 份拷贝。snackbar 一律走 App 级宿主。
    val onUpload: () -> Unit = {
        state.syncUpload()
    }
    val onCloudRestore: () -> Unit = {
        state.setShowBackupPicker(true)
        // 失败/为空时关选择器与提示都由上面的收集器负责（改造前写在这个回调里）。
        state.loadCloudBackups()
    }
    val saveNutstore: () -> Unit = {
        state.saveNutstoreCredentials(state.accountInput, state.passwordInput)
        state.setShowNutstoreDialog(false)
        scope.launch { snackbar.showMessage("坚果云账号已保存") }
    }
    val confirmImport: (PendingImport) -> Unit = { pending ->
        pendingImport = null
        state.importBackupWithSnapshot(pending.raw)
    }
    val confirmRestore: () -> Unit = {
        val fileName = state.restoreCandidate?.fileName
        state.setRestoreCandidate(null)
        if (fileName != null) {
            state.syncDownload(fileName)
        }
    }
    val confirmSnapshotRestore: () -> Unit = {
        val fileName = state.restoreSnapshotCandidate?.fileName
        state.setRestoreSnapshotCandidate(null)
        state.setShowSnapshotPicker(false)
        if (fileName != null) {
            state.restoreLocalSnapshot(fileName)
        }
    }

    AppScaffold(
        title = "设置",
        snackbar = snackbar,
        snackbarPlacement = AppSnackbarPlacement.FloatingNav,
        snackbarForm = AppSnackbarForm.Plain,
    ) { padding ->
        // body 保留两套：MD3 是滚动 Column + Surface 分组卡片，Miuix 是 LazyColumn + Preference 组件
        if (isMiuix) {
            MiuixSettingsBody(
                state = state,
                padding = padding,
                onUpload = onUpload,
                onCloudRestore = onCloudRestore,
                onOpenThresholds = onOpenThresholds,
                onOpenCategories = onOpenCategories,
                onOpenLocations = onOpenLocations,
                onOpenArchive = onOpenArchive,
            )
        } else {
            Md3SettingsBody(
                state = state,
                padding = padding,
                onUpload = onUpload,
                onCloudRestore = onCloudRestore,
                onOpenThresholds = onOpenThresholds,
                onOpenCategories = onOpenCategories,
                onOpenLocations = onOpenLocations,
                onOpenArchive = onOpenArchive,
            )
        }

        // ==================== 弹窗 ====================
        // 合并前 9 个弹窗 × 2 套主题 = 18 份实现。文案与动作两版逐字相同的 5 个（10 份）收进
        // AppConfirmDialog（3 个）与 AppOptionDialog（2 个）；剩下 4 个（8 份）排版各成体系，保留 isMiuix 分支。

        // #10a-1：9 个弹窗按领域抽到同包三个文件（内容逐字未改），这里只留调用。
        SettingsBackupDialogs(
            state = state,
            isMiuix = isMiuix,
            pendingImportState = pendingImportState,
            exportLauncher = exportLauncher,
            importLauncher = importLauncher,
            csvExportLauncher = csvExportLauncher,
            confirmImport = confirmImport,
        )
        SettingsCloudDialogs(
            state = state,
            isMiuix = isMiuix,
            saveNutstore = saveNutstore,
            confirmRestore = confirmRestore,
        )
        SettingsSnapshotDialogs(
            state = state,
            isMiuix = isMiuix,
            confirmSnapshotRestore = confirmSnapshotRestore,
        )
    }
}

/**
 * MD3 版设置节：滚动 `Column` + `Surface` 分组卡片（外观 / 物品管理 / 备份与数据 / 关于）。
 * 合并前 `SettingsScreen` 的 Scaffold 内容，逐字搬运；只有备份节两个按钮的 onClick
 * 换成了主函数里的 [onUpload] / [onCloudRestore]。
 */
@Composable
private fun Md3SettingsBody(
    state: SettingsUiState,
    padding: PaddingValues,
    onUpload: () -> Unit,
    onCloudRestore: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenArchive: () -> Unit,
) {
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

/**
 * Miuix 版设置节：`LazyColumn` + 库的 Preference 组件（外观 / 物品管理 / 备份与数据 / 关于）。
 * 合并前 `MiuixSettingsScreen` 的 Scaffold 内容，逐字搬运（`innerPadding` 改名 `padding`，
 * 备份节两行的 onClick 换成 [onUpload] / [onCloudRestore]）；裸 `Text`/`Surface`/`TextButton`
 * 一律换成带 `Miuix` 别名的库控件，以免与 material3 同名符号混淆。
 */
@Composable
private fun MiuixSettingsBody(
    state: SettingsUiState,
    padding: PaddingValues,
    onUpload: () -> Unit,
    onCloudRestore: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
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
                        state.plaintextFallback -> "⚠️ 系统 Keystore 不可用，密码以未加密形式保存"
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
                        onClick = onUpload,
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
                        onClick = onCloudRestore,
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
