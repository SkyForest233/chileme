package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坚果云备份的纯逻辑测试：文件名时间戳解析、大小格式化、PROPFIND 解析（含不同命名空间前缀）。
 */
class CloudBackupTest {

    // ---- displayTime ----

    @Test
    fun `displayTime 解析新版时间戳`() {
        val b = CloudBackup("chileme_backup_20260731_140530.json", 1234)
        assertEquals("2026年7月31日 14:05:30", b.displayTime)
    }

    @Test
    fun `displayTime 旧版单文件备份无时间信息`() {
        val b = CloudBackup("chileme_backup.json", 1234)
        assertEquals("旧版备份（无时间信息）", b.displayTime)
    }

    @Test
    fun `displayTime 无法解析时回退文件名不崩溃`() {
        val b = CloudBackup("chileme_backup_not-a-date.json", 1234)
        assertEquals("chileme_backup_not-a-date.json", b.displayTime)
    }

    @Test
    fun `isLegacy 识别旧版单文件`() {
        assertTrue(CloudBackup("chileme_backup.json", 1).isLegacy)
        assertTrue(!CloudBackup("chileme_backup_20260731_140530.json", 1).isLegacy)
    }

    // ---- displaySize ----

    @Test
    fun `displaySize 按字节分档`() {
        assertEquals("512 B", CloudBackup("x.json", 512).displaySize)
        assertEquals("2.0 KB", CloudBackup("x.json", 2048).displaySize)
        assertEquals("1.5 MB", CloudBackup("x.json", 1024 * 1024 + 512 * 1024).displaySize)
    }

    // ---- parsePropfind ----

    private val propfindXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:multistatus xmlns:D="DAV:" xmlns:ns0="http://ns.jianguoyun.com">
          <D:response>
            <D:href>/dav/ChiLeMe/</D:href>
            <D:propstat>
              <D:prop><D:getcontentlength>0</D:getcontentlength></D:prop>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/dav/ChiLeMe/chileme_backup_20260731_140530.json</D:href>
            <D:propstat>
              <D:prop><D:getcontentlength>2048</D:getcontentlength></D:prop>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/dav/ChiLeMe/chileme_backup.json</D:href>
            <D:propstat>
              <D:prop><D:getcontentlength>999</D:getcontentlength></D:prop>
            </D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `parsePropfind 提取新版与旧版备份`() {
        val list = NutstoreSync.parsePropfind(propfindXml)
        // 目录自身被过滤；只留 2 个备份文件
        assertEquals(2, list.size)
        val names = list.map { it.fileName }.toSet()
        assertTrue("chileme_backup_20260731_140530.json" in names)
        assertTrue("chileme_backup.json" in names)
        assertEquals(2048, list.first { it.fileName.startsWith("chileme_backup_") }.sizeBytes)
    }

    @Test
    fun `parsePropfind 忽略非备份文件`() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/dav/ChiLeMe/random.txt</D:href>
                <D:propstat><D:prop><D:getcontentlength>10</D:getcontentlength></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        assertTrue(NutstoreSync.parsePropfind(xml).isEmpty())
    }

    @Test
    fun `parsePropfind 空响应不崩溃`() {
        assertTrue(NutstoreSync.parsePropfind("").isEmpty())
    }
}
