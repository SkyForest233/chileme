package com.agon.app.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * 仓库的**归档领域：归档、单条与批量恢复、删除归档条目、清空归档**（路线图 #5c，2026-09-18）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选"门面转发"和"领域对象"，
 * 完整取舍写在 `RepositoryCore.kt` 的文件 KDoc 里（一次说清，别处只指路）。
 *
 * 对外调用写法一个字没变：同包内扩展函数用隐式接收者就能解析；唯一要改的是**别的包**的调用方
 * —— `AppViewModel` 为搬走的每个函数加一行 import（本仓禁通配导入）。
 */

internal suspend fun FoodRepository.archiveItems(ids: Set<String>, reason: ArchiveReason) {
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
internal suspend fun FoodRepository.restoreArchived(id: String): Boolean {
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
internal suspend fun FoodRepository.restoreArchivedBatch(ids: Set<String>) {
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

internal suspend fun FoodRepository.deleteArchived(id: String) {
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
internal suspend fun FoodRepository.clearArchive() {
    dataStore.edit { prefs ->
        prefs[archiveKey] = json.encodeToString(emptyList<ArchivedItem>())
        _corruptedKeys.update { it - "archived_items" }
    }
}
