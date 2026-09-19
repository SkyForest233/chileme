package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchivedItem
import com.agon.app.data.BackupData
import com.agon.app.data.CategoryDef
import com.agon.app.data.CloudBackup
import com.agon.app.data.FoodItem
import com.agon.app.data.LocalSnapshot
import com.agon.app.viewmodel.AppViewModel
// ↓ #10b 起 VM 的领域函数按领域搬到了 `viewmodel/AppViewModel<领域>.kt`（同包 internal 扩展函数）⇒ 跨包调用要逐个 import（本仓禁通配导入）；下面的转发写法一字未改
import com.agon.app.viewmodel.buildBackupJson
import com.agon.app.viewmodel.buildCsvExport
import com.agon.app.viewmodel.clearAll
import com.agon.app.viewmodel.importBackupJson
import com.agon.app.viewmodel.importBackupWithSnapshot
import com.agon.app.viewmodel.loadCloudBackups
import com.agon.app.viewmodel.loadLocalSnapshots
import com.agon.app.viewmodel.previewBackup
import com.agon.app.viewmodel.restoreLocalSnapshot
import com.agon.app.viewmodel.saveLocalSnapshot
import com.agon.app.viewmodel.saveNutstoreCredentials
import com.agon.app.viewmodel.syncDownload
import com.agon.app.viewmodel.syncUpload

/**
 * 待用户确认的导入内容：原始 JSON + 预览摘要。
 *
 * 导入是「整体替换」的破坏性操作，两套设置页都先用它弹二次确认
 * （展示将覆盖的条数与备份导出日期），确认后才写库（2026-09-15）。
 */
data class PendingImport(val raw: String, val preview: BackupData)

/**
 * 设置页需要的「动作」。原来是直接持有 `AppViewModel`，抽出窄接口后
 * 状态容器可以在纯 JVM 单测里构造（见 `SettingsStateTest`），
 * 也不需要为了测一个 getter 去准备 Application/Robolectric。
 */
internal interface SettingsActions {
    // 全部声明为 Unit：AppViewModel 的这些方法返回 Job（viewModelScope.launch），
    // 直接写表达式体 override 会因返回类型不匹配编译失败（2026-09-15 CI 实测）。
    fun setDynamicColor(enabled: Boolean)
    fun setDarkMode(mode: Int)
    fun setPalette(name: String)
    fun setThemeStyle(style: String)
    fun setFloatingNav(enabled: Boolean)
    fun setAutoSyncDays(days: Int)
    fun saveNutstoreCredentials(account: String, pass: String)
    fun syncUpload()
    fun loadCloudBackups()
    fun syncDownload(fileName: String)
    fun loadLocalSnapshots()
    fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)?)
    fun restoreLocalSnapshot(fileName: String)
    fun clearAll()
    suspend fun buildBackupJson(): String
    suspend fun buildCsvExport(): String
    suspend fun importBackupJson(raw: String): Boolean
    suspend fun previewBackup(raw: String): BackupData?
    fun importBackupWithSnapshot(raw: String)
}

/**
 * 设置页跨主题共享状态容器。
 *
 * **重组粒度（2026-09-15 重构）**：此前这类字段是普通构造参数 + 一个 29 键的
 * `remember(...)`，任何一处变化（连输入框敲一个字符）都会重建整个对象；对象身份一变，
 * 所有读到 `state.x` 的重组作用域都要重跑。现在：
 *
 * 1. 来自 ViewModel 的 19 个值改为持有各自的 `State`，属性 getter 直接读 `.value`
 *    —— Compose 按「读到哪个 State」精确订阅，改 `syncing` 不会连带 `darkMode` 的读取点；
 * 2. 只属于本屏的 10 个 UI 状态（6 个弹窗 + 2 个候选 + 2 个输入框）直接放容器内部
 *    （`mutableStateOf`），不再作为构造参数、也不再需要 10 个回调 —— 弹窗开关/输入
 *    不再重建容器；
 * 3. 容器由 `remember { }` 持有一次（不再带键列表），身份稳定。
 *
 * 屏幕层的读法（`state.darkMode`、`state.setShowClearDialog(true)`）完全不变。
 */
class SettingsUiState internal constructor(
    private val actions: SettingsActions,
    private val dynamicColorState: State<Boolean>,
    private val darkModeState: State<Int>,
    private val paletteNameState: State<String>,
    private val themeStyleNameState: State<String>,
    private val floatingNavState: State<Boolean>,
    private val itemsState: State<List<FoodItem>>,
    private val archivedState: State<List<ArchivedItem>>,
    private val categoriesState: State<List<CategoryDef>>,
    private val locationsState: State<List<String>>,
    private val nutstoreAccountState: State<String>,
    private val nutstorePasswordState: State<String>,
    private val lastSyncState: State<String>,
    private val credentialBrokenState: State<Boolean>,
    private val plaintextFallbackState: State<Boolean>,
    private val syncingState: State<Boolean>,
    private val autoSyncDaysState: State<Int>,
    private val cloudBackupsState: State<List<CloudBackup>>,
    private val loadingBackupsState: State<Boolean>,
    private val localSnapshotsState: State<List<LocalSnapshot>>,
) {
    val dynamicColor: Boolean get() = dynamicColorState.value
    val darkMode: Int get() = darkModeState.value
    val paletteName: String get() = paletteNameState.value
    val themeStyleName: String get() = themeStyleNameState.value
    val floatingNav: Boolean get() = floatingNavState.value
    val items: List<FoodItem> get() = itemsState.value
    val archived: List<ArchivedItem> get() = archivedState.value
    val categories: List<CategoryDef> get() = categoriesState.value
    val locations: List<String> get() = locationsState.value
    val nutstoreAccount: String get() = nutstoreAccountState.value
    val nutstorePassword: String get() = nutstorePasswordState.value
    val lastSync: String get() = lastSyncState.value
    val credentialBroken: Boolean get() = credentialBrokenState.value

    /** 密码以未加密明文保存（Keystore 不可用时的极端回退）——需提示用户。 */
    val plaintextFallback: Boolean get() = plaintextFallbackState.value
    val syncing: Boolean get() = syncingState.value
    val autoSyncDays: Int get() = autoSyncDaysState.value
    val cloudBackups: List<CloudBackup> get() = cloudBackupsState.value
    val loadingBackups: Boolean get() = loadingBackupsState.value
    val localSnapshots: List<LocalSnapshot> get() = localSnapshotsState.value

    // ---- 本屏私有的 UI 状态（弹窗、候选、输入框）----

    // 这里刻意不用 `var x by mutableStateOf(...)`：属性自动生成的 setter（JVM 名
    // setShowClearDialog(Z)V）会和下面为屏幕保留的 `fun setShowClearDialog(show)` 撞签名
    // （CI 34930365615 报 Platform declaration clash）。改成「私有 MutableState + val
    // getter + 同名函数」，屏幕层调用写法不变，Compose 的读取追踪也不受影响。
    private val showClearDialogState = mutableStateOf(false)
    private val showNutstoreDialogState = mutableStateOf(false)
    private val showBackupPickerState = mutableStateOf(false)
    private val showSnapshotPickerState = mutableStateOf(false)
    private val showExportFormatDialogState = mutableStateOf(false)
    private val showRestoreSourceDialogState = mutableStateOf(false)
    private val restoreCandidateState = mutableStateOf<CloudBackup?>(null)
    private val restoreSnapshotCandidateState = mutableStateOf<LocalSnapshot?>(null)
    private val accountInputState = mutableStateOf("")
    private val passwordInputState = mutableStateOf("")

    val showClearDialog: Boolean get() = showClearDialogState.value
    val showNutstoreDialog: Boolean get() = showNutstoreDialogState.value
    val showBackupPicker: Boolean get() = showBackupPickerState.value
    val showSnapshotPicker: Boolean get() = showSnapshotPickerState.value
    val showExportFormatDialog: Boolean get() = showExportFormatDialogState.value
    val showRestoreSourceDialog: Boolean get() = showRestoreSourceDialogState.value
    val restoreCandidate: CloudBackup? get() = restoreCandidateState.value
    val restoreSnapshotCandidate: LocalSnapshot? get() = restoreSnapshotCandidateState.value
    val accountInput: String get() = accountInputState.value
    val passwordInput: String get() = passwordInputState.value

    fun setShowClearDialog(show: Boolean) {
        showClearDialogState.value = show
    }

    fun setShowNutstoreDialog(show: Boolean) {
        showNutstoreDialogState.value = show
    }

    fun setShowBackupPicker(show: Boolean) {
        showBackupPickerState.value = show
    }

    fun setShowSnapshotPicker(show: Boolean) {
        showSnapshotPickerState.value = show
    }

    fun setShowExportFormatDialog(show: Boolean) {
        showExportFormatDialogState.value = show
    }

    fun setShowRestoreSourceDialog(show: Boolean) {
        showRestoreSourceDialogState.value = show
    }

    fun setRestoreCandidate(candidate: CloudBackup?) {
        restoreCandidateState.value = candidate
    }

    fun setRestoreSnapshotCandidate(candidate: LocalSnapshot?) {
        restoreSnapshotCandidateState.value = candidate
    }

    fun setAccountInput(account: String) {
        accountInputState.value = account
    }

    fun setPasswordInput(password: String) {
        passwordInputState.value = password
    }

    /** 打开「坚果云账号」弹窗时用当前已存凭据初始化输入框。 */
    fun fillCredentialInputs(account: String, password: String) {
        accountInputState.value = account
        passwordInputState.value = password
    }

    // ---- 动作转发 ----

    fun setDynamicColor(enabled: Boolean) = actions.setDynamicColor(enabled)
    fun setDarkMode(mode: Int) = actions.setDarkMode(mode)
    fun setPalette(name: String) = actions.setPalette(name)
    fun setThemeStyle(style: String) = actions.setThemeStyle(style)
    fun setFloatingNav(enabled: Boolean) = actions.setFloatingNav(enabled)
    fun setAutoSyncDays(days: Int) = actions.setAutoSyncDays(days)

    fun saveNutstoreCredentials(account: String, pass: String) =
        actions.saveNutstoreCredentials(account, pass)

    fun syncUpload() = actions.syncUpload()
    fun loadCloudBackups() = actions.loadCloudBackups()
    fun syncDownload(fileName: String) = actions.syncDownload(fileName)

    fun loadLocalSnapshots() = actions.loadLocalSnapshots()
    fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)? = null) = actions.saveLocalSnapshot(onDone)
    fun restoreLocalSnapshot(fileName: String) = actions.restoreLocalSnapshot(fileName)

    fun clearAll() = actions.clearAll()

    suspend fun buildBackupJson(): String = actions.buildBackupJson()
    suspend fun buildCsvExport(): String = actions.buildCsvExport()

    /** 裸导入（无预览/无自动快照）。屏幕层请用 [previewBackup] + [importBackupWithSnapshot]。 */
    suspend fun importBackupJson(raw: String): Boolean = actions.importBackupJson(raw)

    /** 解析备份用于导入前预览（不改动数据）；非备份 / 畸形 JSON 返回 null。 */
    suspend fun previewBackup(raw: String): BackupData? = actions.previewBackup(raw)

    /** 导入前先存一份本地快照，再整体替换。成败经设置页的 `UiEvent` 报信（#4c）。 */
    fun importBackupWithSnapshot(raw: String) = actions.importBackupWithSnapshot(raw)
}

/** `SettingsActions` 的 ViewModel 实现（保持 `AppViewModel` 不变）。 */
private class ViewModelSettingsActions(private val viewModel: AppViewModel) : SettingsActions {
    override fun setDynamicColor(enabled: Boolean) {
        viewModel.setDynamicColor(enabled)
    }
    override fun setDarkMode(mode: Int) {
        viewModel.setDarkMode(mode)
    }
    override fun setPalette(name: String) {
        viewModel.setPalette(name)
    }
    override fun setThemeStyle(style: String) {
        viewModel.setThemeStyle(style)
    }
    override fun setFloatingNav(enabled: Boolean) {
        viewModel.setFloatingNav(enabled)
    }
    override fun setAutoSyncDays(days: Int) {
        viewModel.setAutoSyncDays(days)
    }
    override fun saveNutstoreCredentials(account: String, pass: String) {
        viewModel.saveNutstoreCredentials(account, pass)
    }

    override fun syncUpload() {
        viewModel.syncUpload()
    }
    override fun loadCloudBackups() {
        viewModel.loadCloudBackups()
    }

    override fun syncDownload(fileName: String) {
        viewModel.syncDownload(fileName)
    }

    override fun loadLocalSnapshots() {
        viewModel.loadLocalSnapshots()
    }
    override fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)?) {
        viewModel.saveLocalSnapshot(onDone)
    }
    override fun restoreLocalSnapshot(fileName: String) {
        viewModel.restoreLocalSnapshot(fileName)
    }

    override fun clearAll() {
        viewModel.clearAll()
    }
    override suspend fun buildBackupJson(): String = viewModel.buildBackupJson()
    override suspend fun buildCsvExport(): String = viewModel.buildCsvExport()
    override suspend fun importBackupJson(raw: String): Boolean = viewModel.importBackupJson(raw)
    override suspend fun previewBackup(raw: String): BackupData? = viewModel.previewBackup(raw)
    override fun importBackupWithSnapshot(raw: String) {
        viewModel.importBackupWithSnapshot(raw)
    }
}

@Composable
fun rememberSettingsUiState(viewModel: AppViewModel): SettingsUiState {
    // collectAsStateWithLifecycle 是 @Composable，必须在 remember 之外调用；
    // 它返回的 State 在同一组合内是稳定实例（内部由 remember 持有，key 只有
    // lifecycle/flow/context），所以下面只在首次组合时把这一批 State 装进容器 —— 容器
    // 身份稳定，读到 `state.x` 的作用域才会按各自的 State 精确重组。
    val dynamicColor = viewModel.dynamicColor.collectAsStateWithLifecycle()
    val darkMode = viewModel.darkMode.collectAsStateWithLifecycle()
    val paletteName = viewModel.palette.collectAsStateWithLifecycle()
    val themeStyleName = viewModel.themeStyle.collectAsStateWithLifecycle()
    val floatingNav = viewModel.floatingNav.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsStateWithLifecycle()
    val archived = viewModel.archived.collectAsStateWithLifecycle()
    val categories = viewModel.categories.collectAsStateWithLifecycle()
    val locations = viewModel.locations.collectAsStateWithLifecycle()
    val nutstoreAccount = viewModel.nutstoreAccount.collectAsStateWithLifecycle()
    val nutstorePassword = viewModel.nutstorePassword.collectAsStateWithLifecycle()
    val lastSync = viewModel.lastSync.collectAsStateWithLifecycle()
    val credentialBroken = viewModel.nutstoreCredentialBroken.collectAsStateWithLifecycle()
    val plaintextFallback = viewModel.nutstorePlaintextFallback.collectAsStateWithLifecycle()
    val syncing = viewModel.syncing.collectAsStateWithLifecycle()
    val autoSyncDays = viewModel.autoSyncDays.collectAsStateWithLifecycle()
    val cloudBackups = viewModel.cloudBackups.collectAsStateWithLifecycle()
    val loadingBackups = viewModel.loadingBackups.collectAsStateWithLifecycle()
    val localSnapshots = viewModel.localSnapshots.collectAsStateWithLifecycle()

    val state = remember {
        SettingsUiState(
            actions = ViewModelSettingsActions(viewModel),
            dynamicColorState = dynamicColor,
            darkModeState = darkMode,
            paletteNameState = paletteName,
            themeStyleNameState = themeStyleName,
            floatingNavState = floatingNav,
            itemsState = items,
            archivedState = archived,
            categoriesState = categories,
            locationsState = locations,
            nutstoreAccountState = nutstoreAccount,
            nutstorePasswordState = nutstorePassword,
            lastSyncState = lastSync,
            credentialBrokenState = credentialBroken,
            plaintextFallbackState = plaintextFallback,
            syncingState = syncing,
            autoSyncDaysState = autoSyncDays,
            cloudBackupsState = cloudBackups,
            loadingBackupsState = loadingBackups,
            localSnapshotsState = localSnapshots,
        )
    }

    // 打开弹窗时用已存凭据填输入框：读的是容器内部的 State，弹窗内部的重组即可。
    LaunchedEffect(state.showNutstoreDialog, state.nutstoreAccount, state.nutstorePassword) {
        if (state.showNutstoreDialog) {
            state.fillCredentialInputs(state.nutstoreAccount, state.nutstorePassword)
        }
    }

    return state
}
