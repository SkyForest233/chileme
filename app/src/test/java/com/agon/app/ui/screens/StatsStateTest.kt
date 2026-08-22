package com.agon.app.ui.screens

import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsStateTest {

    private val today = LocalDate.of(2026, 8, 22)

    private fun record(name: String, amount: Int, date: LocalDate, category: String = "snack") =
        ConsumptionRecord(
            name = name,
            amount = amount,
            unit = "包",
            epochDay = date.toEpochDay(),
            category = category,
            id = "test-${name}-${date}",
        )

    private fun item(id: String, name: String, category: String, quantity: Int = 1) =
        FoodItem(
            id = id,
            name = name,
            category = category,
            quantity = quantity,
            unit = "包",
            productionEpochDay = today.minusDays(10).toEpochDay(),
            shelfLifeDays = 30,
        )

    // ---- 周 / 月消耗计算 ----

    @Test
    fun `calculateConsumedThisWeek 仅聚合近 7 天内的消耗`() {
        val records = listOf(
            record("薯片", 2, today), // 今天
            record("可乐", 1, today.minusDays(3)), // 3天前
            record("饼干", 3, today.minusDays(6)), // 6天前（第7天边界）
            record("坚果", 5, today.minusDays(7)), // 7天前（超出本周窗口）
            record("旧泡面", 10, today.minusDays(30)), // 上个月
        )
        val result = calculateConsumedThisWeek(records, today)
        assertEquals(2 + 1 + 3, result)
    }

    @Test
    fun `calculateConsumedThisMonth 仅聚合当月 1 号以后的消耗`() {
        val records = listOf(
            record("薯片", 2, LocalDate.of(2026, 8, 22)),
            record("可乐", 3, LocalDate.of(2026, 8, 1)), // 当月 1 号
            record("上月牛奶", 5, LocalDate.of(2026, 7, 31)), // 上月最后一天
            record("半年前零食", 20, LocalDate.of(2026, 2, 15)),
        )
        val result = calculateConsumedThisMonth(records, today)
        assertEquals(2 + 3, result)
    }

    // ---- 7 天趋势计算 ----

    @Test
    fun `calculateDailyTrend 返回连续 7 天且按时间正序排列`() {
        val records = listOf(
            record("薯片", 2, today.minusDays(2)),
            record("可乐", 3, today.minusDays(2)), // 同一天多条合并
            record("牛奶", 1, today),
        )
        val trend = calculateDailyTrend(records, today)
        assertEquals(7, trend.size)

        // 验证日期序列是从 6 天前到今天
        val expectedDates = (0..6).map { today.minusDays(6L - it) }
        assertEquals(expectedDates, trend.map { it.first })

        // 验证各天消耗数量
        val twoDaysAgoAmount = trend.first { it.first == today.minusDays(2) }.second
        val todayAmount = trend.first { it.first == today }.second
        val otherDaysAmount = trend.filter { it.first != today.minusDays(2) && it.first != today }.map { it.second }

        assertEquals(5, twoDaysAgoAmount) // 2 + 3
        assertEquals(1, todayAmount)
        assertTrue(otherDaysAmount.all { it == 0 })
    }

    // ---- 分类库存占比计算 ----

    @Test
    fun `calculateCategoryShare 按分类求和并按数量降序排列且过滤数量为0的分类`() {
        val items = listOf(
            item("1", "薯片", "snack", quantity = 3),
            item("2", "海苔", "snack", quantity = 2), // snack 合计 5
            item("3", "可乐", "drink", quantity = 8), // drink 合计 8
            item("4", "苹果", "fresh", quantity = 0), // 数量为 0 应被过滤
        )
        val share = calculateCategoryShare(items)
        assertEquals(2, share.size)
        assertEquals("drink" to 8, share[0])
        assertEquals("snack" to 5, share[1])
    }

    // ---- TOP 5 消耗排行计算 ----

    @Test
    fun `calculateTopConsumed 聚合多笔同名消耗并按总数降序截取前5名`() {
        val records = listOf(
            record("薯片", 2, today.minusDays(1), "snack"),
            record("薯片", 3, today.minusDays(5), "snack"), // 薯片合计 5
            record("可乐", 10, today.minusDays(2), "drink"), // 可乐合计 10
            record("牛奶", 8, today.minusDays(3), "drink"),  // 牛奶合计 8
            record("饼干", 4, today.minusDays(4), "snack"),  // 饼干合计 4
            record("苹果", 6, today.minusDays(1), "fresh"),  // 苹果合计 6
            record("海苔", 1, today.minusDays(2), "snack"),  // 海苔合计 1（第6名，应被截断）
        )
        val top = calculateTopConsumed(records, limit = 5)
        assertEquals(5, top.size)
        assertEquals("可乐", top[0].first)
        assertEquals(10, top[0].third)

        assertEquals("牛奶", top[1].first)
        assertEquals(8, top[1].third)

        assertEquals("苹果", top[2].first)
        assertEquals(6, top[2].third)

        assertEquals("薯片", top[3].first)
        assertEquals(5, top[3].third)

        assertEquals("饼干", top[4].first)
        assertEquals(4, top[4].third)
    }
}
