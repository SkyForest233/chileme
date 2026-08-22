package com.agon.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.data.CloudBackup
import com.agon.app.data.FoodItem
import com.agon.app.viewmodel.AppViewModel

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
    val showClearDialog: Boolean,
    val showNutstoreDialog: Boolean,
    val showBackupPicker: Boolean,
    val restoreCandidate: CloudBackup?,
    val accountInput: String,
    val passwordInput: String,
    private val viewModel: AppViewModel,
    private val onShowClearDialogChanged: (Boolean) -> Unit,
    private val onShowNutstoreDialogChanged: (Boolean) -> Unit,
    private val onShowBackupPickerChanged: (Boolean) -> Unit,
    private val onRestoreCandidateChanged: (CloudBackup?) -> Unit,
    private val onAccountInputChanged: (String) -> Unit,
    private val onPasswordInputChanged: (String) -> Unit,
) {
    fun setShowClearDialog(show: Boolean) = onShowClearDialogChanged(show)
    fun setShowNutstoreDialog(show: Boolean) = onShowNutstoreDialogChanged(show)
    fun setShowBackupPicker(show: Boolean) = onShowBackupPickerChanged(show)
    fun setRestoreCandidate(candidate: CloudBackup?) = onRestoreCandidateChanged(candidate)
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
    fun clearAll() = viewModel.clearAll()

    suspend fun buildBackupJson(): String = viewModel.buildBackupJson()
    suspend fun importBackupJson(raw: String): Boolean = viewModel.importBackupJson(raw)
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

    var showClearDialog by remember { mutableStateOf(false) }
    var showNutstoreDialog by remember { mutableStateOf(false) }
    var showBackupPicker by remember { mutableStateOf(false) }
    var restoreCandidate by remember { mutableStateOf<CloudBackup?>(null) }

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
        showClearDialog,
        showNutstoreDialog,
        showBackupPicker,
        restoreCandidate,
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
            showClearDialog = showClearDialog,
            showNutstoreDialog = showNutstoreDialog,
            showBackupPicker = showBackupPicker,
            restoreCandidate = restoreCandidate,
            accountInput = accountInput,
            passwordInput = passwordInput,
            viewModel = viewModel,
            onShowClearDialogChanged = { showClearDialog = it },
            onShowNutstoreDialogChanged = { showNutstoreDialog = it },
            onShowBackupPickerChanged = { showBackupPicker = it },
            onRestoreCandidateChanged = { restoreCandidate = it },
            onAccountInputChanged = { accountInput = it },
            onPasswordInputChanged = { passwordInput = it },
        )
    }
}
