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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

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
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * 检测到数据损坏的 key 集合（如 "food_items"）。非空时 UI 应提示用户，
     * 且所有涉及该 key 的写操作都会被跳过，避免把损坏状态"洗"成空数据。
     */
    internal val _corruptedKeys = MutableStateFlow<Set<String>>(emptySet())
    val corruptedKeys: StateFlow<Set<String>> = _corruptedKeys.asStateFlow()

    /** 重型 key 的「原始串 → 解码结果」缓存，见 [rawFlow]。 */
    internal val decodeCache = DecodeCache()

    private val itemsKey = stringPreferencesKey("food_items")
    private val archiveKey = stringPreferencesKey("archived_items")
    private val consumptionKey = stringPreferencesKey("consumption_records")
    private val historyKey = stringPreferencesKey("history_entries")
    private val thresholdsKey = stringPreferencesKey("category_thresholds")
    private val categoriesKey = stringPreferencesKey("custom_categories")
    private val locationsKey = stringPreferencesKey("custom_locations")
    private val seededKey = booleanPreferencesKey("seeded")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val darkModeKey = intPreferencesKey("dark_mode")
    private val paletteKey = stringPreferencesKey("palette")
    private val themeStyleKey = stringPreferencesKey("theme_style")
    private val floatingNavKey = booleanPreferencesKey("floating_nav")
    private val nutstoreAccountKey = stringPreferencesKey("nutstore_account")
    private val nutstorePasswordKey = stringPreferencesKey("nutstore_password")
    private val nutstorePasswordEncKey = stringPreferencesKey("nutstore_password_enc")
    private val lastSyncKey = stringPreferencesKey("last_sync_time")
    private val autoSyncDaysKey = intPreferencesKey("auto_sync_days")
    private val lastAutoSyncEpochDayKey = stringPreferencesKey("last_auto_sync_epoch_day")

    // ---- 解码 ----
    //
    // 「用户资产型」key（items / archived / consumption / history）走三态 Decoded，
    // 解析失败时拒绝写入并留档取证；
    // 「配置型」key（thresholds / categories / locations）丢失可重设，维持回落默认值的旧行为。
    //
    // `decodeStrict` 本体在 `RepositoryCore.kt`（共用底座）；下面 7 个是各领域 key 的具名包装。
    private fun decodeItems(raw: String?): Decoded<List<FoodItem>> =
        decodeStrict("food_items", raw)

    private fun decodeArchive(raw: String?): Decoded<List<ArchivedItem>> =
        decodeStrict("archived_items", raw)

    private fun decodeConsumption(raw: String?): Decoded<List<ConsumptionRecord>> =
        decodeStrict("consumption_records", raw)

    private fun decodeHistory(raw: String?): Decoded<List<HistoryEntry>> =
        decodeStrict("history_entries", raw)

    // 配置型：解析失败回落默认值即可，不阻断写入。
    private fun decodeThresholds(raw: String?): Map<String, Int> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private fun decodeCategories(raw: String?): List<CategoryDef> =
        raw?.let { runCatching { json.decodeFromString<List<CategoryDef>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: DefaultCategories

    private fun decodeLocations(raw: String?): List<String> =
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

    /** 启动时迁移：若存在旧版明文密码，加密后写入新 key 并删除明文。 */
    suspend fun migratePlaintextPassword() {
        dataStore.edit { prefs ->
            val plain = prefs[nutstorePasswordKey]
            if (!plain.isNullOrBlank()) {
                val enc = SecureStore.encrypt(plain)
                if (enc != null) {
                    prefs[nutstorePasswordEncKey] = enc
                    prefs.remove(nutstorePasswordKey)
                }
            }
        }
    }

    /** 启动时迁移：给无 id 的旧消耗记录补 UUID，供删除/撤销精确定位。 */
    suspend fun migrateConsumptionIds() {
        dataStore.edit { prefs ->
            val decoded = decodeConsumption(prefs[consumptionKey])
            if (isCorrupt(decoded)) return@edit
            val records = decoded.orElse(emptyList())
            if (records.any { it.id == null }) {
                prefs[consumptionKey] = json.encodeToString(
                    records.map { if (it.id == null) it.copy(id = UUID.randomUUID().toString()) else it }
                )
            }
        }
    }

    suspend fun seedIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[seededKey] == true) return@edit
            // 库存 key 损坏时绝不种子化：否则会把损坏数据直接覆盖成 8 条示例。
            if (isCorrupt(decodeItems(prefs[itemsKey]))) return@edit
            val today = LocalDate.now(clock).toEpochDay()
            fun id() = UUID.randomUUID().toString()
            val seed = listOf(
                FoodItem(id(), "鲜牛奶", "DAIRY", 2, "瓶", today - 12, 15, location = "冰箱"),
                FoodItem(id(), "草莓酸奶", "DAIRY", 4, "杯", today - 25, 21, location = "冰箱"),
                FoodItem(id(), "每日混合坚果", "NUTS", 1, "袋", today - 175, 180, location = "零食柜"),
                FoodItem(id(), "芒果干", "FRUIT", 2, "袋", today - 85, 90, location = "零食柜"),
                FoodItem(id(), "奥利奥夹心饼干", "SNACK", 3, "包", today - 60, 270, location = "零食柜"),
                FoodItem(id(), "冰红茶", "DRINK", 6, "瓶", today - 100, 365, location = "储物间"),
                FoodItem(id(), "红烧牛肉面", "INSTANT", 5, "桶", today - 30, 240, location = "厨房"),
                FoodItem(id(), "大白兔奶糖", "CANDY", 1, "包", today - 200, 365, location = "零食柜"),
            )
            prefs[itemsKey] = json.encodeToString(seed)
            prefs[seededKey] = true
        }
    }

    /**
     * 新增/编辑一条库存。
     *
     * **守卫按 key 粒度**（2026-09-15）：库存是本次写入的主数据，损坏时拒绝覆盖；
     * 「录入历史」只是输入联想的辅助数据，它损坏时**不再连带锁死新增/编辑食品**，
     * 而是跳过历史写入并记日志（原始串保持不动，留档仍在 filesDir/corrupt/）。
     */
    suspend fun upsert(item: FoodItem) {
        dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            if (isCorrupt(itemsDecoded)) return@edit
            val current = itemsDecoded.orElse(emptyList())
            val updated = if (current.any { it.id == item.id }) {
                current.map { if (it.id == item.id) item else it }
            } else {
                listOf(item) + current
            }
            prefs[itemsKey] = json.encodeToString(updated)

            val historyDecoded = decodeHistory(prefs[historyKey])
            if (isCorrupt(historyDecoded)) {
                Log.w(TAG, "history_entries 损坏：本次跳过录入历史写入（库存已正常保存）")
                return@edit
            }
            val history = historyDecoded.orElse(emptyList())
            val entry = HistoryEntry(
                name = item.name,
                category = item.category,
                unit = item.unit,
                shelfLifeDays = item.shelfLifeDays,
                location = item.location,
                coverText = item.coverText,
                note = item.note,
                expiringThresholdDays = item.expiringThresholdDays,
            )
            val newHistory = (listOf(entry) + history.filterNot { it.name == entry.name }).take(50)
            prefs[historyKey] = json.encodeToString(newHistory)
        }
    }

    suspend fun archiveItems(ids: Set<String>, reason: ArchiveReason) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(itemsDecoded, archiveDecoded)) return@edit
            val current = itemsDecoded.orElse(emptyList())
            val (toArchive, keep) = current.partition { it.id in ids }
            if (toArchive.isEmpty()) return@edit
            val today = LocalDate.now(clock).toEpochDay()
            val archive = archiveDecoded.orElse(emptyList())
            val newArchive = (toArchive.map { ArchivedItem(it, today, reason) } + archive).take(200)
            prefs[itemsKey] = json.encodeToString(keep)
            prefs[archiveKey] = json.encodeToString(newArchive)
        }
    }

    /**
     * 从归档恢复。去重策略：
     * - 库存中已有同 ID → 直接从归档移除（重复恢复/滑删撤销竞态）
     * - 库存中已有同名且同生产日期的记录 → 合并数量到现有记录（至少 +1），不产生重复条目
     * - 否则 → 作为新记录插入（数量为 0 的已吃完记录恢复为 1）
     * @return 若发生了合并返回 true（用于 UI 提示）
     */
    suspend fun restoreArchived(id: String): Boolean {
        var merged = false
        dataStore.edit { prefs ->
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            val itemsDecoded = decodeItems(prefs[itemsKey])
            if (isCorrupt(archiveDecoded, itemsDecoded)) return@edit
            val archive = archiveDecoded.orElse(emptyList())
            val entry = archive.find { it.item.id == id } ?: return@edit
            val items = itemsDecoded.orElse(emptyList())
            val plan = planRestore(entry, items)
            merged = plan.merged
            prefs[itemsKey] = json.encodeToString(plan.newItems)
            prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == id })
        }
        return merged
    }

    /**
     * 批量恢复归档（一次性 edit，原子化）。旧实现是 N 次独立 `restoreArchived`，
     * 每次 edit 都会重发整份 Preferences 并全量解码；批量较大时非原子且性能差。
     * 语义与 `planRestore` 一致（同 ID 去重 / 同名同生产日期合并 / 数量 0 恢复为 1）。
     */
    suspend fun restoreArchivedBatch(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            val itemsDecoded = decodeItems(prefs[itemsKey])
            if (isCorrupt(archiveDecoded, itemsDecoded)) return@edit
            var items = itemsDecoded.orElse(emptyList())
            var archive = archiveDecoded.orElse(emptyList())
            for (id in ids) {
                val entry = archive.find { it.item.id == id } ?: continue
                val plan = planRestore(entry, items)
                items = plan.newItems
                archive = archive.filterNot { it.item.id == id }
            }
            prefs[itemsKey] = json.encodeToString(items)
            prefs[archiveKey] = json.encodeToString(archive)
        }
    }

    suspend fun deleteArchived(id: String) {
        dataStore.edit { prefs ->
            val decoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(decoded)) return@edit
            val archive = decoded.orElse(emptyList())
            prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == id })
        }
    }

    /**
     * 清空归档。这是用户显式发起的破坏性操作，即便归档 key 已损坏也应允许执行
     * （清空本身就是要丢弃这些数据），故不加损坏守卫。
     */
    suspend fun clearArchive() {
        dataStore.edit { prefs ->
            prefs[archiveKey] = json.encodeToString(emptyList<ArchivedItem>())
            _corruptedKeys.update { it - "archived_items" }
        }
    }

    /**
     * 批量修改食品的存放位置。
     */
    suspend fun updateLocationBatch(ids: Set<String>, newLocation: String) {
        if (ids.isEmpty()) return
        val trimmed = newLocation.trim()
        dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            if (isCorrupt(itemsDecoded)) return@edit
            val items = itemsDecoded.orElse(emptyList())
            val updated = items.map {
                if (it.id in ids) it.copy(location = trimmed) else it
            }
            prefs[itemsKey] = json.encodeToString(updated)

            if (trimmed.isNotBlank()) {
                val locs = decodeLocations(prefs[locationsKey])
                if (trimmed !in locs) {
                    prefs[locationsKey] = json.encodeToString(locs + trimmed)
                }
            }
        }
    }

    /**
     * 调整数量；减少时自动记录消耗。
     * 吃完（数量减到 0）时自动移入归档（原因：已吃完）。
     * @return 本次操作的结果（是否触发自动归档 + 新写的消耗记录 id，供撤销）。
     */
    suspend fun changeQuantity(id: String, delta: Int): QuantityChangeResult {
        var autoArchived = false
        var consumptionId: String? = null
        dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            // 主数据：数量写在库存上，损坏时必须拒绝覆盖。
            if (isCorrupt(itemsDecoded)) return@edit
            // 附带数据按 key 各自判断（2026-09-15）：消耗记录/归档损坏只降级对应副作用，
            // 不再让「改个数量」整体失效。
            val consumptionDecoded = decodeConsumption(prefs[consumptionKey])
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            val consumptionOk = !isCorrupt(consumptionDecoded)
            val archiveOk = !isCorrupt(archiveDecoded)
            val current = itemsDecoded.orElse(emptyList())
            val item = current.find { it.id == id } ?: return@edit
            val newQty = (item.quantity + delta).coerceAtLeast(0)
            val consumed = if (delta < 0) item.quantity - newQty else 0
            if (consumed > 0 && !consumptionOk) {
                Log.w(TAG, "consumption_records 损坏：本次扣减不写消耗记录（库存已更新）")
            }
            if (consumed > 0 && consumptionOk) {
                consumptionId = UUID.randomUUID().toString()
                val records = consumptionDecoded.orElse(emptyList())
                val record = ConsumptionRecord(
                    name = item.name,
                    category = item.category,
                    amount = consumed,
                    unit = item.unit,
                    epochDay = LocalDate.now(clock).toEpochDay(),
                    id = consumptionId,
                )
                prefs[consumptionKey] = json.encodeToString(
                    compactConsumption(listOf(record) + records)
                )
            }
            if (newQty == 0 && delta < 0 && archiveOk) {
                // 吃完了 → 自动归档
                val today = LocalDate.now(clock).toEpochDay()
                val archive = archiveDecoded.orElse(emptyList())
                val entry = ArchivedItem(item.copy(quantity = 0), today, ArchiveReason.CONSUMED)
                prefs[archiveKey] = json.encodeToString((listOf(entry) + archive).take(200))
                prefs[itemsKey] = json.encodeToString(current.filterNot { it.id == id })
                autoArchived = true
            } else {
                // 注意：归档损坏且刚好减到 0 时走这里 —— 刻意「保留 0 数量记录、不归档」，
                // 因为把库存删掉却写不进归档 = 数据丢失。（坏掉的那份数据仍留档待恢复）
                prefs[itemsKey] = json.encodeToString(
                    current.map { if (it.id == id) it.copy(quantity = newQty) else it }
                )
            }
        }
        return QuantityChangeResult(autoArchived, consumptionId)
    }

    /**
     * 删除单条消耗记录（修正误触/错误统计；仅删记录，不回滚库存数量）。
     * 优先按 id 精确定位；id 为 null 的旧记录（迁移前）按「内容完全相等」匹配，
     * 避免 `record.id?.let{...}` 把关导致的无 id 记录删除按钮静默无效。
     */
    suspend fun deleteConsumption(record: ConsumptionRecord) {
        // 月度聚合记录不允许单条删除：一条 = 整月合计，删掉等于抹掉整月历史（2026-09-15）。
        // UI 侧已不提供删除按钮，这里是最后一道防线——撤销删除等路径也绕不过它。
        if (!record.isDeletable()) {
            Log.w(TAG, "拒绝删除月度聚合记录：${record.name} ${record.amount}${record.unit}（epochDay=${record.epochDay}）")
            return
        }
        dataStore.edit { prefs ->
            val decoded = decodeConsumption(prefs[consumptionKey])
            if (isCorrupt(decoded)) return@edit
            val records = decoded.orElse(emptyList())
            val filtered = if (record.id != null) {
                records.filterNot { it.id == record.id }
            } else {
                records.filterNot { it == record }
            }
            prefs[consumptionKey] = json.encodeToString(filtered)
        }
    }

    /** 重新插入一条消耗记录（撤销删除用）。index 为删除前在日期倒序列表中的位置。 */
    suspend fun addConsumption(record: ConsumptionRecord, index: Int? = null) {
        dataStore.edit { prefs ->
            val decoded = decodeConsumption(prefs[consumptionKey])
            if (isCorrupt(decoded)) return@edit
            val records = decoded.orElse(emptyList())
                .sortedByDescending { it.epochDay }
                .toMutableList()
            val i = (index ?: 0).coerceIn(0, records.size)
            records.add(i, record)
            prefs[consumptionKey] = json.encodeToString(compactConsumption(records))
        }
    }

    /**
     * 撤销一次减少消耗：删除对应消耗记录，并把该食品数量 +1。
     * 若该食品因减到 0 已被自动归档，则从归档恢复为数量 1。
     */
    suspend fun undoConsumption(itemId: String, consumptionId: String) {
        dataStore.edit { prefs ->
            val consumptionDecoded = decodeConsumption(prefs[consumptionKey])
            val itemsDecoded = decodeItems(prefs[itemsKey])
            if (isCorrupt(consumptionDecoded, itemsDecoded)) return@edit
            // 归档只在「该食品已因减到 0 被自动归档」这一分支才需要写，按需判定（2026-09-15）。
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            val records = consumptionDecoded.orElse(emptyList())
            prefs[consumptionKey] = json.encodeToString(records.filterNot { it.id == consumptionId })

            val items = itemsDecoded.orElse(emptyList())
            val item = items.find { it.id == itemId }
            if (item != null) {
                prefs[itemsKey] = json.encodeToString(
                    items.map { if (it.id == itemId) it.copy(quantity = it.quantity + 1) else it }
                )
            } else {
                // 已被自动归档（减到 0），从归档恢复为数量 1
                if (isCorrupt(archiveDecoded)) {
                    Log.w(TAG, "archived_items 损坏：无法从归档恢复该食品（消耗记录已撤销）")
                    return@edit
                }
                val archive = archiveDecoded.orElse(emptyList())
                val entry = archive.find { it.item.id == itemId }
                if (entry != null) {
                    val restored = entry.item.copy(quantity = 1)
                    prefs[itemsKey] = json.encodeToString(listOf(restored) + items)
                    prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == itemId })
                }
            }
        }
    }

    suspend fun setCategoryThreshold(categoryId: String, days: Int) {
        dataStore.edit { prefs ->
            val current = decodeThresholds(prefs[thresholdsKey]).toMutableMap()
            current[categoryId] = days.coerceIn(1, 365)
            prefs[thresholdsKey] = json.encodeToString(current.toMap())
        }
    }

    suspend fun setCategories(categories: List<CategoryDef>) {
        dataStore.edit { prefs ->
            prefs[categoriesKey] = json.encodeToString(categories)
        }
    }

    suspend fun setLocations(locations: List<String>) {
        dataStore.edit { prefs ->
            prefs[locationsKey] = json.encodeToString(locations)
        }
    }

    /**
     * 清空全部库存。用户显式发起的破坏性操作（设置页有二次确认），
     * 即便 key 已损坏也应允许执行，并借此解除损坏态。
     */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(emptyList<FoodItem>())
            _corruptedKeys.update { it - "food_items" }
        }
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

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setDarkMode(mode: Int) {
        dataStore.edit { it[darkModeKey] = mode }
    }

    suspend fun setPalette(name: String) {
        dataStore.edit { it[paletteKey] = name }
    }

    suspend fun setThemeStyle(name: String) {
        dataStore.edit { it[themeStyleKey] = name }
    }

    suspend fun setFloatingNav(enabled: Boolean) {
        dataStore.edit { it[floatingNavKey] = enabled }
    }

    suspend fun setNutstoreCredentials(account: String, password: String) {
        dataStore.edit { prefs ->
            prefs[nutstoreAccountKey] = account.trim()
            val enc = SecureStore.encrypt(password.trim())
            if (enc != null) {
                prefs[nutstorePasswordEncKey] = enc
                prefs.remove(nutstorePasswordKey) // 确保明文不再落盘
            } else {
                // Keystore 不可用的极端回退：为了不打断同步功能只能先存明文，
                // 但**绝不能静默**——记日志 + 由 nutstorePlaintextFallbackFlow 让设置页提示用户。
                // 下次启动 migratePlaintextPassword 会重试加密。
                Log.e(TAG, "凭据加密失败（Keystore 不可用？），本次以未加密形式保存，将于下次启动重试")
                prefs[nutstorePasswordKey] = password.trim()
            }
        }
    }

    suspend fun setAutoSyncDays(days: Int) {
        dataStore.edit { it[autoSyncDaysKey] = days.coerceIn(0, 30) }
    }

    suspend fun setLastAutoSyncEpochDay(epochDay: Long) {
        dataStore.edit { it[lastAutoSyncEpochDayKey] = epochDay.toString() }
    }

    suspend fun setLastSync(text: String) {
        dataStore.edit { it[lastSyncKey] = text }
    }

    // ---- 消耗记录压缩：不再粗暴裁剪前 1000 条 ----

    /**
     * 保留最近 90 天的逐笔明细；更早的记录按「月 × 名称」聚合为单条
     * （epochDay 归一到当月 1 号，amount 求和）。
     * 长期统计（排行榜/月度消耗）不失真，存储规模有界。
     */
    private fun compactConsumption(records: List<ConsumptionRecord>): List<ConsumptionRecord> =
        compactConsumptionAt(records, LocalDate.now(clock))

    // ---- Backup ----

    /**
     * 导出备份。
     * @throws IllegalStateException 若任一「用户资产型」key 处于损坏态——
     * 此时导出的备份会缺失该部分数据，静默导出等于给用户一份残缺备份，
     * 反而可能被用来覆盖掉尚可抢救的原始数据。
     */
    suspend fun buildBackupJson(): String {
        val prefs = dataStore.data.first()
        val itemsDecoded = decodeItems(prefs[itemsKey])
        val archiveDecoded = decodeArchive(prefs[archiveKey])
        val consumptionDecoded = decodeConsumption(prefs[consumptionKey])
        val historyDecoded = decodeHistory(prefs[historyKey])
        check(!isCorrupt(itemsDecoded, archiveDecoded, consumptionDecoded, historyDecoded)) {
            "部分数据损坏，已取消导出以免生成残缺备份"
        }
        val backup = BackupData(
            items = itemsDecoded.orElse(emptyList()),
            archived = archiveDecoded.orElse(emptyList()),
            consumption = consumptionDecoded.orElse(emptyList()),
            history = historyDecoded.orElse(emptyList()),
            categoryThresholds = decodeThresholds(prefs[thresholdsKey]),
            categories = decodeCategories(prefs[categoriesKey]),
            locations = decodeLocations(prefs[locationsKey]),
        )
        return prettyJson.encodeToString(backup)
    }

    /**
     * 导出为 CSV 表格内容（带 UTF-8 BOM）。
     */
    suspend fun buildCsvExport(): String {
        val prefs = dataStore.data.first()
        val itemsDecoded = decodeItems(prefs[itemsKey])
        val items = itemsDecoded.orElse(emptyList())
        val categories = decodeCategories(prefs[categoriesKey])
        val thresholds = decodeThresholds(prefs[thresholdsKey])
        return buildCsvExport(items, categories, thresholds, LocalDate.now(clock))
    }

    /**
     * 解析备份内容用于「导入前预览」，**不改动任何数据**（2026-09-15 新增）。
     *
     * 导入是「整体替换」的破坏性操作，原先点一下文件就直接覆盖，用户看不到将覆盖什么。
     * 现在 UI 先调本方法拿到摘要（条数 / 导出日期 / schema 版本）弹二次确认，再执行
     * [importBackupJson]。
     *
     * 只认真正像备份的文件：必须含 `items` 键（v1/v2 备份均导出该字段）。
     * 否则 `{}`、`{"foo":1}` 这类合法 JSON 也会因字段默认值解码「成功」，
     * 变成一个能清空用户数据的「合法空备份」。
     *
     * @return 合法备份返回 [BackupData]；非备份 / 畸形 JSON 返回 null。
     */
    suspend fun previewBackup(raw: String): BackupData? = withContext(Dispatchers.Default) {
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            if ("items" !in obj) {
                Log.w(TAG, "importBackupJson 预览被拒：文件不含 items 字段，不是本应用的备份")
                return@runCatching null
            }
            json.decodeFromString<BackupData>(raw)
        }
            .onFailure { Log.w(TAG, "importBackupJson 预览解析失败", it) }
            .getOrNull()
    }

    /**
     * 从备份整体替换。这是"用已知良好的数据覆盖当前状态"，
     * 因此**允许**在损坏态下执行——正是损坏后的恢复手段，成功后解除损坏标记。
     */
    suspend fun importBackupJson(raw: String): Boolean {
        val backup = runCatching { json.decodeFromString<BackupData>(raw) }.getOrNull() ?: return false
        dataStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(backup.items)
            prefs[archiveKey] = json.encodeToString(backup.archived)
            prefs[consumptionKey] = json.encodeToString(backup.consumption)
            prefs[historyKey] = json.encodeToString(backup.history)
            prefs[thresholdsKey] = json.encodeToString(backup.categoryThresholds)
            if (backup.categories.isNotEmpty()) {
                prefs[categoriesKey] = json.encodeToString(backup.categories)
            }
            if (backup.locations.isNotEmpty()) {
                prefs[locationsKey] = json.encodeToString(backup.locations)
            }
            prefs[seededKey] = true
        }
        _corruptedKeys.value = emptySet()
        return true
    }
}
