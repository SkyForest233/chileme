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
 *
 * 三条约定（2026-09-19 M1-4 起成立，改这个文件前先读）：
 * 1. **只有写侧建钥**：[encrypt] 走 `getOrCreateKey()`，[decrypt] 走 `readKey()`；
 * 2. **建钥冲突不当故障**：`generateKey()` 抛异常后先看"别名现在存不存在"，在就复用那把；
 * 3. **失败一律返回 null**（不返回空串），调用方据此区分"加密失败"与"加密成功"，
 *    见 [encrypt] 的 KDoc 与 `CorruptGuardTest` 那条守卫。
 */
object SecureStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "chileme_webdav_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG = "SecureStore"

    /**
     * 只读已有密钥，**绝不创建**（读侧专用，见 [decrypt]）。
     * `load(null)` 是 AndroidKeyStore 的要求：它不从文件加载，只是把 keystore 打开。
     */
    private fun readKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    /**
     * 取密钥，没有就建一把。
     *
     * 两条并发路径会在**同一台设备的首次使用**上撞上（M1-4，2026-09-19）：启动时的
     * `migratePlaintextPassword()`（跑在 `viewModelScope`/Main）与用户在设置页点保存
     * （`setNutstoreCredentials`）都会走到这里。两边都先 `getEntry(...) == null`、都去
     * `generateKey()` ⇒ 后到的那个抛 `KeyAlreadyExistsException` ⇒ 一路被 [encrypt] 的
     * `catch (e: Exception)` 吞成 null ⇒ 调用方判定"Keystore 不可用"，把**明文密码落盘**并提示用户
     * 安全性降级。而密钥其实建好了、好好的：用户被告知加密坏了，下次启动的 `migratePlaintextPassword`
     * 还要再赌一次。
     *
     * 修法两层，缺一不可：
     * - `synchronized` 把「读→建」串行化，消除同进程内绝大多数撞车；
     * - **建完撞上就把它读回来**：别名已存在不是故障，是"别人刚建好了"，而已有那把正是我们要的
     *   ⇒ 重读一次即可，不降级、不报错。这层覆盖 `synchronized` 挡不到的情况
     *   （另一条线程已经进了系统调用、或进程刚被重启）。
     *
     * ⚠️ 加锁范围刻意只包住"读→建"，不包住加密本身：`Cipher.doFinal` 才是耗时项，
     * 圈进来会让并发的凭据读写全部串行。
     */
    private val keyCreationLock = Any()

    private fun getOrCreateKey(): SecretKey = synchronized(keyCreationLock) {
        readKey() ?: run {
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
            try {
                generator.generateKey()
            } catch (e: Exception) {
                // 判据不看异常类型，只看「密钥现在到底在不在」：
                // 别名冲突在不同 API/OEM 路径下会被包成 `KeyAlreadyExistsException`、
                // `KeyStoreException` 或 `ProviderException`（AOSP 里密钥对与对称密钥走的是不同的抛法），
                // 只 catch 前者会漏。而"现在读得到"就足够说明这局是撞车不是坏了 ⇒ 读回来用；
                // 读不到才把原异常交回 encrypt 的降级路径。
                val existing = readKey()
                if (existing == null) throw e
                Log.w(TAG, "generateKey 失败但 $KEY_ALIAS 已存在（并发建钥），改用现存的那把", e)
                existing
            }
        }
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

    /**
     * 解密；失败（包括密钥丢失/数据损坏）返回 null 并记日志。
     *
     * 用 [readKey] 而不是 [getOrCreateKey]：**读侧建一把新钥是纯伤害**。换机/恢复备份后本机没有
     * 对应密钥，此时旧密文照样解不开（`doFinal` 抛 AEADBadTagException，结果仍是 null），
     * 但白建了一把与任何数据都无关的密钥、还顺手把它占在 `KEY_ALIAS` 上；而 `decrypt` 是
     * `nutstorePasswordFlow` 每次重发都会走的路径 ⇒ 一个"读"操作带写副作用，还会与并发加密抢建
     * （正是 M1-4 修的那个撞车）。缺钥就该直说缺钥，让 `nutstoreCredentialBrokenFlow` 那条 UI 提示接管。
     */
    fun decrypt(stored: String): String? = try {
        val parts = stored.split(":", limit = 2)
        val key = readKey()
        if (key == null) {
            Log.w(TAG, "凭据解密失败：本机没有 Keystore 密钥（换机恢复或清除凭据后属预期，需用户重填）")
            null
        } else if (parts.size != 2) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        Log.w(TAG, "凭据解密失败（系统 Keystore 密钥丢失或数据损坏）", e)
        null
    }
}
