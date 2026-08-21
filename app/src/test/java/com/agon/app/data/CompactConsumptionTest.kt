package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 消耗记录压缩逻辑测试。
 *
 * 这组用例的价值在于：`compactConsumption` 原本是 private 且内部直接调用
 * LocalDate.now()，**无法测试**。为了能测，把它抽成顶层纯函数
 * `compactConsumptionAt(records, today, idFactory)` —— 这正是单测带来的设计改善：
 * 依赖（当前时间、id 生成）变成显式参数，行为可预测、可复现。
 */
class CompactConsumptionTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun record(
        name: String,
        amount: Int,
        date: LocalDate,
        unit: String = "件",
        id: String? = "x",
    ) = ConsumptionRecord(
        name = name,
        amount = amount,
        unit = unit,
        epochDay = date.toEpochDay(),
        id = id,
    )

    /** 固定 id 工厂，避免随机 UUID 让断言不可复现。 */
    private var seq = 0
    private val fixedIds = { "gen-${seq++}" }

    // ---- 90 天分界 ----

    @Test
    fun `90 天内的记录保持逐笔明细不被聚合`() {
        val records = listOf(
            record("牛奶", 1, today.minusDays(1)),
            record("牛奶", 2, today.minusDays(2)),
        )
        val result = compactConsumptionAt(records, today, fixedIds)
        // 两笔都在窗口内，应原样保留（不合并成 3）
        assertEquals(2, result.size)
        assertTrue(result.all { it.amount == 1 || it.amount == 2 })
    }

    @Test
    fun `超过 90 天的同月同名记录被聚合为一条并求和`() {
        val old = today.minusDays(200)
        val records = listOf(
            record("牛奶", 1, old),
            record("牛奶", 2, old.plusDays(1)),
        )
        val result = compactConsumptionAt(records, today, fixedIds)
        assertEquals(1, result.size)
        assertEquals(3, result[0].amount)
        // epochDay 归一到当月 1 号
        assertEquals(1, LocalDate.ofEpochDay(result[0].epochDay).dayOfMonth)
    }

    @Test
    fun `不同月份的记录不会被合并`() {
        val records = listOf(
            record("牛奶", 1, LocalDate.of(2026, 1, 15)),
            record("牛奶", 2, LocalDate.of(2026, 2, 15)),
        )
        val result = compactConsumptionAt(records, today, fixedIds)
        assertEquals(2, result.size)
    }

    // ---- 回归测试：本次修复的 bug ----

    @Test
    fun `聚合记录必须带 id 否则删除按钮静默失效`() {
        // 回归：此前聚合分支未设置 id，ConsumptionRecord.id 默认 null，
        // 消耗记录页 `record.id?.let { delete(it) }` 直接跳过 → 点删除没反应。
        val records = listOf(record("牛奶", 1, today.minusDays(200)))
        val result = compactConsumptionAt(records, today, fixedIds)
        assertNotNull("聚合记录的 id 不能为 null", result[0].id)
    }

    @Test
    fun `同名但不同单位的记录不能被合并`() {
        // 单测抓到的真实缺陷：分组键原为 Triple(year, month, name)，不含 unit。
        // "牛奶 3 瓶" 与 "牛奶 2 箱" 会被合并成 "牛奶 5 瓶" —— 数量凭空错算。
        val old = today.minusDays(200)
        val records = listOf(
            record("牛奶", 3, old, unit = "瓶"),
            record("牛奶", 2, old.plusDays(1), unit = "箱"),
        )
        val result = compactConsumptionAt(records, today, fixedIds)
        assertEquals("不同单位应分别聚合", 2, result.size)
        assertEquals(3, result.first { it.unit == "瓶" }.amount)
        assertEquals(2, result.first { it.unit == "箱" }.amount)
    }

    // ---- 边界 ----

    @Test
    fun `空列表不崩溃`() {
        assertEquals(emptyList<ConsumptionRecord>(), compactConsumptionAt(emptyList(), today, fixedIds))
    }

    @Test
    fun `结果按日期倒序排列`() {
        val records = listOf(
            record("A", 1, today.minusDays(5)),
            record("B", 1, today.minusDays(1)),
            record("C", 1, today.minusDays(3)),
        )
        val result = compactConsumptionAt(records, today, fixedIds)
        val days = result.map { it.epochDay }
        assertEquals(days.sortedDescending(), days)
    }
}
