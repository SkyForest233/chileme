package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchivedItem
import com.agon.app.data.BackupData
import com.agon.app.data.CategoryDef
import com.agon.app.data.CloudBackup
import com.agon.app.data.FoodItem
import com.agon.app.data.LocalSnapshot
import com.agon.app.viewmodel.AppViewModel

/**
 * 待用户确认的导入内容：原始 JSON + 预览摘要。
 *
 * 导入是「整体替换」的破坏性操作，两套设置页都先用它弹二次确认
 * （展示将覆盖的条数与备份导出日期），确认后才写库（2026-09-15）。
 */
data class PendingImport(val raw: String, val preview: BackupData)

/**
 * 设置页跨主题共享状态容器。
 */
class SettingsUiState(
    val dynamicColor: Boolean,
    val darkMode: Int,
    val paletteName: String,
    val themeStyleName: String,
    val floatingNav: Boolean,
    val items: List<FoodItem>,
    val archived: List<ArchivedItem>,
    val categories: List<CategoryDef>,
    val locations: List<String>,
    val nutstoreAccount: String,
    val nutstorePassword: String,
    val lastSync: String,
    val credentialBroken: Boolean,
    val syncing: Boolean,
    val autoSyncDays: Int,
    val cloudBackups: List<CloudBackup>,
    val loadingBackups: Boolean,
    val localSnapshots: List<LocalSnapshot>,
    val showClearDialog: Boolean,
    val showNutstoreDialog: Boolean,
    val showBackupPicker: Boolean,
    val showSnapshotPicker: Boolean,
    val showExportFormatDialog: Boolean,
    val showRestoreSourceDialog: Boolean,
    val restoreCandidate: CloudBackup?,
    val restoreSnapshotCandidate: LocalSnapshot?,
    val accountInput: String,
    val passwordInput: String,
    private val viewModel: AppViewModel,
    private val onShowClearDialogChanged: (Boolean) -> Unit,
    private val onShowNutstoreDialogChanged: (Boolean) -> Unit,
    private val onShowBackupPickerChanged: (Boolean) -> Unit,
    private val onShowSnapshotPickerChanged: (Boolean) -> Unit,
    private val onShowExportFormatDialogChanged: (Boolean) -> Unit,
    private val onShowRestoreSourceDialogChanged: (Boolean) -> Unit,
    private val onRestoreCandidateChanged: (CloudBackup?) -> Unit,
    private val onRestoreSnapshotCandidateChanged: (LocalSnapshot?) -> Unit,
    private val onAccountInputChanged: (String) -> Unit,
    private val onPasswordInputChanged: (String) -> Unit,
) {
    fun setShowClearDialog(show: Boolean) = onShowClearDialogChanged(show)
    fun setShowNutstoreDialog(show: Boolean) = onShowNutstoreDialogChanged(show)
    fun setShowBackupPicker(show: Boolean) = onShowBackupPickerChanged(show)
    fun setShowSnapshotPicker(show: Boolean) = onShowSnapshotPickerChanged(show)
    fun setShowExportFormatDialog(show: Boolean) = onShowExportFormatDialogChanged(show)
    fun setShowRestoreSourceDialog(show: Boolean) = onShowRestoreSourceDialogChanged(show)
    fun setRestoreCandidate(candidate: CloudBackup?) = onRestoreCandidateChanged(candidate)
    fun setRestoreSnapshotCandidate(candidate: LocalSnapshot?) = onRestoreSnapshotCandidateChanged(candidate)
    fun setAccountInput(account: String) = onAccountInputChanged(account)
    fun setPasswordInput(password: String) = onPasswordInputChanged(password)

    fun setDynamicColor(enabled: Boolean) = viewModel.setDynamicColor(enabled)
    fun setDarkMode(mode: Int) = viewModel.setDarkMode(mode)
    fun setPalette(name: String) = viewModel.setPalette(name)
    fun setThemeStyle(style: String) = viewModel.setThemeStyle(style)
    fun setFloatingNav(enabled: Boolean) = viewModel.setFloatingNav(enabled)
    fun setAutoSyncDays(days: Int) = viewModel.setAutoSyncDays(days)

    fun saveNutstoreCredentials(account: String, pass: String) =
        viewModel.saveNutstoreCredentials(account, pass)

    fun syncUpload(onResult: (Boolean, String) -> Unit) = viewModel.syncUpload(onResult)
    fun loadCloudBackups(onResult: (Boolean, String) -> Unit) = viewModel.loadCloudBackups(onResult)
    fun syncDownload(fileName: String, onResult: (Boolean, String) -> Unit) = viewModel.syncDownload(fileName, onResult)

    fun loadLocalSnapshots() = viewModel.loadLocalSnapshots()
    fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)? = null) = viewModel.saveLocalSnapshot(onDone)
    fun restoreLocalSnapshot(fileName: String, onResult: (Boolean, String) -> Unit) =
        viewModel.restoreLocalSnapshot(fileName, onResult)

    fun clearAll() = viewModel.clearAll()

    suspend fun buildBackupJson(): String = viewModel.buildBackupJson()
    suspend fun buildCsvExport(): String = viewModel.buildCsvExport()
    /** 裸导入（无预览/无自动快照）。屏幕层请用 [previewBackup] + [importBackupWithSnapshot]。 */
    suspend fun importBackupJson(raw: String): Boolean = viewModel.importBackupJson(raw)

    /** 解析备份用于导入前预览（不改动数据）；非备份 / 畸形 JSON 返回 null。 */
    suspend fun previewBackup(raw: String): BackupData? = viewModel.previewBackup(raw)

    /** 导入前先存一份本地快照，再整体替换。参数二 = 快照是否保存成功。 */
    fun importBackupWithSnapshot(raw: String, onResult: (ok: Boolean, snapshotSaved: Boolean) -> Unit) =
        viewModel.importBackupWithSnapshot(raw, onResult)
}

@Composable
fun rememberSettingsUiState(viewModel: AppViewModel): SettingsUiState {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val paletteName by viewModel.palette.collectAsStateWithLifecycle()
    val themeStyleName by viewModel.themeStyle.collectAsStateWithLifecycle()
    val floatingNav by viewModel.floatingNav.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val nutstoreAccount by viewModel.nutstoreAccount.collectAsStateWithLifecycle()
    val nutstorePassword by viewModel.nutstorePassword.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val credentialBroken by viewModel.nutstoreCredentialBroken.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val autoSyncDays by viewModel.autoSyncDays.collectAsStateWithLifecycle()
    val cloudBackups by viewModel.cloudBackups.collectAsStateWithLifecycle()
    val loadingBackups by viewModel.loadingBackups.collectAsStateWithLifecycle()
    val localSnapshots by viewModel.localSnapshots.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    var showNutstoreDialog by remember { mutableStateOf(false) }
    var showBackupPicker by remember { mutableStateOf(false) }
    var showSnapshotPicker by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showRestoreSourceDialog by remember { mutableStateOf(false) }
    var restoreCandidate by remember { mutableStateOf<CloudBackup?>(null) }
    var restoreSnapshotCandidate by remember { mutableStateOf<LocalSnapshot?>(null) }

    var accountInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    LaunchedEffect(showNutstoreDialog, nutstoreAccount, nutstorePassword) {
        if (showNutstoreDialog) {
            accountInput = nutstoreAccount
            passwordInput = nutstorePassword
        }
    }

    return remember(
        dynamicColor,
        darkMode,
        paletteName,
        themeStyleName,
        floatingNav,
        items,
        archived,
        categories,
        locations,
        nutstoreAccount,
        nutstorePassword,
        lastSync,
        credentialBroken,
        syncing,
        autoSyncDays,
        cloudBackups,
        loadingBackups,
        localSnapshots,
        showClearDialog,
        showNutstoreDialog,
        showBackupPicker,
        showSnapshotPicker,
        showExportFormatDialog,
        showRestoreSourceDialog,
        restoreCandidate,
        restoreSnapshotCandidate,
        accountInput,
        passwordInput,
    ) {
        SettingsUiState(
            dynamicColor = dynamicColor,
            darkMode = darkMode,
            paletteName = paletteName,
            themeStyleName = themeStyleName,
            floatingNav = floatingNav,
            items = items,
            archived = archived,
            categories = categories,
            locations = locations,
            nutstoreAccount = nutstoreAccount,
            nutstorePassword = nutstorePassword,
            lastSync = lastSync,
            credentialBroken = credentialBroken,
            syncing = syncing,
            autoSyncDays = autoSyncDays,
            cloudBackups = cloudBackups,
            loadingBackups = loadingBackups,
            localSnapshots = localSnapshots,
            showClearDialog = showClearDialog,
            showNutstoreDialog = showNutstoreDialog,
            showBackupPicker = showBackupPicker,
            showSnapshotPicker = showSnapshotPicker,
            showExportFormatDialog = showExportFormatDialog,
            showRestoreSourceDialog = showRestoreSourceDialog,
            restoreCandidate = restoreCandidate,
            restoreSnapshotCandidate = restoreSnapshotCandidate,
            accountInput = accountInput,
            passwordInput = passwordInput,
            viewModel = viewModel,
            onShowClearDialogChanged = { showClearDialog = it },
            onShowNutstoreDialogChanged = { showNutstoreDialog = it },
            onShowBackupPickerChanged = { showBackupPicker = it },
            onShowSnapshotPickerChanged = { showSnapshotPicker = it },
            onShowExportFormatDialogChanged = { showExportFormatDialog = it },
            onShowRestoreSourceDialogChanged = { showRestoreSourceDialog = it },
            onRestoreCandidateChanged = { restoreCandidate = it },
            onRestoreSnapshotCandidateChanged = { restoreSnapshotCandidate = it },
            onAccountInputChanged = { accountInput = it },
            onPasswordInputChanged = { passwordInput = it },
        )
    }
}
