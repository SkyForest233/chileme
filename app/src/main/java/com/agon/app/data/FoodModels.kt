package com.agon.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

const val DEFAULT_EXPIRING_THRESHOLD = 7

/**
 * 可自定义分类：id 稳定不变（默认分类沿用旧枚举名，新增用 UUID），
 * label/emoji 可编辑。FoodItem.category 存 id 字符串——与旧版枚举 JSON 直接兼容。
 */
@Serializable
data class CategoryDef(val id: String, val label: String, val emoji: String)

val DefaultCategories = listOf(
    CategoryDef("SNACK", "零食", "🍪"),
    CategoryDef("DRINK", "饮料", "🥤"),
    CategoryDef("DAIRY", "乳制品", "🥛"),
    CategoryDef("CANDY", "糖果", "🍬"),
    CategoryDef("INSTANT", "速食", "🍜"),
    CategoryDef("FRUIT", "果干", "🍇"),
    CategoryDef("NUTS", "坚果", "🥜"),
    CategoryDef("OTHER", "其他", "🧺"),
)

val DefaultLocations = listOf("零食柜", "冰箱", "厨房", "储物间", "客厅", "卧室")

val FallbackCategory = CategoryDef("OTHER", "其他", "🧺")

fun List<CategoryDef>.byId(id: String): CategoryDef =
    firstOrNull { it.id == id } ?: FallbackCategory

enum class FoodStatus { SAFE, EXPIRING, EXPIRED }

@Serializable
data class FoodItem(
    val id: String,
    val name: String,
    val category: String = "SNACK",
    val quantity: Int = 1,
    val unit: String = "件",
    val productionEpochDay: Long,
    val shelfLifeDays: Int,
    val note: String = "",
    val location: String = "",
    val photoPath: String = "",
    val expiringThresholdDays: Int? = null,
    /** 自定义封面 emoji/短文字；与照片都未设置时回退分类 emoji */
    val coverText: String = "",
)

@Serializable
enum class ArchiveReason(val label: String, val emoji: String) {
    DELETED("已删除", "🗑️"),
    CONSUMED("已吃完", "😋"),
    EXPIRED("过期清理", "⚠️"),
}

@Serializable
data class ArchivedItem(
    val item: FoodItem,
    val archivedEpochDay: Long,
    val reason: ArchiveReason,
)

@Serializable
data class ConsumptionRecord(
    val name: String,
    val category: String = "SNACK",
    val amount: Int,
    val unit: String,
    val epochDay: Long,
)

@Serializable
data class HistoryEntry(
    val name: String,
    val category: String = "SNACK",
    val unit: String,
    val shelfLifeDays: Int,
    val location: String = "",
    val coverText: String = "",
    val note: String = "",
    val expiringThresholdDays: Int? = null,
)

/** 把任意食品记录转为联想条目——用于库存/归档参与名称联想 */
fun FoodItem.toHistoryEntry() = HistoryEntry(
    name = name,
    category = category,
    unit = unit,
    shelfLifeDays = shelfLifeDays,
    location = location,
    coverText = coverText,
    note = note,
    expiringThresholdDays = expiringThresholdDays,
)

@Serializable
data class BackupData(
    val version: Int = 2,
    val exportedEpochDay: Long = LocalDate.now().toEpochDay(),
    val items: List<FoodItem> = emptyList(),
    val archived: List<ArchivedItem> = emptyList(),
    val consumption: List<ConsumptionRecord> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val categoryThresholds: Map<String, Int> = emptyMap(),
    val categories: List<CategoryDef> = emptyList(),
    val locations: List<String> = emptyList(),
)

val FoodItem.productionDate: LocalDate
    get() = LocalDate.ofEpochDay(productionEpochDay)

val FoodItem.expiryDate: LocalDate
    get() = productionDate.plusDays(shelfLifeDays.toLong())

val FoodItem.daysLeft: Long
    get() = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)

fun FoodItem.effectiveThreshold(categoryThresholds: Map<String, Int>): Int =
    expiringThresholdDays ?: categoryThresholds[category] ?: DEFAULT_EXPIRING_THRESHOLD

fun FoodItem.statusFor(categoryThresholds: Map<String, Int>): FoodStatus {
    val t = effectiveThreshold(categoryThresholds)
    return when {
        daysLeft < 0 -> FoodStatus.EXPIRED
        daysLeft <= t -> FoodStatus.EXPIRING
        else -> FoodStatus.SAFE
    }
}

val FoodItem.freshness: Float
    get() = if (shelfLifeDays <= 0) 0f else (daysLeft.toFloat() / shelfLifeDays.toFloat()).coerceIn(0f, 1f)

/** 正相关进度：保质期已经过去的比例（时间走了多少进度条就走多少） */
val FoodItem.elapsedRatio: Float
    get() = 1f - freshness

val FoodItem.remainingText: String
    get() = when {
        daysLeft < 0 -> "已过期 ${-daysLeft} 天"
        daysLeft == 0L -> "今天到期"
        else -> "还剩 $daysLeft 天"
    }

private val cnDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val cnDayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)

fun LocalDate.cn(): String = format(cnDateFormatter)
fun LocalDate.cnDay(): String = format(cnDayFormatter)
