package com.agon.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * `CalendarMonthLayout` 的行为真值表 + 位置守卫（09-19 #11e，`ui/components/` 这一层的**第一份**单测）。
 *
 * 为什么这批数字是写死的：抽取之前，月网格的算式住在 `@Composable` 里，等于**没人能测**；
 * 而"改前必红"的证据在搬运笔里是"逐行相等"，对数学本身不够 ⇒ 用字面真值钉住。四个易错点各一条：
 * 月首偏移、闰年 2 月、跨年、行数的上下界（2027-02 只有 4 行、2027-08 要 6 行）。
 * 期望值由 Python 标准库 `calendar.monthcalendar`（周一起、空位为 0）独立核过 —— 60 个月的铺法与这里
 * 每条字面常量都对得上。**没有拿 Kotlin 那四行公式再算一遍当期望值**：那样等于断言"代码等于它自己"，永远绿。
 *
 * 最后一条是位置守卫：算式只许住一个文件（防"复制一份、原地没删"），并且装配体必须仍在调用
 * （防"把调用删掉来让判据变绿"）—— 口径与 `StatsChartsLocationTest` 一致：**钉哪一层，不钉哪个文件**
 * （09-19 第七次红就是钉死文件名被一次合法拆分改红的，教训见 `devlog/2026-09-19.md` §18.6）。
 */
class CalendarMonthLayoutTest {

    private val componentsDir = listOf(
        "app/src/main/java/com/agon/app/ui/components",
        "src/main/java/com/agon/app/ui/components",
    ).map(::File).firstOrNull { it.isDirectory }

    private fun ktFiles(): List<Pair<String, String>> =
        componentsDir!!.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty()
            .map { it.name to it.readText() }
            .sortedBy { it.first }

    @Test
    fun `月首偏移与前导空格`() {
        // 2026-09-01 是周二 ⇒ 前导 1 格；当月 30 天 ⇒ 31 格、5 行
        val layout = CalendarMonthLayout(YearMonth.of(2026, 9))
        assertEquals("9 月 1 日周二 ⇒ 前导 1 格", 1, layout.leading)
        assertEquals(30, layout.daysInMonth)
        assertEquals(31, layout.cells)
        assertEquals(5, layout.rows)
        assertNull("首格是前导空格", layout.dayNumAt(0, 0))
        assertEquals(1, layout.dayNumAt(0, 1)!!)
        assertEquals(30, layout.dayNumAt(4, 2)!!)
        assertNull("30 号之后没有格", layout.dayNumAt(4, 3))
    }

    @Test
    fun `一周从周一起`() {
        // 2027-02-01 恰是周一 ⇒ 前导 0 格、28 天正好铺满 4 行，一格不多
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2027, 2, 1).dayOfWeek)
        val feb = CalendarMonthLayout(YearMonth.of(2027, 2))
        assertEquals(0, feb.leading)
        assertEquals(28, feb.cells)
        assertEquals(4, feb.rows)
        assertEquals(1, feb.dayNumAt(0, 0)!!)
        assertEquals(28, feb.dayNumAt(3, 6)!!)
        assertNull("最后一行铺满 ⇒ 没有第 5 行", feb.dayNumAt(4, 0))
    }

    @Test
    fun `闰年二月与平年二月`() {
        // 2024-02：1 号周四（前导 3）、29 天 ⇒ 32 格、5 行，末日落在第 5 行第 4 列
        val leap = CalendarMonthLayout(YearMonth.of(2024, 2))
        assertEquals(29, leap.daysInMonth)
        assertEquals(3, leap.leading)
        assertEquals(5, leap.rows)
        assertEquals(29, leap.dayNumAt(4, 3)!!)
        assertNull(leap.dayNumAt(4, 4))
        // 2026-02：1 号周日（前导 6）、28 天 ⇒ 34 格仍是 5 行，末行只铺到第 6 列
        val plain = CalendarMonthLayout(YearMonth.of(2026, 2))
        assertEquals(28, plain.daysInMonth)
        assertEquals(6, plain.leading)
        assertEquals(5, plain.rows)
        assertEquals(28, plain.dayNumAt(4, 5)!!)
        assertNull("末行第 7 列是空的", plain.dayNumAt(4, 6))
    }

    @Test
    fun `跨年两个月的偏移各自独立`() {
        // 2026-12：前导 1、31 天 ⇒ 32 格 5 行，31 号在 (4,3)
        val dec = CalendarMonthLayout(YearMonth.of(2026, 12))
        assertEquals(1, dec.leading)
        assertEquals(31, dec.daysInMonth)
        assertEquals(5, dec.rows)
        assertEquals(31, dec.dayNumAt(4, 3)!!)
        // 2027-01：前导 4、31 天 ⇒ 35 格 5 行，1 号在 (0,4)、31 号在 (4,6)
        val jan = CalendarMonthLayout(YearMonth.of(2027, 1))
        assertEquals(4, jan.leading)
        assertEquals(31, jan.daysInMonth)
        assertEquals(5, jan.rows)
        assertEquals(1, jan.dayNumAt(0, 4)!!)
        assertNull(jan.dayNumAt(0, 3))
        assertEquals(31, jan.dayNumAt(4, 6)!!)
    }

    @Test
    fun `行数下界与上界都出现过`() {
        // 4 行：2027-02（前导 0、28 天）；6 行：2027-08（前导 6、31 天 ⇒ 37 格）与 2025-03（前导 5、31 天 ⇒ 36 格）
        assertEquals(4, CalendarMonthLayout(YearMonth.of(2027, 2)).rows)
        val aug = CalendarMonthLayout(YearMonth.of(2027, 8))
        assertEquals(37, aug.cells)
        assertEquals(6, aug.rows)
        assertEquals(31, aug.dayNumAt(5, 1)!!)
        val mar = CalendarMonthLayout(YearMonth.of(2025, 3))
        assertEquals(36, mar.cells)
        assertEquals(6, mar.rows)
        assertEquals(31, mar.dayNumAt(5, 0)!!)
    }

    @Test
    fun `连续六十个月每号恰好出现一次`() {
        val months = (0 until 60).map { YearMonth.of(2024, 1).plusMonths(it.toLong()) }
        val bad = months.mapNotNull { month ->
            val layout = CalendarMonthLayout(month)
            val laid = (0 until layout.rows).flatMap { row ->
                (0 until 7).map { col -> layout.dayNumAt(row, col) }
            }.filterNotNull().sorted()
            val expected = (1..layout.daysInMonth).toList()
            when {
                laid != expected -> "$month 铺出来的号数不是一整个月：$laid"
                layout.rows !in 4..6 -> "$month 行数 ${layout.rows} 越界"
                layout.rows * 7 - layout.cells < 0 -> "$month 格子数 ${layout.cells} 放不下"
                else -> null
            }
        }
        assertEquals("60 个月里有网格不闭合的月份：" + bad, emptyList<String>(), bad)
    }

    @Test
    fun `月网格算式只住一个文件且装配体仍在调用`() {
        assertTrue("`ui/components` 目录没找到（工作目录变了？）", componentsDir != null)
        val files = ktFiles()
        assertTrue("扫到 0 个组件文件 ⇒ 探测本身失效", files.isNotEmpty())
        val needles = listOf("dayOfWeek.value - 1", "(cells + 6) / 7", "lengthOfMonth()")
        val owners = files.filter { entry -> needles.any { it in entry.second } }.map { it.first }
        assertEquals(
            "月网格算式出现在多处（只许住在 CalendarMonthLayout.kt；复制一份、原地不删就是这种形状）：" + owners,
            listOf("CalendarMonthLayout.kt"),
            owners,
        )
        val card = files.firstOrNull { it.first == "ExpiryCalendar.kt" }
        assertTrue("`ExpiryCalendar.kt` 没找到", card != null)
        assertTrue(
            "`ExpiryCalendar.kt` 不再调用 CalendarMonthLayout ⇒ 调用点被删坏了",
            card!!.second.contains("CalendarMonthLayout(month)"),
        )
    }
}
