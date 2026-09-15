package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [DecodeCache] 的行为测试（2026-09-15）。
 *
 * 它存在的理由是「同一份 JSON 在启动期被解 3 遍」（`itemsFlow` 被 items / suggestionSource /
 * ready 各收一份）。这里用计数版 decode 证明：同一个 raw 只会触发一次解码。
 */
class DecodeCacheTest {

    @Test
    fun `同一个 raw 只解一次`() {
        val cache = DecodeCache()
        var decodes = 0
        val decode: (String?) -> String = { raw ->
            decodes++
            "decoded:$raw"
        }

        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))
        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))
        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))

        assertEquals(1, decodes)
        assertEquals(1, cache.misses)
        assertEquals(2, cache.hits)
    }

    @Test
    fun `raw 变化后重新解码`() {
        val cache = DecodeCache()
        var decodes = 0
        val decode: (String?) -> String = { raw ->
            decodes++
            "decoded:$raw"
        }

        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))
        assertEquals("decoded:b", cache.resolve("food_items", "b", decode))
        assertEquals("decoded:b", cache.resolve("food_items", "b", decode))

        assertEquals(2, decodes)
        assertEquals(2, cache.misses)
        assertEquals(1, cache.hits)
    }

    @Test
    fun `不同 key 各自独立缓存`() {
        val cache = DecodeCache()
        var decodes = 0
        val decode: (String?) -> String = { raw ->
            decodes++
            "decoded:$raw"
        }

        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))
        assertEquals("decoded:a", cache.resolve("archived_items", "a", decode))
        assertEquals("decoded:a", cache.resolve("food_items", "a", decode))

        assertEquals("同一份 raw 在两个 key 下应各解一次", 2, decodes)
    }

    @Test
    fun `null raw（key 不存在）也会被缓存`() {
        val cache = DecodeCache()
        var decodes = 0
        val decode: (String?) -> Int = {
            decodes++
            0
        }

        assertEquals(0, cache.resolve("food_items", null, decode))
        assertEquals(0, cache.resolve("food_items", null, decode))
        assertEquals("key 不存在时不该反复走解码路径", 1, decodes)
    }

    @Test
    fun `同一个 raw 返回同一个对象（解码副作用只跑一次）`() {
        // decodeStrict 里挂着 markCorrupt（写留档文件 + 置损坏位），缓存必须让它只跑一次；
        // 顺带保证 Decoded.Corrupt 这类带异常对象的结果被复用而不是每次新建。
        val cache = DecodeCache()
        var sideEffects = 0
        val decode: (String?) -> List<String> = {
            sideEffects++
            emptyList()
        }

        val first = cache.resolve("food_items", "broken", decode)
        val second = cache.resolve("food_items", "broken", decode)

        assertSame(first, second)
        assertEquals(1, sideEffects)
    }
}
