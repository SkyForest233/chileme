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
    val onShowClearDialogChange: (Boolean) -> Unit,
    val showNutstoreDialog: Boolean,
    val onShowNutstoreDialogChange: (Boolean) -> Unit,
    val showBackupPicker: Boolean,
    val onShowBackupPickerChange: (Boolean) -> Unit,
    val restoreCandidate: CloudBackup?,
    val onRestoreCandidateChange: (CloudBackup?) -> Unit,
    val accountInput: String,
    val onAccountInputChange: (String) -> Unit,
    val passwordInput: String,
    val onPasswordInputChange: (String) -> Unit,
    val onSetDynamicColor: (Boolean) -> Unit,
    val onSetDarkMode: (Int) -> Unit,
    val onSetPalette: (String) -> Unit,
    val onSetThemeStyle: (String) -> Unit,
    val onSetFloatingNav: (Boolean) -> Unit,
    val onSetAutoSyncDays: (Int) -> Unit,
    val onSaveNutstoreCredentials: (String, String) -> Unit,
    val onSyncUpload: (onResult: (Boolean, String) -> Unit) -> Unit,
    val onLoadCloudBackups: (onResult: (Boolean, String) -> Unit) -> Unit,
    val onSyncDownload: (fileName: String, onResult: (Boolean, String) -> Unit) -> Unit,
    val onClearAll: () -> Unit,
    val buildBackupJson: suspend () -> String,
    val importBackupJson: suspend (String) -> Boolean,
)

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
            onShowClearDialogChange = { showClearDialog = it },
            showNutstoreDialog = showNutstoreDialog,
            onShowNutstoreDialogChange = { showNutstoreDialog = it },
            showBackupPicker = showBackupPicker,
            onShowBackupPickerChange = { showBackupPicker = it },
            restoreCandidate = restoreCandidate,
            onRestoreCandidateChange = { restoreCandidate = it },
            accountInput = accountInput,
            onAccountInputChange = { accountInput = it },
            passwordInput = passwordInput,
            onPasswordInputChange = { passwordInput = it },
            onSetDynamicColor = { viewModel.setDynamicColor(it) },
            onSetDarkMode = { viewModel.setDarkMode(it) },
            onSetPalette = { viewModel.setPalette(it) },
            onSetThemeStyle = { viewModel.setThemeStyle(it) },
            onSetFloatingNav = { viewModel.setFloatingNav(it) },
            onSetAutoSyncDays = { viewModel.setAutoSyncDays(it) },
            onSaveNutstoreCredentials = { a, p -> viewModel.saveNutstoreCredentials(a, p) },
            onSyncUpload = { onResult -> viewModel.syncUpload(onResult) },
            onLoadCloudBackups = { onResult -> viewModel.loadCloudBackups(onResult) },
            onSyncDownload = { fileName, onResult -> viewModel.syncDownload(fileName, onResult) },
            onClearAll = { viewModel.clearAll() },
            buildBackupJson = { viewModel.buildBackupJson() },
            importBackupJson = { raw -> viewModel.importBackupJson(raw) },
        )
    }
}
