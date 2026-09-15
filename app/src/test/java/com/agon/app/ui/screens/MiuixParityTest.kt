package com.agon.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 双主题「骨架可以复制、业务量不许复制」的静态守卫。
 *
 * 背景（2026-09-15 修复）：`MiuixStatsScreen` 曾把 `StatsState` 的统计计算手抄了一遍，
 * 于是 `StatsStateTest` 测的是「MIUIX 主题下根本不会执行」的那份代码——两份实现、一份被测。
 * 改统计口径时只改一处，两套主题就会静默不一致，且没有任何检查会发现。
 *
 * 一条 JVM 单测比自定义 lint 便宜得多：源码就在仓库里，直接读文件断言即可。
 * 规则（只针对 `Miuix*.kt`）：
 * 1. 必须调用某个 `remember*UiState(` —— 即业务数据来自已测的状态容器；
 * 2. 不得出现 `sumOf {` / `groupBy {` —— 即不在 UI 层重新做聚合计算。
 *
 * 若将来确有必须在 UI 层聚合的场景，请先把计算抽成 `*State.kt` 里的纯函数（带单测）再调用。
 */
class MiuixParityTest {

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun sourceFile(relativePath: String): File? =
        listOf("src/main/java/", "app/src/main/java/")
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }

    private fun screensDir(): File? =
        listOf("src/main/java/com/agon/app/ui/screens", "app/src/main/java/com/agon/app/ui/screens")
            .map(::File)
            .firstOrNull { it.isDirectory }

    @Test
    fun `每个 Miuix 屏幕都必须调用 remember UiState 状态容器`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        val miuixScreens = dir!!.listFiles { f -> f.name.startsWith("Miuix") && f.name.endsWith(".kt") }
            .orEmpty()
            .filter { it.name != "MiuixParityTest.kt" }
        assertTrue("未找到任何 Miuix*Screen.kt，路径假设失效", miuixScreens.isNotEmpty())

        val offenders = miuixScreens.filter { file ->
            !file.readText().contains(Regex("remember[A-Za-z]*UiState\\("))
        }
        assertTrue(
            "以下 Miuix 屏幕没有调用 remember*UiState，可能各自抄了一份业务逻辑：" +
                offenders.joinToString { it.name } + "\n" +
                "业务数据应来自 XxxState.kt 的共用状态容器（见 MiuixStatsScreen 的修复）",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `Miuix 屏幕不得内联聚合计算`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        val miuixScreens = dir!!.listFiles { f -> f.name.startsWith("Miuix") && f.name.endsWith(".kt") }
            .orEmpty()
        assertTrue("未找到任何 Miuix*Screen.kt，路径假设失效", miuixScreens.isNotEmpty())

        // 只拦「聚合」这类业务计算：布局相关的 map/filter 不在此列
        val forbidden = listOf("sumOf {", "groupBy {", "count {", ".sortedByDescending")
        val offenders = miuixScreens.mapNotNull { file ->
            val hits = forbidden.filter { it in file.readText() }
            if (hits.isEmpty()) null else "${file.name}: ${hits.joinToString()}"
        }
        assertTrue(
            "Miuix 屏幕里出现了聚合计算，应下沉到 *State.kt 的纯函数（可 JVM 单测）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `MiuixStatsScreen 复用统计状态层`() {
        val file = sourceFile("com/agon/app/ui/screens/MiuixStatsScreen.kt")
        assumeTrue("找不到 MiuixStatsScreen.kt（非 Gradle 工作目录？），跳过", file != null)
        val src = file!!.readText()
        assertTrue("MiuixStatsScreen 未调用 rememberStatsUiState，统计口径会出现第二份实现", "rememberStatsUiState(viewModel)" in src)
        assertTrue(
            "MiuixStatsScreen 里仍有内联统计计算（wastedTotal / 周月消耗 / 趋势）",
            listOf("wastedTotal = archived.count", "epochDay >= weekAgo", "withDayOfMonth(1)").none { it in src },
        )
    }
}
