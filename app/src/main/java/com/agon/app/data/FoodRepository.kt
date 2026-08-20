package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

private val Context.dataStore by preferencesDataStore("pantry_store")

class FoodRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

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

    private fun decodeItems(raw: String?): List<FoodItem> =
        raw?.let { runCatching { json.decodeFromString<List<FoodItem>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeArchive(raw: String?): List<ArchivedItem> =
        raw?.let { runCatching { json.decodeFromString<List<ArchivedItem>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeConsumption(raw: String?): List<ConsumptionRecord> =
        raw?.let { runCatching { json.decodeFromString<List<ConsumptionRecord>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeHistory(raw: String?): List<HistoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeThresholds(raw: String?): Map<String, Int> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private fun decodeCategories(raw: String?): List<CategoryDef> =
        raw?.let { runCatching { json.decodeFromString<List<CategoryDef>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: DefaultCategories

    private fun decodeLocations(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: DefaultLocations

    val itemsFlow: Flow<List<FoodItem>> =
        context.dataStore.data.map { decodeItems(it[itemsKey]) }

    val archiveFlow: Flow<List<ArchivedItem>> =
        context.dataStore.data.map { decodeArchive(it[archiveKey]) }

    val consumptionFlow: Flow<List<ConsumptionRecord>> =
        context.dataStore.data.map { decodeConsumption(it[consumptionKey]) }

    val historyFlow: Flow<List<HistoryEntry>> =
        context.dataStore.data.map { decodeHistory(it[historyKey]) }

    val thresholdsFlow: Flow<Map<String, Int>> =
        context.dataStore.data.map { decodeThresholds(it[thresholdsKey]) }

    val categoriesFlow: Flow<List<CategoryDef>> =
        context.dataStore.data.map { decodeCategories(it[categoriesKey]) }

    val locationsFlow: Flow<List<String>> =
        context.dataStore.data.map { decodeLocations(it[locationsKey]) }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[dynamicColorKey] ?: false }

    val darkModeFlow: Flow<Int> = context.dataStore.data.map { it[darkModeKey] ?: 0 }

    val paletteFlow: Flow<String> = context.dataStore.data.map { it[paletteKey] ?: "MINT" }

    val themeStyleFlow: Flow<String> = context.dataStore.data.map { it[themeStyleKey] ?: "MATERIAL3" }

    val floatingNavFlow: Flow<Boolean> = context.dataStore.data.map { it[floatingNavKey] ?: true }

    val nutstoreAccountFlow: Flow<String> = context.dataStore.data.map { it[nutstoreAccountKey] ?: "" }

    /**
     * 密码仅以 Keystore 加密密文存储；读取时解密。
     * 兼容迁移：若发现旧版明文 key 尚存，优先读明文（随后 seedIfNeeded/save 会完成迁移并抹除明文）。
     */
    val nutstorePasswordFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[nutstorePasswordKey]?.takeIf { it.isNotBlank() }
            ?: prefs[nutstorePasswordEncKey]?.let { SecureStore.decrypt(it) }
            ?: ""
    }

    val lastSyncFlow: Flow<String> = context.dataStore.data.map { it[lastSyncKey] ?: "" }

    /** 自动同步间隔（天）；0 = 关闭自动同步 */
    val autoSyncDaysFlow: Flow<Int> = context.dataStore.data.map { it[autoSyncDaysKey] ?: 0 }

    val lastAutoSyncEpochDayFlow: Flow<Long> =
        context.dataStore.data.map { it[lastAutoSyncEpochDayKey]?.toLongOrNull() ?: 0L }

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

    suspend fun seedIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[seededKey] == true) return@edit
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
            val current = decodeItems(prefs[itemsKey])
            val updated = if (current.any { it.id == item.id }) {
                current.map { if (it.id == item.id) item else it }
            } else {
                listOf(item) + current
            }
            prefs[itemsKey] = json.encodeToString(updated)

            val history = decodeHistory(prefs[historyKey])
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
            val current = decodeItems(prefs[itemsKey])
            val (toArchive, keep) = current.partition { it.id in ids }
            if (toArchive.isEmpty()) return@edit
            val today = LocalDate.now().toEpochDay()
            val archive = decodeArchive(prefs[archiveKey])
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
            val archive = decodeArchive(prefs[archiveKey])
            val entry = archive.find { it.item.id == id } ?: return@edit
            val items = decodeItems(prefs[itemsKey])
            val restored = entry.item.let { if (it.quantity <= 0) it.copy(quantity = 1) else it }

            val newItems = when {
                items.any { it.id == restored.id } -> items // 同 ID 已存在，仅移除归档
                else -> {
                    val dup = items.find {
                        it.name == restored.name && it.productionEpochDay == restored.productionEpochDay
                    }
                    if (dup != null) {
                        merged = true
                        items.map {
                            if (it.id == dup.id) it.copy(quantity = it.quantity + restored.quantity) else it
                        }
                    } else {
                        listOf(restored) + items
                    }
                }
            }
            prefs[itemsKey] = json.encodeToString(newItems)
            prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == id })
        }
        return merged
    }

    suspend fun deleteArchived(id: String) {
        context.dataStore.edit { prefs ->
            val archive = decodeArchive(prefs[archiveKey])
            prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == id })
        }
    }

    suspend fun clearArchive() {
        context.dataStore.edit { prefs ->
            prefs[archiveKey] = json.encodeToString(emptyList<ArchivedItem>())
        }
    }

    /**
     * 调整数量；减少时自动记录消耗。
     * 吃完（数量减到 0）时自动移入归档（原因：已吃完）。
     * @return 若本次操作触发了自动归档则返回 true。
     */
    suspend fun changeQuantity(id: String, delta: Int): Boolean {
        var autoArchived = false
        context.dataStore.edit { prefs ->
            val current = decodeItems(prefs[itemsKey])
            val item = current.find { it.id == id } ?: return@edit
            val newQty = (item.quantity + delta).coerceAtLeast(0)
            val consumed = if (delta < 0) item.quantity - newQty else 0
            if (consumed > 0) {
                val records = decodeConsumption(prefs[consumptionKey])
                val record = ConsumptionRecord(
                    name = item.name,
                    category = item.category,
                    amount = consumed,
                    unit = item.unit,
                    epochDay = LocalDate.now().toEpochDay(),
                )
                prefs[consumptionKey] = json.encodeToString(
                    compactConsumption(listOf(record) + records)
                )
            }
            if (newQty == 0 && delta < 0) {
                // 吃完了 → 自动归档
                val today = LocalDate.now().toEpochDay()
                val archive = decodeArchive(prefs[archiveKey])
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
        return autoArchived
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

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(emptyList<FoodItem>())
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
    private fun compactConsumption(records: List<ConsumptionRecord>): List<ConsumptionRecord> {
        val cutoff = LocalDate.now().minusDays(90).toEpochDay()
        val (recent, old) = records.partition { it.epochDay >= cutoff }
        val aggregated = old
            .groupBy { record ->
                val date = LocalDate.ofEpochDay(record.epochDay)
                Triple(date.year, date.monthValue, record.name)
            }
            .map { (key, group) ->
                val (year, month, _) = key
                ConsumptionRecord(
                    name = group.first().name,
                    category = group.first().category,
                    amount = group.sumOf { it.amount },
                    unit = group.first().unit,
                    epochDay = LocalDate.of(year, month, 1).toEpochDay(),
                )
            }
        return (recent + aggregated).sortedByDescending { it.epochDay }
    }

    // ---- Backup ----

    suspend fun buildBackupJson(): String {
        val prefs = context.dataStore.data.first()
        val backup = BackupData(
            items = decodeItems(prefs[itemsKey]),
            archived = decodeArchive(prefs[archiveKey]),
            consumption = decodeConsumption(prefs[consumptionKey]),
            history = decodeHistory(prefs[historyKey]),
            categoryThresholds = decodeThresholds(prefs[thresholdsKey]),
            categories = decodeCategories(prefs[categoriesKey]),
            locations = decodeLocations(prefs[locationsKey]),
        )
        return prettyJson.encodeToString(backup)
    }

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
        return true
    }
}
