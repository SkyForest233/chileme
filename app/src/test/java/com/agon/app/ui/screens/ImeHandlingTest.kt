package com.agon.app.ui.screens

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

        assertTrue(
            "App 级浮层应有 ≥3 处 .imePadding()（Snackbar + 悬浮/常驻两条批量操作栏），实际 $imeCount",
            imeCount >= 3,
        )
        assertTrue("原有 navigationBarsPadding() 不应被删（底栏仍在用），实际 $navCount", navCount >= 3)
        // A 方案（2026-09-15 产品决定）：底部导航栏不随键盘抬升，
        // 因此 imePadding 的数量应严格少于 navigationBarsPadding 的数量。
        assertTrue(
            "底栏不应跟随键盘抬升（A 方案）：imePadding 数（$imeCount）应少于 navigationBarsPadding 数（$navCount）",
            imeCount < navCount,
        )
    }

    @Test
    fun `带输入框的 MD3 弹窗必须关闭 decorFitsSystemWindows`() {
        val files = listOf(
            "com/agon/app/ui/screens/SettingsScreen.kt",        // 坚果云账号 / 应用密码
            "com/agon/app/ui/screens/ManageScreens.kt",         // 分类名称 + Emoji、添加存放位置
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
