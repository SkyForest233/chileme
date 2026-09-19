package com.agon.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 统计页「装配 / 区块」的**位置守卫**（09-19 #11d ②）。
 *
 * 一刀一个区块，每刀往下面这张表加一行。三条判据各自能拦一种偷懒：
 * 1. **区块标记注释只许住在区块文件里** —— `// ---- 近 7 天消耗趋势…----` 这种分隔线是「这块属于哪儿」的
 *    物理证据；它还留在 `StatsScreen.kt` 就说明只是复制了一份、原地没动。
 * 2. **细节不许回流**：区块用到的状态字段不许再出现在装配体里（否则等于把画法抄了两遍 —— 09-15
 *    `MiuixStatsScreen` 手抄 `StatsState` 就是同一类事故）。
 * 3. **调用点必须在**：拦「把调用删掉来让判据变绿」这种最省事的假拆分。
 *
 * 与 `ui/components/StatsChartsLocationTest`（层界：屏幕层不许自绘 Canvas）配套；两个守卫都不是数函数个数。
 */
class StatsSectionLocationTest {

    /** 一刀一行：区块标记 → 住所 → 不许回流的字段 → 调用点函数名。 */
    private class Cut(val marker: String, val home: String, val noReturn: List<String>, val entry: String)

    private val cuts = listOf(
        Cut(
            marker = "// ---- 近 7 天消耗趋势（柱状图）----",
            home = "StatsTrendSection.kt",
            noReturn = listOf("state.dailyTrend", "state.maxDaily"),
            entry = "StatsTrendSection(",
        ),
        Cut(
            marker = "// ---- 库存分类占比（环图 + 图例）----",
            home = "StatsCategorySection.kt",
            noReturn = listOf("state.categoryShare"),
            entry = "StatsCategorySection(",
        ),
        Cut(
            marker = "// ---- 消耗排行榜 ----",
            home = "StatsTopConsumedSection.kt",
            noReturn = listOf("state.topConsumed", "state.hasAnyConsumption"),
            entry = "StatsTopConsumedSection(",
        ),
    )

    private val screensDir = listOf(
        "app/src/main/java/com/agon/app/ui/screens",
        "src/main/java/com/agon/app/ui/screens",
    ).map(::File).firstOrNull { it.isDirectory }

    @Test
    fun `区块标记只住在区块文件里`() {
        val dir = screensDir
        assertTrue("`ui/screens` 目录没找到", dir != null)
        val files = dir!!.listFiles { f -> f.isFile && f.name.endsWith(".kt") }.orEmpty()
        assertTrue("扫到 0 个文件 ⇒ 探测本身失效", files.isNotEmpty())
        val bad = mutableListOf<String>()
        for (cut in cuts) {
            val owners = files.filter { cut.marker in it.readText() }.map { it.name }.sorted()
            if (owners != listOf(cut.home)) bad += "${cut.entry} 的标记在 $owners，应在 [${cut.home}]"
        }
        assertEquals("区块标记的住所不对：\n" + bad.joinToString("\n"), emptyList<String>(), bad)
    }

    /** 只看代码：`StatsScreen.kt` 的文件头 KDoc 里写着「柱高比例用 `if (state.maxDaily > 0) …`」这类**描述**，
     *  拿它判「回流」会永远红 —— 判据要判的是代码里还留着画法，不是文档提过这个名字。 */
    private fun codeOnly(src: String): String =
        src.lines().filter {
            val t = it.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }.joinToString("\n")

    @Test
    fun `画法细节不回流装配体`() {
        val dir = screensDir
        assertTrue("`ui/screens` 目录没找到", dir != null)
        val entry = File(dir!!, "StatsScreen.kt")
        assertTrue("`StatsScreen.kt` 没找到", entry.exists())
        val src = codeOnly(entry.readText())
        val back = cuts.flatMap { c -> c.noReturn.filter { src.contains(it) } }
        assertEquals("装配体里还留着区块细节（等于没搬完）：" + back, emptyList<String>(), back)
    }

    @Test
    fun `装配体仍在调用每个区块`() {
        val dir = screensDir
        assertTrue("`ui/screens` 目录没找到", dir != null)
        val src = File(dir!!, "StatsScreen.kt").readText()
        val missing = cuts.map { it.entry }.filterNot { src.contains(it) }  // 调用点看原文即可
        assertEquals("装配体没调用这些区块 ⇒ 拆分变成了删除：" + missing, emptyList<String>(), missing)
        // 阳性对照：区块文件里必须真有画法（否则可以搬一个空壳来绿判据）
        for (cut in cuts) {
            val body = File(dir, cut.home).readText()
            assertTrue("${cut.home} 里没有画法（空壳?）", body.contains("AppSection(") || body.contains("Row("))
        }
    }
}
