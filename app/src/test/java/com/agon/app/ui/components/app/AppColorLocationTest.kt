package com.agon.app.ui.components.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 「取色口子集中在一处」的位置守卫（2026-09-19，路线图 #11a）。
 *
 * 钉的是**一件事一个文件**，刻意不钉行数、也不钉"有几个函数"（那种数会随功能增减腐烂，
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
 * 为什么是读源码文本：JVM 单测里没有 Compose 运行时，而"某类声明住在哪个文件"本来就只是位置问题；
 * 与 [com.agon.app.data.CorruptGuardTest] / [com.agon.app.data.BackupRulesTest] 同一类，理由写在各自 KDoc。
 */
class AppColorLocationTest {

    private val colorDef = Regex("""^(?:internal |private )?fun app\w*Color\w*\(""", RegexOption.MULTILINE)

    /** 先剥注释再匹配：KDoc 里举一个旧写法（`AppColors.kt` 那条就提到了别的名字）不算实现。 */
    private fun codeOf(file: File): String = file.readText().lines()
        .filterNot { val t = it.trim(); t.startsWith("*") || t.startsWith("//") || t.startsWith("/*") }
        .joinToString("\n")

    /** Gradle 的测试工作目录是模块目录（`app/`），IDE 里可能用仓库根 ⇒ 两处都找。 */
    private fun dirUnderSrc(vararg parts: String): File? =
        listOf(arrayArrayOf("src", "main", "java", "com", "agon", "app") + parts,
            arrayArrayOf("app", "src", "main", "java", "com", "agon", "app") + parts)
            .map { File(it.joinToString("/")) }
            .firstOrNull { it.isDirectory }

    private fun ktFiles(vararg parts: String): List<File> {
        val dir = dirUnderSrc(*parts)
        assumeTrue("找不到 ${parts.joinToString("/")}（非 Gradle 工作目录？），跳过", dir != null)
        return dir!!.listFiles { f -> f.isFile && f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }
    }

    @Test
    fun `跨主题取色口子只允许住在 AppColors 一个文件里`() {
        val files = ktFiles("ui", "components", "app")
        assertTrue("组件层文件列表为空（口径变了？）", files.isNotEmpty())
        val offenders = files.filter { it.name != "AppColors.kt" && colorDef.containsMatchIn(codeOf(it)) }
        assertTrue(
            "以下文件自己定义了跨主题取色口子，应并进 ui/components/app/AppColors.kt：" +
                offenders.joinToString { it.name },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `文本组件文件不再兼任颜色层`() {
        val appText = ktFiles("ui", "components", "app").firstOrNull { it.name == "AppText.kt" }
        assumeTrue("找不到 AppText.kt，跳过", appText != null)
        assertFalse(
            "AppText.kt 又出现 colorScheme 直取 —— 色板角色只在 AppColors.kt 里对一次，" +
                "否则两主题的取色又开始各写一遍",
            codeOf(appText!!).contains("colorScheme"),
        )
    }

    @Test
    fun `屏幕层不得自己定义取色函数`() {
        val offenders = ktFiles("ui", "screens").filter { colorDef.containsMatchIn(codeOf(it)) }
        assertTrue(
            "屏幕层新增了跨主题取色函数（应回到组件层 AppColors.kt，或由组件层开口子）：" +
                offenders.joinToString { it.name },
            offenders.isEmpty(),
        )
    }
}
