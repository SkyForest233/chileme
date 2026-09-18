package com.agon.app.data

import android.util.Log
import androidx.datastore.preferences.core.edit

/**
 * 仓库的**凭据域：坚果云账号与密码的加密写入、以及旧版明文密码的启动迁移**（路线图 #5c，2026-09-18）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选"门面转发"和"领域对象"，
 * 完整取舍写在 `RepositoryCore.kt` 的文件 KDoc 里（一次说清，别处只指路）。
 *
 * 对外调用写法一个字没变：同包内扩展函数用隐式接收者就能解析；唯一要改的是**别的包**的调用方
 * —— `AppViewModel` 为搬走的每个函数加一行 import（本仓禁通配导入）。
 */

// 与 `FoodRepository.kt` / `RepositoryCore.kt` 里的 TAG 是同一个字符串的副本，理由见那边的注释
// （本包已有多个文件级 private TAG，升成包级常量可能撞名，而本地没编译器验证不了）。
private const val TAG = "FoodRepository"

/** 启动时迁移：若存在旧版明文密码，加密后写入新 key 并删除明文。 */
internal suspend fun FoodRepository.migratePlaintextPassword() {
    dataStore.edit { prefs ->
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
    dataStore.edit { prefs ->
        prefs[nutstoreAccountKey] = account.trim()
        val enc = SecureStore.encrypt(password.trim())
        if (enc != null) {
            prefs[nutstorePasswordEncKey] = enc
            prefs.remove(nutstorePasswordKey) // 确保明文不再落盘
        } else {
            // Keystore 不可用的极端回退：为了不打断同步功能只能先存明文，
            // 但**绝不能静默**——记日志 + 由 nutstorePlaintextFallbackFlow 让设置页提示用户。
            // 下次启动 migratePlaintextPassword 会重试加密。
            Log.e(TAG, "凭据加密失败（Keystore 不可用？），本次以未加密形式保存，将于下次启动重试")
            prefs[nutstorePasswordKey] = password.trim()
        }
    }
}
