package com.agon.app.data

import org.junit.Assert.assertEquals
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

    /**
     * 粗略截出某个函数体：从签名起，到「4 空格缩进的收尾花括号」或「空行 + 恰好 4 空格缩进」为止。
     *
     * 注意不能用 `indexOf("\n\n    ")`：更深缩进的行（如 12 空格的语句块）也以它开头，
     * 会提前截断函数体（CI 第一次跑时 `upsert` 就是这么被截到 `historyDecoded` 之前的）。
     */
    private fun functionBody(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("源码里找不到 $signature", start >= 0)
        val rest = src.substring(start)
        val end = listOf(
            Regex("\\n {4}\\}").find(rest)?.range?.first ?: -1,
            Regex("\\n\\n {4}[^ \\n]").find(rest)?.range?.first ?: -1,
        ).filter { it > 0 }.minOrNull() ?: rest.length
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
    fun `损坏数据有放弃入口且首页接上`() {
        val repo = read("com/agon/app/data/FoodRepository.kt")
        val vm = read("com/agon/app/viewmodel/AppViewModel.kt")
        val home = read("com/agon/app/ui/screens/HomeScreen.kt")
        assumeTrue("找不到相关源码（非 Gradle 工作目录？），跳过", listOf(repo, vm, home).all { it != null })

        assertTrue("FoodRepository 缺少 discardCorrupt()", repo!!.contains("suspend fun discardCorrupt(keys: Set<String>)"))
        assertTrue("discardCorrupt 必须同时解除损坏标记", repo.contains("_corruptedKeys.update { it - keys }"))
        assertTrue("AppViewModel 缺少 discardCorruptData()", vm!!.contains("fun discardCorruptData()"))

        // 首页已于 2026-09-16 合并为单文件双主题（第三批 #3 第 4 对）：一份源码覆盖两套主题，
        // 所以不再逐主题点名（原来这里是 HomeScreen.kt + MiuixHomeScreen.kt 两份各断言一遍）。
        // 万一哪天又冒出 MiuixHomeScreen.kt，ScreenParityTest 的 MergedScreens 会先红。
        val src = home!!
        assertTrue("HomeScreen.kt 的损坏横幅没有接上 onDiscard 入口", src.contains("onDiscard = {"))
        assertTrue("HomeScreen.kt 缺少放弃确认弹窗", src.contains("放弃损坏的数据？"))
    }

    @Test
    fun `凭据加密失败必须可区分且调用方按非空判定`() {
        val store = read("com/agon/app/data/SecureStore.kt")
        val repo = read("com/agon/app/data/FoodRepository.kt")
        assumeTrue("找不到 SecureStore.kt / FoodRepository.kt，跳过", store != null && repo != null)

        // 旧实现失败返回空串，调用方的 `enc != null` 恒为真：既写不进密文（"" 覆盖已有密文），
        // 又会删掉明文键 —— 凭据直接丢失；而且「Keystore 不可用」的降级没有任何 UI 能感知。
        // 这个恒真条件连编译器都只是 warning，所以在这里钉一个测试。
        assertTrue(
            "SecureStore.encrypt 必须返回可空类型（失败返回 null 才能与成功区分）",
            store!!.contains("fun encrypt(plain: String): String?"),
        )
        assertTrue("encrypt 失败必须记日志，不能静默", store.contains("Log.e(TAG, \"凭据加密失败"))
        assertEquals(
            "两处凭据写入都必须走非空判定，否则加密失败会写入空密文",
            2,
            Regex("if \\(enc != null\\)").findAll(repo!!).count(),
        )
        assertTrue(
            "明文降级必须能被 UI 感知（nutstorePlaintextFallbackFlow → 设置页提示）",
            repo.contains("nutstorePlaintextFallbackFlow"),
        )
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
