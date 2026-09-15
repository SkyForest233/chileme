package com.agon.app.ui.screens

import com.agon.app.data.FoodItem
import com.agon.app.data.FoodStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeScreenStateTest {

    private val today = LocalDate.of(2026, 8, 22)

    private fun food(
        id: String,
        name: String,
        category: String = "snack",
        daysFromToday: Long = 10,
        quantity: Int = 1,
    ): FoodItem {
        val expiry = today.plusDays(daysFromToday)
        val prod = expiry.minusDays(30)
        return FoodItem(
            id = id,
            name = name,
            category = category,
            quantity = quantity,
            unit = "包",
            productionEpochDay = prod.toEpochDay(),
            shelfLifeDays = 30,
        )
    }

    @Test
    fun `calculateUrgentItems 仅提取临期与过期项并按紧迫度升序排列`() {
        val items = listOf(
            food("safe", "安全食品", daysFromToday = 25), // 安全（> 7天）
            food("expired_yesterday", "昨天过期", daysFromToday = -1), // 过期
            food("expiring_soon", "明天到期", daysFromToday = 1), // 临期
            food("expiring_in_5_days", "5天后到期", daysFromToday = 5), // 临期
        )

        val urgent = calculateUrgentItems(items, thresholds = emptyMap(), today = today)
        assertEquals(3, urgent.size)
        // 验证排序：按剩余天数升序（最紧急的最前）
        assertEquals(listOf("expired_yesterday", "expiring_soon", "expiring_in_5_days"), urgent.map { it.id })
    }

    @Test
    fun `quantityOfStatus 按件求和而不是数记录条数`() {
        val items = listOf(
            food("milk", "牛奶", daysFromToday = -1, quantity = 6), // 过期 6 件
            food("yogurt", "酸奶", daysFromToday = -2, quantity = 2), // 过期 2 件
            food("bread", "面包", daysFromToday = 1, quantity = 3), // 临期 3 件
            food("safe", "安全食品", daysFromToday = 25, quantity = 9), // 不计入
        )

        // 首页横幅「有 N 件食品已过期/即将到期」走的是件数，不是 records 条数
        assertEquals(8, quantityOfStatus(items, emptyMap(), today, FoodStatus.EXPIRED))
        assertEquals(3, quantityOfStatus(items, emptyMap(), today, FoodStatus.EXPIRING))
        assertEquals(0, quantityOfStatus(emptyList(), emptyMap(), today, FoodStatus.EXPIRED))
    }

    @Test
    fun `calculateUrgentItems 遵循分类自定义阈值`() {
        val thresholds = mapOf("dairy" to 14)
        val items = listOf(
            food("milk", "牛奶", category = "dairy", daysFromToday = 10), // 自定义 14 天下为临期
            food("chips", "薯片", category = "snack", daysFromToday = 10), // 默认 7 天下为安全
        )

        val urgent = calculateUrgentItems(items, thresholds = thresholds, today = today)
        assertEquals(1, urgent.size)
        assertEquals("milk", urgent[0].id)
    }
}
