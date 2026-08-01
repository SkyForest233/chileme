package com.agon.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a picked/captured image into the app's private covers directory so
 * the reference stays valid after the original content Uri expires.
 * Returns the absolute file path, or null on failure.
 */
suspend fun copyImageToCovers(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        out.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * 清理孤儿封面图片：删除 covers/ 目录中不被任何库存或归档记录引用的文件。
 * 在应用启动时调用（替换/移除封面后的旧文件会在下次启动时回收）。
 * 返回删除的文件数。
 */
suspend fun cleanupOrphanCovers(context: Context, referencedPaths: Set<String>): Int =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "covers")
            var deleted = 0
            dir.listFiles()?.forEach { file ->
                if (file.absolutePath !in referencedPaths && file.delete()) deleted++
            }
            deleted
        } catch (e: Exception) {
            0
        }
    }
