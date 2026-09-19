package com.agon.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 编辑页保存路径与封面落盘的守卫（2026-09-19，M1-2）。
 *
 * 钉住两件事，都是"错了不报错、只是数据没了"那一类：
 * 1. **保存要等落盘才返回**。改动前是 `viewModel.upsert(item)` 紧跟 `onBack()`：
 *    写协程只是被排进队列，屏幕已经出栈。今天能写进去纯靠"VM 是 Activity 级"这个巧合，
 *    而路线图 #10c 正要把 VM 拆成每屏一个 ⇒ 那时它就成了"点保存 = 丢数据"。
 *    现在写仍然起在 `viewModelScope`（屏幕没了也写完），但导航必须等 `job.join()` 之后。
 * 2. **封面只允许"整张出现"**。改动前两处 `scope.launch { copyImageToCovers(...) }` 直接往正式文件名里写，
 *    选完图立刻返回会让协程被取消 ⇒ 留下一份截断的 JPEG，而它 `exists()` 为真、`photoPath` 指着它
 *    ⇒ 渲染侧的 `File.exists()` 判定通过、Coil 解码失败 = 一处永久坏图。
 *    现在先写 `.tmp-<uuid>.jpg` 再 `renameTo`（同目录改名在 Linux 上原子），失败/取消只有临时文件、
 *    且不带正式名 ⇒ 下次启动由 `cleanupOrphanCovers()` 收走。
 *
 * 为什么是读源码文本：本仓没有 Robolectric 也没有 Compose UI 测试（`androidTest` 目录为空），
 * 而"协程在写一半时被取消"这种行为在纯 JVM 下无法复现（`Bitmap.compress` 需要 Android 框架）。
 * 与 `CorruptGuardTest` / `ScreenParityTest` 同一套路：结构约束用源码断言钉，真机行为另算一轮。
 */
class EditFoodSaveGuardTest {

    private fun read(vararg candidates: String): String {
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
        assumeTrue("找不到源文件（非 Gradle 工作目录？），跳过", file != null)
        return file!!.readText()
    }

    /**
     * 剥掉整行注释 —— KDoc 的 `*` 续行、`//` 行注释、块注释开头那行。
     * 不剥的话，注释里提到的旧代码（比如"改动前是 take(200)"）会被当成命中。
     * ⚠️ 这里刻意不把被剥的三种前缀写成字面量放在 KDoc 里：块注释会嵌套，
     * 说明文字里出现"斜杠星"就等于在本文件里开了一个永不闭合的注释（09-17 CI 红过一次）。
     */
    private fun codeLines(src: String): String = src.lines()
        .filterNot {
            val t = it.trim()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }
        .joinToString("\n")

    private fun editFoodCode(): String {
        val src = read(
            "src/main/java/com/agon/app/ui/screens/EditFoodScreen.kt",
            "app/src/main/java/com/agon/app/ui/screens/EditFoodScreen.kt",
        )
        return codeLines(src)
    }

    private fun imageStoreCode(): String {
        val src = read(
            "src/main/java/com/agon/app/data/ImageStore.kt",
            "app/src/main/java/com/agon/app/data/ImageStore.kt",
        )
        return codeLines(src)
    }

    @Test
    fun `保存走 await 版本且导航排在 join 之后`() {
        val code = editFoodCode()
        assertTrue("编辑页又用回 fire-and-forget 的 upsert 了", code.contains("viewModel.upsertAndAwait("))
        assertFalse("编辑页不该再调 `viewModel.upsert(`（那条不返回 Job，等不到落盘）", code.contains("viewModel.upsert("))
        val join = code.indexOf("job.join()")
        val back = code.indexOf("onBack()")
        assertTrue("保存路径里必须 join 等待落盘", join >= 0)
        assertTrue(
            "onBack() 必须排在 job.join() 之后 —— 顺序反了就又是「点了保存但没保存上」",
            back > join,
        )
    }

    @Test
    fun `保存期间按钮不再受理第二次点击`() {
        val code = editFoodCode()
        assertTrue("缺 `else if (!saving)` 这道闩：连点两下在新增模式里会造出两条记录", code.contains("} else if (!saving) {"))
        assertTrue("保存按钮没接 `enabled`", code.contains("enabled = !saving"))
    }

    @Test
    fun `封面写入先落临时名再 rename 成正式名`() {
        val code = imageStoreCode()
        assertTrue("封面临时名前缀常量不见了", code.contains("COVER_TMP_PREFIX"))
        assertTrue("改名收口函数不见了（正式文件必须只由 renameTo 产生）", code.contains("tmp.renameTo(out)"))
        for (fragment in listOf("tmp.outputStream()", "FileOutputStream(tmp)")) {
            assertTrue("封面写入没落到临时文件上：缺 $fragment", code.contains(fragment))
        }
        for (forbidden in listOf("out.outputStream()", "FileOutputStream(out)")) {
            assertFalse(
                "正式名文件不该被当成写入目标 —— 半成品会被 exists() 认成好文件：$forbidden",
                code.contains(forbidden),
            )
        }
        // JPEG 编码失败（compress 返回 false）也必须算失败：它是"文件写完了但内容不合法"，exists() 看不出来。
        assertTrue("compress 的返回值被忽略了", code.contains("if (!compressed)"))
    }

    @Test
    fun `任何退出路径都清掉半成品`() {
        val code = imageStoreCode()
        val inFunction = code.substringAfter("suspend fun copyImageToCovers(")
        assertTrue("copyImageToCovers 缺 finally 清理（早退/取消都会留下半成品）", inFunction.contains("finally {"))
        assertTrue("finally 里没删 tmp", inFunction.contains("tmp.exists()") && inFunction.contains("tmp.delete()"))
    }

    @Test
    fun `孤儿清理不放过临时名`() {
        // tmp 之所以安全，全靠"cleanupOrphanCovers 会删掉 covers/ 下任何未被引用的文件"。
        // 哪天有人给它加个"只删 .jpg 结尾"或"跳过点开头"的过滤，半成品就会永久留在用户设备上。
        val code = imageStoreCode()
        val inCleanup = code.substringAfter("suspend fun cleanupOrphanCovers(")
        assertFalse("孤儿清理加了按扩展名过滤", inCleanup.contains("endsWith("))
        assertFalse("孤儿清理跳过了点开头的文件", inCleanup.contains("startsWith("))
        assertTrue("孤儿清理不再是「不在引用集合里就删」", inCleanup.contains("!in referencedPaths"))
    }
}
