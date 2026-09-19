/*
 * 坚果云 WebDAV 的**协议层**（09-19 #11c ② 从 `CloudSync.kt` 的 `object NutstoreSync` 里搬出）。
 *
 * 这一层只管「怎么跟 WebDAV 说话」：客户端超时怎么配、鉴权头怎么拼、目录怎么建（MKCOL）、
 * 目录怎么列（PROPFIND + 容忍不同命名空间前缀的解析）、云端文件名怎么认。
 * 它不认识「保留 3 份」「旧版单文件排在最后」这类业务规则 —— 那几条留在 `CloudSync.kt`。
 *
 * 为什么值得单独一层（`docs/ROADMAP.md` #11c 的理由）：P1 清单里的 **OkHttp 5 升级**要动的只有这里。
 * 这条边界由守卫 `data/CloudSyncLocationTest` 钉住：`data/` 下 `okhttp3.` 开头的 import
 * 只允许出现在本文件 —— 所以编排层要发 body 时走 [NutstoreWebdav.jsonBody]，不自己 import 扩展函数。
 */
package com.agon.app.data

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * WebDAV 细节。成员全部整块搬自改造前的 `NutstoreSync`，逻辑一字未改；
 * 唯一新增的是 [ROOT_URL] / [urlOf] / [jsonBody] 三个「少写一点」的辅助，
 * 它们的作用只是把 `okhttp3.*` 关在本文件里。
 */
internal object NutstoreWebdav {
    private const val BASE_URL = "https://dav.jianguoyun.com/dav"
    private const val DIR = "ChiLeMe"

    /** 云端根目录（`…/dav/ChiLeMe`）。*/
    private const val ROOT_URL = "$BASE_URL/$DIR"

    /** 旧版单文件备份的名字（不参与轮转删除）。*/
    const val LEGACY_FILE_NAME = "chileme_backup.json"

    /** 新版备份名的前缀。⚠️ 文件名必须继续单调递增 —— 轮转是按名字倒序排的。*/
    const val PREFIX = "chileme_backup_"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val XML_TYPE = "text/xml; charset=utf-8".toMediaType()

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun request(url: String, auth: String) =
        Request.Builder().url(url).header("Authorization", auth)

    /** 云端目录里某个文件的完整 URL（拆分前各处都是手写 `"$BASE_URL/$DIR/$name"`）。*/
    fun urlOf(name: String) = "$ROOT_URL/$name"

    /** body 只有一种类型（JSON），所以 `toRequestBody` 扩展留在这层，编排层不必 import okhttp。*/
    fun jsonBody(text: String) = text.toRequestBody(JSON_TYPE)

    fun authOf(account: String, password: String) =
        Credentials.basic(account.trim(), password.trim())

    /** 自动创建目录（已存在时坚果云返回 405，视为成功）。*/
    fun ensureDir(auth: String) {
        val mkcol = request("$ROOT_URL/", auth).method("MKCOL", null).build()
        client.newCall(mkcol).execute().use { resp ->
            if (resp.code == 401) error(NUTSTORE_AUTH_MESSAGE)
            if (!resp.isSuccessful && resp.code != 405) {
                error("创建云端目录失败（HTTP ${resp.code}）")
            }
        }
    }

    /** 列云端目录：新的在前；旧版单文件（如存在）排在最后。*/
    fun listDir(auth: String): List<CloudBackup> {
        val body =
            """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:getcontentlength/></D:prop></D:propfind>"""
        val propfind = request("$ROOT_URL/", auth)
            .method("PROPFIND", body.toRequestBody(XML_TYPE))
            .header("Depth", "1")
            .build()
        val xml = client.newCall(propfind).execute().use { resp ->
            when {
                resp.code == 401 -> error(NUTSTORE_AUTH_MESSAGE)
                resp.code == 404 -> return emptyList()
                resp.code >= 400 -> error("获取云端备份列表失败（HTTP ${resp.code}）")
                else -> resp.body?.string() ?: ""
            }
        }
        val (versioned, legacy) = parsePropfind(xml).partition { !it.isLegacy }
        return versioned.sortedByDescending { it.fileName } + legacy
    }

    /** 解析 PROPFIND 响应，提取备份文件名与大小（容忍不同命名空间前缀）。*/
    fun parsePropfind(xml: String): List<CloudBackup> {
        val blocks = xml.split(Regex("</[a-zA-Z0-9]*:?response>", RegexOption.IGNORE_CASE))
        val hrefRegex =
            Regex("<[a-zA-Z0-9]*:?href>([^<]+)</[a-zA-Z0-9]*:?href>", RegexOption.IGNORE_CASE)
        val sizeRegex =
            Regex("<[a-zA-Z0-9]*:?getcontentlength[^>]*>(\\d+)<", RegexOption.IGNORE_CASE)
        // 原来是 for + 两个 continue（href 缺失 / 不是备份文件），detekt 的
        // LoopWithTooManyJumpStatements（阈值 1）报「一个循环里跳转太多」。换成 mapNotNull：
        // 语义等价（顺序不变、两种跳过都变成返回 null）、跳转语句 0 条。
        // 行为由 CloudBackupTest 的 3 条 parsePropfind 断言兜住（新版+旧版、忽略非备份、空响应）。
        return blocks.mapNotNull { block ->
            val href = hrefRegex.find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val name = URLDecoder.decode(href, "UTF-8").trimEnd('/').substringAfterLast('/')
            val isBackup = name == LEGACY_FILE_NAME ||
                (name.startsWith(PREFIX) && name.endsWith(".json"))
            if (!isBackup) return@mapNotNull null
            val size = sizeRegex.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            CloudBackup(name, size)
        }
    }
}
