package com.agon.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-GCM 加解密工具。
 * 用于加密存储坚果云应用密码 —— 密钥由系统 Keystore 托管，
 * 不落盘、不可导出，即使 DataStore 文件泄露也无法解密。
 */
object SecureStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "chileme_webdav_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG = "SecureStore"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * 返回 "base64(iv):base64(ciphertext)"；失败（Keystore 不可用等）返回 null 并记日志。
     *
     * 2026-09-15 修正：此前失败返回**空串**，调用方无法把「加密失败」与「加密成功」区分开，
     * 于是 `prefs[nutstorePasswordEncKey] = ""` 会覆盖掉已有密文、同时删掉明文键 —— 凭据直接丢失；
     * 而「Keystore 不可用」这种降级也不会被任何 UI 感知（静默落明文）。改成可空返回后，
     * 调用方可以显式走 `nutstorePlaintextFallbackFlow` 提示用户，并在下次启动重试加密。
     */
    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ct = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$iv:$ct"
    } catch (e: Exception) {
        Log.e(TAG, "凭据加密失败（Keystore 不可用？），调用方需回退并提示用户", e)
        null
    }

    /** 解密；失败（包括密钥丢失/数据损坏）返回 null 并记日志。 */
    fun decrypt(stored: String): String? = try {
        val parts = stored.split(":", limit = 2)
        if (parts.size != 2) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        Log.w(TAG, "凭据解密失败（系统 Keystore 密钥丢失或数据损坏）", e)
        null
    }
}
