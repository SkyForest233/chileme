package com.agon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `data/` 层的**位置守卫**（09-19 #11c）。
 *
 * 与 `ui/components/app/ComponentAppHomeTest` 同一族、同一判据形态：**一个符号只有一个家**，
 * 外加一条本层专属的「协议不外泄」判定（见第 3 个测试）。
 * 为什么这样写：`data/` 里全是 `internal` / 同包顶层函数，编译器不会因为「这段逻辑住在隔壁文件」而报错，
 * ktlint / detekt / `tools/kt-lexcheck.py` 也都看不见职责边界 —— 只有把住所钉进测试，
 * 下一次有人图省事往 `CloudSync.kt` 里再塞一个 `if` 时才会被拦下。
 *
 * ⚠️ 登记表里的值 = **本笔做完之后的事实**。日后移动符号时，改这一行就是记账本身。
 */
class CloudSyncLocationTest {

    private val dataDir = listOf(
        "app/src/main/java/com/agon/app/data",
        "src/main/java/com/agon/app/data",
    ).map(::File).firstOrNull { it.isDirectory }

    /** 符号 → 唯一住所（同包内移动时 import 一处不改，所以这张表是唯一的守门人）。 */
    private val home = linkedMapOf(
        "NUTSTORE_AUTH_MESSAGE" to "OpFailure.kt",
        "OpFailure" to "OpFailure.kt",
        "toOpFailure" to "OpFailure.kt",
        "isAutoSyncDue" to "AutoSyncPolicy.kt",
        "CLOUD_BACKUP_KEEP" to "CloudSync.kt",
        "CloudBackup" to "CloudSync.kt",
        "NutstoreSync" to "CloudSync.kt",
    )

    private fun read(name: String): String? {
        val dir = dataDir ?: return null
        val f = File(dir, name)
        if (!f.exists()) return null
        return f.readLines()
            .filter { it.trim().isNotEmpty() && !it.trim().startsWith("//") && !it.trim().startsWith("*") }
            .joinToString("\n")
    }

    @Test
    fun `data 层每个登记符号只有一个家`() {
        val dir = dataDir
        assertTrue("`data/` 目录没找到（工作目录变了？）", dir != null)
        val files = dir!!.listFiles { f -> f.isFile && f.name.endsWith(".kt") }
            .orEmpty().map { it.name to read(it.name)!! }.toMap()
        val where = linkedMapOf<String, MutableList<String>>()
        // 必须是「声明」而不是「引用」：行首要以可见性/关键字开头，名字紧跟在关键字后
        val head = Regex(
            "^(?:@|val |var |fun |object |class |interface |internal |private |public |" +
                "const val |data class |sealed interface |sealed class |enum class )"
        )
        val name = Regex("""(?:val|var|fun|object|class|interface)\s+(?:[\w.]+\.)?(\w+)""")
        for ((file, code) in files) {
            for (line in code.split("\n")) {
                if (!head.containsMatchIn(line)) continue
                val m = name.find(line) ?: continue
                where.getOrPut(m.groupValues[1]) { mutableListOf() }.add(file)
            }
        }
        val bad = home.mapNotNull { (sym, expected) ->
            val owners = where[sym].orEmpty().distinct().sorted()
            if (owners == listOf(expected)) null else "$sym 住在 ${if (owners.isEmpty()) "（没找到）" else owners}"
        }
        assertEquals("符号唯一住所被破坏：\n" + bad.joinToString("\n"), emptyList<String>(), bad)
    }

    @Test
    fun `搬家搬干净了 —— 原文件里不留第二份`() {
        val code = read("CloudSync.kt")
        assertTrue("`CloudSync.kt` 没找到", code != null)
        val leftovers = listOf(
            "sealed interface OpFailure",
            "fun Throwable.toOpFailure",
            "internal fun isAutoSyncDue",
            "const val NUTSTORE_AUTH_MESSAGE",
        ).filter { code!!.contains(it) }
        assertEquals("已搬走的声明还在 CloudSync.kt 里（重复定义）", emptyList<String>(), leftovers)
    }

    @Test
    fun `失败分类只认一个 401 文案常量`() {
        // `toOpFailure` 靠字符串相等认出「凭据错」⇒ 抛出点与分类点必须共用同一个常量。
        // 一旦有人抄第二份文案，分类会**静默失效**（编译、ktlint、detekt 都不报）。
        val dir = dataDir
        assertTrue("`data/` 目录没找到", dir != null)
        val decl = File(dir!!, "OpFailure.kt").readText()
        assertTrue("NUTSTORE_AUTH_MESSAGE 的定义只能有一处", Regex("const val NUTSTORE_AUTH_MESSAGE").find(decl) != null)
        val dupes = dir.listFiles { f: File -> f.isFile && f.name.endsWith(".kt") }.orEmpty()
            .filter { Regex("\"账号或应用密码错误\"").containsMatchIn(it.readText().substringAfter("package ")) }
            .map { it.name }
            // 允许出现在 OpFailure.kt（定义处）与测试里；主源码里出现第二份就算抄写
        assertEquals("「401 文案」在 data/ 主源码里被抄了第二遍：" + dupes.joinToString(), emptyList<String>(), dupes - "OpFailure.kt")
    }
}
