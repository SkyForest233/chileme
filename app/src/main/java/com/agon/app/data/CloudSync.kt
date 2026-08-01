package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 云端保留的备份份数：本次 + 之前 2 次 */
const val CLOUD_BACKUP_KEEP = 3

/** 云端备份条目（文件名内嵌时间戳，按名倒序即按时间倒序） */
data class CloudBackup(val fileName: String, val sizeBytes: Long) {
    val isLegacy: Boolean get() = fileName == NutstoreSync.LEGACY_FILE_NAME

    /** 可读时间，如 "2026年7月31日 14:05:30"；旧版单文件备份无时间戳 */
    val displayTime: String
        get() = if (isLegacy) "旧版备份（无时间信息）" else runCatching {
            val ts = fileName.removePrefix("chileme_backup_").removeSuffix(".json")
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
    private const val BASE_URL = "https://dav.jianguoyun.com/dav"
    private const val DIR = "ChiLeMe"
    const val LEGACY_FILE_NAME = "chileme_backup.json"
    private const val PREFIX = "chileme_backup_"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val XML_TYPE = "text/xml; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun request(url: String, auth: String) =
        Request.Builder().url(url).header("Authorization", auth)

    private fun authOf(account: String, password: String) =
        Credentials.basic(account.trim(), password.trim())

    /** 自动创建目录（已存在时坚果云返回 405，视为成功）。 */
    private fun ensureDir(auth: String) {
        val mkcol = request("$BASE_URL/$DIR/", auth).method("MKCOL", null).build()
        client.newCall(mkcol).execute().use { resp ->
            if (resp.code == 401) error("账号或应用密码错误")
            if (!resp.isSuccessful && resp.code != 405) {
                error("创建云端目录失败（HTTP ${resp.code}）")
            }
        }
    }

    /**
     * 上传新备份（时间戳文件名）并轮转清理：
     * 上传成功后仅保留最近 [CLOUD_BACKUP_KEEP] 份新版备份，更旧的自动删除。
     * 旧版单文件备份不受影响。
     */
    suspend fun upload(account: String, password: String, json: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = authOf(account, password)
                ensureDir(auth)
                val fileName = PREFIX +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".json"
                val put = request("$BASE_URL/$DIR/$fileName", auth)
                    .put(json.toRequestBody(JSON_TYPE))
                    .build()
                client.newCall(put).execute().use { resp ->
                    if (resp.code == 401) error("账号或应用密码错误")
                    if (!resp.isSuccessful) error("上传失败（HTTP ${resp.code}）")
                }
                // 轮转：删除多余的旧版本（仅限新版时间戳文件）
                val versioned = listInternal(auth).filter { !it.isLegacy }
                versioned.drop(CLOUD_BACKUP_KEEP).forEach { old ->
                    runCatching {
                        client.newCall(
                            request("$BASE_URL/$DIR/${old.fileName}", auth).delete().build()
                        ).execute().close()
                    }
                }
            }
        }

    /** 列出云端全部备份，新的在前；旧版单文件（如存在）排在最后。 */
    suspend fun listBackups(account: String, password: String): Result<List<CloudBackup>> =
        withContext(Dispatchers.IO) {
            runCatching { listInternal(authOf(account, password)) }
        }

    private fun listInternal(auth: String): List<CloudBackup> {
        val body =
            """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:getcontentlength/></D:prop></D:propfind>"""
        val propfind = request("$BASE_URL/$DIR/", auth)
            .method("PROPFIND", body.toRequestBody(XML_TYPE))
            .header("Depth", "1")
            .build()
        val xml = client.newCall(propfind).execute().use { resp ->
            when {
                resp.code == 401 -> error("账号或应用密码错误")
                resp.code == 404 -> return emptyList()
                resp.code >= 400 -> error("获取云端备份列表失败（HTTP ${resp.code}）")
                else -> resp.body?.string() ?: ""
            }
        }
        val (versioned, legacy) = parsePropfind(xml).partition { !it.isLegacy }
        return versioned.sortedByDescending { it.fileName } + legacy
    }

    /** 下载指定备份文件的 JSON 内容。 */
    suspend fun download(account: String, password: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val get = request("$BASE_URL/$DIR/$fileName", authOf(account, password))
                    .get()
                    .build()
                client.newCall(get).execute().use { resp ->
                    when {
                        resp.code == 401 -> error("账号或应用密码错误")
                        resp.code == 404 -> error("该备份已不存在，请刷新列表")
                        !resp.isSuccessful -> error("下载失败（HTTP ${resp.code}）")
                        else -> resp.body?.string()?.takeIf { it.isNotBlank() }
                            ?: error("云端备份为空")
                    }
                }
            }
        }

    /** 解析 PROPFIND 响应，提取备份文件名与大小（容忍不同命名空间前缀）。 */
    private fun parsePropfind(xml: String): List<CloudBackup> {
        val results = mutableListOf<CloudBackup>()
        val blocks = xml.split(Regex("</[a-zA-Z0-9]*:?response>", RegexOption.IGNORE_CASE))
        val hrefRegex =
            Regex("<[a-zA-Z0-9]*:?href>([^<]+)</[a-zA-Z0-9]*:?href>", RegexOption.IGNORE_CASE)
        val sizeRegex =
            Regex("<[a-zA-Z0-9]*:?getcontentlength[^>]*>(\\d+)<", RegexOption.IGNORE_CASE)
        for (block in blocks) {
            val href = hrefRegex.find(block)?.groupValues?.get(1) ?: continue
            val name = URLDecoder.decode(href, "UTF-8").trimEnd('/').substringAfterLast('/')
            val isBackup = name == LEGACY_FILE_NAME ||
                (name.startsWith(PREFIX) && name.endsWith(".json"))
            if (!isBackup) continue
            val size = sizeRegex.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            results.add(CloudBackup(name, size))
        }
        return results
    }
}
