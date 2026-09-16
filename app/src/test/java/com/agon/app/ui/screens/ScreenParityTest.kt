package com.agon.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 「骨架可以复制、业务量不许复制」的静态守卫。
 *
 * 背景（2026-09-15 修复）：`MiuixStatsScreen` 曾把 `StatsState` 的统计计算手抄了一遍，
 * 于是 `StatsStateTest` 测的是「MIUIX 主题下根本不会执行」的那份代码——两份实现、一份被测。
 * 改统计口径时只改一处，两套主题就会静默不一致，且没有任何检查会发现。
 *
 * 一条 JVM 单测比自定义 lint 便宜得多：源码就在仓库里，直接读文件断言即可。
 *
 * **2026-09-16 范围扩大（原名 `MiuixParityTest`）**：第三批 #3 正在把「一个屏幕两份文件」合并成
 * 「一份文件 + `ui/components/app/` 里的双主题骨架」。合并后文件名不再带 `Miuix` 前缀，
 * 按前缀枚举会让**刚合并完的屏幕逃出守卫**（Miuix 分支还在文件里，却没人查它了）。
 * 故规则改为覆盖 screens 目录下的所有屏幕文件：
 * 1. 必须调用某个 `remember*UiState(` —— 即业务数据来自已测的状态容器；
 * 2. 不得出现 `sumOf {` / `groupBy {` / `count {` / `.sortedByDescending` —— 即不在 UI 层重做聚合；
 * 3. 统计页专项：口径只许来自 `StatsState`；
 * 4. 已合并的屏幕不许把 `Miuix<同名>` 的第二实现加回来。
 *
 * 若将来确有必须在 UI 层聚合的场景，请先把计算抽成 `*State.kt` 里的纯函数（带单测）再调用。
 */
class ScreenParityTest {

    private companion object {
        /**
         * 规则 1 的豁免位：编辑页是表单，字段本身就是 `rememberSaveable` 的本地编辑态，
         * 没有也不需要共享状态容器（它也没有 Miuix 双胞胎，见 `docs/DESIGN_SPEC.md` §7
         * 「刻意保留 MD3+桥接」）。规则 2（禁止内联聚合）对它照样生效。
         */
        val NoSharedStateScreens = setOf("EditFoodScreen.kt")

        /** 已完成双主题合并的屏幕：不许再出现 `Miuix<同名>.kt` 第二实现。逐对合并时往这里加。 */
        val MergedScreens = setOf(
            "ConsumptionLogScreen.kt",   // 第 1 对（2026-09-16）
            "ArchiveScreen.kt",          // 第 2 对（2026-09-16）
            "FoodDetailScreen.kt",       // 第 3 对（2026-09-16）
            "HomeScreen.kt",             // 第 4 对（2026-09-16）
            "FoodListScreen.kt",         // 第 5 对（2026-09-16）
            "ManageScreens.kt",          // 第 6 对（2026-09-16）：一个文件里三个管理页
            "StatsScreen.kt",            // 第 7 对（2026-09-16）：图表本来就是 Canvas 自绘，与主题无关
            // 第 8 对（2026-09-16）：八对全数完成。两版只有 287 行逐字相同（八对里最低），
            // body 仍是 Md3SettingsBody / MiuixSettingsBody 两套；去重发生在弹窗（AppConfirmDialog /
            // AppOptionDialog）、SAF 启动器与 AppScaffold 骨架上。规则 1 靠 rememberSettingsUiState 通过。
            "SettingsScreen.kt",
        )
    }

    /** Gradle 的测试工作目录是模块目录（app/），IDE 也可能用仓库根目录，两处都找一下。 */
    private fun screensDir(): File? =
        listOf("src/main/java/com/agon/app/ui/screens", "app/src/main/java/com/agon/app/ui/screens")
            .map(::File)
            .firstOrNull { it.isDirectory }

    /** 屏幕文件 = `*Screen.kt` 与 `*Screens.kt`（管理三页合住一个文件）；`*State.kt` 是状态层，不在此列。 */
    private fun screenFiles(dir: File): List<File> =
        dir.listFiles { f -> f.name.endsWith("Screen.kt") || f.name.endsWith("Screens.kt") }
            .orEmpty()
            .sortedBy { it.name }

    @Test
    fun `每个屏幕都必须调用 remember UiState 状态容器`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        val screens = screenFiles(dir!!).filter { it.name !in NoSharedStateScreens }
        assertTrue("未找到任何屏幕文件，路径假设失效", screens.isNotEmpty())

        val offenders = screens.filter { file ->
            !file.readText().contains(Regex("remember[A-Za-z]*UiState\\("))
        }
        assertTrue(
            "以下屏幕没有调用 remember*UiState，可能各自抄了一份业务逻辑：" +
                offenders.joinToString { it.name } + "\n" +
                "业务数据应来自 XxxState.kt 的共用状态容器（见 2026-09-15 MiuixStatsScreen 的修复）；" +
                "确属表单类本地编辑态的屏幕，请写进 NoSharedStateScreens 并说明理由",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `屏幕不得内联聚合计算`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        val screens = screenFiles(dir!!)
        assertTrue("未找到任何屏幕文件，路径假设失效", screens.isNotEmpty())

        // 只拦「聚合」这类业务计算：布局相关的 map/filter 不在此列
        val forbidden = listOf("sumOf {", "groupBy {", "count {", ".sortedByDescending")
        val offenders = screens.mapNotNull { file ->
            val hits = forbidden.filter { it in file.readText() }
            if (hits.isEmpty()) null else "${file.name}: ${hits.joinToString()}"
        }
        assertTrue(
            "屏幕文件里出现了聚合计算，应下沉到 *State.kt 的纯函数（可 JVM 单测）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `统计页复用统计状态层`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        // 合并前后都要盯着：现在查 MiuixStatsScreen，合并后同一条断言自动落到 StatsScreen 上
        val statsScreens = screenFiles(dir!!).filter { it.name.endsWith("StatsScreen.kt") }
        assumeTrue("未找到统计页源码，跳过", statsScreens.isNotEmpty())

        statsScreens.forEach { file ->
            val src = file.readText()
            assertTrue(
                "${file.name} 未调用 rememberStatsUiState，统计口径会出现第二份实现",
                "rememberStatsUiState(viewModel)" in src,
            )
            assertTrue(
                "${file.name} 里仍有内联统计计算（wastedTotal / 周月消耗 / 趋势）",
                listOf("wastedTotal = archived.count", "epochDay >= weekAgo", "withDayOfMonth(1)").none { it in src },
            )
        }
    }

    @Test
    fun `已合并的屏幕不许再出现 Miuix 第二实现`() {
        val dir = screensDir()
        assumeTrue("找不到 screens 源码目录（非 Gradle 工作目录？），跳过", dir != null)
        val names = dir!!.listFiles().orEmpty().map { it.name }.toSet()

        val twins = MergedScreens.mapNotNull { merged ->
            val twin = "Miuix$merged"
            if (twin in names) "$twin（$merged 已合并为单文件，主题差异应走 ui/components/app/）" else null
        }
        assertTrue("以下双主题第二实现又被加回来了：\n" + twins.joinToString("\n"), twins.isEmpty())

        // 反向也守一下：合并 ≠ 把整屏删掉
        MergedScreens.forEach { merged ->
            assertTrue("$merged 不见了——合并后屏幕本体必须还在", merged in names)
        }
    }
}
