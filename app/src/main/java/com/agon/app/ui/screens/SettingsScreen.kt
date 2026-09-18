package com.agon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.agon.app.data.readBackupText
import com.agon.app.ui.components.app.AppScaffold
import com.agon.app.ui.components.app.AppSnackbarForm
import com.agon.app.ui.components.app.AppSnackbarPlacement
import com.agon.app.ui.components.app.rememberAppSnackbarHostState
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.DataOp
import com.agon.app.viewmodel.UiEvent
import kotlinx.coroutines.launch

/**
 * 设置页（双主题单文件）。外观（主题风格 / 深浅 / 动态取色 / 配色方案 / 悬浮导航）、
 * 物品管理入口（临期阈值 / 分类 / 存放位置 / 归档）、备份与数据（导出 JSON/CSV、导入、
 * 坚果云同步、云端与本地快照恢复、自动同步、清空库存）、关于。
 *
 * **#10a-1（2026-09-18）**：9 个弹窗的实现已按领域**逐字搬**到同包三个文件 ——
 * 备份与导入类 `SettingsBackupDialogs.kt`、坚果云与云端恢复类 `SettingsCloudDialogs.kt`、
 * 本地快照类 `SettingsSnapshotDialogs.kt`。下面「弹窗」与「照抄而非统一的地方」两节讲的
 * **就是那三个文件里的代码**，判定与理由原样有效（本次只搬不改，去重与否的结论一个都没动）。
 *
 * **#10a-2（2026-09-18）**：两套 body 与两个 MD3 专用小组件也已**逐字搬**到同包四个文件 ——
 * `SettingsBodyMd3.kt`（MD3 body；其中「备份与数据」一节再抽成 `SettingsBackupMd3.kt` 里的
 * `Md3BackupSection`，那是本次唯一新增的组合边界）、`SettingsBodyMiuix.kt`（Miuix body）、
 * `SettingsMd3Widgets.kt`（`SettingsNavRow` + `PaletteSwatch`，只被 MD3 body 调用）。
 * 本文件至此只剩**入口装配**：状态容器、SAF 启动器、事件收集、共用动作，以及弹窗与 body 的调用
 * （1,705 → 1,079 → 现在只剩装配。⚠️ 本文件自己的行数**刻意不写死在这里** —— 改一次 KDoc 就会漂，
 * 实测用 `wc -l` 本文件，或看 `bash tools/doc-metrics.sh`）。下面「body 保留两套」那段讲的**就是那两个
 * body 文件里的代码**，判定与理由同样原样有效。
 *
 * 2026-09-16 由 `SettingsScreen.kt`(1,142) + `MiuixSettingsScreen.kt`(958) 合并（第三批 #3 第 8 对）。
 * 两版去掉 package/import/注释后是 771 / 664 行代码，其中 **287 行逐字相同**（把 `MaterialTheme`↔`MiuixTheme`、
 * `.typography.`↔`.textStyles.`、`Miuix` 前缀归一化后是 311 行）—— **八对里最低的一对**（第 7 对统计页 271/294）。
 * 原因不是抄漏了，而是两版的排版习语根本不同：MD3 是「滚动 `Column` + `Surface` 分组卡片 + 手写行」，
 * Miuix 是「`LazyColumn` + 库的 Preference 组件（`SmallTitle`/`Card`/`RadioButtonPreference`/
 * `SwitchPreference`/`ArrowPreference`/`OverlayDropdownPreference`）」。所以 body 保留两套
 * （[Md3SettingsBody] / [MiuixSettingsBody]，都是逐字搬运；#10a-2 起分别在 `SettingsBodyMd3.kt` /
 * `SettingsBodyMiuix.kt`），去重收益来自下面三处：
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
