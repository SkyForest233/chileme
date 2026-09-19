package com.agon.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.ChiliMeApp
import com.agon.app.data.ArchivedItem
import com.agon.app.data.CategoryDef
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.DefaultCategories
import com.agon.app.data.DefaultLocations
import com.agon.app.data.FoodItem
import com.agon.app.data.HistoryEntry
import com.agon.app.data.isAutoSyncDue
// ↓ #5c 起仓库的领域函数搬到了各自的领域文件（同包 internal 扩展函数）⇒ 跨包调用要逐个 import
import com.agon.app.data.seedIfNeeded
import com.agon.app.data.migrateConsumptionIds
import com.agon.app.data.buildBackupJson
import com.agon.app.data.setLastAutoSyncEpochDay
import com.agon.app.data.setLastSync
import com.agon.app.data.migrateLegacyCredentials
import com.agon.app.data.migratePlaintextPassword
import com.agon.app.data.CloudBackup
import com.agon.app.data.LocalSnapshot
import com.agon.app.data.LocalSnapshotStore
import com.agon.app.data.NutstoreSync
import com.agon.app.data.cleanupOrphanCovers
import com.agon.app.data.toHistoryEntry
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
import kotlinx.coroutines.launch

private const val TAG = "AppViewModel"

// ⚠️ 下面有几个成员是 `internal` 而不是 `private`（`repo` / `clock` / `emit` 与若干 `MutableStateFlow`）：
// #10b 把领域函数搬成**同包扩展函数**（`AppViewModel<领域>.kt`）之后，那些函数体要够得着它们
// —— `internal` 只是**模块内**可见，不出 App 模块，与 #5c 在 data 层的口径一致。
// 类里刻意**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决那条路）。
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

    internal val repo = container.repo

    /** 全 App 唯一的时钟（见 [com.agon.app.AppContainer.clock]）；本文件取时间一律走它。 */
    internal val clock = container.clock

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
    internal val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /**
     * 首帧门控：DataStore 真正发出第一次数据前为 false。
     * 避免启动时先用默认主题/空列表渲染一帧再“闪”成真实内容。
     */
    val ready: StateFlow<Boolean> =
        combine(repo.itemsFlow, repo.paletteFlow, repo.darkModeFlow, repo.themeStyleFlow, repo.floatingNavFlow) { _, _, _, _, _ -> true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 临时 UI 状态：Snackbar 展示“撤销”时隐藏 FAB，避免挡住撤销按钮 */
    internal val _fabSuppressed = MutableStateFlow(false)
    val fabSuppressed: StateFlow<Boolean> = _fabSuppressed.asStateFlow()

    /** 多选模式选中的食品 id 集合（v2.8 提升到 VM，供 MainActivity 批量操作栏与列表页共用） */
    internal val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    // ---- 一次性 UI 事件（路线图 #4a，2026-09-18）----
    //
    // 此前这里是 3 个「可空 StateFlow + 手工 consumeXxx()」（撤销消耗 / 删消耗记录 / 恢复归档），
    // 加上下面 maybeAutoSync 里的第 4 个（自动同步提示）。StateFlow 会向新订阅者重放最后一个值，
    // 所以「看过就撕」全靠每个收集点记得调 consume —— 4/4 都记得，但那是纪律不是机制。
    // 改成 Channel 后**接收即出队**，consume 函数与可空状态一起消失。
    // 设计理由（含「为什么是三条队列而不是一条」）见 viewmodel/UiEvent.kt 的类注释。
    internal val appShellEvents = Channel<UiEvent>(Channel.BUFFERED)
    internal val homeEvents = Channel<UiEvent>(Channel.BUFFERED)
    internal val consumptionLogEvents = Channel<UiEvent>(Channel.BUFFERED)
    internal val settingsEvents = Channel<UiEvent>(Channel.BUFFERED)

    /** 主壳覆盖层（`MainApp`）的事件：撤销消耗、恢复归档。 */
    val appShellUiEvents: Flow<UiEvent> = appShellEvents.receiveAsFlow()

    /** 首页 `AppScaffold` 的事件：自动同步完成提示。 */
    val homeUiEvents: Flow<UiEvent> = homeEvents.receiveAsFlow()

    /** 消耗记录页 `AppScaffold` 的事件：删除记录的撤销。 */
    val consumptionLogUiEvents: Flow<UiEvent> = consumptionLogEvents.receiveAsFlow()

    /** 设置页 `AppScaffold` 的事件：同步 / 还原的成败提示（#4c 之前是 4 个 `(Boolean, String)` 回调）。 */
    val settingsUiEvents: Flow<UiEvent> = settingsEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            // 凭据搬家（M1-1）：旧版把坚果云三个 key 存在业务数据那份 DataStore 里，现在它们住在
            // 被备份规则排除的 credentials_store。必须先搬，再跑明文加密迁移 —— 反过来的话
            // migratePlaintextPassword 会在（空的）新文件里找不到明文，明文就永远留在会进备份的那份文件里。
            repo.migrateLegacyCredentials()
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

    // ---- 本地快照管理 ----

    internal val _localSnapshots = MutableStateFlow<List<LocalSnapshot>>(emptyList())
    val localSnapshots: StateFlow<List<LocalSnapshot>> = _localSnapshots.asStateFlow()

    // ---- 云端备份列表（恢复时选择版本） ----

    internal val _cloudBackups = MutableStateFlow<List<CloudBackup>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackup>> = _cloudBackups.asStateFlow()

    internal val _loadingBackups = MutableStateFlow(false)
    val loadingBackups: StateFlow<Boolean> = _loadingBackups.asStateFlow()
}
