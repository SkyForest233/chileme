package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalSnapshotTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `cleanupOldSnapshots 严格保留最新的 maxKeep 份并删除多余文件`() {
        val dir = tempFolder.newFolder("snapshots")
        // 创建 5 个模拟快照文件
        val f1 = File(dir, "snapshot_20260820_100000.json").apply { writeText("{}"); setLastModified(1000L) }
        val f2 = File(dir, "snapshot_20260821_100000.json").apply { writeText("{}"); setLastModified(2000L) }
        val f3 = File(dir, "snapshot_20260822_100000.json").apply { writeText("{}"); setLastModified(3000L) }
        val f4 = File(dir, "snapshot_20260822_120000.json").apply { writeText("{}"); setLastModified(4000L) }
        val f5 = File(dir, "snapshot_20260822_140000.json").apply { writeText("{}"); setLastModified(5000L) }

        LocalSnapshotStore.cleanupOldSnapshots(dir, maxKeep = 3)

        val remaining = dir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        assertEquals(3, remaining.size)
        assertTrue(f5.name in remaining)
        assertTrue(f4.name in remaining)
        assertTrue(f3.name in remaining)
        assertTrue(f1.name !in remaining)
        assertTrue(f2.name !in remaining)
    }

    @Test
    fun `countItemsInSnapshot 解析 items 条数而不是全文数 id`() {
        // 旧实现用 Regex("\"id\"\\s*:") 全文匹配，会把归档/消耗/历史里的 id 也算进去。
        val json = """
            {
              "version": 2,
              "items": [
                {"id": "a", "name": "牛奶"},
                {"id": "b", "name": "饼干"}
              ],
              "archived": [ {"item": {"id": "c", "name": "过期酸奶"}} ],
              "consumption": [ {"id": "d"}, {"id": "e"} ],
              "history": [ {"id": "f"} ]
            }
        """.trimIndent()
        assertEquals(2, countItemsInSnapshot(json))
    }

    @Test
    fun `countItemsInSnapshot 对空档与畸形内容返回 0`() {
        assertEquals(0, countItemsInSnapshot("""{"items": []}"""))
        assertEquals(0, countItemsInSnapshot("{}"))
        assertEquals(0, countItemsInSnapshot("这不是 JSON"))
        assertEquals(0, countItemsInSnapshot(""))
    }

    @Test
    fun `countItemsInSnapshot 容忍未来新增字段`() {
        val json = """{"items":[{"id":"a"}],"someFutureField":{"x":1}}"""
        assertEquals(1, countItemsInSnapshot(json))
    }

    @Test
    fun `LocalSnapshot displaySize 字节与容量格式化正确`() {
        val b1 = LocalSnapshot("s1.json", 500L, 0L, 2)
        assertEquals("500 B", b1.displaySize)

        val b2 = LocalSnapshot("s2.json", 2048L, 0L, 5)
        assertEquals("2.0 KB", b2.displaySize)

        val b3 = LocalSnapshot("s3.json", 1024L * 1024L * 2L, 0L, 10)
        assertEquals("2.0 MB", b3.displaySize)
    }
}
