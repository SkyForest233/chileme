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
import com.agon.app.data.NutstoreSync
import com.agon.app.data.QuantityChangeResult
import com.agon.app.data.cleanupOrphanCovers
import com.agon.app.data.daysLeft
import com.agon.app.data.toHistoryEntry
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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

    /**
     * 名称联想统一数据源：录入历史 + 当前库存 + 归档食品，按名称去重。
     * 顺序即优先级——历史（最近录入在前）优先，其次库存，最后归档；
     * 保证所有出现过的食品（含已归档）都能被联想匹配到。
     */
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

    val nutstoreAccount: StateFlow<String> =
        repo.nutstoreAccountFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val nutstorePassword: StateFlow<String> =
        repo.nutstorePasswordFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastSync: StateFlow<String> =
        repo.lastSyncFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** 自动同步间隔（天），0 = 关闭 */
    val autoSyncDays: StateFlow<Int> =
        repo.autoSyncDaysFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 云同步进行中标志 */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /**
     * 首帧门控：DataStore 真正发出第一次数据前为 false。
     * 避免启动时先用默认主题/空列表渲染一帧再“闪”成真实内容。
     */
    val ready: StateFlow<Boolean> =
        combine(repo.itemsFlow, repo.paletteFlow, repo.darkModeFlow, repo.themeStyleFlow, repo.floatingNavFlow) { _, _, _, _, _ -> true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 临时 UI 状态：Snackbar 展示“撤销”时隐藏 FAB，避免挡住撤销按钮 */
    private val _fabSuppressed = MutableStateFlow(false)
    val fabSuppressed: StateFlow<Boolean> = _fabSuppressed.asStateFlow()

    fun setFabSuppressed(suppressed: Boolean) {
        _fabSuppressed.value = suppressed
    }

    /** 多选模式选中的食品 id 集合（v2.8 提升到 VM，供 MainActivity 批量操作栏与列表页共用） */
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

    /** 「撤销一次消耗」请求：列表页减少数量后，供 MainActivity 弹撤销 Snackbar。 */
    data class UndoRequest(val itemId: String, val consumptionId: String)

    private val _undoRequest = MutableStateFlow<UndoRequest?>(null)
    val undoRequest: StateFlow<UndoRequest?> = _undoRequest.asStateFlow()

    fun consumeUndoRequest() {
        _undoRequest.value = null
    }

    /** 撤销最近一次减少消耗：删消耗记录 + 数量回滚。 */
    fun undoConsumption(request: UndoRequest) = viewModelScope.launch {
        repo.undoConsumption(request.itemId, request.consumptionId)
    }

    /** 删除消耗记录后的「撤销」状态：保存被删记录，供恢复。 */
    private val _deletedConsumption = MutableStateFlow<ConsumptionRecord?>(null)
    val deletedConsumption: StateFlow<ConsumptionRecord?> = _deletedConsumption.asStateFlow()

    fun consumeDeletedConsumption() {
        _deletedConsumption.value = null
    }

    /** 删除单条消耗记录（修正统计），并记录被删内容供撤销。 */
    fun deleteConsumption(id: String) = viewModelScope.launch {
        val record = consumption.value.firstOrNull { it.id == id } ?: return@launch
        repo.deleteConsumption(id)
        _deletedConsumption.value = record
    }

    /** 撤销删除消耗记录：重新插回。必须传入 collect 时拿到的 record——consume 会先把 Flow 置空。 */
    fun undoDeleteConsumption(record: ConsumptionRecord) = viewModelScope.launch {
        repo.addConsumption(record)
        if (_deletedConsumption.value?.id == record.id) {
            _deletedConsumption.value = null
        }
    }

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            // 安全迁移：旧版明文密码 → Keystore 加密密文
            repo.migratePlaintextPassword()
            // 迁移：旧消耗记录补 id（供删除/撤销定位）
            repo.migrateConsumptionIds()
            // 启动时清理孤儿封面图片（未被库存/归档引用的文件）
            val referenced = buildSet {
                repo.itemsFlow.first().forEach { if (it.photoPath.isNotBlank()) add(it.photoPath) }
                repo.archiveFlow.first().forEach { if (it.item.photoPath.isNotBlank()) add(it.item.photoPath) }
            }
            cleanupOrphanCovers(getApplication(), referenced)
            // 自动同步：到期且凭据完整时静默上传
            maybeAutoSync()
        }
    }

    /** 自动同步消息（供 UI Snackbar 展示，消费后置空） */
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
        val result = NutstoreSync.upload(account, password, repo.buildBackupJson())
        if (result.isSuccess) {
            repo.setLastAutoSyncEpochDay(today)
            val time = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            repo.setLastSync("自动同步于 $time")
            _autoSyncMessage.value = "已自动同步到坚果云 ☁️"
        }
        // 失败静默忽略，下次启动重试；不打扰用户
    }

    fun setAutoSyncDays(days: Int) = viewModelScope.launch { repo.setAutoSyncDays(days) }

    fun upsert(item: FoodItem) = viewModelScope.launch { repo.upsert(item) }

    fun archive(id: String, reason: ArchiveReason) =
        viewModelScope.launch { repo.archiveItems(setOf(id), reason) }

    fun archiveBatch(ids: Set<String>, reason: ArchiveReason) =
        viewModelScope.launch { repo.archiveItems(ids, reason) }

    fun restoreArchivedBatch(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { repo.restoreArchived(it) }
    }

    /** 恢复单条归档；回调参数 merged = 是否与现有库存合并（同名同生产日期去重）。 */
    fun restoreArchivedSmart(id: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(repo.restoreArchived(id))
    }

    fun cleanExpired() = viewModelScope.launch {
        val ids = items.value.filter { it.daysLeft < 0 }.map { it.id }.toSet()
        repo.archiveItems(ids, ArchiveReason.EXPIRED)
    }

    fun restoreArchived(id: String) = viewModelScope.launch { repo.restoreArchived(id) }


    fun deleteArchived(id: String) = viewModelScope.launch { repo.deleteArchived(id) }

    fun clearArchive() = viewModelScope.launch { repo.clearArchive() }

    /**
     * 调整数量；吃完（减到 0）时仓库层会自动归档。
     * @param onAutoArchived 自动归档发生时回调（用于 UI 提示）
     * @param withUndo 减少时是否暴露「撤销」请求（列表页步进器减号用，详情页吃掉一份走 consumeOne 不用）
     */
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

    // ---- 分类管理 ----

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

    // ---- 位置管理 ----

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

    suspend fun buildBackupJson(): String = repo.buildBackupJson()

    suspend fun importBackupJson(raw: String): Boolean = repo.importBackupJson(raw)

    // ---- 坚果云同步 ----

    fun saveNutstoreCredentials(account: String, password: String) =
        viewModelScope.launch { repo.setNutstoreCredentials(account, password) }

    /** 上传当前数据到坚果云。回调参数：成功与否、提示消息。 */
    fun syncUpload(onResult: (Boolean, String) -> Unit) = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            onResult(false, "请先填写并保存坚果云账号和应用密码")
            return@launch
        }
        _syncing.value = true
        val json = repo.buildBackupJson()
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

    // ---- 云端备份列表（恢复时选择版本） ----

    private val _cloudBackups = MutableStateFlow<List<CloudBackup>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackup>> = _cloudBackups.asStateFlow()

    private val _loadingBackups = MutableStateFlow(false)
    val loadingBackups: StateFlow<Boolean> = _loadingBackups.asStateFlow()

    /** 拉取云端备份列表，供用户选择恢复哪一份。 */
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

    /** 从坚果云下载指定备份并恢复（整体替换）。 */
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
