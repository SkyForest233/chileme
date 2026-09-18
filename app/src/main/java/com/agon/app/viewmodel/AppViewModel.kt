package com.agon.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.ChiliMeApp
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.BackupData
import com.agon.app.data.CategoryDef
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.DefaultCategories
import com.agon.app.data.DefaultLocations
import com.agon.app.data.FoodItem
import com.agon.app.data.HistoryEntry
import com.agon.app.data.isAutoSyncDue
// ↓ #5c 起仓库的领域函数搬到了各自的领域文件（同包 internal 扩展函数）⇒ 跨包调用要逐个 import
import com.agon.app.data.seedIfNeeded
import com.agon.app.data.upsert
import com.agon.app.data.updateLocationBatch
import com.agon.app.data.archiveItems
import com.agon.app.data.restoreArchived
import com.agon.app.data.restoreArchivedBatch
import com.agon.app.data.deleteArchived
import com.agon.app.data.clearArchive
import com.agon.app.data.OpFailure
import com.agon.app.data.toOpFailure
import com.agon.app.data.CloudBackup
import com.agon.app.data.LocalSnapshot
import com.agon.app.data.LocalSnapshotStore
import com.agon.app.data.NutstoreSync
import com.agon.app.data.QuantityChangeResult
import com.agon.app.data.cleanupOrphanCovers
import com.agon.app.data.daysLeft
import com.agon.app.data.toHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "AppViewModel"

/**
 * 「还没填凭据」这句话在 3 个入口（上传 / 拉列表 / 下载）各写了一遍，且字字相同 ⇒ 抽成常量。
 * 不是为省字：三处若各写一遍，改一处忘两处就会让用户在同一件事上看到三种说法。
 *
 * 放顶层而不是类内：Kotlin 的 `const val` 只能在**顶层或 companion object** 里，
 * 类体内直接写 `private const val` 编译不过（本文件的 [TAG] 同样是顶层，沿用这个惯例）。
 */
private const val NO_CREDENTIALS_MESSAGE = "请先填写并保存坚果云账号和应用密码"

class AppViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 仓库与时钟都从 App 级容器取（#5a 建容器 / #5b 接时钟）—— 本文件里**不再现场构造**，
     * 也**不再直接向系统要时间**。
     *
     * 这里用**硬转型**而不是 `as? … ?: FoodRepository(application)` 那种"兜底再 new 一个"：
     * 兜底会悄悄造出第二个仓库实例（各带一份损坏状态与解码缓存），比直接崩更难查。
     * 转型失败只可能是 `AndroidManifest.xml` 少了 `android:name=".ChiliMeApp"` 这类配置错，
     * 属于一启动就炸、原因明确的编程错误 ⇒ 让它响。
     *
     * ⚠️ 构造签名必须保持 `(Application)` 不变：**不能**加带默认值的第二参数 ——
     * `ViewModelProvider` 的默认工厂用反射找 `(Application)` 构造器，而 Kotlin 的默认参数
     * 只生成带 `DefaultConstructorMarker` 的合成构造器 ⇒ 反射找不到、运行时崩。
     * 所以时钟一类依赖一律从容器取，不从构造参数进（#5b 正是这么接的）。
     */
    private val container = (application as ChiliMeApp).container

    private val repo = container.repo

    /** 全 App 唯一的时钟（见 [com.agon.app.AppContainer.clock]）；本文件取时间一律走它。 */
    private val clock = container.clock

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
        // 复用上面三个已经 stateIn 好的 StateFlow（2026-09-15）：此前这里又各收集了一遍
        // 仓库的冷流，同一份 JSON 在启动期被多解一次。
        combine(history, items, archived) { history, items, archived ->
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

    /**
     * 数据损坏告警：存在解析失败的用户资产 key。非空时相关写操作已被仓库层拒绝，
     * UI 应显著提示用户（原始串已留档到 filesDir/corrupt/）。
     */
    val corruptedKeys: StateFlow<Set<String>> = repo.corruptedKeys

    /** 云同步凭据已失效（有密文但解不开，典型为换设备后恢复了云备份）。 */
    val nutstoreCredentialBroken: StateFlow<Boolean> =
        repo.nutstoreCredentialBrokenFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 密码以未加密明文保存（Keystore 不可用时的极端回退）——设置页需明确提示。 */
    val nutstorePlaintextFallback: StateFlow<Boolean> =
        repo.nutstorePlaintextFallbackFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    // ---- 一次性 UI 事件（路线图 #4a，2026-09-18）----
    //
    // 此前这里是 3 个「可空 StateFlow + 手工 consumeXxx()」（撤销消耗 / 删消耗记录 / 恢复归档），
    // 加上下面 maybeAutoSync 里的第 4 个（自动同步提示）。StateFlow 会向新订阅者重放最后一个值，
    // 所以「看过就撕」全靠每个收集点记得调 consume —— 4/4 都记得，但那是纪律不是机制。
    // 改成 Channel 后**接收即出队**，consume 函数与可空状态一起消失。
    // 设计理由（含「为什么是三条队列而不是一条」）见 viewmodel/UiEvent.kt 的类注释。
    private val appShellEvents = Channel<UiEvent>(Channel.BUFFERED)
    private val homeEvents = Channel<UiEvent>(Channel.BUFFERED)
    private val consumptionLogEvents = Channel<UiEvent>(Channel.BUFFERED)
    private val settingsEvents = Channel<UiEvent>(Channel.BUFFERED)

    /** 主壳覆盖层（`MainApp`）的事件：撤销消耗、恢复归档。 */
    val appShellUiEvents: Flow<UiEvent> = appShellEvents.receiveAsFlow()

    /** 首页 `AppScaffold` 的事件：自动同步完成提示。 */
    val homeUiEvents: Flow<UiEvent> = homeEvents.receiveAsFlow()

    /** 消耗记录页 `AppScaffold` 的事件：删除记录的撤销。 */
    val consumptionLogUiEvents: Flow<UiEvent> = consumptionLogEvents.receiveAsFlow()

    /** 设置页 `AppScaffold` 的事件：同步 / 还原的成败提示（#4c 之前是 4 个 `(Boolean, String)` 回调）。 */
    val settingsUiEvents: Flow<UiEvent> = settingsEvents.receiveAsFlow()

    /** 一次性事件的**唯一发送点**：按 [UiEvent.surface] 分流到对应宿主的队列。 */
    private suspend fun emit(event: UiEvent) {
        when (event.surface) {
            UiSurface.AppShell -> appShellEvents.send(event)
            UiSurface.Home -> homeEvents.send(event)
            UiSurface.ConsumptionLog -> consumptionLogEvents.send(event)
            UiSurface.Settings -> settingsEvents.send(event)
        }
    }

    /** 撤销最近一次减少消耗：删消耗记录 + 数量回滚。 */
    fun undoConsumption(event: UiEvent.UndoConsumption) = viewModelScope.launch {
        repo.undoConsumption(event.itemId, event.consumptionId)
    }

    fun restoreArchivedWithUndo(entry: ArchivedItem) = viewModelScope.launch {
        val merged = repo.restoreArchived(entry.item.id)
        emit(UiEvent.UndoRestoreArchived(entry.item, entry.reason, merged))
    }

    /** 删除单条消耗记录（修正统计），并记下原位置供撤销插回。 */
    fun deleteConsumption(record: ConsumptionRecord) = viewModelScope.launch {
        val sorted = consumption.value.sortedByDescending { it.epochDay }
        // 优先按 id 精确定位；id 为 null 的旧记录按内容匹配，避免删除静默失效
        val index = sorted.indexOfFirst {
            if (record.id != null) it.id == record.id else it == record
        }
        val target = sorted.getOrNull(index) ?: return@launch
        repo.deleteConsumption(target)
        emit(UiEvent.UndoDeleteConsumption(target, index.coerceAtLeast(0)))
    }

    /** 撤销删除：按原下标插回，避免被提到列表最前。 */
    fun undoDeleteConsumption(record: ConsumptionRecord, index: Int) = viewModelScope.launch {
        repo.addConsumption(record, index)
        // 原先这里还有一句「若待处理的撤销事件正是这条记录就清空它」的防御性代码：
        // 收集端一直是「先 consume 再弹条」，弹条期间那个状态早已是 null，故那句永远不成立；
        // 改用 Channel 后事件接收即出队，也没有「待处理的事件」可清 ⇒ 一并删掉。
    }

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            // 安全迁移：旧版明文密码 → Keystore 加密密文
            repo.migratePlaintextPassword()
            // 迁移：旧消耗记录补 id（供删除/撤销定位）
            repo.migrateConsumptionIds()
            // 启动时清理孤儿封面图片（未被库存/归档引用的文件）。
            //
            // 关键：损坏态下**必须跳过**。items/archive 解码失败时 rawFlow 会回落空集
            // （这是读路径的预期行为），若照此清理，covers/ 下的文件会被全部当成孤儿删除——
            // 而图片无法从 corrupt/ 的 JSON 留档里恢复，等于把「保护数据」的机制变成
            // 「销毁数据」。（2026-09-15 修复）
            if (repo.corruptedKeys.value.isEmpty()) {
                val referenced = buildSet {
                    repo.itemsFlow.first().forEach { if (it.photoPath.isNotBlank()) add(it.photoPath) }
                    repo.archiveFlow.first().forEach { if (it.item.photoPath.isNotBlank()) add(it.item.photoPath) }
                }
                cleanupOrphanCovers(getApplication(), referenced)
            } else {
                Log.w(TAG, "检测到数据损坏（${repo.corruptedKeys.value}），跳过孤儿封面清理以免误删图片")
            }
            // 自动同步：到期且凭据完整时静默上传
            maybeAutoSync()
            // 本地滚动冷备：若今日尚无快照则静默保存一份
            maybeAutoSnapshot()
        }
    }

    private suspend fun maybeAutoSync() {
        val days = repo.autoSyncDaysFlow.first()
        if (days <= 0) return
        val account = repo.nutstoreAccountFlow.first()
        val password = repo.nutstorePasswordFlow.first()
        if (account.isBlank() || password.isBlank()) return
        val today = LocalDate.now(clock).toEpochDay()
        val last = repo.lastAutoSyncEpochDayFlow.first()
        // 判定抽成了纯函数（见 isAutoSyncDue 的注释）：这条跨零点边界此前长在 VM 里没法测
        if (!isAutoSyncDue(last, today, days)) return
        // 数据损坏时 buildBackupJson 抛异常：静默跳过本次自动同步，
        // 绝不能把残缺备份推上云端覆盖掉云端的完好版本。
        val payload = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
        val result = NutstoreSync.upload(account, password, payload, clock)
        if (result.isSuccess) {
            repo.setLastAutoSyncEpochDay(today)
            val time = java.time.LocalDateTime.now(clock)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            repo.setLastSync("自动同步于 $time")
            emit(UiEvent.Notice("已自动同步到坚果云 ☁️"))
        }
        // 失败静默忽略，下次启动重试；不打扰用户
    }

    private suspend fun maybeAutoSnapshot() {
        val snapshots = LocalSnapshotStore.listSnapshots(getApplication())
        val today = LocalDate.now(clock)
        // ⚠️ 这里两种时间来源相遇：`today` 来自注入的时钟，而快照文件的修改时刻按**系统时区**解读。
        // 生产上两者同一个时区（容器给的就是系统时区时钟）⇒ 行为与改造前逐位相同；
        // 但若哪天要单测这个函数并塞一个别的时区的固定时钟，这条比较会偏一天 —— 届时把下面
        // 的时区也改成从时钟取（clock.zone），别只改一半。
        val hasSnapshotToday = snapshots.any {
            Instant.ofEpochMilli(it.modifiedEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate() == today
        }
        if (!hasSnapshotToday) {
            val json = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
            LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock)
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

    /** 恢复单条归档；回调参数 merged = 是否与现有库存合并（同名同生产日期去重）。 */
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
            emit(UiEvent.UndoConsumption(id, result.consumptionId))
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

    fun updateLocationBatch(ids: Set<String>, newLocation: String) = viewModelScope.launch {
        repo.updateLocationBatch(ids, newLocation)
    }

    suspend fun buildBackupJson(): String = repo.buildBackupJson()

    suspend fun buildCsvExport(): String = repo.buildCsvExport()

    suspend fun importBackupJson(raw: String): Boolean = repo.importBackupJson(raw)

    /** 解析备份用于导入前预览（不改动数据）。非备份 / 畸形 JSON 返回 null。 */
    suspend fun previewBackup(raw: String): BackupData? = repo.previewBackup(raw)

    /**
     * 恢复类操作的公共前置步骤：留一份「操作前状态」本地快照，返回是否保存成功。
     *
     * 三条恢复路径（文件导入 / 坚果云整版本恢复 / 本地快照还原）都走它，
     * 保证任何一次整体替换之前都有一份可回退的快照。
     * 快照失败（例如当前数据本身已损坏、无法序列化）不阻断恢复——那种情况正是恢复的用途。
     */
    private suspend fun snapshotBeforeRestore(): Boolean = withContext(Dispatchers.IO) {
        runCatching { repo.buildBackupJson() }.getOrNull()
            ?.let { json -> LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock) != null }
            ?: false
    }

    /**
     * 导入备份的推荐入口：**先写一份本地快照兜底，再整体替换**。
     *
     * 导入是不可撤销的破坏性操作，此前点一下文件就直接覆盖。现在：
     * 1. 先 `buildBackupJson()` + `LocalSnapshotStore.saveSnapshot()` 留一份「导入前状态」，
     *    用户可在设置页「本地快照」里一键回到导入前；
     * 2. 再执行 [com.agon.app.data.FoodRepository.importBackupJson]。
     *    （这里写**全限定名**：`FoodRepository` 的 import 在 #5a 之后只剩这一处 KDoc 引用了，
     *    而本仓的口径是 import 只服务代码 —— `tools/kt-lexcheck.py` 会把"只被注释用着的 import"
     *    报成未使用，detekt 的 `UnusedImports` 又是关的（核查第 14 处），所以只能自己守。)
     *
     * 快照失败（例如当前数据本身已损坏、无法序列化）**不阻断导入**——那种情况正是导入的用途。
     *
     * 成败经 [UiEvent]（落点 [UiSurface.Settings]）报信：那三句话此前写在 `SettingsScreen` 里，
     * 由界面拿 `(ok, snapshotSaved)` 两个布尔拼出来 —— 与还原快照/云端恢复的文案是同一套句式，
     * 却分散在两个文件里。#4c 一并收到 VM，句式与用词逐字未改。
     */
    fun importBackupWithSnapshot(raw: String) =
        viewModelScope.launch {
            // 快照（含整份 JSON 序列化与落盘）与导入都放 IO 线程，避免主线程卡顿
            val snapshotSaved = snapshotBeforeRestore()
            if (snapshotSaved) loadLocalSnapshots()
            val ok = withContext(Dispatchers.IO) { repo.importBackupJson(raw) }
            if (ok) {
                emit(
                    UiEvent.Notice(
                        if (snapshotSaved) "导入成功，数据已恢复 ✅（已自动留存导入前快照）"
                        else "导入成功，数据已恢复 ✅（导入前快照未能保存）",
                        UiSurface.Settings,
                    ),
                )
            } else {
                emit(UiEvent.OpFailed(DataOp.ImportBackup, OpFailure.Other("导入失败：文件格式不正确")))
            }
        }

    // ---- 本地快照管理 ----

    private val _localSnapshots = MutableStateFlow<List<LocalSnapshot>>(emptyList())
    val localSnapshots: StateFlow<List<LocalSnapshot>> = _localSnapshots.asStateFlow()

    /**
     * 刷新本地快照列表。读取（含逐份解析 JSON 数条数）已下沉到 IO 线程，
     * 这里 fire-and-forget 地更新 UI 状态 —— 调用方无需等待。
     */
    fun loadLocalSnapshots() {
        viewModelScope.launch {
            _localSnapshots.value = LocalSnapshotStore.listSnapshots(getApplication())
        }
    }

    fun saveLocalSnapshot(onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
        val json = runCatching { repo.buildBackupJson() }.getOrNull()
        if (json != null) {
            LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock)
            loadLocalSnapshots()
            onDone?.invoke(true)
        } else {
            onDone?.invoke(false)
        }
    }

    /**
     * 从本地快照还原。
     *
     * 顺序很重要：**先读出目标快照内容，再写「还原前状态」快照**。
     * 反过来的话，快照份数已达上限（[LocalSnapshotStore.MAX_SNAPSHOTS] = 3）时，
     * 新写的那份会把要还原的旧快照挤掉（按修改时间淘汰），导致「点了还原却报找不到」。
     */
    fun restoreLocalSnapshot(fileName: String) = viewModelScope.launch {
        // 两条失败路径（读不出来 / 导不进去）今天是同一句话，故共用一个值 —— 写两遍就有改一处忘一处的风险。
        val corrupt = OpFailure.Other("快照文件损坏或无法还原")
        val raw = LocalSnapshotStore.readSnapshot(getApplication(), fileName)
        if (raw == null) {
            emit(UiEvent.OpFailed(DataOp.RestoreSnapshot, corrupt))
            return@launch
        }
        val snapshotSaved = snapshotBeforeRestore()
        if (snapshotSaved) loadLocalSnapshots()
        if (repo.importBackupJson(raw)) {
            loadLocalSnapshots()
            emit(
                UiEvent.Notice(
                    if (snapshotSaved) "已从本地快照还原数据 ✅（已自动留存还原前快照）"
                    else "已从本地快照还原数据 ✅（还原前快照未能保存）",
                    UiSurface.Settings,
                ),
            )
        } else {
            emit(UiEvent.OpFailed(DataOp.RestoreSnapshot, corrupt))
        }
    }

    // ---- 坚果云同步 ----

    fun saveNutstoreCredentials(account: String, password: String) =
        viewModelScope.launch { repo.setNutstoreCredentials(account, password) }

    /**
     * 上传当前数据到坚果云。成败经 [UiEvent]（落点 [UiSurface.Settings]）报信，不再要回调。
     *
     * 本地数据异常那条走 [OpFailure.Other] 而不是 `toOpFailure()`：它不是同步失败，
     * 用类型归类会把一个本地错误误报成"网络问题"。
     */
    fun syncUpload() = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            emit(UiEvent.OpFailed(DataOp.Upload, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
            return@launch
        }
        _syncing.value = true
        // 同上：损坏态下拒绝上传，避免残缺备份覆盖云端完好版本。
        val json = runCatching { repo.buildBackupJson() }.getOrElse {
            _syncing.value = false
            emit(UiEvent.OpFailed(DataOp.Upload, OpFailure.Other(it.message ?: "数据异常，已取消上传")))
            return@launch
        }
        val result = NutstoreSync.upload(account, password, json, clock)
        _syncing.value = false
        result.fold(
            onSuccess = {
                val time = java.time.LocalDateTime.now(clock)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                repo.setLastSync("上传于 $time")
                emit(UiEvent.Notice("已上传到坚果云 ☁️", UiSurface.Settings))
            },
            onFailure = { emit(UiEvent.OpFailed(DataOp.Upload, it.toOpFailure("上传失败"))) },
        )
    }

    // ---- 云端备份列表（恢复时选择版本） ----

    private val _cloudBackups = MutableStateFlow<List<CloudBackup>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackup>> = _cloudBackups.asStateFlow()

    private val _loadingBackups = MutableStateFlow(false)
    val loadingBackups: StateFlow<Boolean> = _loadingBackups.asStateFlow()

    /**
     * 拉取云端备份列表，供用户选择恢复哪一份。
     *
     * 三种结果各走各路（改造前它们被压进一个 Boolean）：
     * - **非空** ⇒ 不发事件：列表由 [cloudBackups] 这个 `StateFlow` 驱动，选择器保持打开
     *   （改造前是 `onResult(true, "")`，界面拿到空字符串什么也不做）；
     * - **空** ⇒ [UiEvent.CloudBackupsEmpty]：请求是成功的，只是没东西可恢复；
     * - **失败** ⇒ [UiEvent.OpFailed] 带分类。
     * 后两种都要关掉选择器 —— 由收集端按事件类型决定，见 `SettingsScreen`。
     */
    fun loadCloudBackups() = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            emit(UiEvent.OpFailed(DataOp.ListBackups, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
            return@launch
        }
        _loadingBackups.value = true
        val result = NutstoreSync.listBackups(account, password)
        _loadingBackups.value = false
        result.fold(
            onSuccess = { list ->
                _cloudBackups.value = list
                if (list.isEmpty()) emit(UiEvent.CloudBackupsEmpty("云端暂无备份，请先上传"))
            },
            onFailure = { emit(UiEvent.OpFailed(DataOp.ListBackups, it.toOpFailure("获取备份列表失败"))) },
        )
    }

    /** 从坚果云下载指定备份并恢复（整体替换）。成败经 [UiEvent]（落点 [UiSurface.Settings]）报信。 */
    fun syncDownload(fileName: String) = viewModelScope.launch {
        val account = nutstoreAccount.value
        val password = nutstorePassword.value
        if (account.isBlank() || password.isBlank()) {
            emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
            return@launch
        }
        _syncing.value = true
        val result = NutstoreSync.download(account, password, fileName)
        _syncing.value = false
        val raw = result.getOrNull()
        if (raw == null) {
            val failure = result.exceptionOrNull()?.toOpFailure("下载失败") ?: OpFailure.Other("下载失败")
            emit(UiEvent.OpFailed(DataOp.Download, failure))
            return@launch
        }
        // 与「文件导入」同一套前置校验：必须含 items 键，否则拒绝覆盖（防「合法空备份」清空数据）。
        if (repo.previewBackup(raw) == null) {
            emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other("云端备份格式不正确")))
            return@launch
        }
        val snapshotSaved = snapshotBeforeRestore()
        if (snapshotSaved) loadLocalSnapshots()
        if (repo.importBackupJson(raw)) {
            val time = java.time.LocalDateTime.now(clock)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            repo.setLastSync("恢复于 $time")
            emit(
                UiEvent.Notice(
                    if (snapshotSaved) "已从坚果云恢复数据 ✅（已自动留存恢复前快照）"
                    else "已从坚果云恢复数据 ✅（恢复前快照未能保存）",
                    UiSurface.Settings,
                ),
            )
        } else {
            emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other("云端备份格式不正确")))
        }
    }

    /**
     * 放弃处于损坏态的数据（UI 二次确认后调用）：删除该 key 的内容并解除损坏标记，
     * 让相关写入恢复正常。原文留档保留在 filesDir/corrupt/。
     */
    fun discardCorruptData() = viewModelScope.launch {
        repo.discardCorrupt(repo.corruptedKeys.value)
    }
}
