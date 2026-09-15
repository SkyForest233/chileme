package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {

    // ---- CSV 公式注入防护（2026-09-15）----

    @Test
    fun `以公式前缀开头的字段会被加单引号转义`() {
        // Excel / LibreOffice / Numbers 会把 = + - @ 开头的单元格当公式执行；
        // 用户备注里若写「=HYPERLINK(...)」，导出后被别人打开就可能变成钓鱼链接。
        assertEquals("'=1+1", escapeCsvField("=1+1"))
        assertEquals("'+1", escapeCsvField("+1"))
        assertEquals("'-1", escapeCsvField("-1"))
        assertEquals("'@SUM(A1)", escapeCsvField("@SUM(A1)"))
    }

    @Test
    fun `普通文本与转义规则不受影响`() {
        assertEquals("牛奶", escapeCsvField("牛奶"))
        assertEquals("", escapeCsvField(""))
        assertEquals("\"a,b\"", escapeCsvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", escapeCsvField("say \"hi\""))
        // 含逗号的公式同样先加单引号、再整体加引号
        assertEquals("\"'=A1,B1\"", escapeCsvField("=A1,B1"))
    }

    private val today = LocalDate.of(2026, 8, 22)
    private val categories = listOf(
        CategoryDef("snack", "零食", "🍿"),
        CategoryDef("drink", "饮品", "🥤"),
    )

    private fun item(
        id: String,
        name: String,
        category: String = "snack",
        location: String = "零食柜",
        quantity: Int = 2,
        unit: String = "包",
        daysFromToday: Long = 10,
        note: String = "",
    ): FoodItem {
        val expiry = today.plusDays(daysFromToday)
        val prod = expiry.minusDays(30)
        return FoodItem(
            id = id,
            name = name,
            category = category,
            location = location,
            quantity = quantity,
            unit = unit,
            productionEpochDay = prod.toEpochDay(),
            shelfLifeDays = 30,
            note = note,
        )
    }

    @Test
    fun `buildCsvExport 包含 UTF-8 BOM 且表头正确`() {
        val csv = buildCsvExport(emptyList(), categories, today = today)
        assertTrue("开头必须包含 UTF-8 BOM", csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("食品名称,分类,存放位置,数量,单位,生产日期,保质期(天),预计到期日,剩余天数,状态,备注"))
    }

    @Test
    fun `buildCsvExport 正确格式化食品属性`() {
        val items = listOf(
            item("1", "乐事薯片", category = "snack", location = "客厅", quantity = 3, unit = "袋", daysFromToday = 15, note = "原味"),
            item("2", "可乐", category = "drink", location = "", quantity = 1, unit = "罐", daysFromToday = -2),
        )
        val csv = buildCsvExport(items, categories, today = today)
        val lines = csv.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }

        assertEquals(3, lines.size) // header + 2 items
        val row1 = lines[1]
        assertTrue(row1.contains("乐事薯片,零食,客厅,3,袋,"))
        assertTrue(row1.contains("安全,原味"))

        val row2 = lines[2]
        assertTrue(row2.contains("可乐,饮品,未设置,1,罐,"))
        assertTrue(row2.contains("已过期"))
    }

    @Test
    fun `escapeCsvField 特殊字符转义正确`() {
        assertEquals("普通文本", escapeCsvField("普通文本"))
        assertEquals("\"包含,逗号\"", escapeCsvField("包含,逗号"))
        assertEquals("\"包含\"\"引号\"\"\"", escapeCsvField("包含\"引号\""))
        assertEquals("\"多行\n文本\"", escapeCsvField("多行\n文本"))
    }
}
