package com.agon.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 启动编排的**顺序与位置**守卫（09-19 #11f）。
 *
 * `runPantryStartup()` 是从 `AppViewModel.init` 里搬出来的，搬的时候承诺「逐行相同、只左移」⇒ 本文件不
 * 重复证明那件事（提交信息里的判据①已用逐行比对核过），只钉住**这段代码真正的正确性来源**：
 *
 * 1. **顺序**（`启动七步在 runPantryStartup 里严格保序`）：这条链上每一步都读上一步写下的东西，重排会静默
 *    出错 —— 例如 `migrateLegacyCredentials()` 一旦跑到 `migratePlaintextPassword()` 后面，明文就留在旧
 *    DataStore 里等一次永远不会来的加密（M1-1 的起因）。
 * 2. **损坏态门**（`孤儿封面清理只在数据完好时才跑`）：解码失败时 `rawFlow` 回落空列表，照此清理会把
 *    `covers/` 下所有文件当孤儿删掉，而图无法从 `corrupt/` 的 JSON 留档里恢复（09-15 修过的真事故）。
 * 3. **顺序只住一处**（`七步的住所恰好是启动文件`）：装配体不许再抄一份步骤 —— 抄一份就有了第二个「真相」，
 *    改一处漏一处。与 `ui/screens/StatsSectionLocationTest` 同口径：**正向**问"该住哪儿"，而不是写
 *    「本文件不含 X」那种会自相矛盾的否定式（11b 的守卫就栽在后者上）。
 *
 * ⚠️ 三段判据都是**源码形状**而不是运行时行为，这是刻意的取舍：这段的正确性完全等于"顺序 + 一道门"，
 * 而顺序是文本性质 ⇒ 文本就能穷尽。真要在 JVM 里跑 `runPantryStartup()`，得配 Robolectric + 真容器
 * （`ChiliMeApp` → `AppContainer` → DataStore / Keystore 文件），那台机器首先证明的是"DataStore 能跑"；
 * 要看顺序反而得给仓库塞 mock 记调用序列 —— 比这三条更绕、更脆，而且 mock 出来的仓库不等于真仓库。
 * 顺序一旦错了**没有编译错、也没有崩溃**，只会在下次启动时少掉凭据或少掉封面 ⇒ 正适合用守卫钉住。
 */
class AppViewModelStartupTest {

    private val startup = "com/agon/app/viewmodel/AppViewModelStartup.kt"
    private val viewModel = "com/agon/app/viewmodel/AppViewModel.kt"
    private val startupName = "AppViewModelStartup.kt"

    /** 源码根：Gradle 跑测试时 CWD 是模块目录（`app/`），从仓库根跑时多一层前缀。 */
    private val sourceRoot: File? =
        listOf("src/main/java", "app/src/main/java").map(::File).firstOrNull { it.isDirectory }

    /**
     * 按行剥掉注释与 KDoc：`*` 或 `//` 开头的行整行丢弃。
     *
     * 朴素实现（与 `SnackbarCopyTest` 同源同款），够用但**不是词法分析**。这里要它只为了一件事：
     * 「某句话里提到某个函数名」不该算成「某处调用了它」—— 实测 `UiEvent.kt` 的 KDoc 里就有一句
     * `maybeAutoSync()`，不剥注释的话第 3 条守卫会假红。
     */
    private fun codeOnly(src: String): String =
        src.lineSequence()
            .filterNot { it.isBlank() || it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")

    private fun code(path: String): String {
        val root = sourceRoot ?: error("找不到源码根（非 Gradle 工作目录？）")
        return codeOnly(File(root, path).readText())
    }

    /** 取出某个顶层函数的函数体（含外层花括号）：靠花括号配平，不靠"下一段长什么样"。 */
    private fun bodyOf(src: String, signature: String): String {
        val at = src.indexOf(signature)
        assertTrue("找不到签名：$signature", at >= 0)
        val open = src.indexOf('{', at)
        assertTrue("$signature 后面没有 {", open >= 0)
        var depth = 0
        var end = -1
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        assertTrue("$signature 的花括号没配平", end > open)
        return src.substring(open, end + 1)
    }

    /** 启动七步，按**必须执行的先后**排列。针脚取"调用形状"（带接收者前缀），不是声明形状。 */
    private val steps = listOf(
        "repo.seedIfNeeded()",
        "repo.migrateLegacyCredentials()",
        "repo.migratePlaintextPassword()",
        "repo.migrateConsumptionIds()",
        "cleanupOrphanCovers(",
        "maybeAutoSync()",
        "maybeAutoSnapshot()",
    )

    @Test
    fun `启动七步在 runPantryStartup 里严格保序`() {
        val body = bodyOf(code(startup), "internal suspend fun AppViewModel.runPantryStartup() {")
        val at = steps.map { needle ->
            val first = body.indexOf(needle)
            assertTrue("`runPantryStartup` 里没有 $needle", first >= 0)
            assertEquals("$needle 在启动序列里出现了不止一次（重复播种 / 重复迁移都是 bug）", -1, body.indexOf(needle, first + 1))
            first
        }
        assertEquals(
            "启动顺序被改了。七步在函数体里的字节位置应严格递增，实测 $at",
            at.sorted(),
            at,
        )
    }

    @Test
    fun `孤儿封面清理只在数据完好时才跑`() {
        val body = bodyOf(code(startup), "internal suspend fun AppViewModel.runPantryStartup() {")
        val gate = body.indexOf("if (repo.corruptedKeys.value.isEmpty()) {")
        val clean = body.indexOf("cleanupOrphanCovers(")
        val elseAt = body.indexOf("} else {")
        val log = body.indexOf("Log.w(")
        listOf(
            "损坏态门 `if (repo.corruptedKeys.value.isEmpty())`" to gate,
            "清理调用 `cleanupOrphanCovers(`" to clean,
            "else 分支" to elseAt,
            "else 分支里的 `Log.w(`" to log,
        ).forEach { (what, i) -> assertTrue("$what 不在 `runPantryStartup` 里", i >= 0) }
        assertTrue("清理必须发生在门之后：损坏态下 rawFlow 回落空列表 ⇒ 照此清理会把 covers/ 全删", gate < clean)
        assertTrue("清理必须在 `} else {` 之前 ⇒ 它只属于门内那个分支", clean < elseAt)
        assertTrue("else 分支只许记日志（不许「顺手也清一下」）", elseAt < log)
    }

    @Test
    fun `七步的住所恰好是启动文件`() {
        val dir = sourceRoot?.resolve("com/agon/app/viewmodel")
        assertTrue("找不到 `viewmodel` 目录", dir != null && dir!!.isDirectory)
        val files = dir!!.listFiles { f -> f.isFile && f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }
        assertTrue("扫到 0 个文件 ⇒ 探测本身失效，这条守卫无从判断", files.isNotEmpty())
        val bad = mutableListOf<String>()
        for (needle in steps) {
            val hits = files.filter { needle in codeOnly(it.readText()) }.map { it.name }
            if (hits != listOf(startupName)) bad += "$needle 住在 $hits，应恰为 [$startupName]"
        }
        assertEquals("启动步骤被复制到别处、或从启动文件里消失了（出现第二个真相）", emptyList<String>(), bad)
    }

    @Test
    fun `装配点只是把编排交出去一次`() {
        val src = code(viewModel)
        val init = bodyOf(src, "    init {")
        val statements = init
            .removePrefix("{").removeSuffix("}")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        assertEquals(
            "`AppViewModel.init` 应当只是「把编排交给 runPantryStartup」；多一行就是第二个真相",
            "viewModelScope.launch { runPantryStartup() }",
            statements,
        )
        // 类里**不留转发**：成员会遮蔽扩展，`fun runPantryStartup() = runPantryStartup()` 是无限递归而编译通过
        assertEquals("VM 里 `runPantryStartup()` 只许被调用一次", 1, Regex("runPantryStartup\\(\\)").findAll(src).count())
    }
}
