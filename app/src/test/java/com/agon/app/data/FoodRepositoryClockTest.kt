package com.agon.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 「时钟注入真的生效」的集成测试（路线图 **#5b**，2026-09-18）。
 *
 * 改造前仓储里那 7 处取时间都是直接向系统要，于是**任何按日期分支的行为都钉不死**：
 * 种子数据的相对日期、归档条目记下的那天、消耗记录的日子、90 天压缩的分界、
 * 损坏留档文件名里的时间戳 —— 测试只能写「相对今天」，而「今天」在跑的当下才知道，
 * 跨零点前后跑同一份测试甚至可能给出不同结果。这里走 internal 主构造把固定时钟传进去，
 * 把这几处一次钉牢。
 *
 * 架子照抄 [FoodRepositoryGuardTest]：真实 DataStore（临时文件）+ 注入的留档目录，
 * 纯 JVM，不需要 Robolectric（理由见那个文件的类注释）。
 */
class FoodRepositoryClockTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 固定的「现在」= 2026-03-05T07:08:09Z（时分秒是为了断言留档文件名里的时间戳）。
     *
     * ⚠️ 刻意选一个**离写这测试当天很远**的日子（当时真实日期是 2026-09-18，差了半年多）：
     * 万一哪天有人把仓储里的时钟调用改回系统时钟，本文件按「固定的今天」摆好的数据
     * 会落到完全不同的相对位置上 ⇒ 断言必红。若固定日期恰好等于真实日期，
     * 这种回归会**静默通过**，守卫等于没写。
     */
    private val fixedNow = LocalDateTime.of(2026, 3, 5, 7, 8, 9)
    private val clock: Clock = Clock.fixed(fixedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val today: LocalDate = fixedNow.toLocalDate()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var corruptDir: File
    private lateinit var repo: FoodRepository

    private val itemsKey = stringPreferencesKey("food_items")
    private val consumptionKey = stringPreferencesKey("consumption_records")

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "clock.preferences_pb") },
        )
        corruptDir = File(tmp.root, "corrupt")
        repo = FoodRepository(dataStore, corruptDir, clock)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---- 夹具 ----

    private fun item(id: String, name: String = "牛奶", quantity: Int = 2) = FoodItem(
        id = id,
        name = name,
        quantity = quantity,
        unit = "瓶",
        productionEpochDay = today.toEpochDay() - 1,
        shelfLifeDays = 30,
    )

    private suspend fun seedRaw(key: Preferences.Key<String>, raw: String) {
        dataStore.edit { it[key] = raw }
    }

    // ---- 用例 ----

    @Test
    fun `种子数据的生产日期跟着注入的时钟走`() = runBlocking {
        repo.seedIfNeeded()

        val items = repo.itemsFlow.first()
        assertEquals("种子应写入 8 条示例", 8, items.size)
        val byName = items.associateBy { it.name }
        assertEquals(
            "「鲜牛奶」= 注入的今天 - 12 天（改造前这里问系统时钟，测试钉不住）",
            today.toEpochDay() - 12,
            byName.getValue("鲜牛奶").productionEpochDay,
        )
        assertEquals(
            "「大白兔奶糖」= 注入的今天 - 200 天",
            today.toEpochDay() - 200,
            byName.getValue("大白兔奶糖").productionEpochDay,
        )
    }

    @Test
    fun `归档条目记下的那天是注入的今天`() = runBlocking {
        repo.upsert(item("id-1"))

        repo.archiveItems(setOf("id-1"), ArchiveReason.DELETED)

        val archived = repo.archiveFlow.first()
        assertEquals(1, archived.size)
        assertEquals("归档日期必须来自注入的时钟", today.toEpochDay(), archived[0].archivedEpochDay)
        assertEquals(ArchiveReason.DELETED, archived[0].reason)
    }

    @Test
    fun `消耗记录与「吃完自动归档」都用注入的今天`() = runBlocking {
        repo.upsert(item("id-1", quantity = 1))

        val result = repo.changeQuantity("id-1", -1)

        assertTrue("吃到 0 应触发自动归档", result.autoArchived)
        val consumed = repo.consumptionFlow.first()
        assertEquals(1, consumed.size)
        assertEquals("消耗记录的日子 = 注入的今天", today.toEpochDay(), consumed[0].epochDay)
        assertFalse("新写的是逐笔明细，不是月度聚合", consumed[0].aggregated)
        val archived = repo.archiveFlow.first()
        assertEquals(1, archived.size)
        assertEquals(today.toEpochDay(), archived[0].archivedEpochDay)
        assertEquals(ArchiveReason.CONSUMED, archived[0].reason)
    }

    @Test
    fun `90 天分界按注入的今天算：界内留逐笔，界外聚合到月初`() = runBlocking {
        repo.upsert(item("id-1", quantity = 5))
        // 直接写原始 JSON 摆夹具，绕开 addConsumption —— 它自己也会压缩，
        // 走它的话「界外那条」在摆进去的当下就被聚合了，测不到 changeQuantity 这次的压缩。
        val recentDay = today.minusDays(1) // 2026-03-04：分界（今天 - 90）之内 ⇒ 该留逐笔
        val oldDay = today.minusDays(200) // 2025-08-17：远早于分界 ⇒ 该被聚合
        seedRaw(
            consumptionKey,
            json.encodeToString(
                listOf(
                    ConsumptionRecord("牛奶", "DAIRY", 1, "瓶", recentDay.toEpochDay(), "c-recent"),
                    ConsumptionRecord("牛奶", "DAIRY", 2, "瓶", oldDay.toEpochDay(), "c-old"),
                ),
            ),
        )

        repo.changeQuantity("id-1", -1) // 任何一次消耗写入都会触发压缩

        val records = repo.consumptionFlow.first()
        assertEquals("压缩后应是 3 条：新写的 + 界内那条 + 界外聚合出的那条", 3, records.size)

        val recent = records.single { it.id == "c-recent" }
        assertEquals("界内必须保持逐笔明细，日期原样不动", recentDay.toEpochDay(), recent.epochDay)
        assertFalse(recent.aggregated)

        val agg = records.single { it.aggregated }
        assertEquals(
            "界外那条应聚合到**注入的今天**所算出的当月 1 号（用系统时钟会落到别的月份）",
            LocalDate.of(2025, 8, 1).toEpochDay(),
            agg.epochDay,
        )
        assertEquals(2, agg.amount)

        val fresh = records.single { it.epochDay == today.toEpochDay() }
        assertEquals("新写的消耗记录用注入的今天", "牛奶", fresh.name)
    }

    @Test
    fun `损坏留档的文件名用注入的时间戳`() = runBlocking {
        seedRaw(itemsKey, "{\"broken\":[1,2,3")

        repo.upsert(item("id-1")) // 解码失败 ⇒ markCorrupt 留档

        assertEquals(
            "留档文件名里的时间戳（yyyyMMdd_HHmmss）必须来自注入的时钟",
            listOf("food_items-20260305_070809.json"),
            corruptDir.listFiles()!!.map { it.name },
        )
    }

    @Test
    fun `CSV 导出的剩余天数按注入的今天算`() = runBlocking {
        // 保质期正好到「注入的今天」为止 ⇒ 剩余 0 天、状态「临期」。
        // 若这里问的是系统时钟，剩余天数会变成「真实今天到 2026-03-05 的差」（负一百多天）
        // ⇒ 状态变「已过期」，下面这条断言必红。
        repo.upsert(
            item("id-csv").copy(
                productionEpochDay = today.minusDays(30).toEpochDay(),
                shelfLifeDays = 30,
            ),
        )

        val csv = repo.buildCsvExport()

        assertTrue("CSV 里那一行的「剩余天数,状态」应是 0,临期；实际：$csv", csv.contains(",0,临期,"))
    }
}
