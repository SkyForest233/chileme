package com.agon.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 归档保留上限的行为测试（2026-09-19，M1-3）。
 *
 * 背景：`archiveItems` 与 `changeQuantity` 的自动归档各写了一份 `(新条目 + 旧列表).take(N)`，
 * **超出上限的最老归档被静默丢掉**：不通知、不计数、也不影响"归档里能找回"这个承诺的兑现。
 * 顺带还污染了统计口径 —— `StatsState` 的历史合计就是读这份列表。
 *
 * 修法是"三件事一起"，本类按这三件事各有一组断言：
 * 1. 上限收成一个常量（`ARCHIVE_RETENTION`）+ 一个纯函数（`trimArchiveRetention`），两处调用点共用；
 * 2. 挤掉的条数**必须可见**：`archiveItems` 返回它，且同一事务里累加 `archive_overflow_total`；
 * 3. 上限抬高（200 → 1000），但**保留**上限 —— DataStore 是整份 JSON 全量重写，列表长度直接决定
 *    每次改数量要序列化多少条（见 `ARCHIVE_RETENTION` 的 KDoc）。
 *
 * 溢出用 `retention` 注入小值来测，生产调用方一律不传（与 #5b 注入 `clock` 同一套理由）。
 * 与 `FoodRepositoryGuardTest` 同法：走 `internal` 主构造 + 临时文件上的真实 DataStore，纯 JVM，不要 Robolectric。
 */
class ArchiveRetentionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: FoodRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        )
        repo = FoodRepository(dataStore, File(tmp.root, "corrupt"))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun item(id: String, qty: Int = 1) = FoodItem(
        id = id,
        name = "测试物品",
        category = "OTHER",
        quantity = qty,
        unit = "个",
        productionEpochDay = 20000L,
        shelfLifeDays = 9999,
    )

    private fun entry(id: String) = ArchivedItem(item(id), 20001L, ArchiveReason.DELETED)

    // ---- 1. 纯函数：截断与计数 ----

    @Test
    fun `未超上限时原样保留且 dropped 为 0`() {
        val trim = trimArchiveRetention(listOf(entry("new-1"), entry("new-2")), listOf(entry("old-1")), retention = 3)
        assertEquals(0, trim.dropped)
        assertEquals(listOf("new-1", "new-2", "old-1"), trim.entries.map { it.item.id })
    }

    @Test
    fun `超出上限时保留最新的 N 条并如实报出被挤掉的条数`() {
        // 新条目在前（这是仓库两处调用点共同的形状），故被挤掉的必然是最老的旧归档。
        val trim = trimArchiveRetention(
            newEntries = listOf(entry("new-1"), entry("new-2")),
            existing = (1..4).map { entry("old-$it") },
            retention = 3,
        )
        assertEquals(3, trim.entries.size)
        assertEquals(3, trim.dropped)
        assertEquals(listOf("new-1", "new-2", "old-1"), trim.entries.map { it.item.id })
    }

    @Test
    fun `空列表与 retention 为 0 都不越界`() {
        assertEquals(0, trimArchiveRetention(emptyList(), emptyList(), retention = 0).dropped)
        // retention=0 意味着"一条都不留"：新条目被挤掉，dropped 记的是**全部**条数（不取负数）。
        val zero = trimArchiveRetention(listOf(entry("a")), listOf(entry("b")), retention = 0)
        assertEquals(0, zero.entries.size)
        assertEquals(2, zero.dropped)
    }

    // ---- 2. 行为：返回值 + 累计计数器（同一事务） ----

    @Test
    fun `archiveItems 返回被挤掉的条数并把它计入累计账本`() = runBlocking {
        repeat(4) { repo.upsert(item("id-$it")) }
        assertEquals(
            "上限 3 而本次要归档 4 条 ⇒ 必须报出挤掉了 1 条（旧实现返回 Unit，UI 无从得知）",
            1,
            repo.archiveItems((0..3).map { "id-$it" }.toSet(), ArchiveReason.DELETED, retention = 3),
        )
        assertEquals(1, repo.archiveOverflowFlow.first())

        // 第二次溢出要在同一个数上**累加**，而不是覆盖（否则"历史上丢过多少"永远只剩最后一次）。
        // 此时归档里已有 3 条（新格式 [id-3, id-2, id-1]），再归档 1 条 ⇒ 共 4 条、上限 2 ⇒ 丢 2 条。
        repo.upsert(item("id-9"))
        assertEquals(
            2,
            repo.archiveItems(setOf("id-9"), ArchiveReason.DELETED, retention = 2),
        )
        assertEquals(3, repo.archiveOverflowFlow.first())
        assertEquals(2, repo.archiveFlow.first().size)
    }

    @Test
    fun `没有溢出时不写计数器_避免无谓的整表重发`() = runBlocking {
        repeat(3) { repo.upsert(item("id-$it")) }
        assertEquals(0, repo.archiveItems((0..2).map { "id-$it" }.toSet(), ArchiveReason.DELETED, retention = 1000))
        assertEquals(
            "没挤掉任何一条 ⇒ `archive_overflow_total` 这个 key 压根不该存在（读流回落默认 0）",
            null,
            dataStore.data.first()[repo.archiveOverflowKey],
        )
        assertEquals(0, repo.archiveOverflowFlow.first())
    }

    @Test
    fun `清空归档不清零累计账本`() = runBlocking {
        repeat(4) { repo.upsert(item("id-$it")) }
        repo.archiveItems((0..3).map { "id-$it" }.toSet(), ArchiveReason.DELETED, retention = 1)
        assertEquals(3, repo.archiveOverflowFlow.first())

        repo.clearArchive()

        assertTrue("归档列表已空", repo.archiveFlow.first().isEmpty())
        assertEquals(
            "「丢过多少」是历史事实，清空归档不该让它一笔勾销（改这条语义请连同本断言一起改）",
            3,
            repo.archiveOverflowFlow.first(),
        )
    }

    // ---- 3. 常量与调用点：上限抬高但仍在，且两处共用一个收口 ----

    @Test
    fun `上限已抬高但仍然存在`() {
        // 200 太低（一台常用的设备半年就能写满），但"无上限"在 DataStore 上是不成立的：
        // 每次写都是整份 JSON 全量重写。抬到 1000 并把口径写进 KDoc。
        assertTrue("ARCHIVE_RETENTION 至少要是 1000（本轮的修法）", ARCHIVE_RETENTION >= 1000)
        assertTrue("但仍然必须有上限", ARCHIVE_RETENTION < Int.MAX_VALUE)
    }

    @Test
    fun `两处归档写入点都走同一个收口函数`() {
        // 截断逻辑一旦分叉，"改了上限但自动归档那处没改"就是下一次静默丢数据的来源。
        val archive = read("com/agon/app/data/FoodArchive.kt")
        val consumption = read("com/agon/app/data/FoodConsumption.kt")
        assumeTrue("源文件路径变了，跳过", archive != null && consumption != null)

        for ((name, src) in listOf("FoodArchive.kt" to archive!!, "FoodConsumption.kt" to consumption!!)) {
            val code = codeLines(src)
            assertTrue("$name 没有把归档截断交给 trimArchiveRetention", code.contains("trimArchiveRetention("))
            assertFalse(
                "$name 的代码里还有 `take(<字面量>)`：上限必须只由 trimArchiveRetention / ARCHIVE_RETENTION 决定" +
                    "（KDoc 里提到旧的 take(200) 是允许的，故只扫非注释行）",
                Regex("""take\(\s*\d+\s*\)""").containsMatchIn(code),
            )
        }
    }

    /**
     * 剥掉注释行。本仓 `ImeHandlingTest.codeOnly()` 有更强的词法实现，但那是 UI 侧的、且刻意只做**整行**剥除
     * 以免误伤字符串里的 `//`；这里只需要"别把 KDoc 里提到旧代码的 prose 当成代码"，同一招够用。
     */
    private fun codeLines(src: String): String {
        fun isComment(trimmed: String): Boolean =
            trimmed.isEmpty() || trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")

        return src.lines().filterNot { isComment(it.trimStart()) }.joinToString("\n")
    }

    private fun read(resource: String): String? {
        val relative = "src/main/java/$resource"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
        return file?.readText()
    }
}
