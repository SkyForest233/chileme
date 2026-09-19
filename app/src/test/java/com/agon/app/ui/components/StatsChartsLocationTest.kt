package com.agon.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 统计图表件的**层界守卫**（09-19 #11d ①）。
 *
 * 三条判据里最要紧的是第一条：`ui/screens/` 下不许出现 `Canvas(` ——
 * 屏幕文件的职责是「装配状态 + 排版」，自绘图形属于组件层。这条一旦立住，
 * `StatsScreen` 里那 66 行环图与图例就再也回不去，而下一个想复用环图的视图也只剩「import」一条路
 * （以前它是「再抄一遍 drawArc」—— 09-15 那次 `MiuixStatsScreen` 手抄统计口径就是同一类事故）。
 *
 * 形态与 `data/CloudSyncLocationTest`、`ui/components/app/ComponentAppHomeTest` 一致：
 * 位置判据 + 「搬走的不回流」，并且每条都自带阳性对照（探测不工作就等于没守卫）。
 */
class StatsChartsLocationTest {

    private val screensDir = listOf(
        "app/src/main/java/com/agon/app/ui/screens",
        "src/main/java/com/agon/app/ui/screens",
    ).map(::File).firstOrNull { it.isDirectory }

    private val componentsDir = listOf(
        "app/src/main/java/com/agon/app/ui/components",
        "src/main/java/com/agon/app/ui/components",
    ).map(::File).firstOrNull { it.isDirectory }

    private fun ktFiles(dir: File): List<Pair<String, String>> =
        dir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty()
            .map { it.name to it.readText() }
            .sortedBy { it.first }

    @Test
    fun `屏幕层不许自绘 Canvas`() {
        val dir = screensDir
        assertTrue("`ui/screens` 目录没找到（工作目录变了？）", dir != null)
        val files = ktFiles(dir!!)
        assertTrue("扫到 0 个屏幕文件 —— 探测本身失效了", files.isNotEmpty())
        val offenders = files.filter { "Canvas(" in it.second }.map { it.first }
        assertEquals("`ui/screens/` 里出现自绘 Canvas，图形件请放 `ui/components/`：" + offenders, emptyList<String>(), offenders)
        // 阳性对照：组件层确实有 Canvas —— 少了这条，"0 命中" 可能只是扫错了目录
        val comps = ktFiles(componentsDir ?: dir)
        assertTrue("`ui/components/` 里一个 Canvas 都没有 ⇒ 判据的对照不成立", comps.any { "Canvas(" in it.second })
    }

    @Test
    fun `两个图表件只有一个家`() {
        val comp = componentsDir
        val scr = screensDir
        assertTrue("`ui/components` 或 `ui/screens` 目录没找到", comp != null && scr != null)
        val home = linkedMapOf<String, String>()
        val decl = Regex("^(?:internal |private |public )?fun (DonutChart|LegendRow)\\(")
        for ((file, src) in ktFiles(comp!!) + ktFiles(scr!!)) {
            for (line in src.lines()) {
                // 去缩进：只认顶层声明就看不见 object 里的成员（09-19 在 data 层踩过一次假守卫）
                val m = decl.find(line.trimStart()) ?: continue
                val sym = m.groupValues[1]
                home[sym] = (home[sym] ?: "") + " " + file
            }
        }
        val bad = home.filter { it.value.trim() != "StatsCharts.kt" }
        assertEquals("DonutChart / LegendRow 的住所不唯一或不在组件层：" + bad, emptyMap<String, String>(), bad)
        assertEquals("两个图表件都得被扫到（登记表失效的另一种形状）", 2, home.size)
    }

    @Test
    fun `屏幕层不再自带图表件定义`() {
        val dir = screensDir
        assertTrue("`ui/screens` 目录没找到（工作目录变了？）", dir != null)
        val files = ktFiles(dir!!)
        assertTrue("扫到 0 个屏幕文件 —— 探测本身失效了", files.isNotEmpty())
        val leftovers = files.flatMap { (name, src) ->
            listOf("fun DonutChart(", "fun LegendRow(")
                .filter { src.contains(it) }
                .map { "$name 里还有定义：$it" }
        }
        assertEquals("已搬走的图表件定义还留在屏幕层：" + leftovers, emptyList<String>(), leftovers)
        // ⚠️ 调用点判据的口径在 09-19 #11d② 之后从「`StatsScreen.kt` 必须调」放宽成「`ui/screens/` 里必须有人调」：
        // 那一刀把 `DonutChart(...)` 连同整个区块搬进了 `StatsCategorySection.kt`，钉死文件名会误报
        // （CI run `35476442138` 正红在旧口径上：194 绿 1 红）。层界守卫钉的是**哪一层**，不是**哪个文件** ——
        // 文件会随拆分搬家，层不会。
        val callers = files.filter { "DonutChart(" in it.second }.map { it.first }
        assertTrue("`ui/screens/` 里没人调用 DonutChart ⇒ 调用点被删坏了", callers.isNotEmpty())
    }
}
