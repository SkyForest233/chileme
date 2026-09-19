package com.agon.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock

private val Context.dataStore by preferencesDataStore("pantry_store")

// ⚠️ 与 `RepositoryCore.kt` / 各领域文件里的 TAG 是同一个字符串的**副本**，刻意不提成包级共享常量：
// 本包已有 `BackupFile.kt` / `ImageStore.kt` 各自的文件级 private TAG，再放一个包级 internal TAG
// 可能与它们在同一文件作用域里撞名（本地没编译器，这种赌不划算）。
private const val TAG = "FoodRepository"

/**
 * 数据仓库。
 *
 * 两条构造路径：生产用 [FoodRepository] 的 `Context` + `Clock` 构造（App 私有 DataStore +
 * `filesDir/corrupt` 留档目录 + App 级时钟）；主构造是 `internal` 的「依赖显式版」，单测可以传
 * 一个临时文件上的 DataStore、临时留档目录与一个**固定时钟**，从而在**纯 JVM** 下测仓储的
 * 写入守卫与「跨零点」这类日期行为（不需要 Robolectric —— 少一个 SDK 模拟层，也少一份依赖）。
 *
 * **时钟为什么带默认值**（#5b）：默认值就是改造前的行为（系统时钟）⇒ 生产路径逐位不变，
 * 而且单测不传时钟也照样编译（既有的守卫单测就是两参构造）。生产唯一的构造点在 `AppContainer`，
 * 那里把 App 级时钟**显式**传进来 ⇒ 默认值这条路在生产上不会被走到；留着它只是免得每个
 * internal 构造的调用方都得写一个时钟。
 *
 * 仓库内部一律问 `clock` 要时间，不再直接向系统要（口径见 `tools/doc-metrics.sh` 的
 * 「数据层+VM 函数体硬调 now()」那条守卫）。⚠️ 这句刻意不写出工厂方法的连写形式：
 * 该脚本数调用点的正则**含注释**，散文里连写一次就把计数撑大一次。
 */
class FoodRepository internal constructor(
    internal val dataStore: DataStore<Preferences>,
    internal val corruptDir: File,
    internal val clock: Clock = Clock.systemDefaultZone(),
) {
    /** 生产路径：`pantry_store` DataStore + `filesDir/corrupt`，时钟由 App 容器注入。 */
    constructor(context: Context, clock: Clock) : this(
        context.dataStore,
        File(context.filesDir, "corrupt"),
        clock,
    )

    internal val json = Json { ignoreUnknownKeys = true }
    internal val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * 检测到数据损坏的 key 集合（如 "food_items"）。非空时 UI 应提示用户，
     * 且所有涉及该 key 的写操作都会被跳过，避免把损坏状态"洗"成空数据。
     */
    internal val _corruptedKeys = MutableStateFlow<Set<String>>(emptySet())
    val corruptedKeys: StateFlow<Set<String>> = _corruptedKeys.asStateFlow()

    /** 重型 key 的「原始串 → 解码结果」缓存，见 [rawFlow]。 */
    internal val decodeCache = DecodeCache()

    internal val itemsKey = stringPreferencesKey("food_items")
    internal val archiveKey = stringPreferencesKey("archived_items")
    internal val consumptionKey = stringPreferencesKey("consumption_records")
    internal val historyKey = stringPreferencesKey("history_entries")
    internal val thresholdsKey = stringPreferencesKey("category_thresholds")
    internal val categoriesKey = stringPreferencesKey("custom_categories")
    internal val locationsKey = stringPreferencesKey("custom_locations")
    internal val seededKey = booleanPreferencesKey("seeded")
    internal val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    internal val darkModeKey = intPreferencesKey("dark_mode")
    internal val paletteKey = stringPreferencesKey("palette")
    internal val themeStyleKey = stringPreferencesKey("theme_style")
    internal val floatingNavKey = booleanPreferencesKey("floating_nav")
    internal val nutstoreAccountKey = stringPreferencesKey("nutstore_account")
    internal val nutstorePasswordKey = stringPreferencesKey("nutstore_password")
    internal val nutstorePasswordEncKey = stringPreferencesKey("nutstore_password_enc")
    internal val lastSyncKey = stringPreferencesKey("last_sync_time")
    internal val autoSyncDaysKey = intPreferencesKey("auto_sync_days")
    internal val lastAutoSyncEpochDayKey = stringPreferencesKey("last_auto_sync_epoch_day")

    // ---- 解码 ----
    //
    // 「用户资产型」key（items / archived / consumption / history）走三态 Decoded，
    // 解析失败时拒绝写入并留档取证；
    // 「配置型」key（thresholds / categories / locations）丢失可重设，维持回落默认值的旧行为。
    //
    // `decodeStrict` 本体在 `RepositoryCore.kt`（共用底座）；下面 7 个是各领域 key 的具名包装。
    internal fun decodeItems(raw: String?): Decoded<List<FoodItem>> =
        decodeStrict("food_items", raw)

    internal fun decodeArchive(raw: String?): Decoded<List<ArchivedItem>> =
        decodeStrict("archived_items", raw)

    internal fun decodeConsumption(raw: String?): Decoded<List<ConsumptionRecord>> =
        decodeStrict("consumption_records", raw)

    internal fun decodeHistory(raw: String?): Decoded<List<HistoryEntry>> =
        decodeStrict("history_entries", raw)

    // 配置型：解析失败回落默认值即可，不阻断写入。
    internal fun decodeThresholds(raw: String?): Map<String, Int> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    internal fun decodeCategories(raw: String?): List<CategoryDef> =
        raw?.let { runCatching { json.decodeFromString<List<CategoryDef>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: DefaultCategories

    internal fun decodeLocations(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: DefaultLocations

    // ---- 对外读取流 ----
    //
    // 三态解码 / 损坏留档 / 读取兜底（resilientRead / rawFlow / lightFlow）与留档目录裁剪
    // 都在 `RepositoryCore.kt`：那是共用底座，不属于任何单一领域。

    val itemsFlow: Flow<List<FoodItem>> =
        rawFlow(itemsKey) { decodeItems(it).orElse(emptyList()) }

    val archiveFlow: Flow<List<ArchivedItem>> =
        rawFlow(archiveKey) { decodeArchive(it).orElse(emptyList()) }

    val consumptionFlow: Flow<List<ConsumptionRecord>> =
        rawFlow(consumptionKey) { decodeConsumption(it).orElse(emptyList()) }

    val historyFlow: Flow<List<HistoryEntry>> =
        rawFlow(historyKey) { decodeHistory(it).orElse(emptyList()) }

    val thresholdsFlow: Flow<Map<String, Int>> =
        rawFlow(thresholdsKey, ::decodeThresholds)

    val categoriesFlow: Flow<List<CategoryDef>> =
        rawFlow(categoriesKey, ::decodeCategories)

    val locationsFlow: Flow<List<String>> =
        rawFlow(locationsKey, ::decodeLocations)

    val dynamicColorFlow: Flow<Boolean> =
        lightFlow("dynamic_color", fallback = false) { it[dynamicColorKey] ?: false }

    val darkModeFlow: Flow<Int> =
        lightFlow("dark_mode", fallback = 0) { it[darkModeKey] ?: 0 }

    val paletteFlow: Flow<String> =
        lightFlow("palette", fallback = "MINT") { it[paletteKey] ?: "MINT" }

    val themeStyleFlow: Flow<String> =
        lightFlow("theme_style", fallback = "MATERIAL3") { it[themeStyleKey] ?: "MATERIAL3" }

    val floatingNavFlow: Flow<Boolean> =
        lightFlow("floating_nav", fallback = true) { it[floatingNavKey] ?: true }

    val nutstoreAccountFlow: Flow<String> =
        lightFlow("nutstore_account", fallback = "") { it[nutstoreAccountKey] ?: "" }

    /**
     * 密码仅以 Keystore 加密密文存储；读取时解密。
     * 兼容迁移：若发现旧版明文 key 尚存，优先读明文（随后 seedIfNeeded/save 会完成迁移并抹除明文）。
     *
     * 解密是 Keystore 操作（非平凡开销），先按密文去重再切到 Default 线程，
     * 避免每次 DataStore 重发都在主线程做一次 AES-GCM。
     */
    val nutstorePasswordFlow: Flow<String> = nutstoreCredentialKeysFlow()
        .map { (plain, enc) ->
            plain?.takeIf { it.isNotBlank() }
                ?: enc?.let { SecureStore.decrypt(it) }
                ?: ""
        }
        .flowOn(Dispatchers.Default)

    /**
     * 云同步凭据是否已失效：存在密文但解不开（典型场景——换设备后恢复了云备份，
     * 而 Keystore 密钥不跨设备）。UI 据此提示用户重新填写应用密码，
     * 避免用户面对一个"看起来已配置、却永远同步失败"的账号。
     */
    val nutstoreCredentialBrokenFlow: Flow<Boolean> = nutstoreCredentialKeysFlow()
        .map { (plain, enc) ->
            plain.isNullOrBlank() && !enc.isNullOrBlank() && SecureStore.decrypt(enc) == null
        }
        .flowOn(Dispatchers.Default)

    /**
     * 密码是否以「未加密明文」形式落在 DataStore 里（Keystore 不可用时的极端回退）。
     * 功能可用但安全性降级，UI 必须明确告知用户；下次启动 [migratePlaintextPassword] 会重试加密。
     */
    val nutstorePlaintextFallbackFlow: Flow<Boolean> = nutstoreCredentialKeysFlow()
        .map { (plain, _) -> !plain.isNullOrBlank() }

    /**
     * 凭据两个 key 的原始值（明文待迁移 / 密文），已做读兜底与去重。
     * 上面两个 flow 共用它，避免各自重复一遍 resilientRead 与解密去重逻辑。
     */
    private fun nutstoreCredentialKeysFlow(): Flow<Pair<String?, String?>> =
        resilientRead(
            keyName = "nutstore_password",
            fallback = Pair<String?, String?>(null, null),
        ) { prefs ->
            prefs[nutstorePasswordKey] to prefs[nutstorePasswordEncKey]
        }.distinctUntilChanged()

    val lastSyncFlow: Flow<String> =
        lightFlow("last_sync_time", fallback = "") { it[lastSyncKey] ?: "" }

    /** 自动同步间隔（天）；0 = 关闭自动同步 */
    val autoSyncDaysFlow: Flow<Int> =
        lightFlow("auto_sync_days", fallback = 0) { it[autoSyncDaysKey] ?: 0 }

    val lastAutoSyncEpochDayFlow: Flow<Long> =
        lightFlow("last_auto_sync_epoch_day", fallback = 0L) {
            it[lastAutoSyncEpochDayKey]?.toLongOrNull() ?: 0L
        }

    /** 资产型 key 的名字 → Preferences.Key，供 [discardCorrupt] 按名字删除。 */
    private val assetKeysByName: Map<String, Preferences.Key<String>> by lazy {
        mapOf(
            itemsKey.name to itemsKey,
            archiveKey.name to archiveKey,
            consumptionKey.name to consumptionKey,
            historyKey.name to historyKey,
        )
    }

    /**
     * 放弃处于损坏态的数据：删除该 key 在 DataStore 中的内容并解除损坏标记，
     * 让相关写入恢复正常。
     *
     * 这是用户显式确认的破坏性操作（UI 有二次确认）。原始串不会丢——[markCorrupt]
     * 已把首次发现损坏时的原文留档到 `filesDir/corrupt/`，那个目录本方法**不动**。
     * 只作用于「用户资产型」key；配置型 key 本就不会进入损坏态。
     */
    suspend fun discardCorrupt(keys: Set<String>) {
        val targets = keys.mapNotNull { assetKeysByName[it] }
        if (targets.isEmpty()) return
        dataStore.edit { prefs -> targets.forEach { prefs.remove(it) } }
        _corruptedKeys.update { it - keys }
        Log.w(TAG, "已放弃损坏数据：${targets.joinToString { it.name }}（原文留档仍在 filesDir/corrupt/）")
    }
}
