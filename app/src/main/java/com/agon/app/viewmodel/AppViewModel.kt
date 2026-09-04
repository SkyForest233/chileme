package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.DefaultCategories
import com.agon.app.data.DefaultLocations
import com.agon.app.data.FoodItem
import com.agon.app.data.FoodRepository
import com.agon.app.data.HistoryEntry
import com.agon.app.data.CloudBackup
import com.agon.app.data.LocalSnapshot
import com.agon.app.data.LocalSnapshotStore
import com.agon.app.data.NutstoreSync
import com.agon.app.data.QuantityChangeResult
import com.agon.app.data.cleanupOrphanCovers
import com.agon.app.data.daysLeft
import com.agon.app.data.toHistoryEntry
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FoodRepository(application)

    val items: StateFlow<List<FoodItem>> =
        repo.itemsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val archived: StateFlow<List<ArchivedItem>> =
        repo.archiveFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val consumption: StateFlow<List<ConsumptionRecord>> =
        repo.consumptionFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntry>> =
        repo.historyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val suggestionSource: StateFlow<List<HistoryEntry>> =
        combine(repo.historyFlow, repo.itemsFlow, repo.archiveFlow) { history, items, archived ->
            (history + items.map { it.toHistoryEntry() } + archived.map { it.item.toHistoryEntry() })
                .distinctBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val thresholds: StateFlow<Map<String, Int>> =
        repo.thresholdsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val categories: StateFlow<List<CategoryDef>> =
        repo.categoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultCategories)

    val locations: StateFlow<List<String>> =
        repo.locationsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DefaultLocations)

    val dynamicColor: StateFlow<Boolean> =
        repo.dynamicColorFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val darkMode: StateFlow<Int> =
        repo.darkModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val palette: StateFlow<String> =
        repo.paletteFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "MINT")

    val themeStyle: StateFlow<String> =
        repo.themeStyleFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "MATERIAL3")

    val floatingNav: StateFlow<Boolean> =
        repo.floatingNavFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ---- Phase0 新增 ----
    val colorMode: StateFlow<Int> =
        repo.colorModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val paletteStyle: StateFlow<String> =
        repo.paletteStyleFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "TonalSpot")

    val colorSpec: StateFlow<String> =
        repo.colorSpecFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "SPEC_2025")

    val enableBlur: StateFlow<Boolean> =
        repo.enableBlurFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val enableFloatingBlur: StateFlow<Boolean> =
        repo.enableFloatingBlurFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val enableBadge: StateFlow<Boolean> =
        repo.enableBadgeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val nutstoreAccount: StateFlow<String> =
        repo.nutstoreAccountFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val nutstorePassword: StateFlow<String> =
        repo.nutstorePasswordFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastSync: StateFlow<String> =
        repo.lastSyncFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val corruptedKeys: StateFlow<Set<String>> = repo.corruptedKeys

    val nutstoreCredentialBroken: StateFlow<Boolean> =
        repo.nutstoreCredentialBrokenFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoSyncDays: StateFlow<Int> =
        repo.autoSyncDaysFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val ready: StateFlow<Boolean> =
        combine(repo.itemsFlow, repo.paletteFlow, repo.colorModeFlow, repo.themeStyleFlow, repo.floatingNavFlow) { _, _, _, _, _ -> true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _fabSuppressed = MutableStateFlow(false)
    val fabSuppressed: StateFlow<Boolean> = _fabSuppressed.asStateFlow()

    fun setFabSuppressed(suppressed: Boolean) {
        _fabSuppressed.value = suppressed
    }

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun setSelection(ids: Set<String>) {
        _selectedIds.value = ids
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    data class UndoRequest(val itemId: String, val consumptionId: String)

    private val _undoRequest = MutableStateFlow<UndoRequest?>(null)
    val undoRequest: StateFlow<UndoRequest?> = _undoRequest.asStateFlow()

    fun consumeUndoRequest() {
        _undoRequest.value = null
    }

    fun undoConsumption(request: UndoRequest) = viewModelScope.launch {
        repo.undoConsumption(request.itemId, request.consumptionId)
    }

    data class DeletedConsumption(val record: ConsumptionRecord, val index: Int)

    private val _deletedConsumption = MutableStateFlow<DeletedConsumption?>(null)
    val deletedConsumption: StateFlow<DeletedConsumption?> = _deletedConsumption.asStateFlow()

    fun consumeDeletedConsumption() {
        _deletedConsumption.value = null
    }

    data class RestoredArchivedEvent(val item: FoodItem, val reason: ArchiveReason, val merged: Boolean)

    private val _restoredArchivedEvent = MutableStateFlow<RestoredArchivedEvent?>(null)
    val restoredArchivedEvent: StateFlow<RestoredArchivedEvent?> = _restoredArchivedEvent.asStateFlow()

    fun consumeRestoredArchivedEvent() {
        _restoredArchivedEvent.value = null
    }

    fun restoreArchivedWithUndo(entry: ArchivedItem) = viewModelScope.launch {
        val merged = repo.restoreArchived(entry.item.id)
        _restoredArchivedEvent.value = RestoredArchivedEvent(entry.item, entry.reason, merged)
    }

    fun deleteConsumption(record: ConsumptionRecord) = viewModelScope.launch {
        val sorted = consumption.value.sortedByDescending { it.epochDay }
        val index = sorted.indexOfFirst {
            if (record.id != null) it.id == record.id else it == record
        }
        val target = sorted.getOrNull(index) ?: return@launch
        repo.deleteConsumption(target)
        _deletedConsumption.value = DeletedConsumption(target, index.coerceAtLeast(0))
    }

    fun undoDeleteConsumption(record: ConsumptionRecord, index: Int) = viewModelScope.launch {
        repo.addConsumption(record, index)
        if (_deletedConsumption.value?.record?.id == record.id) {
            _deletedConsumption.value = null
        }
    }

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            repo.migratePlaintextPassword()
            repo.migrateConsumptionIds()
            val referenced = buildSet {
                repo.itemsFlow.first().forEach { if (it.photoPath.isNotBlank()) add(it.photoPath) }
                repo.archiveFlow.first().forEach { if (it.item.photoPath.isNotBlank()) add(it.item.photoPath) }
            }
            cleanupOrphanCovers(getApplication(), referenced)
            maybeAutoSync()
            maybeAutoSnapshot()
        }
    }

    private val _autoSyncMessage = MutableStateFlow<String?>(null)
    val autoSyncMessage: StateFlow<String?> = _autoSyncMessage.asStateFlow()

    fun consumeAutoSyncMessage() {
        _autoSyncMessage.value = null
    }

    private suspend fun maybeAutoSync() {
        val days = repo.autoSyncDaysFlow.first()
        if (days <= 0) return
        val account = repo.nutstoreAccountFlow.first()
        val password = repo.nutstorePasswordFlow.first()
        if (account.isBlank() || password.isBlank()) return
        val today = LocalDate.now().toEpochDay()
        val last = repo.lastAutoSyncEpochDayFlow.first()
        if (today - last < days) return
        val payload = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
        val result = NutstoreSync.upload(account, password, payload)
        if (result.isSuccess) {
            repo.setLastAutoSyncEpochDay(today)
            val time = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            repo.setLastSync("自动同步于 $time")
            _autoSyncMessage.value = "已自动同步到坚果云 ☁️"
        }
    }

    private suspend fun maybeAutoSnapshot() {
        val snapshots = LocalSnapshotStore.listSnapshots(getApplication())
        val today = LocalDate.now()
        val hasSnapshotToday = snapshots.any {
            Instant.ofEpochMilli(it.modifiedEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate() == today
        }
        if (!hasSnapshotToday) {
            val json = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
            LocalSnapshotStore.saveSnapshot(getApplication(), json)
            loadLocalSnapshots()
        }
    }

    fun setAutoSyncDays(days: Int) = viewModelScope.launch { repo.setAutoSyncDays(days) }

    fun upsert(item: FoodItem) = viewModelScope.launch { repo.upsert(item) }

    fun archive(id: String, reason: ArchiveReason) =
        viewModelScope.launch { repo.archiveItems(setOf(id), reason) }

    fun archiveBatch(ids: Set<String>, reason: ArchiveReason) =
        viewModelScope.launch { repo.archiveItems(ids, reason) }

    fun restoreArchivedBatch(ids: Set<String>) = viewModelScope.launch {
        repo.restoreArchivedBatch(ids)
    }

    fun restoreArchivedSmart(id: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(repo.restoreArchived(id))
    }

    fun cleanExpired(onDone: ((Set<String>) -> Unit)? = null) = viewModelScope.launch {
        val ids = items.value.filter { it.daysLeft < 0 }.map { it.id }.toSet()
        if (ids.isNotEmpty()) {
            repo.archiveItems(ids, ArchiveReason.EXPIRED)
            onDone?.invoke(ids)
        }
    }

    fun restoreArchived(id: String) = viewModelScope.launch { repo.restoreArchived(id) }

    fun deleteArchived(id: String) = viewModelScope.launch { repo.deleteArchived(id) }

    fun clearArchive() = viewModelScope.launch { repo.clearArchive() }

    fun changeQuantity(
        id: String,
        delta: Int,
        onAutoArchived: (() -> Unit)? = null,
        withUndo: Boolean = false,
    ) = viewModelScope.launch {
        val result: QuantityChangeResult = repo.changeQuantity(id, delta)
        if (result.autoArchived) onAutoArchived?.invoke()
        if (withUndo && delta < 0 && result.consumptionId != null) {
            _undoRequest.value = UndoRequest(id, result.consumptionId!!)
        }
    }

    fun consumeOne(id: String, onAutoArchived: (() -> Unit)? = null) =
        changeQuantity(id, -1, onAutoArchived)

    fun setCategoryThreshold(categoryId: String, days: Int) =
        viewModelScope.launch { repo.setCategoryThreshold(categoryId, days) }

    fun addCategory(label: String, emoji: String) = viewModelScope.launch {
        val def = CategoryDef(UUID.randomUUID().toString(), label.trim(), emoji.trim().ifBlank { "🍽️" })
        repo.setCategories(categories.value + def)
    }

    fun updateCategory(def: CategoryDef) = viewModelScope.launch {
        repo.setCategories(categories.value.map { if (it.id == def.id) def else it })
    }

    fun deleteCategory(id: String) = viewModelScope.launch {
        val remaining = categories.value.filterNot { it.id == id }
        if (remaining.isNotEmpty()) repo.setCategories(remaining)
    }

    fun addLocation(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && trimmed !in locations.value) {
            repo.setLocations(locations.value + trimmed)
        }
    }

    fun deleteLocation(name: String) = viewModelScope.launch {
        repo.setLocations(locations.value.filterNot { it == name })
    }

    fun clearAll() = viewModelScope.launch { repo.clearAll() }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }

    fun setDarkMode(mode: Int) = viewModelScope.launch { repo.setDarkMode(mode) }

    fun setPalette(name: String) = viewModelScope.launch { repo.setPalette(name) }

    fun setThemeStyle(name: String) = viewModelScope.launch { repo.setThemeStyle(name) }

    fun setFloatingNav(enabled: Boolean) = viewModelScope.launch { repo.setFloatingNav(enabled) }

    fun setColorMode(mode: Int) = viewModelScope.launch { repo.setColorMode(mode) }

    fun setPaletteStyle(name: String) = viewModelScope.launch { repo.setPaletteStyle(name) }

    fun setColorSpec(name: String) = viewModelScope.launch { repo.setColorSpec(name) }

    fun setEnableBlur(enabled: Boolean) = viewModelScope.launch { repo.setEnableBlur(enabled) }

    fun setEnableFloatingBlur(enabled: Boolean) = viewModelScope.launch { repo.setEnableFloatingBlur(enabled) }

    fun setEnableBadge(enabled: Boolean) = viewModelScope.launch { repo.setEnableBadge(enabled) }

    fun updateLocationBatch(ids: Set<String>, newLocation: String) = viewModelScope.launch {
        repo.updateLocationBatch(ids, newLocation)
    }

    suspend fun buildBackupJson(): String = repo.buildBackupJson()

    suspend fun buildCsvExport(): String = repo.buildCsvExport()

    suspend fun importBackupJson(raw: String): Boolean = repo.importBackupJson(raw)

    private val _localSnapshots = MutableStateFlow<List<LocalSnapshot>>(emptyList())
    val localSnapshots: StateFlow<List<LocalSnapshot>> = _localSnapshots.asStateFlow()

    fun loadLocalSnapshots() {
        _localSnapshots.value = LocalSnapshotStore.listSnapshots(getApplication())
    }

    fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
        val json = runCatching { repo.buildBackupJson() }.getOrNull()
        if (json != null) {
            LocalSnapshotStore.saveSnapshot(getApplication(), json)
            loadLocalSnapshots()
            onDone?.invoke(true)
        } else {
            onDone?.invoke(false)
        }
    }

    fun restoreLocalSnapshot(fileName: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val raw = LocalSnapshotStore.readSnapshot(getApplication(), fileName)
        if (raw != null && repo.importBackupJson(raw)) {
            loadLocalSnapshots()
            onResult(true, "已成功从本地快照还原数据 ✅")
        } else {
            onResult(false, "快照文件损坏或无法还原")
        }
    }

    fun saveNutstoreCredentials(account: String, password: String) =
        viewModelScope.launch { repo.setNutstoreCredentials(account, password) }

    fun syncUpload(onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            onResult(false, "请先填写并保存坚果云账号和应用密码")
            return@launch
        }
        _syncing.value = true
        val json = runCatching { repo.buildBackupJson() }.getOrElse {
            _syncing.value = false
            onResult(false, it.message ?: "数据异常，已取消上传")
            return@launch
        }
        val result = NutstoreSync.upload(account, password, json)
        _syncing.value = false
        result.fold(
            onSuccess = {
                val time = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                repo.setLastSync("上传于 $time")
                onResult(true, "已上传到坚果云 ☁️")
            },
            onFailure = { onResult(false, it.message ?: "上传失败") },
        )
    }

    private val _cloudBackups = MutableStateFlow<List<CloudBackup>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackup>> = _cloudBackups.asStateFlow()

    private val _loadingBackups = MutableStateFlow(false)
    val loadingBackups: StateFlow<Boolean> = _loadingBackups.asStateFlow()

    fun loadCloudBackups(onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            onResult(false, "请先填写并保存坚果云账号和应用密码")
            return@launch
        }
        _loadingBackups.value = true
        val result = NutstoreSync.listBackups(account, password)
        _loadingBackups.value = false
        result.fold(
            onSuccess = { list ->
                _cloudBackups.value = list
                if (list.isEmpty()) onResult(false, "云端暂无备份，请先上传")
                else onResult(true, "")
            },
            onFailure = { onResult(false, it.message ?: "获取备份列表失败") },
        )
    }

    fun syncDownload(fileName: String, onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            onResult(false, "请先填写并保存坚果云账号和应用密码")
            return@launch
        }
        _syncing.value = true
        val result = NutstoreSync.download(account, password, fileName)
        _syncing.value = false
        result.fold(
            onSuccess = { raw ->
                if (repo.importBackupJson(raw)) {
                    val time = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    repo.setLastSync("恢复于 $time")
                    onResult(true, "已从坚果云恢复数据 ✅")
                } else {
                    onResult(false, "云端备份格式不正确")
                }
            },
            onFailure = { onResult(false, it.message ?: "下载失败") },
        )
    }
}
