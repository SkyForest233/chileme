package com.agon.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份兼容性与解码三态测试。
 * 关键：`Json` 的 `ignoreUnknownKeys=true` + 字段默认值保证旧版（v1）备份可解析；
 * 解码失败必须被识别为 Corrupt（而非当作「无数据」），写路径才能拒绝覆盖。
 */
class BackupCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- BackupData v1 → v2 兼容 ----

    /** v1 备份：无 version/categories/locations（这些字段是 v2 新增） */
    private val v1BackupJson = """
        {
          "items": [
            {
              "id": "i1", "name": "牛奶", "category": "DAIRY",
              "quantity": 2, "unit": "瓶",
              "productionEpochDay": 20000, "shelfLifeDays": 15
            }
          ],
          "archived": [],
          "consumption": [],
          "history": [],
          "categoryThresholds": { "DAIRY": 5 }
        }
    """.trimIndent()

    @Test
    fun `v1 备份可解析且缺省字段回落到默认`() {
        val backup = json.decodeFromString<BackupData>(v1BackupJson)
        assertEquals(1, backup.items.size)
        assertEquals("牛奶", backup.items[0].name)
        assertEquals(5, backup.categoryThresholds["DAIRY"])
        assertTrue(backup.categories.isEmpty())   // v2 新增，缺省为空
        assertTrue(backup.locations.isEmpty())
    }

    @Test
    fun `v1 备份解码后 version 回落默认 2`() {
        val backup = json.decodeFromString<BackupData>(v1BackupJson)
        assertEquals(2, backup.version)
    }

    @Test
    fun `未知字段被忽略不崩溃`() {
        val withExtra = v1BackupJson.replace(
            "\"categoryThresholds\": { \"DAIRY\": 5 }",
            "\"categoryThresholds\": { \"DAIRY\": 5 }, \"some_future_field\": [1,2,3]",
        )
        val backup = json.decodeFromString<BackupData>(withExtra)
        assertEquals(1, backup.items.size)
    }

    @Test
    fun `畸形 JSON 解码抛异常而非吞掉`() {
        val bad = runCatching { json.decodeFromString<BackupData>("{ not valid json") }
        assertTrue(bad.isFailure)
    }

    // ---- Decoded 三态 ----

    @Test
    fun `orElse 对 Ok 返回真实值`() {
        val d: Decoded<Int> = Decoded.Ok(5)
        assertEquals(5, d.orElse(0))
    }

    @Test
    fun `orElse 对 Empty 回落默认`() {
        val d: Decoded<Int> = Decoded.Empty
        assertEquals(0, d.orElse(0))
    }

    @Test
    fun `orElse 对 Corrupt 回落默认`() {
        val d: Decoded<Int> = Decoded.Corrupt("raw", RuntimeException("boom"))
        assertEquals(0, d.orElse(0))
    }

    @Test
    fun `isCorrupt 仅在存在 Corrupt 时为真`() {
        val ok: Decoded<Int> = Decoded.Ok(1)
        val empty: Decoded<Int> = Decoded.Empty
        val corrupt: Decoded<Int> = Decoded.Corrupt("raw", RuntimeException("boom"))
        assertFalse(corruptList(ok, empty))              // 全正常 → 不守卫
        assertTrue(corruptList(ok, empty, corrupt))      // 任一损坏 → 守卫
    }

    /** 镜像 FoodRepository.isCorrupt 的判定（该函数是 private，这里验证语义）。 */
    private fun corruptList(vararg decoded: Decoded<*>): Boolean =
        decoded.any { it is Decoded.Corrupt }
}
