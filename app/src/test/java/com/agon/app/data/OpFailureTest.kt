package com.agon.app.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败分类 [OpFailure] 与 `Throwable.toOpFailure()` 的行为守卫（路线图 #4c，2026-09-18）。
 *
 * 这是**真行为测试**（纯 JVM 可跑，不读源码文本）：分类逻辑是本轮唯一新增的"会做判断"的代码，
 * 而它的输入是异常对象 —— 没法在真机上一条条造出来（DNS 失败、401、超时各要一种网络环境），
 * 所以只有单测能覆盖。
 *
 * 另一件事它也在钉：**文案逐字未改**。#4c 是纯搬运（把 `(Boolean, String)` 回调换成事件），
 * 用户看到的每句话都必须和改造前一模一样；下面几条把改造前各调用点的兜底话原样写死，
 * 谁改了就会红 —— 那时请把它当行为变更处理（单独提交 + 真机复测），而不是顺手改断言。
 */
class OpFailureTest {

    /** 自定义异常，避免为了造一个"消息为 null / 为空白"的样本去 `throw` 泛型异常（detekt 会管）。 */
    private class Fake(message: String?) : Exception(message)

    @Test
    fun `凭据错归到 Auth，文案与抛出点共用同一个常量`() {
        val failure = Fake(NUTSTORE_AUTH_MESSAGE).toOpFailure("上传失败")
        assertTrue("401 那句话必须归到 Auth，否则界面无法据此引导用户重填凭据", failure is OpFailure.Auth)
        assertEquals("账号或应用密码错误", failure.message)
        // 反证：换一句别的话就不该被判成 Auth（分类靠的是那个常量，不是"看着像"）。
        assertFalse(Fake("账号密码好像不对").toOpFailure("上传失败") is OpFailure.Auth)
    }

    @Test
    fun `网络层异常归到 Network（含两个常见子类）`() {
        assertTrue(IOException("stream was reset").toOpFailure("上传失败") is OpFailure.Network)
        assertTrue(
            "UnknownHostException 是 IOException 的子类，断网/DNS 污染时最常见",
            UnknownHostException("dav.jianguoyun.com").toOpFailure("下载失败") is OpFailure.Network,
        )
        assertTrue(
            "SocketTimeoutException 也是 IOException 的子类，弱网时最常见",
            SocketTimeoutException("timeout").toOpFailure("获取备份列表失败") is OpFailure.Network,
        )
    }

    @Test
    fun `其它异常归到 Other 并原样保留消息`() {
        // 坚果云那边用 error("…") 抛的是 IllegalStateException，消息本身就是给用户看的中文
        // （例如「上传失败（HTTP 507）」「该备份已不存在，请刷新列表」「云端备份为空」）。
        val failure = Fake("上传失败（HTTP 507）").toOpFailure("上传失败")
        assertTrue(failure is OpFailure.Other)
        assertEquals("上传失败（HTTP 507）", failure.message)
        assertEquals(
            "该备份已不存在，请刷新列表",
            Fake("该备份已不存在，请刷新列表").toOpFailure("下载失败").message,
        )
    }

    @Test
    fun `消息缺失或空白时用各调用点自己的兜底话`() {
        // 改造前三个调用点各有各的兜底：`it.message ?: "上传失败"` / `"获取备份列表失败"` / `"下载失败"`。
        // 逐个钉住，避免"统一成一个兜底"这种看不出来的文案变更。
        assertEquals("上传失败", Fake(null).toOpFailure("上传失败").message)
        assertEquals("获取备份列表失败", Fake(null).toOpFailure("获取备份列表失败").message)
        assertEquals("下载失败", Fake(null).toOpFailure("下载失败").message)
        // 空白消息等同于没有消息：否则用户会看到一条空提示。
        assertEquals("下载失败", Fake("   ").toOpFailure("下载失败").message)
    }

    @Test
    fun `网络类失败目前仍原样透出英文技术串（**故意钉住"还没修"**）`() {
        // ⚠️ 这条不是"期望如此"，而是"现状如此、且改动需要用户点头"：
        // OkHttp 的网络异常消息是英文技术串（DNS/超时/连接重置），改造前就被 `it.message` 原样甩给用户，
        // #4c 只做类型化、**没有改一个字的文案**。把它换成中文属用户可见的行为变更 ⇒ 要单独提交 + 真机复测，
        // 已登记为待用户决策项（见 devlog/2026-09-18.md 与 ROADMAP #4c）。
        // 真做了那个改动，这条会红 —— 那时请把断言改成新文案，并在 devlog 记一条行为变更。
        val failure = IOException("Unable to resolve host \"dav.jianguoyun.com\"").toOpFailure("上传失败")
        assertTrue("分类要是 Network（这正是可以据此改文案的那一档）", failure is OpFailure.Network)
        assertEquals("Unable to resolve host \"dav.jianguoyun.com\"", failure.message)
    }

    @Test
    fun `三档分类都能装下消息且互不混淆`() {
        val cases = listOf(
            OpFailure.Auth("a") to "a",
            OpFailure.Network("n") to "n",
            OpFailure.Other("o") to "o",
        )
        cases.forEach { (failure, want) -> assertEquals(want, failure.message) }
        // 三档互斥：同一句话被归到哪一档，取决于异常类型与那句常量，而不是消息内容里的关键词。
        val same = "同一句话"
        assertFalse(Fake(same).toOpFailure("x") is OpFailure.Network)
        assertTrue(IOException(same).toOpFailure("x") is OpFailure.Network)
    }
}
