package com.agon.app.ui.screens

import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ArchiveStateTest {

    private val today = LocalDate.of(2026, 8, 22)

    private fun archived(id: String, name: String, reason: ArchiveReason) =
        ArchivedItem(
            item = FoodItem(
                id = id,
                name = name,
                category = "snack",
                quantity = 1,
                unit = "包",
                productionEpochDay = today.minusDays(30).toEpochDay(),
                shelfLifeDays = 30,
            ),
            archivedEpochDay = today.toEpochDay(),
            reason = reason,
        )

    @Test
    fun `filterArchiveItems 支持按归档原因筛选`() {
        val list = listOf(
            archived("1", "已吃完牛奶", ArchiveReason.CONSUMED),
            archived("2", "过期清理面包", ArchiveReason.EXPIRED),
            archived("3", "手动删除饼干", ArchiveReason.DELETED),
        )

        val consumed = filterArchiveItems(list, reasonFilter = ArchiveReason.CONSUMED)
        assertEquals(listOf("1"), consumed.map { it.item.id })

        val expired = filterArchiveItems(list, reasonFilter = ArchiveReason.EXPIRED)
        assertEquals(listOf("2"), expired.map { it.item.id })

        val deleted = filterArchiveItems(list, reasonFilter = ArchiveReason.DELETED)
        assertEquals(listOf("3"), deleted.map { it.item.id })

        val all = filterArchiveItems(list, reasonFilter = null)
        assertEquals(3, all.size)
    }

    @Test
    fun `filterArchiveItems 支持按名称关键词模糊搜索`() {
        val list = listOf(
            archived("1", "乐事原味薯片", ArchiveReason.CONSUMED),
            archived("2", "乐事黄瓜味薯片", ArchiveReason.EXPIRED),
            archived("3", "可口可乐", ArchiveReason.CONSUMED),
        )

        val result = filterArchiveItems(list, query = "  薯片  ")
        assertEquals(2, result.size)
        assertEquals(listOf("1", "2"), result.map { it.item.id })
    }

    @Test
    fun `filterArchiveItems 组合筛选交集正确`() {
        val list = listOf(
            archived("1", "乐事原味薯片", ArchiveReason.CONSUMED),
            archived("2", "乐事黄瓜味薯片", ArchiveReason.EXPIRED),
            archived("3", "可口可乐", ArchiveReason.CONSUMED),
        )

        val result = filterArchiveItems(list, reasonFilter = ArchiveReason.CONSUMED, query = "薯片")
        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)
    }

    @Test
    fun `filterArchiveItems 空列表安全不崩溃`() {
        assertTrue(filterArchiveItems(emptyList(), reasonFilter = ArchiveReason.CONSUMED, query = "test").isEmpty())
    }
}
