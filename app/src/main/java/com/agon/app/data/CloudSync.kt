package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 坚果云 WebDAV 云同步。
 *
 * 用户需在坚果云网页端「账户信息 → 安全选项 → 第三方应用管理」中
 * 添加应用并生成应用密码（不是登录密码）。
 * 备份文件存放于坚果云根目录下 ChiLeMe/chileme_backup.json。
 */
object NutstoreSync {
    private const val BASE_URL = "https://dav.jianguoyun.com/dav"
    private const val DIR = "ChiLeMe"
    private const val FILE_NAME = "chileme_backup.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 上传备份 JSON。自动创建目录（已存在时坚果云返回 405，视为成功）。 */
    suspend fun upload(account: String, password: String, json: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = Credentials.basic(account.trim(), password.trim())
                val mkcol = Request.Builder()
                    .url("$BASE_URL/$DIR/")
                    .method("MKCOL", null)
                    .header("Authorization", auth)
                    .build()
                client.newCall(mkcol).execute().use { resp ->
                    if (resp.code == 401) error("账号或应用密码错误")
                    if (!resp.isSuccessful && resp.code != 405) {
                        error("创建云端目录失败（HTTP ${resp.code}）")
                    }
                }
                val put = Request.Builder()
                    .url("$BASE_URL/$DIR/$FILE_NAME")
                    .put(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Authorization", auth)
                    .build()
                client.newCall(put).execute().use { resp ->
                    if (resp.code == 401) error("账号或应用密码错误")
                    if (!resp.isSuccessful) error("上传失败（HTTP ${resp.code}）")
                }
            }
        }

    /** 下载云端备份 JSON 内容。 */
    suspend fun download(account: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = Credentials.basic(account.trim(), password.trim())
                val get = Request.Builder()
                    .url("$BASE_URL/$DIR/$FILE_NAME")
                    .get()
                    .header("Authorization", auth)
                    .build()
                client.newCall(get).execute().use { resp ->
                    when {
                        resp.code == 401 -> error("账号或应用密码错误")
                        resp.code == 404 -> error("云端暂无备份，请先上传")
                        !resp.isSuccessful -> error("下载失败（HTTP ${resp.code}）")
                        else -> resp.body?.string()?.takeIf { it.isNotBlank() }
                            ?: error("云端备份为空")
                    }
                }
            }
        }
}
