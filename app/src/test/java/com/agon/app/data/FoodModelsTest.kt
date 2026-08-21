package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * FoodModels.kt 中纯函数 / 派生属性的单元测试。
 *
 * 这些是「纯 JVM 单测」：不需要模拟器、不需要 Android 框架，
 * `./gradlew testDebugUnitTest` 秒级跑完。
 *
 * 注意 daysLeft / statusFor 内部调用 LocalDate.now()，
 * 因此这里用「相对今天」的方式构造数据（见 itemExpiringIn）。
 * 后续若引入 Clock 抽象（见 backlog 阶段 6），这些用例可改为注入固定时间，
 * 届时才能真正覆盖跨零点场景。
 */
class FoodModelsTest {

    /** 构造一个「还剩 daysLeft 天过期」的条目。 */
    private fun itemExpiringIn(
        daysLeft: Long,
        shelfLifeDays: Int = 30,
        category: String = "SNACK",
        threshold: Int? = null,
    ): FoodItem {
        val expiry = LocalDate.now().plusDays(daysLeft)
        return FoodItem(
            id = "test",
            name = "测试食品",
            category = category,
            productionEpochDay = expiry.minusDays(shelfLifeDays.toLong()).toEpochDay(),
            shelfLifeDays = shelfLifeDays,
            expiringThresholdDays = threshold,
        )
    }

    // ---- daysLeft ----

    @Test
    fun `daysLeft 今天到期时为 0`() {
        assertEquals(0L, itemExpiringIn(0).daysLeft)
    }

    @Test
    fun `daysLeft 未过期为正数`() {
        assertEquals(5L, itemExpiringIn(5).daysLeft)
    }

    @Test
    fun `daysLeft 已过期为负数`() {
        assertEquals(-3L, itemExpiringIn(-3).daysLeft)
    }

    // ---- statusFor：边界值最容易写错，逐个钉死 ----

    @Test
    fun `statusFor 刚好等于阈值时算临期`() {
        // daysLeft == threshold 走 <= 分支 → EXPIRING
        val item = itemExpiringIn(7)
        assertEquals(FoodStatus.EXPIRING, item.statusFor(mapOf("SNACK" to 7)))
    }

    @Test
    fun `statusFor 比阈值多一天时算安全`() {
        val item = itemExpiringIn(8)
        assertEquals(FoodStatus.SAFE, item.statusFor(mapOf("SNACK" to 7)))
    }

    @Test
    fun `statusFor 今天到期算临期而非过期`() {
        // daysLeft == 0：不满足 < 0，落入 <= t → EXPIRING
        assertEquals(FoodStatus.EXPIRING, itemExpiringIn(0).statusFor(emptyMap()))
    }

    @Test
    fun `statusFor 昨天到期算过期`() {
        assertEquals(FoodStatus.EXPIRED, itemExpiringIn(-1).statusFor(emptyMap()))
    }

    // ---- effectiveThreshold：三级回退优先级 ----

    @Test
    fun `effectiveThreshold 条目自定义优先于分类设置`() {
        val item = itemExpiringIn(10, threshold = 3)
        assertEquals(3, item.effectiveThreshold(mapOf("SNACK" to 7)))
    }

    @Test
    fun `effectiveThreshold 无自定义时取分类设置`() {
        val item = itemExpiringIn(10)
        assertEquals(7, item.effectiveThreshold(mapOf("SNACK" to 7)))
    }

    @Test
    fun `effectiveThreshold 都没有时取默认值`() {
        val item = itemExpiringIn(10)
        assertEquals(DEFAULT_EXPIRING_THRESHOLD, item.effectiveThreshold(emptyMap()))
    }

    // ---- freshness：除零与越界 ----

    @Test
    fun `freshness 保质期为 0 时不崩溃且返回 0`() {
        // shelfLifeDays <= 0 是脏数据/导入数据可能出现的情况，
        // 若没有这层保护就是除零 → NaN → 进度条渲染异常
        val item = itemExpiringIn(0, shelfLifeDays = 0)
        assertEquals(0f, item.freshness, 0.001f)
    }

    @Test
    fun `freshness 已过期时钳制到 0 而非负数`() {
        val item = itemExpiringIn(-10, shelfLifeDays = 30)
        assertEquals(0f, item.freshness, 0.001f)
    }

    @Test
    fun `freshness 全新时钳制到 1 而非大于 1`() {
        val item = itemExpiringIn(30, shelfLifeDays = 30)
        assertEquals(1f, item.freshness, 0.001f)
    }

    @Test
    fun `elapsedRatio 与 freshness 互补`() {
        val item = itemExpiringIn(15, shelfLifeDays = 30)
        assertEquals(1f, item.freshness + item.elapsedRatio, 0.001f)
    }

    // ---- remainingText：直接面向用户的文案 ----

    @Test
    fun `remainingText 今天到期`() {
        assertEquals("今天到期", itemExpiringIn(0).remainingText)
    }

    @Test
    fun `remainingText 已过期显示正数天数`() {
        // 注意是 -daysLeft，写成 daysLeft 就会显示「已过期 -3 天」
        assertEquals("已过期 3 天", itemExpiringIn(-3).remainingText)
    }

    @Test
    fun `remainingText 未过期`() {
        assertEquals("还剩 5 天", itemExpiringIn(5).remainingText)
    }

    // ---- 分类回退 ----

    @Test
    fun `byId 未知分类回退到其他而不是抛异常`() {
        // 用户删除自定义分类后，旧条目仍持有已失效的 category id
        assertEquals(FallbackCategory, DefaultCategories.byId("已删除的分类"))
    }

    @Test
    fun `byId 已知分类正常返回`() {
        assertEquals("零食", DefaultCategories.byId("SNACK").label)
    }
}
