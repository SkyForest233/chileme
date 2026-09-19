package com.agon.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 凭据与业务数据**分文件**存放的行为测试（2026-09-19，M1-1）。
 *
 * 要钉住的事实只有一条：**任何形态的凭据（账号、密文、Keystore 不可用时的明文回退）都不得出现在
 * 会随系统备份走的 `pantry_store` 里**；反之业务数据不得被排除出备份。
 *
 * 背景：此前 19 个 key 同住 `pantry_store`，为了让 Keystore 密文不外泄，两份备份规则把整个
 * `datastore/` 目录排除了 —— 于是库存/归档/消耗/历史也跟着不进备份，换机或重装后数据归零。
 * 官方 DataStore 文档给的解法是"敏感与非敏感拆成不同 DataStore 文件，再按文件配排除规则"，
 * 本仓照做（`FoodRepository.credentialsStore` + `res/xml/backup_rules.xml`）。
 * 排除规则本身由 [BackupRulesTest] 守；本类只管读写两侧的接线。
 *
 * 三条"改前必红"的判据分别对应：
 * - [凭据读写都落在凭据文件_业务文件里一个 nutstore 键都没有]：改动前账号与密码都写进业务文件 ⇒ 红；
 * - [读流从凭据文件取值]：改动前 `nutstoreAccountFlow` 读的是业务文件 ⇒ 往凭据文件里写值再读，读出空串；
 * - [Keystore 不可用时明文也只进凭据文件]：改动前回退明文落在业务文件 ⇒ 红。
 *   （JVM 单测里 `AndroidKeyStore` 必然不可用 ⇒ 天然走的就是这条降级分支，不需要额外造假。）
 *
 * 与 `FoodRepositoryGuardTest` 同样的两条工程决定：走 `internal` 主构造 + 临时文件上的真实 DataStore
 * （不需要 Robolectric）；`android.util.Log` 由 `isReturnDefaultValues = true` 兜底。
 */
class CredentialsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 业务数据：随备份走的那份文件。 */
    private lateinit var pantry: DataStore<Preferences>

    /** 凭据：被两条备份通道排除的那份文件。 */
    private lateinit var creds: DataStore<Preferences>

    private lateinit var repo: FoodRepository

    @Before
    fun setUp() {
        pantry = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "pantry_store.preferences_pb") },
        )
        creds = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "credentials_store.preferences_pb") },
        )
        // 时钟在这里无关，传生产同一个即可（凭据路径不读日期）。
        repo = FoodRepository(pantry, File(tmp.root, "corrupt"), credentialsStore = creds)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 某个 DataStore 里所有 `nutstore_*` 开头的键名（排序后便于断言可读）。 */
    private suspend fun nutstoreKeysOf(store: DataStore<Preferences>): List<String> =
        store.data.first().asMap().keys.filter { it.name.startsWith("nutstore_") }.map { it.name }.sorted()

    @Test
    fun `凭据读写都落在凭据文件_业务文件里一个 nutstore 键都没有`() = runBlocking {
        repo.setNutstoreCredentials("  me@example.com  ", "  app-pass  ")

        // JVM 环境里 AndroidKeyStore 必然不可用 ⇒ setNutstoreCredentials 走的是明文回退分支，
        // 所以凭据文件里出现的是 nutstore_password 而不是 nutstore_password_enc。
        // 本条断言的重点不是"走了哪一支"，而是**落在哪个文件**：账号 + 明文都在 creds 里。
        assertEquals(
            listOf("nutstore_account", "nutstore_password"),
            nutstoreKeysOf(creds),
        )
        assertEquals(
            "业务数据文件（进系统备份的那份）里不得出现任何 nutstore_* 键",
            emptyList<String>(),
            nutstoreKeysOf(pantry),
        )
        assertEquals("me@example.com", repo.nutstoreAccountFlow.first())
    }

    @Test
    fun `读流从凭据文件取值而不是从业务文件`() = runBlocking {
        creds.edit { it[repo.nutstoreAccountKey] = "from-credentials-store" }
        // 反证：同名 key 若在业务文件里被读到，说明读流仍指着 dataStore。
        pantry.edit { it[repo.nutstoreAccountKey] = "from-pantry-store" }

        assertEquals("from-credentials-store", repo.nutstoreAccountFlow.first())
    }

    @Test
    fun `Keystore 不可用时明文也只进凭据文件`() = runBlocking {
        repo.setNutstoreCredentials("me@example.com", "plain-secret")

        assertTrue(
            "JVM 环境里 AndroidKeyStore 必然不可用 ⇒ 必须被报成明文降级（而不是静默当成已加密）",
            repo.nutstorePlaintextFallbackFlow.first(),
        )
        assertEquals(
            "降级期间的明文密码绝不能进会上传到用户 Google 账号的那份文件",
            emptyList<String>(),
            nutstoreKeysOf(pantry),
        )
    }

    @Test
    fun `旧版存在业务文件里的凭据会在启动迁移时搬到凭据文件并被抹掉`() = runBlocking {
        // 造一个"改动前的状态"：三个 key 都在业务文件里（明文那一份等着被加密，这里只验搬家）。
        pantry.edit { prefs ->
            prefs[repo.nutstoreAccountKey] = "old@example.com"
            prefs[repo.nutstorePasswordEncKey] = "iv:ciphertext"
        }

        repo.migrateLegacyCredentials()

        assertEquals("old@example.com", repo.nutstoreAccountFlow.first())
        assertEquals("iv:ciphertext", creds.data.first()[repo.nutstorePasswordEncKey])
        assertEquals(
            "迁移后业务文件里必须一个 nutstore 键都不剩（否则下次备份还是会把凭据推上云）",
            emptyList<String>(),
            nutstoreKeysOf(pantry),
        )
    }

    @Test
    fun `迁移是幂等的_三键全空时不改写任何文件`() = runBlocking {
        repo.migrateLegacyCredentials()
        repo.migrateLegacyCredentials()

        assertTrue(nutstoreKeysOf(pantry).isEmpty())
        assertTrue(nutstoreKeysOf(creds).isEmpty())
        assertNull(creds.data.first()[repo.nutstorePasswordEncKey])
    }
}
