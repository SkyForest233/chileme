package com.agon.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "BackupFile"

/**
 * 导入备份的读取上限。
 *
 * 正常备份是几十 KB ~ 几 MB；给 20 MB 的余量足以覆盖重度用户，
 * 又能拦住「误选了一个几百 MB 的文件 → `readBytes()` 直接 OOM」。
 */
const val MAX_BACKUP_BYTES = 20L * 1024 * 1024

/**
 * 从系统文件选择器（SAF）返回的 [uri] 读取备份 JSON 文本。
 *
 * 相比原先直接 `openInputStream(uri).readBytes()`（合并前 MD3 / Miuix 两版设置页各一份），
 * 这里补了三件事：
 * 1. 先查 `AssetFileDescriptor.length`（多数 provider 都提供），**在分配内存之前**就能拒绝超大文件；
 * 2. 读取后再校验一次实际字节数（provider 未声明长度时的兜底）；
 * 3. 整体切到 [Dispatchers.IO]，避免在主线程做文件 IPC 读。
 *
 * @return 备份文本；读取失败或超过 [MAX_BACKUP_BYTES] 时返回 null（调用方据此提示用户）。
 */
suspend fun readBackupText(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val declaredLength = context.contentResolver
                .openAssetFileDescriptor(uri, "r")
                ?.use { it.length }
                ?: -1L
            if (declaredLength > MAX_BACKUP_BYTES) {
                Log.w(TAG, "备份文件过大（$declaredLength 字节），已拒绝读取")
                return@runCatching null
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            if (bytes.size > MAX_BACKUP_BYTES) {
                Log.w(TAG, "备份文件过大（${bytes.size} 字节），已拒绝导入")
                return@runCatching null
            }
            bytes.toString(Charsets.UTF_8)
        }
            .onFailure { Log.e(TAG, "读取备份文件失败", it) }
            .getOrNull()
    }
