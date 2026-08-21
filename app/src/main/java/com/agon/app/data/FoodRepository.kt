package com.agon.app.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Context.dataStore by preferencesDataStore("pantry_store")

private const val TAG = "FoodRepository"

/**
 * 解码结果三态。
 *
 * 关键区别是 [Empty] 与 [Corrupt]：此前两者都被压成 `emptyList()`，
 * 于是「解析失败」被当成「没有数据」，随后任何一次写操作都会把空表
 * encode 回去，**一次解析异常就永久摧毁整份用户数据**。
 */
sealed interface Decoded<out T> {
    /** 解析成功。 */
    data class Ok<T>(val value: T) : Decoded<T>

    /** key 不存在——真的没有数据，可安全写入。 */
    data object Empty : Decoded<Nothing>

    /** 存在原始串但解析失败——**禁止覆盖写**，否则用户数据丢失。 */
    data class Corrupt(val raw: String, val cause: Throwable) : Decoded<Nothing>
}

/** 取值；[Decoded.Corrupt] 与 [Decoded.Empty] 一律回落 [fallback]（仅供读路径/UI 展示用）。 */
fun <T> Decoded<T>.orElse(fallback: T): T = when (this) {
    is Decoded.Ok -> value
    else -> fallback
}

class FoodRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * 检测到数据损坏的 key 集合（如 "food_items"）。非空时 UI 应提示用户，
     * 且所有涉及该 key 的写操作都会被跳过，避免把损坏状态"洗"成空数据。
     */
    private val _corruptedKeys = MutableStateFlow<Set<String>>(emptySet())
    val corruptedKeys: StateFlow<Set<String>> = _corruptedKeys.asStateFlow()

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

    /**
     * 三态解码。解析失败时把原始串留档到 filesDir/corrupt/ 并置位 [corruptedKeys]。
     */
    private inline fun <reified T> decodeStrict(keyName: String, raw: String?): Decoded<T> {
        if (raw == null) return Decoded.Empty
        return runCatching { json.decodeFromString<T>(raw) }.fold(
            onSuccess = { Decoded.Ok(it) },
            onFailure = { cause ->
                Log.e(TAG, "解析 $keyName 失败，已拒绝写入以保护数据", cause)
                markCorrupt(keyName, raw)
                Decoded.Corrupt(raw, cause)
            },
        )
    }

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

    /**
     * 首次发现某 key 损坏时，把原始串另存一份供人工恢复/求助，并置位状态供 UI 提示。
     * 同一 key 只留档一次（文件名带时间戳，重复调用不会刷屏）。
     */
    private fun markCorrupt(keyName: String, raw: String) {
        val firstTime = keyName !in _corruptedKeys.value
        _corruptedKeys.update { it + keyName }
        if (!firstTime) return
        runCatching {
            val dir = File(context.filesDir, "corrupt").apply { mkdirs() }
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            File(dir, "$keyName-$stamp.json").writeText(raw)
        }.onFailure { Log.w(TAG, "留档损坏数据失败：$keyName", it) }
    }

    /**
     * 写操作守卫：任一「用户资产型」key 处于损坏态时返回 true，调用方必须放弃本次写入。
     * 只检查本次写操作实际会覆盖的 key。
     */
    private fun isCorrupt(vararg decoded: Decoded<*>): Boolean =
        decoded.any { it is Decoded.Corrupt }

    // ---- 读取 ----
    //
    // DataStore 每次 edit 都会重发整份 Preferences。此前 7 个列表 flow 直接 map+decode，
    // 于是「改一次主题色」会把 items/archive/consumption/history/... 全部重新解析一遍 JSON
    // 并产生全新 List 实例，触发全屏重组；且解码跑在 viewModelScope（Main.immediate）= 主线程。
    //
    // 现统一走 rawFlow：先取原始串 -> distinctUntilChanged（该 key 没变就不往下走）
    // -> 解码 -> flowOn(Default) 移出主线程。

    /** 重型 key（需 JSON 解码）：按原始串去重，解码在 Default 线程。 */
    private fun <T> rawFlow(key: Preferences.Key<String>, decode: (String?) -> T): Flow<T> =
        context.dataStore.data
            .map { it[key] }
            .distinctUntilChanged()
            .map(decode)
            .flowOn(Dispatchers.Default)

    /** 轻量 key（无需解码）：只做去重，不必切线程。 */
    private fun <T> lightFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data.map(transform).distinctUntilChanged()

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

    val dynamicColorFlow: Flow<Boolean> = lightFlow { it[dynamicColorKey] ?: false }

    val darkModeFlow: Flow<Int> = lightFlow { it[darkModeKey] ?: 0 }

    val paletteFlow: Flow<String> = lightFlow { it[paletteKey] ?: "MINT" }

    val themeStyleFlow: Flow<String> = lightFlow { it[themeStyleKey] ?: "MATERIAL3" }

    val floatingNavFlow: Flow<Boolean> = lightFlow { it[floatingNavKey] ?: true }

    val nutstoreAccountFlow: Flow<String> = lightFlow { it[nutstoreAccountKey] ?: "" }

    /**
     * 密码仅以 Keystore 加密密文存储；读取时解密。
     * 兼容迁移：若发现旧版明文 key 尚存，优先读明文（随后 seedIfNeeded/save 会完成迁移并抹除明文）。
     *
     * 解密是 Keystore 操作（非平凡开销），先按密文去重再切到 Default 线程，
     * 避免每次 DataStore 重发都在主线程做一次 AES-GCM。
     */
    val nutstorePasswordFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[nutstorePasswordKey] to prefs[nutstorePasswordEncKey] }
        .distinctUntilChanged()
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
    val nutstoreCredentialBrokenFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[nutstorePasswordKey] to prefs[nutstorePasswordEncKey] }
        .distinctUntilChanged()
        .map { (plain, enc) ->
            plain.isNullOrBlank() && !enc.isNullOrBlank() && SecureStore.decrypt(enc) == null
        }
        .flowOn(Dispatchers.Default)

    val lastSyncFlow: Flow<String> = lightFlow { it[lastSyncKey] ?: "" }

    /** 自动同步间隔（天）；0 = 关闭自动同步 */
    val autoSyncDaysFlow: Flow<Int> = lightFlow { it[autoSyncDaysKey] ?: 0 }

    val lastAutoSyncEpochDayFlow: Flow<Long> =
        lightFlow { it[lastAutoSyncEpochDayKey]?.toLongOrNull() ?: 0L }

    /** 启动时迁移：若存在旧版明文密码，加密后写入新 key 并删除明文。 */
    suspend fun migratePlaintextPassword() {
        context.dataStore.edit { prefs ->
            val plain = prefs[nutstorePasswordKey]
            if (!plain.isNullOrBlank()) {
                val enc = SecureStore.encrypt(plain)
                if (enc.isNotBlank()) {
                    prefs[nutstorePasswordEncKey] = enc
                    prefs.remove(nutstorePasswordKey)
                }
            }
        }
    }

    /** 启动时迁移：给无 id 的旧消耗记录补 UUID，供删除/撤销精确定位。 */
    suspend fun migrateConsumptionIds() {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            if (prefs[seededKey] == true) return@edit
            // 库存 key 损坏时绝不种子化：否则会把损坏数据直接覆盖成 8 条示例。
            if (isCorrupt(decodeItems(prefs[itemsKey]))) return@edit
            val today = LocalDate.now().toEpochDay()
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

    suspend fun upsert(item: FoodItem) {
        context.dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            val historyDecoded = decodeHistory(prefs[historyKey])
            if (isCorrupt(itemsDecoded, historyDecoded)) return@edit
            val current = itemsDecoded.orElse(emptyList())
            val updated = if (current.any { it.id == item.id }) {
                current.map { if (it.id == item.id) item else it }
            } else {
                listOf(item) + current
            }
            prefs[itemsKey] = json.encodeToString(updated)

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
        context.dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(itemsDecoded, archiveDecoded)) return@edit
            val current = itemsDecoded.orElse(emptyList())
            val (toArchive, keep) = current.partition { it.id in ids }
            if (toArchive.isEmpty()) return@edit
            val today = LocalDate.now().toEpochDay()
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
        context.dataStore.edit { prefs ->
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

    suspend fun deleteArchived(id: String) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            prefs[archiveKey] = json.encodeToString(emptyList<ArchivedItem>())
            _corruptedKeys.update { it - "archived_items" }
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
        context.dataStore.edit { prefs ->
            val itemsDecoded = decodeItems(prefs[itemsKey])
            val consumptionDecoded = decodeConsumption(prefs[consumptionKey])
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(itemsDecoded, consumptionDecoded, archiveDecoded)) return@edit
            val current = itemsDecoded.orElse(emptyList())
            val item = current.find { it.id == id } ?: return@edit
            val newQty = (item.quantity + delta).coerceAtLeast(0)
            val consumed = if (delta < 0) item.quantity - newQty else 0
            if (consumed > 0) {
                consumptionId = UUID.randomUUID().toString()
                val records = consumptionDecoded.orElse(emptyList())
                val record = ConsumptionRecord(
                    name = item.name,
                    category = item.category,
                    amount = consumed,
                    unit = item.unit,
                    epochDay = LocalDate.now().toEpochDay(),
                    id = consumptionId,
                )
                prefs[consumptionKey] = json.encodeToString(
                    compactConsumption(listOf(record) + records)
                )
            }
            if (newQty == 0 && delta < 0) {
                // 吃完了 → 自动归档
                val today = LocalDate.now().toEpochDay()
                val archive = archiveDecoded.orElse(emptyList())
                val entry = ArchivedItem(item.copy(quantity = 0), today, ArchiveReason.CONSUMED)
                prefs[archiveKey] = json.encodeToString((listOf(entry) + archive).take(200))
                prefs[itemsKey] = json.encodeToString(current.filterNot { it.id == id })
                autoArchived = true
            } else {
                prefs[itemsKey] = json.encodeToString(
                    current.map { if (it.id == id) it.copy(quantity = newQty) else it }
                )
            }
        }
        return QuantityChangeResult(autoArchived, consumptionId)
    }

    /** 删除单条消耗记录（修正误触/错误统计；仅删记录，不回滚库存数量）。 */
    suspend fun deleteConsumption(id: String) {
        context.dataStore.edit { prefs ->
            val decoded = decodeConsumption(prefs[consumptionKey])
            if (isCorrupt(decoded)) return@edit
            val records = decoded.orElse(emptyList())
            prefs[consumptionKey] = json.encodeToString(records.filterNot { it.id == id })
        }
    }

    /** 重新插入一条消耗记录（撤销删除用）。index 为删除前在日期倒序列表中的位置。 */
    suspend fun addConsumption(record: ConsumptionRecord, index: Int? = null) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val consumptionDecoded = decodeConsumption(prefs[consumptionKey])
            val itemsDecoded = decodeItems(prefs[itemsKey])
            val archiveDecoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(consumptionDecoded, itemsDecoded, archiveDecoded)) return@edit
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
        context.dataStore.edit { prefs ->
            val current = decodeThresholds(prefs[thresholdsKey]).toMutableMap()
            current[categoryId] = days.coerceIn(1, 365)
            prefs[thresholdsKey] = json.encodeToString(current.toMap())
        }
    }

    suspend fun setCategories(categories: List<CategoryDef>) {
        context.dataStore.edit { prefs ->
            prefs[categoriesKey] = json.encodeToString(categories)
        }
    }

    suspend fun setLocations(locations: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[locationsKey] = json.encodeToString(locations)
        }
    }

    /**
     * 清空全部库存。用户显式发起的破坏性操作（设置页有二次确认），
     * 即便 key 已损坏也应允许执行，并借此解除损坏态。
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(emptyList<FoodItem>())
            _corruptedKeys.update { it - "food_items" }
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setDarkMode(mode: Int) {
        context.dataStore.edit { it[darkModeKey] = mode }
    }

    suspend fun setPalette(name: String) {
        context.dataStore.edit { it[paletteKey] = name }
    }

    suspend fun setThemeStyle(name: String) {
        context.dataStore.edit { it[themeStyleKey] = name }
    }

    suspend fun setFloatingNav(enabled: Boolean) {
        context.dataStore.edit { it[floatingNavKey] = enabled }
    }

    suspend fun setNutstoreCredentials(account: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[nutstoreAccountKey] = account.trim()
            val enc = SecureStore.encrypt(password.trim())
            if (enc.isNotBlank()) {
                prefs[nutstorePasswordEncKey] = enc
                prefs.remove(nutstorePasswordKey) // 确保明文不再落盘
            } else {
                // Keystore 不可用的极端回退（不应发生）：保持旧行为以免功能不可用
                prefs[nutstorePasswordKey] = password.trim()
            }
        }
    }

    suspend fun setAutoSyncDays(days: Int) {
        context.dataStore.edit { it[autoSyncDaysKey] = days.coerceIn(0, 30) }
    }

    suspend fun setLastAutoSyncEpochDay(epochDay: Long) {
        context.dataStore.edit { it[lastAutoSyncEpochDayKey] = epochDay.toString() }
    }

    suspend fun setLastSync(text: String) {
        context.dataStore.edit { it[lastSyncKey] = text }
    }

    // ---- 消耗记录压缩：不再粗暴裁剪前 1000 条 ----

    /**
     * 保留最近 90 天的逐笔明细；更早的记录按「月 × 名称」聚合为单条
     * （epochDay 归一到当月 1 号，amount 求和）。
     * 长期统计（排行榜/月度消耗）不失真，存储规模有界。
     */
    private fun compactConsumption(records: List<ConsumptionRecord>): List<ConsumptionRecord> =
        compactConsumptionAt(records, LocalDate.now())

    // ---- Backup ----

    /**
     * 导出备份。
     * @throws IllegalStateException 若任一「用户资产型」key 处于损坏态——
     * 此时导出的备份会缺失该部分数据，静默导出等于给用户一份残缺备份，
     * 反而可能被用来覆盖掉尚可抢救的原始数据。
     */
    suspend fun buildBackupJson(): String {
        val prefs = context.dataStore.data.first()
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
     * 从备份整体替换。这是"用已知良好的数据覆盖当前状态"，
     * 因此**允许**在损坏态下执行——正是损坏后的恢复手段，成功后解除损坏标记。
     */
    suspend fun importBackupJson(raw: String): Boolean {
        val backup = runCatching { json.decodeFromString<BackupData>(raw) }.getOrNull() ?: return false
        context.dataStore.edit { prefs ->
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
