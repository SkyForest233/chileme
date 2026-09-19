package com.agon.app.data

import androidx.datastore.preferences.core.MutablePreferences
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
 *
 * 本文件还管着**归档保留上限**这一件事（`trimArchiveRetention`）：归档是所有"被删掉的数据"唯一的去处，
 * 而它是个环形截断的列表 ⇒ 上限怎么写、挤掉时留不留痕迹，都是数据安全的一部分，故收在这里共用。
 */

/**
 * 归档最多保留多少条（M1-3，2026-09-19；此前是散在两处 `take(200)` 里的字面量）。
 *
 * 为什么这条值得改：`take` 是**静默删除**——写满之后，最老的归档条目会在下一次归档时被挤掉，
 * 既不通知用户、也不留任何记录，"被归档的数据可以从归档里找回"这个承诺就此有个没人知道的洞。
 * 它还在污染统计口径：`StatsState.kt` 的历史合计读的就是这份列表，被截断后统计会偏小
 * （那里的口径注释原本只写了"受 `take(200)` 影响"，即把丢数据当成已知取舍 —— 本轮把它变成有账可查）。
 *
 * 上限本身要留着：`archiveKey` 与库存/消耗记录同住一个 DataStore 文件，每次写都是**整份 JSON 全量重写**，
 * 列表长度直接决定"改一次数量"要序列化多少条、以及备份文件多大。1000 条 × 每条约 0.2KB ≈ 200KB，
 * 仍在一秒内可完成重写的量级；再高就属于"换成按键分片存储或 Room"（见 P1-1，本条不解决那个）。
 *
 * 溢出不再静默：挤掉的条数会计入 [FoodRepository.archiveOverflowKey]（一个只增不减的累计计数器），
 * 由 [FoodRepository.archiveItems] 返回给调用方。为什么不用"通知用户"解决：见 `devlog` 里那条
 * "本轮不动用户可见文案"的说明——文案要单独一轮并真机复测（`SnackbarCopyTest` 的分布断言也在这里）。
 */
internal const val ARCHIVE_RETENTION = 1000

/** [trimArchiveRetention] 的结果：截断后要写回的列表 + 被挤掉的条数（0 = 这次没丢东西）。 */
internal data class ArchiveTrim(val entries: List<ArchivedItem>, val dropped: Int)

/**
 * 归档写入的统一收口：新条目接在旧列表前面 → 截断 → 报出被挤掉的条数。
 *
 * 刻意做成**纯函数 + 可注入的 `retention`**：这是全仓唯一一条"数据会被静默丢掉"的路径，
 * 要能在纯 JVM 下用 3 条数据就把溢出测出来，而不是先造 1000 条。
 * 两处调用点（手动/批量归档、吃完自动归档）共用它，避免上限只在一处生效。
 */
internal fun trimArchiveRetention(
    newEntries: List<ArchivedItem>,
    existing: List<ArchivedItem>,
    retention: Int = ARCHIVE_RETENTION,
): ArchiveTrim {
    val merged = newEntries + existing
    return ArchiveTrim(merged.take(retention), (merged.size - retention).coerceAtLeast(0))
}

/**
 * 溢出计数落盘（与截断同一次 `edit` ⇒ 原子：不会出现"数据挤掉了但没记上一笔"）。
 * 只增不减，`clearArchive()` 也刻意不清零 —— 它是"历史上有多少条被上限吃掉"的账，
 * 不是当前状态；清空归档不该让这件事一笔勾销。
 *
 * `internal` 而非 `private`：两个调用点分居两个文件（本文件的 `archiveItems` 与 `FoodConsumption.kt`
 * 减到 0 时的自动归档），共用这一个写入口。
 */
internal fun FoodRepository.bumpArchiveOverflow(prefs: MutablePreferences, dropped: Int) {
    prefs[archiveOverflowKey] = (prefs[archiveOverflowKey] ?: 0) + dropped
}

/**
 * 归档一批库存项。
 *
 * @param retention 保留上限，默认 [ARCHIVE_RETENTION]。生产没有任何调用方传这个参数；
 *   它存在的唯一理由是"溢出"这件事要用 1000 条数据才测得出来，而注入一个上限就能用 4 条测。
 *   与 #5b 注入 `clock` 是同一套理由（`FoodRepository` 类 KDoc 里那段）：默认值 = 改造前的行为，
 *   生产路径逐位不变。
 * @return 因超出 `retention` 而被挤掉的旧归档条数（0 = 没挤掉）。
 *   调用方现在只有 `AppViewModel` 两处，都刻意**不**据此改 UI（见 [ARCHIVE_RETENTION] 那条说明）；
 *   之所以仍要返回：让"丢了多少"这件事在类型上是可见的，接 UI 时不必再动仓库层。
 */
internal suspend fun FoodRepository.archiveItems(
    ids: Set<String>,
    reason: ArchiveReason,
    retention: Int = ARCHIVE_RETENTION,
): Int {
    if (ids.isEmpty()) return 0
    var dropped = 0
    dataStore.edit { prefs ->
        val itemsDecoded = decodeItems(prefs[itemsKey])
        val archiveDecoded = decodeArchive(prefs[archiveKey])
        if (isCorrupt(itemsDecoded, archiveDecoded)) return@edit
        val current = itemsDecoded.orElse(emptyList())
        val (toArchive, keep) = current.partition { it.id in ids }
        if (toArchive.isEmpty()) return@edit
        val today = LocalDate.now(clock).toEpochDay()
        val archive = archiveDecoded.orElse(emptyList())
        val trim = trimArchiveRetention(toArchive.map { ArchivedItem(it, today, reason) }, archive, retention)
        prefs[itemsKey] = json.encodeToString(keep)
        prefs[archiveKey] = json.encodeToString(trim.entries)
        dropped = trim.dropped
        if (trim.dropped > 0) bumpArchiveOverflow(prefs, trim.dropped)
    }
    return dropped
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
