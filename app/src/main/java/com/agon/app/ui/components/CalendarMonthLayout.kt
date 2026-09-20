package com.agon.app.ui.components

import java.time.YearMonth

/**
 * 月网格的**排版数学**（09-19 #11e 从 `ExpiryCalendar.kt` 的 `MonthGrid` 里抽出来）。
 *
 * 为什么单独一个文件：这四行算式决定日历长什么样，却曾经住在 `@Composable` 里 ——
 * 住在渲染体里就意味着**测不到**（要测得起 Compose），而「月首偏移 / 闰年 2 月 / 跨年」正是日历
 * 最容易错的三处。抽成纯类之后，同一批真值由 `CalendarMonthLayoutTest` 用**字面常量**钉住
 * （不是拿同样的公式再算一遍，那等于没测）。
 *
 * 约定与不变的地方：
 * - **一周从周一起**（`dayOfWeek.value` 里周一 = 1、周日 = 7 ⇒ 前导空格数就是 `value - 1`），
 *   表头「一二三四五六日」与本类的列序是同一套口径；改任何一边都会让整月错位 —— 首行首列的
 *   `isDayOfWeek(MONDAY)` 断言钉的就是这件事。
 * - 网格不补上个月的尾巴：前导格与末尾格都是空的，由调用方渲染 `Spacer`（本类返回 `null`）。
 * - 行数按「格子数除 7 向上取整」= `(cells + 6) / 7`，所以 2027-02 是 4 行、2027-08 是 6 行。
 */
internal class CalendarMonthLayout(month: YearMonth) {

    // 周一 = 1 ... 周日 = 7；前导空格数
    val leading = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    /** 第 [row] 行第 [col] 列对应的"几号"；前导 / 尾部空格返回 `null`（调用方画占位格）。 */
    fun dayNumAt(row: Int, col: Int): Int? {
        val dayNum = row * 7 + col - leading + 1
        return if (dayNum in 1..daysInMonth) dayNum else null
    }
}
