package com.agon.app.viewmodel

import android.app.Application
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
// ↓ #5c 起仓库的领域函数搬到了各自的领域文件（同包 internal 扩展函数）⇒ 跨包调用要逐个 import
import com.agon.app.data.CloudBackup
import com.agon.app.data.LocalSnapshot
import com.agon.app.data.toHistoryEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        // 启动编排整段搬到同包 `AppViewModelStartup.kt` 的 `runPantryStartup()`（#11f）：那段的正确性
        // 是**顺序**（seed 最先 → 凭据搬家先于明文加密 → 封面清理受损坏态门拦住），单独成文才看得见。
        // 类里刻意不留转发：成员会遮蔽扩展，`fun runPantryStartup() = runPantryStartup()` 是无限递归。
        viewModelScope.launch { runPantryStartup() }
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
