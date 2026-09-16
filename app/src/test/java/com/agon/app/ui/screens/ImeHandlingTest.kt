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

    @Test
    fun `含输入框的屏幕必须处理 IME inset`() {
        val screens = listOf(
            "com/agon/app/ui/screens/EditFoodScreen.kt",        // 保存按钮在 bottomBar，最严重
            "com/agon/app/ui/screens/FoodListScreen.kt",        // 搜索框 + 列表末尾
            "com/agon/app/ui/screens/ArchiveScreen.kt",
            "com/agon/app/ui/screens/MiuixFoodListScreen.kt",
            "com/agon/app/ui/screens/MiuixArchiveScreen.kt",
        )
        val contents = screens.associateWith(::read)
        assumeTrue("找不到屏幕源码（非 Gradle 工作目录？），跳过", contents.values.any { it != null })

        val missing = contents.filterValues { it?.contains("imePadding()") != true }.keys
        assertTrue(
            "以下屏幕缺少 Modifier.imePadding()，键盘会盖住底部内容：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `App 级浮层必须同时避让导航栏与键盘`() {
        val src = read("com/agon/app/MainActivity.kt")
        assumeTrue("找不到 MainActivity.kt（非 Gradle 工作目录？），跳过", src != null)
        val text = src!!

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
        // 旧写法是「imePadding 数 < navigationBarsPadding 数」，用数量差间接表达；2026-09-16 给 MD3 弹窗
        // 补上键盘避让后两边都是 4、数量差归零，这个代理指标当场失效（它本来也测不准：任何一处新增浮层
        // 都会让它翻脸，而真正的约束只关于底栏）。改成直接检查导航栏实现那一段不含 imePadding。
        val navStart = text.indexOf("private fun MiuixBottomNav")
        assertTrue(
            "找不到 private fun MiuixBottomNav —— 导航栏实现被搬走了？请同步更新本守卫的读取范围",
            navStart >= 0,
        )
        assertFalse(
            "底栏不应跟随键盘抬升（A 方案）：4 套导航栏实现里出现了 .imePadding()",
            text.substring(navStart).contains(".imePadding()"),
        )
    }

    @Test
    fun `带输入框的 MD3 弹窗必须关闭 decorFitsSystemWindows`() {
        val files = listOf(
            "com/agon/app/ui/screens/SettingsScreen.kt",        // 坚果云账号 / 应用密码
            "com/agon/app/ui/screens/ManageScreens.kt",         // 分类名称 + Emoji、添加存放位置
            "com/agon/app/MainActivity.kt",                     // 批量「移动存放位置」（2026-09-16 补入清单）
        )
        val contents = files.associateWith(::read)
        assumeTrue("找不到设置/管理页源码（非 Gradle 工作目录？），跳过", contents.values.any { it != null })

        val missing = contents.filterValues { it?.contains("decorFitsSystemWindows = false") != true }.keys
        assertTrue(
            "以下文件的 MD3 弹窗没有关闭 decorFitsSystemWindows，IME inset 传不进来、底部按钮会被键盘盖住：$missing",
            missing.isEmpty(),
        )
    }
}
