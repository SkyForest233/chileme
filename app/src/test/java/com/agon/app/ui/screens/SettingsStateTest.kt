package com.agon.app.ui.screens

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.agon.app.data.ArchivedItem
import com.agon.app.data.BackupData
import com.agon.app.data.CategoryDef
import com.agon.app.data.CloudBackup
import com.agon.app.data.FoodItem
import com.agon.app.data.LocalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SettingsUiState` 的字段级订阅测试（2026-09-15 重构）。
 *
 * 这组用例防的是「整个设置页任何一处变化都重建 state 对象」这个回归：
 * 只要某个字段退化成构造时拷贝的普通 `val`，第一个用例就会失败。
 */
class SettingsStateTest {

    private class RecordingActions : SettingsActions {
        val calls = mutableListOf<String>()

        override fun setDynamicColor(enabled: Boolean) {
            calls += "setDynamicColor($enabled)"
        }

        override fun setDarkMode(mode: Int) {
            calls += "setDarkMode($mode)"
        }

        override fun setPalette(name: String) {
            calls += "setPalette($name)"
        }

        override fun setThemeStyle(style: String) {
            calls += "setThemeStyle($style)"
        }

        override fun setFloatingNav(enabled: Boolean) {
            calls += "setFloatingNav($enabled)"
        }

        override fun setAutoSyncDays(days: Int) {
            calls += "setAutoSyncDays($days)"
        }

        override fun saveNutstoreCredentials(account: String, pass: String) {
            calls += "saveNutstoreCredentials($account)"
        }

        override fun syncUpload(onResult: (Boolean, String) -> Unit) {
            calls += "syncUpload"
        }

        override fun loadCloudBackups(onResult: (Boolean, String) -> Unit) {
            calls += "loadCloudBackups"
        }

        override fun syncDownload(fileName: String, onResult: (Boolean, String) -> Unit) {
            calls += "syncDownload($fileName)"
        }

        override fun loadLocalSnapshots() {
            calls += "loadLocalSnapshots"
        }

        override fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)?) {
            calls += "saveLocalSnapshot"
        }

        override fun restoreLocalSnapshot(fileName: String, onResult: (Boolean, String) -> Unit) {
            calls += "restoreLocalSnapshot($fileName)"
        }

        override fun clearAll() {
            calls += "clearAll"
        }

        override suspend fun buildBackupJson(): String = "{}"

        override suspend fun buildCsvExport(): String = ""

        override suspend fun importBackupJson(raw: String): Boolean = true

        override suspend fun previewBackup(raw: String): BackupData? = null

        override fun importBackupWithSnapshot(
            raw: String,
            onResult: (ok: Boolean, snapshotSaved: Boolean) -> Unit,
        ) {
            calls += "importBackupWithSnapshot"
        }
    }

    private fun holder(
        actions: SettingsActions = RecordingActions(),
        darkMode: MutableState<Int> = mutableStateOf(0),
        palette: MutableState<String> = mutableStateOf("MINT"),
        syncing: MutableState<Boolean> = mutableStateOf(false),
        account: MutableState<String> = mutableStateOf(""),
    ): SettingsUiState = SettingsUiState(
        actions = actions,
        dynamicColorState = mutableStateOf(false),
        darkModeState = darkMode,
        paletteNameState = palette,
        themeStyleNameState = mutableStateOf("MATERIAL3"),
        floatingNavState = mutableStateOf(true),
        itemsState = mutableStateOf(emptyList<FoodItem>()),
        archivedState = mutableStateOf(emptyList<ArchivedItem>()),
        categoriesState = mutableStateOf(emptyList<CategoryDef>()),
        locationsState = mutableStateOf(emptyList<String>()),
        nutstoreAccountState = account,
        nutstorePasswordState = account,
        lastSyncState = mutableStateOf(""),
        credentialBrokenState = mutableStateOf(false),
        plaintextFallbackState = mutableStateOf(false),
        syncingState = syncing,
        autoSyncDaysState = mutableStateOf(0),
        cloudBackupsState = mutableStateOf(emptyList<CloudBackup>()),
        loadingBackupsState = mutableStateOf(false),
        localSnapshotsState = mutableStateOf(emptyList<LocalSnapshot>()),
    )

    /** 在只读快照里执行 [block]，返回期间被读到的所有 state 对象。 */
    private fun readsOf(block: () -> Any?): List<Any> {
        val seen = mutableListOf<Any>()
        val snapshot = Snapshot.takeSnapshot(readObserver = { seen += it })
        try {
            snapshot.enter { block() }
        } finally {
            snapshot.dispose()
        }
        return seen
    }

    @Test
    fun `读取一个字段只订阅它自己的 State`() {
        val dark = mutableStateOf(0)
        val palette = mutableStateOf("MINT")
        val state = holder(darkMode = dark, palette = palette)

        val seen = readsOf { state.darkMode }

        assertTrue("读 darkMode 必须订阅 darkMode 的 State", seen.contains(dark))
        assertFalse("读 darkMode 不该顺带订阅 palette", seen.contains(palette))
    }

    @Test
    fun `字段是活引用而不是构造时快照`() {
        val dark = mutableStateOf(0)
        val items = mutableStateOf(emptyList<FoodItem>())
        val state = holder(darkMode = dark)

        dark.value = 2
        items.value = emptyList()

        assertEquals(2, state.darkMode)
        assertEquals(emptyList<FoodItem>(), state.items)
    }

    @Test
    fun `弹窗与输入框状态由容器自己持有且改动立即可见`() {
        val state = holder()

        assertFalse(state.showClearDialog)
        state.setShowClearDialog(true)
        assertTrue(state.showClearDialog)

        state.setShowNutstoreDialog(true)
        assertTrue(state.showNutstoreDialog)
        state.setShowNutstoreDialog(false)
        assertFalse(state.showNutstoreDialog)

        state.setAccountInput("a@b.com")
        state.setPasswordInput("pw")
        assertEquals("a@b.com", state.accountInput)
        assertEquals("pw", state.passwordInput)

        // 打开弹窗时的初始化：用当前已存凭据覆盖两个输入框
        state.fillCredentialInputs("saved@x.com", "saved-pw")
        assertEquals("saved@x.com", state.accountInput)
        assertEquals("saved-pw", state.passwordInput)
    }

    @Test
    fun `动作转发到 SettingsActions`() {
        val actions = RecordingActions()
        val state = holder(actions = actions)

        state.setDarkMode(1)
        state.setFloatingNav(false)
        state.setAutoSyncDays(7)
        state.clearAll()

        assertEquals(
            listOf("setDarkMode(1)", "setFloatingNav(false)", "setAutoSyncDays(7)", "clearAll"),
            actions.calls,
        )
    }
}
