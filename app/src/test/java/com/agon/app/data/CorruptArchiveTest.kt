package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `filesDir/corrupt/` 留档目录的保留上限测试（2026-09-15）。
 *
 * 背景：`markCorrupt` 只在「本次进程首次发现某 key 损坏」时留档一次，但 `corruptedKeys`
 * 是内存态 —— 解析一直失败时，**每次启动都会再留一份**，长期会把应用私有目录塞满。
 * `pruneCorruptDir` 负责压回「每 key 最多 3 份、整目录最多 12 份」。
 */
class CorruptArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** 造一份留档；[ageMillis] 越大越旧（lastModified 越早）。 */
    private fun archive(dir: File, name: String, ageMillis: Long): File =
        File(dir, name).apply {
            writeText("{}")
            setLastModified(System.currentTimeMillis() - ageMillis)
        }

    @Test
    fun `同一 key 只保留最新的 maxPerKey 份`() {
        val dir = tempFolder.newFolder("corrupt")
        // 同 key 5 份：i=1 最旧（age 最大），i=5 最新
        (1..5).forEach { i ->
            archive(dir, "food_items-2026090${i}_101112.json", ageMillis = (6 - i) * 60_000L)
        }

        pruneCorruptDir(dir, maxPerKey = 3, maxTotal = 100)

        val left = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(
            listOf(
                "food_items-20260903_101112.json",
                "food_items-20260904_101112.json",
                "food_items-20260905_101112.json",
            ),
            left,
        )
    }

    @Test
    fun `整目录超上限时先删最旧的`() {
        val dir = tempFolder.newFolder("corrupt")
        // 15 个不同 key，各 1 份 → 单 key 上限不会生效，只有总量上限管用
        (1..15).forEach { i ->
            archive(
                dir,
                "key${i.toString().padStart(2, '0')}-20260901_1011${i.toString().padStart(2, '0')}.json",
                ageMillis = (20 - i) * 60_000L,
            )
        }

        pruneCorruptDir(dir, maxPerKey = 3, maxTotal = 12)

        val left = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(12, left.size)
        assertTrue("最旧的 key01 应被删除", left.none { it.startsWith("key01-") })
        assertTrue("最新的 key15 必须保留", left.any { it.startsWith("key15-") })
    }

    @Test
    fun `非 json 文件与子目录不受影响`() {
        val dir = tempFolder.newFolder("corrupt")
        archive(dir, "food_items-20260901_101112.json", ageMillis = 10 * 60_000L)
        archive(dir, "archived_items-20260901_101113.json", ageMillis = 60_000L)
        val note = File(dir, "readme.txt").apply { writeText("留着") }
        val sub = File(dir, "sub").apply { mkdirs() }

        pruneCorruptDir(dir, maxPerKey = 1, maxTotal = 1)

        assertEquals("只应留最新的那一份 json", 1, dir.listFiles()!!.count { it.name.endsWith(".json") })
        assertTrue("txt 不该被删", note.exists())
        assertTrue("子目录不该被删", sub.exists())
    }

    @Test
    fun `目录不存在或为空时不崩溃`() {
        pruneCorruptDir(File(tempFolder.root, "does-not-exist"))
        pruneCorruptDir(tempFolder.newFolder("empty"))
        assertTrue(true)
    }
}
