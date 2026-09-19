package com.agon.app.ui.components.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守卫（#11b 三刀，09-19）：`ui/components/app/` 里登记过的东西**只有一个家**。
 *
 * #11 的判据是「一件事一个文件」，不是行数——所以这里钉的是**位置**，且刻意不钉数量：
 * 往 `AppBarActions.kt` 再加一个顶栏入口、往 `AppSnackbar.kt` 加一种条形态，都不该让这条测试变红；
 * 但把 `AppMessageScreen` 或 `AppSnackbarHostState` 搬回 `AppChrome.kt`（哪怕只是「临时放一下」）会立刻红。
 *
 * 为什么不用「某文件里 `fun` 的个数」当判据（M1 那几笔的写法）：个数会随正常新增漂移，
 * 每次加东西都要回来改测试，最后就会有人直接把期望值改成实测值，守卫就没了。
 * 位置表相反——它只在有人**换家**时才需要动，而那正是我们想被提醒的时刻。
 *
 * 同 `CorruptGuardTest` / `BackupRulesTest` 的教训：**先剥注释再匹配**，
 * 文档里写一句「`AppMessageScreen` 是整屏消息页」不算定义。
 */
class ComponentAppHomeTest {

    /** 符号 → 它唯一允许住在哪个文件（相对 `ui/components/app/`）。 */
    private val homeOf = linkedMapOf(
        "appSurfaceColor" to "AppColors.kt",
        "appMutedColor" to "AppColors.kt",
        "appErrorColor" to "AppColors.kt",
        "appPrimaryContainerColor" to "AppColors.kt",
        "appOnPrimaryContainerColor" to "AppColors.kt",
        "appPrimaryColor" to "AppColors.kt",
        "appFaintColor" to "AppColors.kt",
        "appHighestContainerColor" to "AppColors.kt",
        "appChartColors" to "AppColors.kt",
        "AppSnackbarHostState" to "AppSnackbar.kt",
        "rememberAppSnackbarHostState" to "AppSnackbar.kt",
        "AppSnackbarPlacement" to "AppSnackbar.kt",
        "AppSnackbarForm" to "AppSnackbar.kt",
        "AppSnackbarHost" to "AppSnackbar.kt",
        "AppEditAction" to "AppBarActions.kt",
        "AppDeleteAction" to "AppBarActions.kt",
        "AppArchiveAction" to "AppBarActions.kt",
        "AppSelectAllAction" to "AppBarActions.kt",
        "AppDestructiveAction" to "AppBarActions.kt",
        "AppBarIconButton" to "AppBarActions.kt",
        "AppMessageScreen" to "AppMessageScreen.kt",
    )

    @Test
    fun `每个登记过的符号都住在它登记的那个文件里`() {
        val dir = appDir() ?: return
        val codeOf = codeByFile(dir)
        val missing = mutableListOf<String>()
        for ((name, file) in homeOf) {
            if (!defines(codeOf[file] ?: "", name)) {
                missing += "$name 应在 $file"
            }
        }
        assertTrue("这些符号不在它该在的文件里：$missing", missing.isEmpty())
    }

    @Test
    fun `同一个符号不会在组件层里被定义两次`() {
        val dir = appDir() ?: return
        val codeOf = codeByFile(dir)
        val dups = mutableListOf<String>()
        for (name in homeOf.keys) {
            val homes = codeOf.filter { (_, code) -> defines(code, name) }.keys.sorted()
            if (homes.size > 1) {
                dups += "$name 同时定义在 $homes"
            }
        }
        assertTrue("同一符号出现两处定义（搬完没删干净）：$dups", dups.isEmpty())
    }

    @Test
    fun `搬出去的职责不许回流到 AppText 或 AppChrome`() {
        val dir = appDir() ?: return
        val codeOf = codeByFile(dir)
        for ((file, needle) in listOf(
"AppText.kt" to "colorScheme",
                "AppChrome.kt" to "class AppSnackbarHostState",
                "AppChrome.kt" to "fun AppSnackbarHost",
                "AppChrome.kt" to "fun AppMessageScreen",
            )
        ) {
            val code = codeOf[file] ?: continue
            assertTrue(
                "$file 里又出现了「$needle」——#11a/#11b 已把这些职责搬去 AppColors.kt / AppSnackbar.kt / " +
                    "AppMessageScreen.kt / AppBarActions.kt，别再搬回来",
                !code.contains(needle),
            )
        }
    }

    @Test
    fun `顶栏动作入口只定义在 AppBarActions 里`() {
        // 钉 #11b ② 的另一半约定：顶栏动作族集中在 AppBarActions.kt。
        // 数量随需求增长是对的（那是正常新增）；但 `AppTopBar` 自己不许再长出动作按钮——
        // 否则「再加一个顶栏入口」又要先考古一遍它该放哪。
        val dir = appDir() ?: return
        val chrome = codeByFile(dir)["AppChrome.kt"] ?: return
        val actionInChrome = Regex("""^fun App\w+Action\(""", RegexOption.MULTILINE).findAll(chrome)
            .map { it.value }.toList()
        assertEquals("AppChrome.kt 里不该再定义顶栏动作入口：", emptyList<String>(), actionInChrome)
    }

    // ---- 辅助 ----

    /** 兼容「从模块目录跑」与「从仓库根跑」两种工作目录；目录不存在时跳过（与其它源码扫描守卫一致）。 */
    private fun appDir(): File? {
        val candidates = listOf(
            File("src/main/java/com/agon/app/ui/components/app"),
            File("app/src/main/java/com/agon/app/ui/components/app"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    private fun codeByFile(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.name to strip(it.readText()) }

    /** 只认顶层声明行形式的定义；口径与 `codeByFile` 一致（已剥注释与 import/package）。 */
    private fun defines(code: String, name: String): Boolean =
        Regex("""^(?:internal |private )?(?:fun|class|enum class|data class)\s+$name\b""", RegexOption.MULTILINE)
            .containsMatchIn(code) ||
            Regex("""^fun\s+$name\w*\(""", RegexOption.MULTILINE).containsMatchIn(code)

    /** 剥块注释、行注释、KDoc 星号行与 import/package：文档里提一句符号名不该被算成定义。 */
    private fun strip(src: String): String {
        val noBlock = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(src, "")
        return noBlock
            .lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .filterNot { it.startsWith("import ") || it.startsWith("package ") }
            .joinToString("\n")
    }
}
