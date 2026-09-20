package com.agon.app.ui.components.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「取色口子集中在一处」的位置守卫（2026-09-19，路线图 #11a）。
 *
 * 钉的是**一件事一个文件**，刻意不钉行数、也不钉“有几个函数”（那种数会随功能增减腐烂，
 * 是 `tools/doc-metrics.sh` 一直在治的那类写死数）。三条：
 * 1. `ui/components/app/` 下凡定义 `app…Color…()` 的跨主题取色口子，**只允许出现在 `AppColors.kt`**；
 * 2. `AppText.kt` 里不得再出现 `colorScheme` —— 职责搬空了，也不许将来把取色塞回文本组件文件；
 * 3. `ui/screens/` 下不得定义任何 `app…Color(` —— 屏幕层自己造一份跨主题取色，正是双主题配色
 *    各写一遍然后慢慢漂移的老病根（#3 那轮合并双主题时清掉的就是这类东西）。
 *
 * **改前必红**：本笔之前那 9 个 helper 全住在 `AppText.kt` ⇒ 第 1、2 条同时红。
 * 阳性对照（把判据本身弄坏也抓得到）：① 在 `AppText.kt` 里加一行 `internal fun appFooColor(): Color = …`
 * ⇒ 第 1 条红；② 在任一 screen 文件里加个同名定义 ⇒ 第 3 条红。
 *
 * 为什么是读源码文本：JVM 单测里没有 Compose 运行时，而“某类声明住在哪个文件”本来就只是位置问题；
 * 与 `com.agon.app.data.CorruptGuardTest` / `BackupRulesTest` 同一类，理由写在各自 KDoc。
 *
 * ⚠️ **09-19 重写说明**（留在这里是给下一个写守卫的人看的）：#11a 的首版用 `vararg` +
 * `arrayArrayOf(...) + parts` 拼目录、用 `assumeTrue` 跳过缺失目录，CI 的 `testDebugUnitTest` 编译不过，
 * 而这里的日志与工件都取不回来（只有 annotation 能读），一时定位不到行号。⇒ 本版把工具方法收敛成
 * `ScreenParityTest.screensDir()` 已在 CI 跑绿的那一种写法（`listOf(两条相对路径).map(::File)` +
 * `listFiles { }.orEmpty().sortedBy { }`），并把“目录找不到”从 `assumeTrue` 改成**显式断言失败** ——
 * 静默跳过的守卫在 CI 上等于没有守卫。
 */
class AppColorLocationTest {

    private val colorDef = Regex("""^(?:internal |private )?fun app\w*Color\w*\(""", RegexOption.MULTILINE)

    /** 先剥注释再匹配：KDoc 里举一个旧写法（`AppColors.kt` 那条就提到了别的名字）不算实现。 */
    private fun codeOf(file: File): String =
        file.readText()
            .lines()
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
            }
            .joinToString("\n")

    /** Gradle 跑测试时 CWD 是模块目录（`app/`），从仓库根跑时多一层前缀 ⇒ 两处都认。 */
    private fun componentsAppDir(): File? =
        listOf(
            "src/main/java/com/agon/app/ui/components/app",
            "app/src/main/java/com/agon/app/ui/components/app",
        )
            .map(::File)
            .firstOrNull { it.isDirectory }

    private fun screensDir(): File? =
        listOf(
            "src/main/java/com/agon/app/ui/screens",
            "app/src/main/java/com/agon/app/ui/screens",
        )
            .map(::File)
            .firstOrNull { it.isDirectory }

    private fun ktFiles(dir: File?): List<File> {
        if (dir == null) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "kt" }
            .orEmpty()
            .sortedBy { it.name }
    }

    @Test
    fun `跨主题取色口子只允许住在 AppColors 一个文件里`() {
        val files = ktFiles(componentsAppDir())
        assertTrue("组件层目录没找到或没有 .kt 文件（工作目录口径变了？）", files.isNotEmpty())
        val offenders = files.filter { f -> f.name != "AppColors.kt" && colorDef.containsMatchIn(codeOf(f)) }
        val names = offenders.map { f -> f.name }.joinToString()
        assertTrue(
            "以下文件自己定义了跨主题取色口子，应并进 ui/components/app/AppColors.kt：$names",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `文本组件文件不再兼任颜色层`() {
        val files = ktFiles(componentsAppDir())
        val appText = files.firstOrNull { f -> f.name == "AppText.kt" }
        assertTrue("AppText.kt 没找到（工作目录口径变了？）", appText != null)
        val hasColorScheme = codeOf(appText!!).contains("colorScheme")
        assertFalse(
            "AppText.kt 又出现 colorScheme 直取 —— 色板角色只在 AppColors.kt 里对一次，" +
                "否则两主题的色板角色又要各写一遍",
            hasColorScheme,
        )
    }

    @Test
    fun `屏幕层不得自己定义取色函数`() {
        val dir = screensDir()
        val files = ktFiles(dir)
        assertTrue("ui/screens 没找到或没有 .kt 文件（工作目录口径变了？）", files.isNotEmpty())
        val offenders = files.filter { f -> colorDef.containsMatchIn(codeOf(f)) }
        val names = offenders.map { f -> f.name }.joinToString()
        assertTrue(
            "屏幕层新增了跨主题取色函数（应回到组件层 AppColors.kt，或由组件层开口子）：$names",
            offenders.isEmpty(),
        )
    }
}
