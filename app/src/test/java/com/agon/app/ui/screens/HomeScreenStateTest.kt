package com.agon.app.ui.screens

import com.agon.app.data.FoodItem
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
    ): FoodItem {
        val expiry = today.plusDays(daysFromToday)
        val prod = expiry.minusDays(30)
        return FoodItem(
            id = id,
            name = name,
            category = category,
            quantity = 1,
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
