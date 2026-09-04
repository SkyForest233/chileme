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

sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>
    data object Empty : Decoded<Nothing>
    data class Corrupt(val raw: String, val cause: Throwable) : Decoded<Nothing>
}

fun <T> Decoded<T>.orElse(fallback: T): T = when (this) {
    is Decoded.Ok -> value
    else -> fallback
}

class FoodRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

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
    // ---- Phase0 新增：对齐 KernelSU 的主题引擎 ----
    private val colorModeKey = intPreferencesKey("color_mode")
    private val paletteStyleKey = stringPreferencesKey("palette_style")
    private val colorSpecKey = stringPreferencesKey("color_spec")
    private val enableBlurKey = booleanPreferencesKey("enable_blur")
    private val enableFloatingBlurKey = booleanPreferencesKey("enable_floating_blur")
    private val enableBadgeKey = booleanPreferencesKey("enable_badge")

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

    private fun decodeThresholds(raw: String?): Map<String, Int> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private fun decodeCategories(raw: String?): List<CategoryDef> =
        raw?.let { runCatching { json.decodeFromString<List<CategoryDef>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: DefaultCategories

    private fun decodeLocations(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: DefaultLocations

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

    private fun isCorrupt(vararg decoded: Decoded<*>): Boolean =
        decoded.any { it is Decoded.Corrupt }

    private fun <T> rawFlow(key: Preferences.Key<String>, decode: (String?) -> T): Flow<T> =
        context.dataStore.data
            .map { it[key] }
            .distinctUntilChanged()
            .map(decode)
            .flowOn(Dispatchers.Default)

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

    // ---- 新主题引擎 Flow ----
    val colorModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[colorModeKey] ?: run {
            val dark = prefs[darkModeKey] ?: 0
            val dyn = prefs[dynamicColorKey] ?: false
            when {
                dyn && dark == 1 -> 4 // MONET_LIGHT
                dyn && dark == 2 -> 5 // MONET_DARK
                dyn -> 3 // MONET_SYSTEM
                else -> dark // 0,1,2
            }
        }
    }.distinctUntilChanged()

    val paletteStyleFlow: Flow<String> = lightFlow { it[paletteStyleKey] ?: "TonalSpot" }
    val colorSpecFlow: Flow<String> = lightFlow { it[colorSpecKey] ?: "SPEC_2025" }
    val enableBlurFlow: Flow<Boolean> = lightFlow { it[enableBlurKey] ?: true }
    val enableFloatingBlurFlow: Flow<Boolean> = lightFlow { it[enableFloatingBlurKey] ?: true }
    val enableBadgeFlow: Flow<Boolean> = lightFlow { it[enableBadgeKey] ?: true }

    val nutstoreAccountFlow: Flow<String> = lightFlow { it[nutstoreAccountKey] ?: "" }

    val nutstorePasswordFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[nutstorePasswordKey] to prefs[nutstorePasswordEncKey] }
        .distinctUntilChanged()
        .map { (plain, enc) ->
            plain?.takeIf { it.isNotBlank() }
                ?: enc?.let { SecureStore.decrypt(it) }
                ?: ""
        }
        .flowOn(Dispatchers.Default)

    val nutstoreCredentialBrokenFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[nutstorePasswordKey] to prefs[nutstorePasswordEncKey] }
        .distinctUntilChanged()
        .map { (plain, enc) ->
            plain.isNullOrBlank() && !enc.isNullOrBlank() && SecureStore.decrypt(enc) == null
        }
        .flowOn(Dispatchers.Default)

    val lastSyncFlow: Flow<String> = lightFlow { it[lastSyncKey] ?: "" }

    val autoSyncDaysFlow: Flow<Int> = lightFlow { it[autoSyncDaysKey] ?: 0 }

    val lastAutoSyncEpochDayFlow: Flow<Long> =
        lightFlow { it[lastAutoSyncEpochDayKey]?.toLongOrNull() ?: 0L }

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

    suspend fun restoreArchivedBatch(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val decoded = decodeArchive(prefs[archiveKey])
            if (isCorrupt(decoded)) return@edit
            val archive = decoded.orElse(emptyList())
            prefs[archiveKey] = json.encodeToString(archive.filterNot { it.item.id == id })
        }
    }

    suspend fun clearArchive() {
        context.dataStore.edit { prefs ->
            prefs[archiveKey] = json.encodeToString(emptyList<ArchivedItem>())
            _corruptedKeys.update { it - "archived_items" }
        }
    }

    suspend fun updateLocationBatch(ids: Set<String>, newLocation: String) {
        if (ids.isEmpty()) return
        val trimmed = newLocation.trim()
        context.dataStore.edit { prefs ->
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

    suspend fun deleteConsumption(record: ConsumptionRecord) {
        context.dataStore.edit { prefs ->
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

    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs[itemsKey] = json.encodeToString(emptyList<FoodItem>())
            _corruptedKeys.update { it - "food_items" }
        }
    }

    // ---- 旧主题 API：保留兼容，但内部同步更新 colorMode ----
    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val currentCMVal = prefs[colorModeKey] ?: run {
                val d = prefs[darkModeKey] ?: 0
                val dyn = prefs[dynamicColorKey] ?: false
                when {
                    dyn && d == 1 -> 4
                    dyn && d == 2 -> 5
                    dyn -> 3
                    else -> d
                }
            }
            val isAmoled = currentCMVal == 6
            val darkModeVal = prefs[darkModeKey] ?: 0
            val newCM = when {
                isAmoled -> 6
                enabled && darkModeVal == 1 -> 4
                enabled && darkModeVal == 2 -> 5
                enabled -> 3
                darkModeVal == 1 -> 1
                darkModeVal == 2 -> 2
                else -> 0
            }
            prefs[colorModeKey] = newCM
            prefs[dynamicColorKey] = enabled
        }
    }

    suspend fun setDarkMode(mode: Int) {
        context.dataStore.edit { prefs ->
            val currentCMVal = prefs[colorModeKey] ?: run {
                val d = prefs[darkModeKey] ?: 0
                val dyn = prefs[dynamicColorKey] ?: false
                when {
                    dyn && d == 1 -> 4
                    dyn && d == 2 -> 5
                    dyn -> 3
                    else -> d
                }
            }
            val isMonet = currentCMVal >= 3
            val isAmoled = currentCMVal == 6
            val newCM = when {
                isAmoled && mode == 2 -> 6
                isMonet && mode == 1 -> 4
                isMonet && mode == 2 -> 5
                isMonet -> 3
                mode == 1 -> 1
                mode == 2 -> 2
                else -> 0
            }
            prefs[colorModeKey] = newCM
            prefs[darkModeKey] = mode
        }
    }

    // ---- 新主题 API ----
    suspend fun setColorMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[colorModeKey] = mode
            // 同步旧 key 保证旧 UI 不错乱
            prefs[darkModeKey] = when (mode) {
                6, 2, 5 -> 2
                1, 4 -> 1
                else -> 0
            }
            prefs[dynamicColorKey] = mode >= 3
        }
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

    suspend fun setPaletteStyle(name: String) {
        context.dataStore.edit { it[paletteStyleKey] = name }
    }

    suspend fun setColorSpec(name: String) {
        context.dataStore.edit { it[colorSpecKey] = name }
    }

    suspend fun setEnableBlur(enabled: Boolean) {
        context.dataStore.edit { it[enableBlurKey] = enabled }
    }

    suspend fun setEnableFloatingBlur(enabled: Boolean) {
        context.dataStore.edit { it[enableFloatingBlurKey] = enabled }
    }

    suspend fun setEnableBadge(enabled: Boolean) {
        context.dataStore.edit { it[enableBadgeKey] = enabled }
    }

    suspend fun setNutstoreCredentials(account: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[nutstoreAccountKey] = account.trim()
            val enc = SecureStore.encrypt(password.trim())
            if (enc.isNotBlank()) {
                prefs[nutstorePasswordEncKey] = enc
                prefs.remove(nutstorePasswordKey)
            } else {
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

    private fun compactConsumption(records: List<ConsumptionRecord>): List<ConsumptionRecord> =
        compactConsumptionAt(records, LocalDate.now())

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

    suspend fun buildCsvExport(): String {
        val prefs = context.dataStore.data.first()
        val itemsDecoded = decodeItems(prefs[itemsKey])
        val items = itemsDecoded.orElse(emptyList())
        val categories = decodeCategories(prefs[categoriesKey])
        val thresholds = decodeThresholds(prefs[thresholdsKey])
        return buildCsvExport(items, categories, thresholds, LocalDate.now())
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
        _corruptedKeys.value = emptySet()
        return true
    }
}
