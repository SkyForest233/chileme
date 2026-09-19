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

/**
 * **业务数据**（库存/归档/消耗/历史/设置/统计）：随 Android 系统备份与换机直传走。
 * 2026-09-19（M1-1）起不再被备份规则排除 —— 用户换机/重装后库存能自己回来。
 */
private val Context.dataStore by preferencesDataStore("pantry_store")

/**
 * **凭据**（坚果云账号 + 应用密码的密文与明文回退）：**独立一个 DataStore 文件**，被两条备份通道整体排除。
 *
 * 为什么要拆：此前凭据三件套与全部业务数据同住 `pantry_store` 一个文件，而 Keystore 密钥不跨设备 ⇒ 备份过去也解不开，
 * 于是两份备份规则直接 `<exclude path="datastore/" />` 把**整个 DataStore 目录**排除了 ——
 * 结果是"为了护一个密钥，把全部用户数据排除在备份之外"。
 * 官方 DataStore 文档给的正是另一条路：
 * 「若 DataStore 同时含非敏感偏好与敏感数据，把它们**拆成不同的 DataStore 文件**，再按文件配
 * `res/xml/data_extraction_rules.xml`」。拆完之后：业务数据进备份，只排除 `credentials_store.preferences_pb`。
 * 顺带解掉原来"换机时 `covers/` 进得来、库存 JSON 进不来 ⇒ `cleanupOrphanCovers()` 把封面全删掉"那处不一致
 * （现在两边同时到位）。
 *
 * ⚠️ **坚果云三个 key 只许住在这里**：明文回退（`nutstore_password`）也写本文件 ——
 * 业务数据那份文件是进备份的，一个密钥都不该被上传到用户 Google 账号里。`CredentialsStoreTest` 钉住这点。
 */
private val Context.credentialsDataStore by preferencesDataStore("credentials_store")

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
 * 凭据的 DataStore 是主构造的第 4 个参数、默认回落到 `dataStore`（见下面的注释）。
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
    /**
     * 凭据专用 DataStore（`credentials_store.preferences_pb`，被备份规则排除）。
     *
     * **默认值 = `dataStore`**，刻意如此：单测里不传它就是"两个文件退化成同一个临时文件"，
     * 既不用改任何既有测试的构造调用，也能照常验证凭据读写与迁移的行为语义；
     * 生产唯一的构造点在 `AppContainer` ⇒ 走的是下面那个 `Context` 构造，两个文件是真的分开。
     */
    internal val credentialsStore: DataStore<Preferences> = dataStore,
) {
    /** 生产路径：`pantry_store` + `credentials_store` 两个 DataStore + `filesDir/corrupt`，时钟由 App 容器注入。 */
    constructor(context: Context, clock: Clock) : this(
        context.dataStore,
        File(context.filesDir, "corrupt"),
        clock,
        context.credentialsDataStore,
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

    // ⚠️ 下面 3 个是**凭据 key，住在 `credentialsStore`（`credentials_store.preferences_pb`）里、不在 `dataStore`**：
    // 业务数据那份文件随系统备份走，密钥一个字都不能进。读写两侧都必须显式带 `store = credentialsStore`
    // （见 `nutstoreAccountFlow` / `nutstoreCredentialKeysFlow` 与 `FoodCredentials.kt` 的两个写入口），
    // 旧版把它们写在 `dataStore` 里，启动时由 `migrateLegacyCredentials()` 一次性搬走。
    internal val nutstoreAccountKey = stringPreferencesKey("nutstore_account")
    internal val nutstorePasswordKey = stringPreferencesKey("nutstore_password")
    internal val nutstorePasswordEncKey = stringPreferencesKey("nutstore_password_enc")
    internal val lastSyncKey = stringPreferencesKey("last_sync_time")
    internal val autoSyncDaysKey = intPreferencesKey("auto_sync_days")
    internal val lastAutoSyncEpochDayKey = stringPreferencesKey("last_auto_sync_epoch_day")

    /**
     * 归档溢出累计计数器（M1-3）：到目前为止有多少条归档因为超出 `ARCHIVE_RETENTION` 被挤掉。
     *
     * 为什么用**独立 key** 而不是"从归档长度反推"或"只在日志里说一句"：截断是 `take()`，被挤掉的条目
     * 当场就从列表里消失了 ⇒ 反推不出来；日志在用户设备上等于没有。它是只增不减的账，
     * 与 `corruptedKeys` 那套"数据出过问题就要留痕"的思路同源（`CLAUDE.md` §5.1）。
     */
    internal val archiveOverflowKey = intPreferencesKey("archive_overflow_total")

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
        lightFlow("nutstore_account", fallback = "", store = credentialsStore) { it[nutstoreAccountKey] ?: "" }

    /**
     * 密码仅以 Keystore 加密密文存储；读取时解密。
     * 兼容迁移：若发现旧版明文 key 尚存，优先读明文（随后 seedIfNeeded/save 会完成迁移并抹除明文）；
     * 旧版把凭据写在业务数据那份 DataStore 里，启动时先由 `migrateLegacyCredentials()` 搬到凭据文件。
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
     * 密码是否以「未加密明文」形式落在**凭据** DataStore 里（Keystore 不可用时的极端回退）。
     * 功能可用但安全性降级，UI 必须明确告知用户；下次启动 [migratePlaintextPassword] 会重试加密。
     *
     * 明文的落点刻意也是 `credentialsStore` 而不是 `dataStore`：业务数据那份文件随系统备份走，
     * 降级期间也不能把密钥推到用户的 Google 账号里。`CredentialsStoreTest` 钉住这条。
     */
    val nutstorePlaintextFallbackFlow: Flow<Boolean> = nutstoreCredentialKeysFlow()
        .map { (plain, _) -> !plain.isNullOrBlank() }

    /**
     * 凭据两个 key 的原始值（明文待迁移 / 密文），已做读兜底与去重。
     * 上面两个 flow 共用它，避免各自重复一遍 resilientRead 与解密去重逻辑。
     *
     * 读的是 [credentialsStore]（凭据文件），不是业务数据那份 —— 见 [nutstoreAccountKey] 上方那条警告。
     */
    private fun nutstoreCredentialKeysFlow(): Flow<Pair<String?, String?>> =
        resilientRead(
            keyName = "nutstore_password",
            fallback = Pair<String?, String?>(null, null),
            store = credentialsStore,
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

    /**
     * 累计有多少条归档被保留上限挤掉（M1-3，只增不减；口径与写入点见 `FoodArchive.kt` 的 `ARCHIVE_RETENTION`）。
     *
     * 目前**没有任何界面消费它** —— 这是刻意的：本轮只把"静默丢弃"变成"可计量的丢弃"，
     * 用户可见文案要单独一轮（`CLAUDE.md` §2 的文案规则）。读流按 §5.2 走 `lightFlow()` 收口，
     * 与其余轻量 key 同一条兜底路径。
     */
    val archiveOverflowFlow: Flow<Int> =
        lightFlow("archive_overflow_total", fallback = 0) { it[archiveOverflowKey] ?: 0 }

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
