package com.agon.app.data

import android.content.Context
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

object LocalSnapshotStore {
    const val MAX_SNAPSHOTS = 3
    private const val DIR_NAME = "snapshots"
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    private fun getDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 保存一份本地快照，并按时间倒序仅保留最新的 [MAX_SNAPSHOTS] 份。
     */
    fun saveSnapshot(context: Context, jsonPayload: String, maxKeep: Int = MAX_SNAPSHOTS): File? {
        return runCatching {
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
     */
    fun listSnapshots(context: Context): List<LocalSnapshot> {
        val dir = getDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("snapshot_") && f.name.endsWith(".json") }
            ?: return emptyList()

        return files.map { file ->
            val itemCount = runCatching {
                val text = file.readText()
                // 统计 items 数组中的对象个数
                Regex("\"id\"\\s*:").findAll(text).count()
            }.getOrDefault(0)

            LocalSnapshot(
                fileName = file.name,
                fileSizeBytes = file.length(),
                modifiedEpochMillis = file.lastModified(),
                itemCount = itemCount,
            )
        }.sortedByDescending { it.modifiedEpochMillis }
    }

    /**
     * 读取指定快照的 JSON 内容。
     */
    fun readSnapshot(context: Context, fileName: String): String? {
        val dir = getDir(context)
        val file = File(dir, fileName)
        return if (file.exists() && file.isFile) {
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
