package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * 仓库的**凭据域：坚果云账号与密码的加密写入、旧版明文密码的启动迁移、以及凭据文件的搬迁**（路线图 #5c，2026-09-18；
 * 凭据独立 DataStore 为 2026-09-19 M1-1）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选"门面转发"和"领域对象"，
 * 完整取舍写在 `RepositoryCore.kt` 的文件 KDoc 里（一次说清，别处只指路）。
 *
 * 对外调用写法一个字没变：同包内扩展函数用隐式接收者就能解析；唯一要改的是**别的包**的调用方
 * —— `AppViewModel` 为搬走的每个函数加一行 import（本仓禁通配导入）。
 *
 * ⚠️ **本文件所有写入口都写 `credentialsStore`，不写 `dataStore`**：业务数据那份文件随系统备份走
 * （换机/重装能把库存找回来），凭据文件则被两条备份通道整体排除（Keystore 密钥不跨设备，
 * 备份过去也解不开；明文回退期间更不能把密钥推上云端）。两个文件为什么要拆开、以及拆掉的是什么，
 * 见 `FoodRepository.kt` 里 `credentialsDataStore` 的注释与 `docs/ARCHITECTURE.md` §5「备份排除规则」。
 */

// 与 `FoodRepository.kt` / `RepositoryCore.kt` 里的 TAG 是同一个字符串的副本，理由见那边的注释
// （本包已有多个文件级 private TAG，升成包级常量可能撞名，而本地没编译器验证不了）。
private const val TAG = "FoodRepository"

/**
 * 启动迁移（M1-1）：把**旧版存在业务数据文件里**的坚果云三个 key 搬到凭据文件，然后从旧文件删掉。
 *
 * 为什么必须有这一步：拆文件之前 `nutstore_account` / `nutstore_password` / `nutstore_password_enc`
 * 都住在 `pantry_store` 里。直接改读 `credentials_store` 的话，**所有老用户会集体"凭据丢失"**——
 * 账号框变空、同步按钮禁用，而那句 `nutstoreCredentialBrokenFlow` 的告警也救不了他们（它只报"有密文解不开"，
 * 此时新文件里连密文都没有）。
 *
 * 顺序刻意是**先写新、后删旧**：中间任何一步进程被杀，最坏结果只是"两处各有一份"（读侧只看新文件 ⇒ 行为正确，
 * 下次启动再跑一次本函数时旧 key 仍在、幂等重写）；反过来先删后写就会真的丢凭据。
 * 三个 key 全为空时直接返回，不产生任何一次多余的 `edit`（每次 edit 都会让对应文件的所有读流重发一遍）。
 *
 * 明文 key 原样搬、**不顺手加密**：加密是 [migratePlaintextPassword] 的职责（它有自己的 Keystore 可用性判定与
 * 失败降级路径），两件事各走各的，失败语义才不会互相纠缠。
 */
internal suspend fun FoodRepository.migrateLegacyCredentials() {
    val legacy = runCatching { dataStore.data.first() }.getOrNull() ?: return
    val account = legacy[nutstoreAccountKey]
    val plain = legacy[nutstorePasswordKey]
    val enc = legacy[nutstorePasswordEncKey]
    if (account.isNullOrBlank() && plain.isNullOrBlank() && enc.isNullOrBlank()) return
    credentialsStore.edit { creds ->
        if (!account.isNullOrBlank()) creds[nutstoreAccountKey] = account
        if (!plain.isNullOrBlank()) creds[nutstorePasswordKey] = plain
        if (!enc.isNullOrBlank()) creds[nutstorePasswordEncKey] = enc
    }
    dataStore.edit { prefs ->
        prefs.remove(nutstoreAccountKey)
        prefs.remove(nutstorePasswordKey)
        prefs.remove(nutstorePasswordEncKey)
    }
    Log.i(TAG, "凭据已从业务数据文件迁到 credentials_store（明文与密文按原样搬，加密由 migratePlaintextPassword 负责）")
}

/** 启动时迁移：若存在旧版明文密码，加密后写入新 key 并删除明文。 */
internal suspend fun FoodRepository.migratePlaintextPassword() {
    credentialsStore.edit { prefs ->
        val plain = prefs[nutstorePasswordKey]
        if (!plain.isNullOrBlank()) {
            val enc = SecureStore.encrypt(plain)
            if (enc != null) {
                prefs[nutstorePasswordEncKey] = enc
                prefs.remove(nutstorePasswordKey)
            }
        }
    }
}

internal suspend fun FoodRepository.setNutstoreCredentials(account: String, password: String) {
    credentialsStore.edit { prefs ->
        prefs[nutstoreAccountKey] = account.trim()
        val enc = SecureStore.encrypt(password.trim())
        if (enc != null) {
            prefs[nutstorePasswordEncKey] = enc
            prefs.remove(nutstorePasswordKey) // 确保明文不再落盘
        } else {
            // Keystore 不可用的极端回退：为了不打断同步功能只能先存明文，
            // 但**绝不能静默**——记日志 + 由 nutstorePlaintextFallbackFlow 让设置页提示用户。
            // 下次启动 migratePlaintextPassword 会重试加密。
            // 明文的落点也是 credentials_store（它被备份规则排除），不是随系统备份走的 dataStore。
            Log.e(TAG, "凭据加密失败（Keystore 不可用？），本次以未加密形式保存，将于下次启动重试")
            prefs[nutstorePasswordKey] = password.trim()
        }
    }
}
