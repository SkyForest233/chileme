/*
 * 坚果云云端备份的**业务层**（09-19 #11c 之后这个文件只剩三件事：上传 + 轮转、列列表、下载）。
 *
 * 怎么跟 WebDAV 说话在 `NutstoreWebdav.kt`；失败怎么变成用户看得见的分类在 `OpFailure.kt`；
 * 「今天该不该自动同步」在 `AutoSyncPolicy.kt`。本层管的是业务规则：
 * 云端文件名里放什么（时间戳，且必须单调递增 —— 轮转就是按名字倒序删的）、留几份（[CLOUD_BACKUP_KEEP]）、
 * 旧版单文件怎么处理（进列表、不参与删除）。
 */
package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 云端保留的备份份数：本次 + 之前 2 次 */
const val CLOUD_BACKUP_KEEP = 3

/** 云端备份条目（文件名内嵌时间戳，按名倒序即按时间倒序） */
data class CloudBackup(val fileName: String, val sizeBytes: Long) {
    val isLegacy: Boolean get() = fileName == NutstoreWebdav.LEGACY_FILE_NAME

    /** 可读时间，如 "2026年7月31日 14:05:30"；旧版单文件备份无时间戳 */
    val displayTime: String
        get() = if (isLegacy) "旧版备份（无时间信息）" else runCatching {
            val ts = fileName.removePrefix(NutstoreWebdav.PREFIX).removeSuffix(".json")
            LocalDateTime.parse(ts, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"))
        }.getOrDefault(fileName)

    val displaySize: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / 1024f / 1024f)
            sizeBytes >= 1024 -> "%.1f KB".format(sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }
}

/**
 * 坚果云 WebDAV 云同步（多版本轮转）。
 *
 * 用户需在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中
 * 添加应用并生成应用密码（不是登录密码）。
 *
 * 备份文件存放于坚果云 ChiLeMe/ 目录：
 * - 新版：chileme_backup_yyyyMMdd_HHmmss.json，上传后自动轮转，最多保留 [CLOUD_BACKUP_KEEP] 份
 * - 兼容旧版单文件 chileme_backup.json：会出现在恢复列表中，且不参与轮转删除
 */
object NutstoreSync {
    /**
     * 上传新备份（时间戳文件名）并轮转清理：
     * 上传成功后仅保留最近 [CLOUD_BACKUP_KEEP] 份新版备份，更旧的自动删除。
     * 旧版单文件备份不受影响。
     */
    suspend fun upload(
        account: String,
        password: String,
        json: String,
        // #5b：云端备份文件名里的时间戳向注入的时钟要（默认值 = 改造前的系统时钟，行为不变）。
        // 轮转按文件名的时间戳倒序，所以这个名字必须继续单调递增 —— 换时钟不改这一点。
        clock: Clock = Clock.systemDefaultZone(),
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = NutstoreWebdav.authOf(account, password)
                NutstoreWebdav.ensureDir(auth)
                val fileName = NutstoreWebdav.PREFIX +
                    LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".json"
                val put = NutstoreWebdav.request(NutstoreWebdav.urlOf(fileName), auth)
                    .put(NutstoreWebdav.jsonBody(json))
                    .build()
                NutstoreWebdav.client.newCall(put).execute().use { resp ->
                    if (resp.code == 401) error(NUTSTORE_AUTH_MESSAGE)
                    if (!resp.isSuccessful) error("上传失败（HTTP ${resp.code}）")
                }
                // 轮转：删除多余的旧版本（仅限新版时间戳文件）
                val versioned = NutstoreWebdav.listDir(auth).filter { !it.isLegacy }
                versioned.drop(CLOUD_BACKUP_KEEP).forEach { old ->
                    runCatching {
                        NutstoreWebdav.client.newCall(
                            NutstoreWebdav.request(NutstoreWebdav.urlOf(old.fileName), auth).delete().build()
                        ).execute().close()
                    }
                }
            }
        }

    /** 列出云端全部备份，新的在前；旧版单文件（如存在）排在最后。 */
    suspend fun listBackups(account: String, password: String): Result<List<CloudBackup>> =
        withContext(Dispatchers.IO) {
            runCatching { NutstoreWebdav.listDir(NutstoreWebdav.authOf(account, password)) }
        }

    /** 下载指定备份文件的 JSON 内容。 */
    suspend fun download(account: String, password: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val get = NutstoreWebdav.request(
                    NutstoreWebdav.urlOf(fileName), NutstoreWebdav.authOf(account, password)
                )
                    .get()
                    .build()
                NutstoreWebdav.client.newCall(get).execute().use { resp ->
                    when {
                        resp.code == 401 -> error(NUTSTORE_AUTH_MESSAGE)
                        resp.code == 404 -> error("该备份已不存在，请刷新列表")
                        !resp.isSuccessful -> error("下载失败（HTTP ${resp.code}）")
                        else -> resp.body?.string()?.takeIf { it.isNotBlank() }
                            ?: error("云端备份为空")
                    }
                }
            }
        }
}
