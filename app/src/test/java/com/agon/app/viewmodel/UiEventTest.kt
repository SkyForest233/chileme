package com.agon.app.viewmodel

import com.agon.app.data.ArchiveReason
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.FoodItem
import com.agon.app.data.OpFailure
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 一次性事件的**落点分流**守卫（路线图 #4a，2026-09-18）。
 *
 * 为什么值得钉：「按 [UiSurface] 分三条队列」是这套设计的全部前提 —— `Channel` 是**单接收方**语义，
 * 分流错了既不编译失败也不崩，只会让提示条**弹到错误的位置**（或弹在一个没人渲染的宿主上，
 * 把整条收集协程堵住、后续撤销条全都不出现）。这类错误只有真机才看得出来，
 * 所以先在纯 JVM 里把「哪个事件落哪个宿主」钉死。
 *
 * 工程约束（与 `CorruptGuardTest` / `ScreenParityTest` 同一套路）：本仓没有 Robolectric
 * 也没有 kotlin-reflect，故结构性约束靠**读源码文本**断言，行为约束靠纯数据类断言。
 */
class UiEventTest {

    private val day = LocalDate.of(2026, 8, 21).toEpochDay()

    private val item = FoodItem(
        id = "i1",
        name = "牛奶",
        quantity = 2,
        unit = "瓶",
        productionEpochDay = day,
        shelfLifeDays = 30,
    )

    private val record = ConsumptionRecord(
        name = "牛奶",
        amount = 1,
        unit = "瓶",
        epochDay = day,
        id = "c1",
    )

    private fun read(relativePath: String): String? =
        listOf("src/main/java/", "app/src/main/java/")
            .map { File(it + relativePath) }
            .firstOrNull { it.exists() }
            ?.readText()

    @Test
    fun `撤销消耗与恢复归档落在主壳覆盖层`() {
        assertEquals(UiSurface.AppShell, UiEvent.UndoConsumption("i1", "c1").surface)
        assertEquals(
            UiSurface.AppShell,
            UiEvent.UndoRestoreArchived(item, ArchiveReason.DELETED, merged = false).surface,
        )
    }

    @Test
    fun `删除消耗的撤销落在消耗记录页而不是主壳`() {
        // 消耗记录页是带 onBack 的二级页，有自己的 AppScaffold 与 Snackbar 宿主（SystemBars 落位）。
        // 若把它分到主壳，撤销条会弹到主壳底部的自定义覆盖层上 ⇒ 那是视觉改动，必须真机复测才敢做。
        assertEquals(UiSurface.ConsumptionLog, UiEvent.UndoDeleteConsumption(record, 3).surface)
    }

    @Test
    fun `只报信的提示落在首页`() {
        // 自动同步在启动时触发，用户当时停在首页；这条若落到主壳覆盖层，
        // 用户切到别的 Tab 时提示会弹在没人渲染的宿主上并把收集协程堵住（见 UiEvent.kt 类注释）。
        assertEquals(UiSurface.Home, UiEvent.Notice("已自动同步到坚果云 ☁️").surface)
    }

    @Test
    fun `四个落点齐全且六类事件恰好覆盖它们`() {
        // 落点数 = AppViewModel 里的队列数：多一个落点就要多一条 Channel 与一个 when 分支，
        // 少一个则有事件无处可去。这条断言让「悄悄改分流」在 CI 上就红。
        assertEquals(4, UiSurface.entries.size)
        val used = listOf(
            UiEvent.UndoConsumption("i1", "c1"),
            UiEvent.UndoRestoreArchived(item, ArchiveReason.CONSUMED, merged = true),
            UiEvent.UndoDeleteConsumption(record, 0),
            UiEvent.Notice("x"),
            UiEvent.OpFailed(DataOp.Upload, OpFailure.Other("x")),
            UiEvent.CloudBackupsEmpty("x"),
        ).map { it.surface }.toSet()
        assertEquals(UiSurface.entries.toSet(), used)
    }

    @Test
    fun `同步与导入的成败都落在设置页`() {
        // 这 5 条改造前是 `(Boolean, String)` / `(Boolean, Boolean)` 回调，界面拿到后自己弹提示。
        // 现在统一走事件，落点是设置页自己的宿主（FloatingNav 落位 + Plain 形态）。
        val upload = UiEvent.OpFailed(DataOp.Upload, OpFailure.Auth("账号或应用密码错误"))
        assertEquals(UiSurface.Settings, upload.surface)
        assertEquals(UiSurface.Settings, UiEvent.CloudBackupsEmpty("云端暂无备份，请先上传").surface)
        assertEquals(
            UiSurface.Settings,
            UiEvent.Notice("已上传到坚果云 ☁️", UiSurface.Settings).surface,
        )
        // Notice 的默认落点仍是首页（启动时那条自动同步提示），改了默认值就会把提示弹到错误的宿主上。
        assertEquals(UiSurface.Home, UiEvent.Notice("已自动同步到坚果云 ☁️").surface)
    }

    @Test
    fun `失败事件带齐分类与操作判别`() {
        // op 是收集端唯一的分流依据：只有 ListBackups 失败才顺手关掉备份选择器（与改造前一致）。
        val failed = UiEvent.OpFailed(DataOp.ListBackups, OpFailure.Network("timeout"))
        assertEquals(DataOp.ListBackups, failed.op)
        assertTrue("失败原因必须带分类，不能只剩一句话", failed.failure is OpFailure.Network)
        assertEquals("timeout", failed.failure.message)
        assertEquals(5, DataOp.entries.size)
    }

    @Test
    fun `撤销事件带齐回滚所需的数据`() {
        val undo = UiEvent.UndoConsumption("i1", "c1")
        assertEquals("i1", undo.itemId)
        assertEquals("c1", undo.consumptionId)

        val restore = UiEvent.UndoRestoreArchived(item, ArchiveReason.EXPIRED, merged = true)
        assertEquals(item, restore.item)
        assertEquals(ArchiveReason.EXPIRED, restore.reason)
        assertTrue("merged 决定提示文案（已合并数量 / 已恢复到零食柜）", restore.merged)

        val delete = UiEvent.UndoDeleteConsumption(record, 7)
        assertEquals(record, delete.record)
        assertEquals("index 是删除前在日期倒序列表里的位置，撤销时按它插回", 7, delete.index)
    }

    @Test
    fun `事件只放数据不放回调`() {
        val src = read("com/agon/app/viewmodel/UiEvent.kt")
        assumeTrue("找不到 UiEvent.kt（非 Gradle 工作目录？），跳过", src != null)
        // 只按行滤掉注释/KDoc（本文件没有 "*/*" 这种会被朴素剥离误伤的字符串字面量，
        // 故不必上词法状态机；真上了的话请用 MiuixDialogContentTest 里那份已验证的实现）。
        val code = src!!.lines().filter {
            val t = it.trim()
            !t.startsWith("*") && !t.startsWith("//") && !t.startsWith("/*")
        }
        assertFalse(
            "事件类里出现了函数类型 —— 事件应只放数据，撤销动作由收集端回调 VM 的方法。" +
                "塞 onUndo 回调会捕获 VM 作用域、也没法在单测里断言载荷",
            code.any { it.contains("-> Unit") || it.contains("-> Boolean") },
        )
        assertTrue("四类事件应齐备", code.joinToString("\n").let {
            it.contains("data class UndoConsumption") &&
                it.contains("data class UndoRestoreArchived") &&
                it.contains("data class UndoDeleteConsumption") &&
                it.contains("data class Notice")
        })
    }
}
