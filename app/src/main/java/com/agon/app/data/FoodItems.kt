package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.edit
import java.time.LocalDate
import java.util.UUID

/**
 * 仓库的**库存领域：种子数据、新增/编辑一条库存、批量改存放位置**（路线图 #5c，2026-09-18）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选"门面转发"和"领域对象"，
 * 完整取舍写在 `RepositoryCore.kt` 的文件 KDoc 里（一次说清，别处只指路）。
 *
 * 对外调用写法一个字没变：同包内扩展函数用隐式接收者就能解析；唯一要改的是**别的包**的调用方
 * —— `AppViewModel` 为搬走的每个函数加一行 import（本仓禁通配导入）。
 */

// 与 `FoodRepository.kt` / `RepositoryCore.kt` 里的 TAG 是同一个字符串的副本，理由见那边的注释
// （本包已有多个文件级 private TAG，升成包级常量可能撞名，而本地没编译器验证不了）。
private const val TAG = "FoodRepository"

internal suspend fun FoodRepository.seedIfNeeded() {
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
internal suspend fun FoodRepository.upsert(item: FoodItem) {
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

/**
 * 批量修改食品的存放位置。
 */
internal suspend fun FoodRepository.updateLocationBatch(ids: Set<String>, newLocation: String) {
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
