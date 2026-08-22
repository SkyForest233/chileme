package com.agon.app.ui.screens

import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FoodListStateTest {

    private val today = LocalDate.of(2026, 8, 22)

    private fun food(
        id: String,
        name: String,
        category: String = "snack",
        location: String = "零食柜",
        daysFromToday: Long = 10,
        quantity: Int = 1,
    ): FoodItem {
        val expiry = today.plusDays(daysFromToday)
        // 设生产日期为 10 天前，保质期为 10 + daysFromToday
        val prod = expiry.minusDays(30)
        return FoodItem(
            id = id,
            name = name,
            category = category,
            location = location,
            quantity = quantity,
            unit = "包",
            productionEpochDay = prod.toEpochDay(),
            shelfLifeDays = 30,
        )
    }

    private fun archived(id: String, name: String, reason: ArchiveReason = ArchiveReason.CONSUMED) =
        ArchivedItem(
            item = food(id, name),
            archivedEpochDay = today.toEpochDay(),
            reason = reason,
        )

    // ---- 状态筛选测试 ----

    @Test
    fun `filterFoodItems 状态筛选正确过滤安全、临期与过期食品`() {
        val items = listOf(
            food("safe", "安全薯片", daysFromToday = 20), // 安全（> 7天）
            food("expiring", "临期牛奶", daysFromToday = 3), // 临期（0~7天）
            food("expired", "过期面包", daysFromToday = -2), // 过期（< 0天）
        )

        val safeResult = filterFoodItems(items, statusFilter = FoodStatusFilter.SAFE, today = today)
        assertEquals(listOf("safe"), safeResult.map { it.id })

        val expiringResult = filterFoodItems(items, statusFilter = FoodStatusFilter.EXPIRING, today = today)
        assertEquals(listOf("expiring"), expiringResult.map { it.id })

        val expiredResult = filterFoodItems(items, statusFilter = FoodStatusFilter.EXPIRED, today = today)
        assertEquals(listOf("expired"), expiredResult.map { it.id })

        val allResult = filterFoodItems(items, statusFilter = FoodStatusFilter.ALL, today = today)
        assertEquals(3, allResult.size)
    }

    @Test
    fun `filterFoodItems 支持分类自定义临期阈值`() {
        val thresholds = mapOf("dairy" to 14) // 乳制品 14 天算临期
        val items = listOf(
            food("milk", "鲜牛奶", category = "dairy", daysFromToday = 10), // 自定义 14 天下算临期
            food("chips", "乐事薯片", category = "snack", daysFromToday = 10), // 默认 7 天下算安全
        )

        val expiringResult = filterFoodItems(
            items = items,
            thresholds = thresholds,
            statusFilter = FoodStatusFilter.EXPIRING,
            today = today,
        )
        assertEquals(listOf("milk"), expiringResult.map { it.id })
    }

    // ---- 组合筛选与排序测试 ----

    @Test
    fun `filterFoodItems 支持名称模糊搜索、分类与存放位置多重交集筛选`() {
        val items = listOf(
            food("1", "乐事原味薯片", category = "snack", location = "客厅零食柜"),
            food("2", "乐事黄瓜味薯片", category = "snack", location = "厨房储物架"),
            food("3", "可口可乐", category = "drink", location = "客厅零食柜"),
            food("4", "百事可乐", category = "drink", location = "冰箱"),
        )

        // 搜索 "薯片" + 分类 "snack" + 位置 "客厅零食柜"
        val result = filterFoodItems(
            items = items,
            query = "  薯片  ",
            categoryFilter = "snack",
            locationFilter = "客厅零食柜",
            today = today,
        )
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `filterFoodItems 排序规则：按剩余天数升序排列，吃完(数量为0)项沉底`() {
        val items = listOf(
            food("later", "较晚到期", daysFromToday = 20, quantity = 2),
            food("zero_qty", "已吃完项", daysFromToday = 1, quantity = 0), // 虽快到期但数量为0，应沉底
            food("soon", "紧急临期", daysFromToday = 2, quantity = 1),
            food("expired", "已过期", daysFromToday = -1, quantity = 1),
        )

        val result = filterFoodItems(items, today = today)
        assertEquals(listOf("expired", "soon", "later", "zero_qty"), result.map { it.id })
    }

    // ---- 归档搜索匹配测试 ----

    @Test
    fun `filterArchivedMatches 空查询返回空，有查询词时进行不区分大小写的模糊匹配`() {
        val archived = listOf(
            archived("a1", "Oreo 饼干"),
            archived("a2", "可口可乐"),
            archived("a3", "原味牛乳"),
        )

        assertTrue(filterArchivedMatches(archived, "").isEmpty())
        assertTrue(filterArchivedMatches(archived, "   ").isEmpty())

        val matchOreo = filterArchivedMatches(archived, "oreo")
        assertEquals(1, matchOreo.size)
        assertEquals("a1", matchOreo[0].item.id)

        val matchMilk = filterArchivedMatches(archived, "乳")
        assertEquals(1, matchMilk.size)
        assertEquals("a3", matchMilk[0].item.id)
    }
}
