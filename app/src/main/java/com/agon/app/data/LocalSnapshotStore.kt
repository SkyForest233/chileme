package com.agon.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LocalSnapshot(
    val fileName: String,
    val fileSizeBytes: Long,
    val modifiedEpochMillis: Long,
    val itemCount: Int,
) {
    val displayTime: String get() {
        val dt = Instant.ofEpochMilli(modifiedEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        return dt.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINESE))
    }

    val displaySize: String get() = when {
        fileSizeBytes < 1024 -> "$fileSizeBytes B"
        fileSizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
    }
}

/**
 * 数一份快照里的库存条数：**解析 JSON 取 `items` 数组长度**，而不是正则数 `"id":`。
 *
 * 旧实现用 `Regex("\"id\"\\s*:")` 全文匹配，会把归档、消耗记录、历史条目里的 id
 * 一并算进去 —— 于是列表里显示的「N 条」明显偏大（一条记录在快照里可能出现多次）。
 *
 * 这里刻意**只数数组长度、不反序列化成 [BackupData]**：条数展示不该被条目字段的 schema 绑架
 * ——旧版本/异常备份里的条目若缺必填字段，整体反序列化会失败，那时明明有库存却显示 0 比不显示更糟
 * （CI 第一次跑正是在「条目只有 id/name 的备份」上踩到这个坑）。
 * 解析失败返回 0：宁可不显示数字，也不显示错的。
 */
internal fun countItemsInSnapshot(text: String): Int = runCatching {
    Json.parseToJsonElement(text).jsonObject["items"]?.jsonArray?.size ?: 0
}.getOrDefault(0)

object LocalSnapshotStore {
    const val MAX_SNAPSHOTS = 3
    private const val DIR_NAME = "snapshots"
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    private fun getDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 保存一份本地快照，并按时间倒序仅保留最新的 [MAX_SNAPSHOTS] 份。
     *
     * 文件写入已下沉到 [Dispatchers.IO]（2026-09-15）：调用点包括启动路径、
     * 每日自动快照与三条恢复路径的前置快照，此前都在主线程同步写盘。
     */
    suspend fun saveSnapshot(context: Context, jsonPayload: String, maxKeep: Int = MAX_SNAPSHOTS): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = getDir(context)
                val timestamp = LocalDateTime.now().format(timeFormatter)
                val file = File(dir, "snapshot_$timestamp.json")
                file.writeText(jsonPayload)

                // 清理多余旧快照
                cleanupOldSnapshots(dir, maxKeep)
                file
            }.getOrNull()
        }

    /**
     * 列出所有本地快照（按修改时间倒序）。
     *
     * 读取 + 逐份解析 JSON（取库存条数）都在 [Dispatchers.IO] 上做（2026-09-15）：
     * 此前在主线程读文件、跑正则，快照多/文件大时会让首屏掉帧。
     */
    suspend fun listSnapshots(context: Context): List<LocalSnapshot> = withContext(Dispatchers.IO) {
        val dir = getDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("snapshot_") && f.name.endsWith(".json") }
            ?: return@withContext emptyList()

        files.map { file ->
            val itemCount = runCatching { countItemsInSnapshot(file.readText()) }.getOrDefault(0)
            LocalSnapshot(
                fileName = file.name,
                fileSizeBytes = file.length(),
                modifiedEpochMillis = file.lastModified(),
                itemCount = itemCount,
            )
        }.sortedByDescending { it.modifiedEpochMillis }
    }

    /**
     * 读取指定快照的 JSON 内容（IO 线程）。
     */
    suspend fun readSnapshot(context: Context, fileName: String): String? = withContext(Dispatchers.IO) {
        val dir = getDir(context)
        val file = File(dir, fileName)
        if (file.exists() && file.isFile) {
            runCatching { file.readText() }.getOrNull()
        } else null
    }

    internal fun cleanupOldSnapshots(dir: File, maxKeep: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("snapshot_") && f.name.endsWith(".json") }
            ?: return
        val sorted = files.sortedByDescending { it.lastModified() }
        if (sorted.size > maxKeep) {
            sorted.drop(maxKeep).forEach { it.delete() }
        }
    }
}
