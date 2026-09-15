package com.agon.app.data

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 「写守卫按 key 粒度降级 + 损坏数据有放弃入口」的静态守卫。
 *
 * 背景（2026-09-15 修复）：原守卫是 `isCorrupt(itemsDecoded, historyDecoded)` 这种
 * 「本次写入涉及的所有 key 一起判」的写法 —— 于是「录入历史」这种只供输入联想的辅助数据
 * 一旦损坏，连「新增/编辑食品」这个核心功能都会静默失效（点了没反应，也没有报错）。
 * 这个测试把修法固化下来，防止以后又写成一起判。
 *
 * 同时守住另外两点：
 * 1. 三条恢复路径（文件导入 / 坚果云整版本恢复 / 本地快照还原）都必须先留「恢复前快照」；
 * 2. 首页横幅必须提供「放弃损坏数据」入口（否则用户只能靠重装或导入备份绕出去）。
 */
class CorruptGuardTest {

    private fun read(relativePath: String): String? =
        listOf("src/main/java/", "app/src/main/java/")
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }
            ?.readText()

    /** 粗略截出某个函数体：从签名起，到第一个 4 空格缩进的收尾花括号。 */
    private fun functionBody(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("源码里找不到 $signature", start >= 0)
        val rest = src.substring(start)
        val end = listOf(rest.indexOf("\n    }"), rest.indexOf("\n\n    "))
            .filter { it > 0 }
            .minOrNull() ?: rest.length
        return rest.substring(0, end)
    }

    @Test
    fun `upsert 不再因录入历史损坏而整体拒绝写入`() {
        val repo = read("com/agon/app/data/FoodRepository.kt")
        assumeTrue("找不到 FoodRepository.kt（非 Gradle 工作目录？），跳过", repo != null)
        val src = repo!!

        assertTrue(
            "upsert 仍在用 isCorrupt(itemsDecoded, historyDecoded) 一起判 —— history 损坏会锁死「新增/编辑食品」",
            !src.contains("isCorrupt(itemsDecoded, historyDecoded)"),
        )

        val body = functionBody(src, "suspend fun upsert(")
        // 主数据（库存）仍然必须拦截
        assertTrue("upsert 必须保留库存损坏时的拒绝写入", body.contains("if (isCorrupt(itemsDecoded)) return@edit"))
        // 库存写完才判定历史，且历史损坏只跳过历史
        val itemsWrite = body.indexOf("prefs[itemsKey] = json.encodeToString(updated)")
        val historyCheck = body.indexOf("decodeHistory(prefs[historyKey])")
        assertTrue("upsert 应先把库存写入、再判定 history", itemsWrite in 1 until historyCheck)
        assertTrue(
            "upsert 对损坏的 history 应记日志并跳过，而不是整体 return",
            body.contains("history_entries 损坏") && body.contains("return@edit"),
        )
    }

    @Test
    fun `changeQuantity 的附带 key 按需判定`() {
        val repo = read("com/agon/app/data/FoodRepository.kt")
        assumeTrue("找不到 FoodRepository.kt，跳过", repo != null)
        val body = functionBody(repo!!, "suspend fun changeQuantity(")

        assertTrue(
            "changeQuantity 仍在把 items/consumption/archive 三个 key 一起判 —— 消耗记录损坏会让「改数量」整体失效",
            !body.contains("isCorrupt(itemsDecoded, consumptionDecoded, archiveDecoded)"),
        )
        assertTrue("应分别判定消耗记录与归档", body.contains("val consumptionOk = !isCorrupt(consumptionDecoded)"))
        assertTrue("应分别判定归档", body.contains("val archiveOk = !isCorrupt(archiveDecoded)"))
        assertTrue("主数据（库存）损坏时仍必须拒绝", body.contains("if (isCorrupt(itemsDecoded)) return@edit"))
        assertTrue("自动归档必须要求 archive 可写", body.contains("if (newQty == 0 && delta < 0 && archiveOk)"))
    }

    @Test
    fun `损坏数据有放弃入口且两个首页都接上`() {
        val repo = read("com/agon/app/data/FoodRepository.kt")
        val vm = read("com/agon/app/viewmodel/AppViewModel.kt")
        val md3 = read("com/agon/app/ui/screens/HomeScreen.kt")
        val miuix = read("com/agon/app/ui/screens/MiuixHomeScreen.kt")
        assumeTrue("找不到相关源码（非 Gradle 工作目录？），跳过", listOf(repo, vm, md3, miuix).all { it != null })

        assertTrue("FoodRepository 缺少 discardCorrupt()", repo!!.contains("suspend fun discardCorrupt(keys: Set<String>)"))
        assertTrue("discardCorrupt 必须同时解除损坏标记", repo.contains("_corruptedKeys.update { it - keys }"))
        assertTrue("AppViewModel 缺少 discardCorruptData()", vm!!.contains("fun discardCorruptData()"))

        listOf("HomeScreen.kt" to md3!!, "MiuixHomeScreen.kt" to miuix!!).forEach { (name, src) ->
            assertTrue("$name 的损坏横幅没有接上 onDiscard 入口", src.contains("onDiscard = {"))
            assertTrue("$name 缺少放弃确认弹窗", src.contains("放弃损坏的数据？"))
        }
    }

    @Test
    fun `三条恢复路径都先留恢复前快照`() {
        val vm = read("com/agon/app/viewmodel/AppViewModel.kt")
        assumeTrue("找不到 AppViewModel.kt，跳过", vm != null)
        val src = vm!!

        assertTrue("缺少公共前置快照方法", src.contains("private suspend fun snapshotBeforeRestore()"))

        val importBody = functionBody(src, "fun importBackupWithSnapshot(")
        assertTrue("文件导入未走公共前置快照", importBody.contains("snapshotBeforeRestore()"))

        val restoreBody = functionBody(src, "fun restoreLocalSnapshot(")
        assertTrue("本地快照还原未走公共前置快照", restoreBody.contains("snapshotBeforeRestore()"))
        // 顺序：先读出目标快照，再写「还原前状态」——反了会在快照满 3 份时把目标挤掉
        val readIdx = restoreBody.indexOf("readSnapshot")
        val snapIdx = restoreBody.indexOf("snapshotBeforeRestore()")
        assertTrue("restoreLocalSnapshot 必须先读取目标快照再写前置快照（否则目标可能被淘汰）", readIdx in 1 until snapIdx)

        val cloudBody = functionBody(src, "fun syncDownload(")
        assertTrue("坚果云恢复未走公共前置快照", cloudBody.contains("snapshotBeforeRestore()"))
        assertTrue("坚果云恢复必须先做 items 键校验再覆盖", cloudBody.contains("previewBackup(raw) == null"))
    }
}
