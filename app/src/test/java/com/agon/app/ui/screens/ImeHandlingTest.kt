package com.agon.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * IME（软键盘）避让的静态守卫。
 *
 * 背景（2026-09-15 修复）：manifest 里的 `adjustResize` 在 targetSdk 35+ 的 edge-to-edge 下
 * 已不再缩窗口——键盘只是「叠」在窗口上，必须由 Compose 自己消费 `WindowInsets.ime`。
 * 漏掉的后果是底部的保存按钮、列表末尾、「撤销」提示条被键盘盖住且点不到，
 * 而这类问题没有任何编译期报错，只能靠静态检查兜住：
 *
 * 1. 含输入框的屏幕：`Scaffold(modifier = Modifier.imePadding())`（整屏缩到键盘之上）；
 * 2. App 级浮层（不在任何 Scaffold 内）：`.navigationBarsPadding().imePadding()`
 *    （两段式等价于旧的 `navigationBarsWithImePadding()` = max(导航栏, 键盘)，不会叠加）；
 * 3. MD3 弹窗是独立浮动窗口，必须 `DialogProperties(decorFitsSystemWindows = false)`
 *    才会把 IME inset 透给内容；Miuix 的 `WindowDialog` 由库内部处理
 *    （见 `MiuixDialog` 的 KDoc），不在守卫范围内。
 *
 * 第 3 条的清单是**按文件点名**的，所以「带输入框的 MD3 弹窗」新出现在哪个文件，就必须把那个文件加进来：
 * 2026-09-16 就是这样漏掉了 `MainActivity.kt` 的批量「移动存放位置」弹窗（有 `OutlinedTextField`，
 * 却两个属性都没写），键盘会盖住「确定移动」按钮。若该弹窗按路线图搬去 `AppDialogs.kt`，清单同步改。
 *
 * 若确实有屏幕不需要（例如页面内没有输入框、或弹窗走 Miuix 实现），
 * 请在下方列表里改，并说明理由——不要为了过测试而加无意义的 `imePadding()`。
 */
class ImeHandlingTest {

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun read(relativePath: String): String? =
        listOf("src/main/java/", "app/src/main/java/")
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }
            ?.readText()

    /**
     * App 外壳（浮层 + 底栏）分散在哪几个文件里 —— 第 2 条守卫按这份清单**跨文件求和**。
     *
     * 2026-09-16 起 `MainActivity.kt` 按职责拆分，守卫从「读一个文件点数」改成「读一组文件求和」；
     * 搬动浮层时必须同步改这份清单。清单里不存在的文件会被跳过（拆分分步做，中间态只有部分文件在）。
     */
    private val chromeFiles = listOf(
        "com/agon/app/MainActivity.kt",   // 拆分前：Snackbar + 批量栏 + 弹窗 + 底栏全在这里；拆分后只剩 Activity 本体
        "com/agon/app/MainApp.kt",        // Snackbar 覆盖层
        "com/agon/app/BatchBars.kt",      // 悬浮 / 常驻两条批量操作栏
        "com/agon/app/AppDialogs.kt",     // 批量「移动存放位置」弹窗（MD3 分支）
        "com/agon/app/NavChrome.kt",      // 4 套底栏：只有 navigationBarsPadding，**没有** imePadding
    )

    /** 其中「底栏实现」所在的文件：A 方案要求它们只避让导航栏、**不**避让键盘。 */
    private val navBarFiles = listOf("com/agon/app/NavChrome.kt")

    /**
     * 含输入框的屏幕清单。
     *
     * 2026-09-16 起双主题逐对合并（第三批 #3）：**合并后一个条目就覆盖两套主题**，
     * 因为 `imePadding()` 写在合并后那一份文件里。所以每合并一对，就把对应的 `Miuix*Screen.kt`
     * 条目删掉（文件已不存在，留着会让 `read()` 返回 null 而误报「缺少 imePadding」）。
     * 归档页、食品列表页已合并（各一份含两主题）；`MiuixArchiveScreen.kt` /
     * `MiuixFoodListScreen.kt` 条目随之删除（列表页那条原本就注着「合并后删掉这一条」）。
     *
     * 键盘避让仍由**屏幕自己**声明 `AppScaffold(modifier = Modifier.imePadding())`，
     * 组件层不无条件加 —— 没有输入框的屏幕不需要，且这样「哪一屏要避让」在屏幕文件里看得见。
     */
    private val imeScreens = listOf(
        "com/agon/app/ui/screens/EditFoodScreen.kt",        // 保存按钮在 bottomBar，最严重
        "com/agon/app/ui/screens/FoodListScreen.kt",        // 已合并双主题（2026-09-16 第 5 对）：搜索框 + 列表末尾
        "com/agon/app/ui/screens/ArchiveScreen.kt",         // 已合并双主题（2026-09-16 第 2 对）：搜索框
    )

    /**
     * 只留代码、去掉注释再点数。
     *
     * 文件头与 KDoc 里写「本文件不得出现 .imePadding()」这类**说明**是好事，但按原始文本点数会把它
     * 算成一处实现（拆分当天就踩到了：注释让计数从 4 变 5，还让底栏那条 assertFalse 直接误报）。
     */
    private fun String.codeOnly(): String =
        replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `含输入框的屏幕必须处理 IME inset`() {
        val contents = imeScreens.associateWith(::read)
        assumeTrue("找不到屏幕源码（非 Gradle 工作目录？），跳过", contents.values.any { it != null })

        val missing = contents.filterValues { it?.contains("imePadding()") != true }.keys
        assertTrue(
            "以下屏幕缺少 Modifier.imePadding()，键盘会盖住底部内容：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `App 级浮层必须同时避让导航栏与键盘`() {
        val chrome = chromeFiles.mapNotNull(::read)
        assumeTrue("找不到任何 App 外壳源码（非 Gradle 工作目录？），跳过", chrome.isNotEmpty())
        val text = chrome.joinToString("\n").codeOnly()

        val imeCount = Regex("\\.imePadding\\(\\)").findAll(text).count()
        val navCount = Regex("\\.navigationBarsPadding\\(\\)").findAll(text).count()

        // 期望位点（新增/搬走浮层时同步改这里；若按路线图把 MainActivity.kt 拆成多个文件，
        // 本测试要改成「读一组文件求和」，否则计数会掉到 0 而误报）：
        //   .imePadding()            ×4 = Snackbar 覆盖层 + 批量操作栏两条（悬浮/常驻）+ 批量「移动存放位置」MD3 弹窗
        //   .navigationBarsPadding() ×4 = 上面前三处 + 悬浮胶囊导航（底栏只避让导航栏，不避让键盘）
        assertTrue(
            "App 级浮层应有 ≥4 处 .imePadding()（Snackbar + 两条批量操作栏 + 移动位置弹窗），实际 $imeCount",
            imeCount >= 4,
        )
        assertTrue("原有 navigationBarsPadding() 不应被删（底栏仍在用），实际 $navCount", navCount >= 4)

        // A 方案（2026-09-15 产品决定）：底部导航栏不随键盘抬升。
        // 2026-09-16 之前这里用「imePadding 数 < navigationBarsPadding 数」间接表达，但那个代理指标会被
        // 任何一处新增浮层推翻（给 MD3 弹窗补键盘避让后两边都是 4，一次正确的修复反被判成违规）。
        // 现在直接对着「底栏实现所在的文件」断言，约束与被约束物一一对应。
        val navBars = navBarFiles.mapNotNull(::read)
        assertTrue(
            "找不到底栏实现 $navBarFiles —— 又被搬走了？请同步更新 navBarFiles",
            navBars.isNotEmpty(),
        )
        assertFalse(
            "底栏不应跟随键盘抬升（A 方案）：底栏实现里出现了 .imePadding()",
            navBars.any { it.codeOnly().contains(".imePadding()") },
        )
    }

    @Test
    fun `带输入框的 MD3 弹窗必须关闭 decorFitsSystemWindows`() {
        val files = listOf(
            "com/agon/app/ui/screens/SettingsScreen.kt",        // 坚果云账号 / 应用密码
            // 分类名称 + Emoji、添加存放位置：2026-09-16 第 6 对把这两个弹窗搬进了组件层，条目跟着搬
            "com/agon/app/ui/components/app/AppFormDialog.kt",
            // 批量「移动存放位置」弹窗：2026-09-16 补入清单时它在 MainActivity.kt，同日拆分后落在 AppDialogs.kt。
            // 弹窗再搬家就改这一行 —— 清单外的文件不会被检查，这正是它当初漏网的原因。
            "com/agon/app/AppDialogs.kt",
        )
        val contents = files.mapNotNull { f -> read(f)?.let { f to it } }.toMap()
        assumeTrue("找不到设置/管理页源码（非 Gradle 工作目录？），跳过", contents.isNotEmpty())

        // 清单里不存在的文件跳过（弹窗按路线图分步搬家，中间态只有一个宿主文件在）
        val missing = contents.filterValues { !it.contains("decorFitsSystemWindows = false") }.keys
        assertTrue(
            "以下文件的 MD3 弹窗没有关闭 decorFitsSystemWindows，IME inset 传不进来、底部按钮会被键盘盖住：$missing",
            missing.isEmpty(),
        )
    }
}
