package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.edit
import java.time.LocalDate
import java.util.UUID

/**
 * 仓库的**消耗与库存变动：改数量（含临期自动归档）、增删消耗记录、撤销、旧数据补 id**（路线图 #5c，2026-09-18）。
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

/** 启动时迁移：给无 id 的旧消耗记录补 UUID，供删除/撤销精确定位。 */
internal suspend fun FoodRepository.migrateConsumptionIds() {
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

/**
 * 调整数量；减少时自动记录消耗。
 * 吃完（数量减到 0）时自动移入归档（原因：已吃完）。
 * @return 本次操作的结果（是否触发自动归档 + 新写的消耗记录 id，供撤销）。
 */
internal suspend fun FoodRepository.changeQuantity(id: String, delta: Int): QuantityChangeResult {
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
            // 吃完了 → 自动归档。截断与溢出计数走 `trimArchiveRetention`（与手动归档同一条口径，
            // M1-3：这里原来是第二处 `(listOf(entry) + archive).take(200)`，上限改动极易只改一处）。
            val today = LocalDate.now(clock).toEpochDay()
            val archive = archiveDecoded.orElse(emptyList())
            val entry = ArchivedItem(item.copy(quantity = 0), today, ArchiveReason.CONSUMED)
            val trim = trimArchiveRetention(listOf(entry), archive)
            prefs[archiveKey] = json.encodeToString(trim.entries)
            if (trim.dropped > 0) {
                // 自动归档是用户**没有主动点**的写路径（减数量减到 0 就发生），挤掉旧归档时
                // 至少要在 logcat 留一行；累计账本先写盘再读出来，省得自己再算一遍。
                bumpArchiveOverflow(prefs, trim.dropped)
                val total = prefs[archiveOverflowKey] ?: 0
                Log.w(TAG, "自动归档挤出 ${trim.dropped} 条历史归档（上限 $ARCHIVE_RETENTION），累计 $total 条")
            }
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
internal suspend fun FoodRepository.deleteConsumption(record: ConsumptionRecord) {
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
internal suspend fun FoodRepository.addConsumption(record: ConsumptionRecord, index: Int? = null) {
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
internal suspend fun FoodRepository.undoConsumption(itemId: String, consumptionId: String) {
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

/**
 * 保留最近 90 天的逐笔明细；更早的记录按「月 × 名称」聚合为单条
 * （epochDay 归一到当月 1 号，amount 求和）。
 * 长期统计（排行榜/月度消耗）不失真，存储规模有界。
 */
internal fun FoodRepository.compactConsumption(records: List<ConsumptionRecord>): List<ConsumptionRecord> =
    compactConsumptionAt(records, LocalDate.now(clock))
