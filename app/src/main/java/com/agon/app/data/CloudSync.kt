package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.time.Clock
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
/**
 * 401 时给用户看的那句话。
 *
 * 抽成常量**不是为了少写几个字**：[toOpFailure] 要靠它把「凭据错」从其它失败里认出来。
 * 同一句话若在抛出点与分类点各写一遍，改一处忘一处就会**静默失去分类**（编译不报错、测试不报错，
 * 只是某天所有凭据错都变成 [OpFailure.Other]）。
 */
const val NUTSTORE_AUTH_MESSAGE = "账号或应用密码错误"

/**
 * 坚果云同步失败的**分类**（路线图 #4c，2026-09-18）。
 *
 * 改造前是什么样：`NutstoreSync` 的三个方法都返回 `Result`，但 VM 一句 `it.message ?: "上传失败"`
 * 就把它压平成 `(Boolean, String)` 交给界面 —— 类型信息全丢，界面只能"有字就显示"。后果有两个：
 * 1. **UI 无法按失败种类做事**：凭据错本该引导用户重填账号密码，网络错本该建议稍后重试，
 *    但两者到手都是同一个 `String`；
 * 2. 网络类异常（OkHttp 的 [IOException]：DNS 解析失败、连接超时…）的 `message` 是**英文技术串**，
 *    被原样甩给用户。
 *
 * ⚠️ **本轮只加类型、不改一个字的文案** —— 包括上面那个英文串照旧透出。
 * 改文案是用户可见的行为变更，按本仓规矩要单独提交 + 真机复测，已登记为待用户决策项
 * （见 `devlog/2026-09-18.md`）。所以 [message] 与改造前逐字相同，`OpFailureTest` 钉住这一点。
 *
 * 只有三类，不是四类：HTTP 状态码类失败（`上传失败（HTTP 507）`）**没有**单列一档，
 * 因为状态码本来就在文案里、用户看得见，而 UI 目前对 507 与 500 没有任何不同处理 ——
 * 为一档没人区分的情况加一个类型，只是让 `when` 多一个分支。真要按状态码分流时再加。
 */
sealed interface OpFailure {
    /** 给用户看的话（与改造前逐字一致）。 */
    val message: String

    /** 401：账号或应用密码不对。重试没用，得改凭据。 */
    data class Auth(override val message: String) : OpFailure

    /** 网络层失败（DNS / 超时 / 连接中断）：稍后重试有意义。 */
    data class Network(override val message: String) : OpFailure

    /** 其它：HTTP 状态码类、云端备份为空、格式不对……状态码已在 [message] 文本里。 */
    data class Other(override val message: String) : OpFailure
}

/**
 * 把 [NutstoreSync] 那三个 `Result` 里的异常归类。
 *
 * @param fallback 异常没带消息（或消息为空白）时给用户看的兜底话 —— 各调用点原本就各有各的兜底
 *   （"上传失败" / "获取备份列表失败" / "下载失败"），逐字保留。
 */
fun Throwable.toOpFailure(fallback: String): OpFailure {
    val text = message?.takeIf { it.isNotBlank() } ?: fallback
    return when {
        // UnknownHostException / SocketTimeoutException 都是 IOException 的子类，一并归到网络类。
        this is IOException -> OpFailure.Network(text)
        text == NUTSTORE_AUTH_MESSAGE -> OpFailure.Auth(text)
        else -> OpFailure.Other(text)
    }
}

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
            if (resp.code == 401) error(NUTSTORE_AUTH_MESSAGE)
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
                val auth = authOf(account, password)
                ensureDir(auth)
                val fileName = PREFIX +
                    LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                    ".json"
                val put = request("$BASE_URL/$DIR/$fileName", auth)
                    .put(json.toRequestBody(JSON_TYPE))
                    .build()
                client.newCall(put).execute().use { resp ->
                    if (resp.code == 401) error(NUTSTORE_AUTH_MESSAGE)
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
                resp.code == 401 -> error(NUTSTORE_AUTH_MESSAGE)
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
                        resp.code == 401 -> error(NUTSTORE_AUTH_MESSAGE)
                        resp.code == 404 -> error("该备份已不存在，请刷新列表")
                        !resp.isSuccessful -> error("下载失败（HTTP ${resp.code}）")
                        else -> resp.body?.string()?.takeIf { it.isNotBlank() }
                            ?: error("云端备份为空")
                    }
                }
            }
        }

    /** 解析 PROPFIND 响应，提取备份文件名与大小（容忍不同命名空间前缀）。 */
    internal fun parsePropfind(xml: String): List<CloudBackup> {
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

/**
 * 自动同步的**间隔判定**（#5b 从 `AppViewModel.maybeAutoSync()` 里抽出来的纯函数）。
 *
 * 抽出来只有一个理由：这段逻辑此前**没法测** —— 它长在需要 Android 环境的 VM 里，
 * 而它偏偏是「跨零点」最敏感的一处（差一天就同步、差一天就不同步）。抽成纯函数后
 * `AutoSyncDueTest` 能把边界钉死；行为逐位不变（原来写的是 `if (today - last < days) return`，
 * 取反即此式）。
 *
 * ⚠️ 「间隔 <= 0 表示关闭自动同步」那条判断**留在 VM 里**、刻意不并进来：它必须在读凭据、
 * 算今天之前就先短路（省掉两次 DataStore 读与一次日期计算），并进来就改变了读取顺序 ——
 * 结果虽一样，但那就不是纯搬运了。
 *
 * @param lastSyncEpochDay 上次自动同步那天的 epochDay（从未同步过时仓库给的是 0 ⇒ 必然到期）
 * @param todayEpochDay 今天的 epochDay —— 调用方从注入的时钟取，不再直接问系统
 * @param intervalDays 用户设的间隔天数（调用方已保证 > 0）
 */
internal fun isAutoSyncDue(lastSyncEpochDay: Long, todayEpochDay: Long, intervalDays: Int): Boolean =
    todayEpochDay - lastSyncEpochDay >= intervalDays
