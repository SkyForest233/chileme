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
    private fun functionBody(src: String, signature: String, indent: Int = 4): String {
        val start = src.indexOf(signature)
        assertTrue("源码里找不到 $signature", start >= 0)
        val rest = src.substring(start)
        // #5c 起领域函数是**顶层扩展函数**（缩进 0），不再都是类成员（缩进 4）⇒ 截断规则参数化。
        // ⚠️ 顶层函数若仍用 4 空格那套规则，截断点会落在函数体里**第一个 4 空格的内部 `}`**
        // （例如 `dataStore.edit { … }` 的收尾）而不是函数末尾 ⇒ 后半段的正向断言假红、
        // `!contains(...)` 这类反向断言假绿。实测 `upsert` 只差 1 行（30 vs 31：它的内部块收尾
        // 刚好靠近函数末尾），但那是运气，不能靠 —— 内部块靠前一点的函数会被截掉大半。
        val pad = " ".repeat(indent)
        val end = listOf(
            Regex("\\n$pad\\}").find(rest)?.range?.first ?: -1,
            Regex("\\n\\n$pad[^ \\n]").find(rest)?.range?.first ?: -1,
        ).filter { it > 0 }.minOrNull() ?: rest.length
        return rest.substring(0, end)
    }

    @Test
    fun `upsert 不再因录入历史损坏而整体拒绝写入`() {
        // #5c-2：upsert 搬到了库存领域文件（顶层扩展函数）⇒ 这条守卫跟着搬，别留在旧文件里假绿
        val repo = read("com/agon/app/data/FoodItems.kt")
        assumeTrue("找不到 FoodItems.kt（非 Gradle 工作目录？），跳过", repo != null)
        val src = repo!!

        assertTrue(
            "upsert 仍在用 isCorrupt(itemsDecoded, historyDecoded) 一起判 —— history 损坏会锁死「新增/编辑食品」",
            !src.contains("isCorrupt(itemsDecoded, historyDecoded)"),
        )

        val body = functionBody(src, "suspend fun FoodRepository.upsert(", indent = 0)
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
        // #5c-4：changeQuantity 搬到了消耗领域文件（顶层扩展函数）⇒ 这条守卫跟着搬
        val repo = read("com/agon/app/data/FoodConsumption.kt")
        assumeTrue("找不到 FoodConsumption.kt，跳过", repo != null)
        val body = functionBody(repo!!, "suspend fun FoodRepository.changeQuantity(", indent = 0)

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
        // #10b-4：`discardCorruptData` 搬到了食物 CRUD 与批量领域文件（同包 `internal` 扩展函数）⇒
        // 这条守卫跟着搬，别留在 `AppViewModel.kt` 里假红（#5c-2 起就是这个规矩）；断言的字面量也要带上
        // 接收者 —— 顶层扩展函数的声明行是 `internal fun AppViewModel.discardCorruptData()`。
        val food = read("com/agon/app/viewmodel/AppViewModelFood.kt")
        val home = read("com/agon/app/ui/screens/HomeScreen.kt")
        assumeTrue("找不到相关源码（非 Gradle 工作目录？），跳过", listOf(repo, food, home).all { it != null })

        assertTrue("FoodRepository 缺少 discardCorrupt()", repo!!.contains("suspend fun discardCorrupt(keys: Set<String>)"))
        assertTrue("discardCorrupt 必须同时解除损坏标记", repo.contains("_corruptedKeys.update { it - keys }"))
        assertTrue(
            "AppViewModel 缺少 discardCorruptData()（#10b-4 起在 AppViewModelFood.kt）",
            food!!.contains("fun AppViewModel.discardCorruptData()"),
        )

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
        // #5c-7：两处凭据写入（migratePlaintextPassword / setNutstoreCredentials）搬到了凭据领域文件，
        // 所以 `if (enc != null)` 的**计数**断言要读它；而 nutstorePlaintextFallbackFlow 是对外读取流，
        // 仍留在 FoodRepository.kt ⇒ 下面两条断言分别读各自的文件，别图省事都指一个。
        val creds = read("com/agon/app/data/FoodCredentials.kt")
        assumeTrue(
            "找不到 SecureStore.kt / FoodRepository.kt / FoodCredentials.kt，跳过",
            store != null && repo != null && creds != null,
        )

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
            Regex("if \\(enc != null\\)").findAll(creds!!).count(),
        )
        assertTrue(
            "明文降级必须能被 UI 感知（nutstorePlaintextFallbackFlow → 设置页提示）",
            repo!!.contains("nutstorePlaintextFallbackFlow"),
        )
    }

    @Test
    fun `建钥竞态不降级且读侧不建钥`() {
        // M1-4（2026-09-19）：首次使用时启动期的 migratePlaintextPassword 与设置页保存会同时撞进
        // getOrCreateKey()，两边都判"没有密钥"、都去 generateKey() ⇒ 后到的抛 KeyAlreadyExistsException
        // ⇒ 被 encrypt 的 catch 吞成 null ⇒ 调用方以为"Keystore 不可用"，把明文密码落盘并提示降级。
        // 密钥明明建得好好的，用户却被告知加密坏了。这里钉住两层修法 + 读侧不再有写副作用。
        val store = read("com/agon/app/data/SecureStore.kt")
        assumeTrue("找不到 SecureStore.kt，跳过", store != null)

        val create = functionBody(store!!, "private fun getOrCreateKey()", indent = 4)
        assertTrue("建钥前必须先读一次（读→建没被串行化就会撞车）", create.contains("readKey() ?:"))
        assertTrue("「读→建」必须整体加锁", create.contains("synchronized("))
        val afterGenerate = create.substringAfter("generator.generateKey()")
        assertTrue(
            "generateKey 之后的兜底必须判「别名已存在就复用」，否则异常会冒到 encrypt 的 catch 里被当成「加密失败」",
            afterGenerate.contains("readKey()"),
        )
        assertTrue(
            "读不到现存密钥时必须把原异常抛出去（吞掉就等于把真故障说成没事）",
            afterGenerate.contains("if (existing == null) throw e"),
        )

        val decryptBody = functionBody(store, "fun decrypt(stored: String): String?", indent = 4)
        assertTrue("解密必须只读密钥（decrypt 是每次重发都会走的路径，带写副作用会把读操作变成抢建）",
            decryptBody.contains("readKey()"))
        assertTrue(
            "解密路径又去建钥了：换机恢复后会白占 KEY_ALIAS、旧密文照样解不开，还会与并发加密抢建",
            !decryptBody.contains("getOrCreateKey()"),
        )
    }

    @Test
    fun `三条恢复路径都先留恢复前快照`() {
        // #10b-1 起「文件导入」「本地快照还原」这两条路径，与公共前置快照方法本身，搬到了同包的
        // AppViewModelBackup.kt；#10b-2 起「坚果云整版本恢复」也搬出去了，落在 AppViewModelCloud.kt
        // ⇒ 三条路径现在分布在**两个领域文件**里，AppViewModel.kt 已不再参与这个测试。
        // 两处都是同包 internal 扩展函数（缩进 0）⇒ `indent = 0`。
        val backup = read("com/agon/app/viewmodel/AppViewModelBackup.kt")
        val cloud = read("com/agon/app/viewmodel/AppViewModelCloud.kt")
        assumeTrue("找不到 AppViewModelBackup.kt / AppViewModelCloud.kt，跳过", backup != null && cloud != null)
        val src = backup!!

        assertTrue(
            "缺少公共前置快照方法",
            src.contains("internal suspend fun AppViewModel.snapshotBeforeRestore()"),
        )

        val importBody = functionBody(src, "fun AppViewModel.importBackupWithSnapshot(", indent = 0)
        assertTrue("文件导入未走公共前置快照", importBody.contains("snapshotBeforeRestore()"))

        val restoreBody = functionBody(src, "fun AppViewModel.restoreLocalSnapshot(", indent = 0)
        assertTrue("本地快照还原未走公共前置快照", restoreBody.contains("snapshotBeforeRestore()"))
        // 顺序：先读出目标快照，再写「还原前状态」——反了会在快照满 3 份时把目标挤掉
        val readIdx = restoreBody.indexOf("readSnapshot")
        val snapIdx = restoreBody.indexOf("snapshotBeforeRestore()")
        assertTrue("restoreLocalSnapshot 必须先读取目标快照再写前置快照（否则目标可能被淘汰）", readIdx in 1 until snapIdx)

        val cloudBody = functionBody(cloud!!, "fun AppViewModel.syncDownload(", indent = 0)
        assertTrue("坚果云恢复未走公共前置快照", cloudBody.contains("snapshotBeforeRestore()"))
        assertTrue("坚果云恢复必须先做 items 键校验再覆盖", cloudBody.contains("previewBackup(raw) == null"))
    }
}
