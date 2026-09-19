package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * 仓库的**备份、导出与整体清空：这三类都要一次性读写全部 key**（路线图 #5c，2026-09-18）。
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

/**
 * 清空全部库存。用户显式发起的破坏性操作（设置页有二次确认），
 * 即便 key 已损坏也应允许执行，并借此解除损坏态。
 */
internal suspend fun FoodRepository.clearAll() {
    dataStore.edit { prefs ->
        prefs[itemsKey] = json.encodeToString(emptyList<FoodItem>())
        _corruptedKeys.update { it - "food_items" }
    }
}

/**
 * 导出备份。
 * @throws IllegalStateException 若任一「用户资产型」key 处于损坏态——
 * 此时导出的备份会缺失该部分数据，静默导出等于给用户一份残缺备份，
 * 反而可能被用来覆盖掉尚可抢救的原始数据。
 */
internal suspend fun FoodRepository.buildBackupJson(): String {
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
internal suspend fun FoodRepository.buildCsvExport(): String {
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
internal suspend fun FoodRepository.previewBackup(raw: String): BackupData? = withContext(Dispatchers.Default) {
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
internal suspend fun FoodRepository.importBackupJson(raw: String): Boolean {
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
