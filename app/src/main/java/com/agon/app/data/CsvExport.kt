package com.agon.app.data

import java.time.LocalDate

/**
 * 将库存食品导出为标准 CSV 格式。
 * 开头包含 UTF-8 BOM，确保 Windows/Mac/手机端 Excel 直接打开中文不乱码。
 */
fun buildCsvExport(
    items: List<FoodItem>,
    categories: List<CategoryDef>,
    thresholds: Map<String, Int> = emptyMap(),
    today: LocalDate = LocalDate.now(),
): String {
    val sb = StringBuilder()
    // UTF-8 BOM
    sb.append('\uFEFF')
    sb.append("食品名称,分类,存放位置,数量,单位,生产日期,保质期(天),预计到期日,剩余天数,状态,备注\n")
    for (item in items) {
        val catLabel = categories.byId(item.category).label
        val statusLabel = when (item.statusForAt(today, thresholds)) {
            FoodStatus.SAFE -> "安全"
            FoodStatus.EXPIRING -> "临期"
            FoodStatus.EXPIRED -> "已过期"
        }
        val daysLeft = item.daysLeftAt(today)
        sb.append(escapeCsvField(item.name)).append(',')
        sb.append(escapeCsvField(catLabel)).append(',')
        sb.append(escapeCsvField(item.location.ifBlank { "未设置" })).append(',')
        sb.append(item.quantity).append(',')
        sb.append(escapeCsvField(item.unit)).append(',')
        sb.append(item.productionDate.dot()).append(',')
        sb.append(item.shelfLifeDays).append(',')
        sb.append(item.expiryDate.dot()).append(',')
        sb.append(daysLeft).append(',')
        sb.append(escapeCsvField(statusLabel)).append(',')
        sb.append(escapeCsvField(item.note)).append('\n')
    }
    return sb.toString()
}

internal fun escapeCsvField(value: String): String {
    // 公式注入防护（2026-09-15）：Excel / LibreOffice / Google Sheets 会把以 = + - @ 开头的
    // 单元格当公式执行（`=HYPERLINK(...)`、`=cmd|...`），表格被分享出去时可能变成钓鱼载体。
    // 通行做法是前置一个半角单引号让表格把它当纯文本（'' 本身也属于需要防的前缀）。
    val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    if (safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r')) {
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    return safe
}
