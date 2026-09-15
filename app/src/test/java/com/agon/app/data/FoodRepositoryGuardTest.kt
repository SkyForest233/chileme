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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * 仓储层写入守卫的**集成测试**（2026-09-15）。
 *
 * 此前只有 `CorruptGuardTest` 那种「读源码断言」——只能证明守卫写在源码里，
 * 证明不了「守卫真的拦住写入」。这里用**真实 DataStore**（临时文件）+ 注入的留档目录，
 * 把写路径整体跑一遍：损坏态下写入必须被丢弃、原始串一个字都不能变。
 *
 * 两条工程决定：
 * 1. 走 `FoodRepository(dataStore, corruptDir)` 这个 internal 主构造，**不需要 Robolectric**——
 *    真要上 Robolectric 得对付 compileSdk 37（4.16.1 只到 SDK 36）与 AGP 9 的兼容性，
 *    而这里需要的只是一个临时文件上的 DataStore 和一个临时目录；
 * 2. `BuildConfig`/`android.util.Log` 由 `testOptions.unitTests.isReturnDefaultValues = true`
 *    兜底（守卫路径里会打日志，未开这个开关会抛「not mocked」）。
 */
class FoodRepositoryGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var corruptDir: File
    private lateinit var repo: FoodRepository

    private val itemsKey = stringPreferencesKey("food_items")
    private val historyKey = stringPreferencesKey("history_entries")
    private val consumptionKey = stringPreferencesKey("consumption_records")

    /** 任意非法 JSON：解码必然失败，同时「没被覆盖」这个断言一眼可查。 */
    private val corruptRaw = "{\"broken\":[1,2,3"

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        )
        corruptDir = File(tmp.root, "corrupt")
        repo = FoodRepository(dataStore, corruptDir)
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
        productionEpochDay = LocalDate.now().toEpochDay(),
        shelfLifeDays = 30,
    )

    private suspend fun seedRaw(key: Preferences.Key<String>, raw: String) {
        dataStore.edit { it[key] = raw }
    }

    private suspend fun rawOf(key: Preferences.Key<String>): String? = dataStore.data.first()[key]

    // ---- 用例 ----

    @Test
    fun `items 损坏时 upsert 拒绝写入，原样保留并留档`() = runBlocking {
        seedRaw(itemsKey, corruptRaw)

        repo.upsert(item("id-1"))

        assertEquals("损坏的原始串绝不能被覆盖", corruptRaw, rawOf(itemsKey))
        assertTrue(repo.corruptedKeys.value.contains("food_items"))
        assertTrue(
            "首次发现损坏应把原文留档到注入的目录（markCorrupt）",
            corruptDir.listFiles()!!.any { it.name.startsWith("food_items-") },
        )
    }

    @Test
    fun `正常数据时 upsert 正常写入`() = runBlocking {
        repo.upsert(item("id-1"))

        val items = repo.itemsFlow.first()
        assertEquals(1, items.size)
        assertEquals("牛奶", items[0].name)
        assertTrue(repo.corruptedKeys.value.isEmpty())
    }

    @Test
    fun `history 损坏不影响库存写入（守卫按 key 粒度）`() = runBlocking {
        repo.upsert(item("id-1"))
        seedRaw(historyKey, corruptRaw)

        repo.upsert(item("id-2"))

        assertEquals("history 坏掉不该挡住库存写入", 2, repo.itemsFlow.first().size)
        assertEquals("history 的原始串也不能被洗掉", corruptRaw, rawOf(historyKey))
        assertTrue(repo.corruptedKeys.value.contains("history_entries"))
        assertFalse(repo.corruptedKeys.value.contains("food_items"))
    }

    @Test
    fun `items 损坏时 changeQuantity 拒绝写入`() = runBlocking {
        seedRaw(itemsKey, corruptRaw)

        val result = repo.changeQuantity("id-1", -1)

        assertEquals(QuantityChangeResult(autoArchived = false, consumptionId = null), result)
        assertEquals(corruptRaw, rawOf(itemsKey))
    }

    @Test
    fun `consumption 损坏时仍更新库存，只是不写消耗记录`() = runBlocking {
        repo.upsert(item("id-1", quantity = 2))
        seedRaw(consumptionKey, corruptRaw)

        val result = repo.changeQuantity("id-1", -1)

        assertEquals("库存主数据照常更新", 1, repo.itemsFlow.first().single().quantity)
        assertNull("消耗记录未写入，也就没有 consumptionId", result.consumptionId)
        assertEquals("消耗记录的原始串不能被覆盖", corruptRaw, rawOf(consumptionKey))
    }

    @Test
    fun `放弃损坏数据后写入恢复`() = runBlocking {
        seedRaw(itemsKey, corruptRaw)
        repo.upsert(item("id-1"))
        assertEquals(corruptRaw, rawOf(itemsKey))

        repo.discardCorrupt(setOf("food_items"))

        assertFalse(repo.corruptedKeys.value.contains("food_items"))
        repo.upsert(item("id-2"))
        assertEquals(1, repo.itemsFlow.first().size)

        // 留档不受影响：原文仍在 corruptDir 里，供人工恢复
        assertTrue(corruptDir.listFiles()!!.any { it.name.startsWith("food_items-") })
    }

    @Test
    fun `月度聚合记录不会被 deleteConsumption 删除`() = runBlocking {
        val aggregated = ConsumptionRecord(
            name = "牛奶",
            amount = 42,
            unit = "瓶",
            epochDay = LocalDate.now().minusMonths(4).withDayOfMonth(1).toEpochDay(),
            id = "c-aggregated",
            aggregated = true,
        )
        seedRaw(consumptionKey, json.encodeToString(listOf(aggregated)))

        repo.deleteConsumption(aggregated)

        assertEquals(
            "聚合记录一条 = 整月合计，删除请求必须被仓库层拦下",
            1,
            repo.consumptionFlow.first().size,
        )
    }

    @Test
    fun `逐笔消耗记录可以正常删除`() = runBlocking {
        val recent = ConsumptionRecord(
            name = "面包",
            amount = 1,
            unit = "个",
            epochDay = LocalDate.now().toEpochDay(),
            id = "c-recent",
            aggregated = false,
        )
        seedRaw(consumptionKey, json.encodeToString(listOf(recent)))

        repo.deleteConsumption(recent)

        assertTrue(repo.consumptionFlow.first().isEmpty())
    }
}
