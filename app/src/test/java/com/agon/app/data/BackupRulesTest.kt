package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 备份/迁移排除清单的守卫（2026-09-19，M1-1）。
 *
 * 钉住的是那条被踩过的边界：**"护住凭据"不许顺手把用户数据一起排除在备份之外**。
 * 改动前的两份 XML 都写着 `<exclude domain="file" path="datastore/" />` —— 因为业务数据与凭据同住
 * `pantry_store` 一个文件，只能整目录排除，后果是换机/重装后库存归零。
 * 现在凭据独立成 `credentials_store.preferences_pb`（读写接线见 [CredentialsStoreTest]），
 * 于是排除项可以精确到那一个文件，业务数据回到备份通道里。
 *
 * 为什么是"读 XML 文本"而不是行为测试：这两个文件是**纯配置**，JVM 单测里没有任何 API 会去消费它们
 * （消费方是系统的 `BackupManager`/`android:dataExtractionRules`，只有真机与 `adb backup` 才走得到）。
 * 在没有 instrumentation 测试的前提下，配置-as-code 的文本断言就是能拿到的最强守卫。
 * 与 `CorruptGuardTest` / `ScreenParityTest` 同一类，替换方案见 `docs/audits/2026-09-19-independent-review.md` §7.2-8。
 */
class BackupRulesTest {

    private val credentialsFile = "datastore/credentials_store.preferences_pb"
    private val pantryFile = "datastore/pantry_store.preferences_pb"

    /** Manifest 里挂这两份规则（以及 `allowBackup` 本身）的三个属性名，写成断言时会顶到 140 字符上限。 */
    private val allowBackup = "android:allowBackup=\"true\""
    private val fullBackupContent = "android:fullBackupContent=\"@xml/backup_rules\""
    private val dataExtractionRules = "android:dataExtractionRules=\"@xml/data_extraction_rules\""

    /**
     * Gradle 的测试工作目录是**模块目录**（`app/`），所以 `src/main/...` 就够；
     * 多加一条 `app/...` 候选是为了在 IDE 里把仓库根当工作目录直接跑单测时也不误报（`ScreenParityTest` 同款做法）。
     */
    private fun firstExisting(vararg relativePaths: String): File? =
        relativePaths.map { File(it) }.firstOrNull { it.exists() }

    /** `res/xml/` 下的文件名（backup_rules.xml 等）。 */
    private fun text(name: String): String = read(
        "src/main/res/xml/$name",
        "app/src/main/res/xml/$name",
    )

    private fun read(vararg candidates: String): String {
        val file = firstExisting(*candidates)
        assumeTrue("找不到 ${candidates.joinToString()}（非 Gradle 工作目录？），跳过", file != null)
        return stripComments(file!!.readText())
    }

    /**
     * 先把注释整段剥掉再解析。
     *
     * ⚠️ 这条不是可选的：`backup_rules.xml` 顶部那段说明**引用了旧写法原文**（"此前是排除整个
     * `datastore/` 目录"那句里就含一条 `<exclude ... />`），不剥注释的话 `excludes()` 会把这句
     * 历史说明当成一条生效规则，于是"不许再整目录排除"那条断言永远红 —— 第一次跑 CI 就是这么红的。
     * 剥注释同时保住了守卫的本事：将来谁把真规则加回去，照样红。
     * 与 `CorruptGuardTest`/`EditFoodSaveGuardTest` 的 `codeLines()` 是同一招式（那边是 Kotlin 行注释）。
     */
    private fun stripComments(xml: String): String = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(xml, "")

    /** 取 `<exclude ... path="X" />` 里的 X（三份作用域共用这一个解析器）。 */
    private fun excludes(xml: String): Set<String> =
        Regex("""<exclude\s+domain="([^"]+)"\s+path="([^"]+)"\s*/>""")
            .findAll(xml)
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .toSet()

    /** 截出 `<cloud-backup>` / `<device-transfer>` 的作用域块。 */
    private fun scope(xml: String, name: String): String {
        val start = xml.indexOf("<$name>")
        assertTrue("data_extraction_rules.xml 缺少 <$name> 块", start >= 0)
        val end = xml.indexOf("</$name>", start)
        assertTrue("data_extraction_rules.xml 的 <$name> 块没有收尾", end > start)
        return xml.substring(start, end)
    }

    @Test
    fun `业务数据文件不再被排除_整目录排除那条已废除`() {
        val full = excludes(text("backup_rules.xml"))
        val extraction = text("data_extraction_rules.xml")
        val cloud = excludes(scope(extraction, "cloud-backup"))
        val transfer = excludes(scope(extraction, "device-transfer"))

        for ((label, set) in listOf("fullBackupContent" to full, "cloud-backup" to cloud, "device-transfer" to transfer)) {
            assertFalse(
                "$label 又退回了「排除整个 datastore/ 目录」—— 那会把用户的库存数据一起排除在备份之外。" +
                    "要挡的是凭据，按文件排除 $credentialsFile",
                set.contains("file:datastore/"),
            )
            assertFalse(
                "$label 不得排除业务数据文件本身（$pantryFile）",
                set.contains("file:$pantryFile"),
            )
            assertTrue("$label 必须排除凭据文件", set.contains("file:$credentialsFile"))
            assertTrue("$label 必须排除损坏留档目录", set.contains("file:corrupt/"))
            assertTrue("$label 必须排除本机滚动快照目录", set.contains("file:snapshots/"))
        }
    }

    @Test
    fun `两份规则文件的云备份作用域逐条一致`() {
        // backup_rules.xml 服务 API 26–30，data_extraction_rules.xml 的 <cloud-backup> 服务 API 31+。
        // 两者的语义都是"上传到用户 Google 账号"，所以排除清单必须逐条相等 ——
        // 少一条就等于"老设备上备份全量、新设备上漏一项"，而这种差异只在换机后才看得出来。
        assertEquals(
            "backup_rules.xml 与 data_extraction_rules.xml 的 <cloud-backup> 排除清单不一致（改一份要改两份）",
            excludes(text("backup_rules.xml")),
            excludes(scope(text("data_extraction_rules.xml"), "cloud-backup")),
        )
    }

    @Test
    fun `换机直传放行封面_云备份不放行_这条不对称是刻意的`() {
        val extraction = text("data_extraction_rules.xml")
        val cloud = excludes(scope(extraction, "cloud-backup"))
        val transfer = excludes(scope(extraction, "device-transfer"))

        assertTrue(
            "云备份不放行 covers/（体积不可控；恢复后 photoPath 悬空由渲染侧回落 emoji）",
            cloud.contains("file:covers/"),
        )
        assertFalse(
            "换机直传必须放行 covers/：同一时刻 pantry_store 也过去了 ⇒ 封面与引用它的库存一起到位。" +
                "若把它也排除掉，就等于恢复了 2026-09-16 记在 ARCHITECTURE.md 的那处不一致" +
                "（图到了、JSON 没到 ⇒ 启动时 cleanupOrphanCovers() 把封面当孤儿全删）",
            transfer.contains("file:covers/"),
        )
        // 除 covers/ 之外，两个作用域的差集必须只有这一项。
        assertEquals(
            "cloud-backup 与 device-transfer 的排除清单只允许差 covers/ 这一条",
            setOf("file:covers/"),
            cloud - transfer,
        )
    }

    @Test
    fun `manifest 仍然挂着两份规则`() {
        val manifest = read("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        // 这里刻意不用 raw string：被匹配的片段本身以 `"` 收尾，`"""…""""` 是非法字面量。
        // 三个属性名提成常量，免得每行都要把「断言说明 + 属性」写满 140 字符的上限。
        assertTrue("android:allowBackup 必须为 true，否则下面两份规则文件都不生效", manifest.contains(allowBackup))
        assertTrue("少了 android:fullBackupContent（API 26–30 那条通道）", manifest.contains(fullBackupContent))
        assertTrue("少了 android:dataExtractionRules（API 31+ 那条通道）", manifest.contains(dataExtractionRules))
    }
}
