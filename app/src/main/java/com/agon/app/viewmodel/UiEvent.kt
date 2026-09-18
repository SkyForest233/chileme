package com.agon.app.viewmodel

import com.agon.app.data.ArchiveReason
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.FoodItem

/**
 * 一次性 UI 事件（路线图 #4a，2026-09-18）。
 *
 * **为什么要有这个类型**：此前 4 条一次性提示各自是「可空 `StateFlow` + 手工 `consumeXxx()`」——
 * `_undoRequest` / `_deletedConsumption` / `_restoredArchivedEvent` / `_autoSyncMessage`。
 * 那样写有两个结构性问题：
 * 1. `StateFlow` 会向**新订阅者重放最后一个值**，所以「看过就撕」全靠每个收集点记得调 `consume`。
 *    2026-09-18 复核时 4/4 都记得（没有正在发生的重放 bug），但那是**纪律不是机制** ——
 *    新增第 5 条时忘调就会静默重复弹，而且没有任何东西会报警。
 * 2. 事件载荷混在 VM 的公开状态里，UI 拿到的是可空数据类，得自己 `filterNotNull()`。
 *
 * 改用 `Channel` 后**接收即出队**：`consume` 函数与可空状态一起消失，重放也不再可能。
 *
 * **为什么按 [surface] 分成三条队列、而不是共用一条**：`Channel` 是**单接收方**语义 ——
 * 多个收集协程挂在同一条队列上会互相抢事件（谁先 `receive` 谁拿到），结果不确定。
 * 而本仓有**三个**互不相干的 Snackbar 宿主，各自只在自己那个界面被组合时才应该弹：
 *
 * - [UiSurface.AppShell]：`MainApp` 的自定义覆盖层（动画偏移 + `imePadding`），全 App 常在；
 * - [UiSurface.Home]：首页 `AppScaffold` 内的宿主（`FloatingNav` 落位）。自动同步提示必须落在这里，
 *   且**只在首页组合期间**才弹 —— 用户停在统计页时若照弹，`showSnackbar` 会挂在一个没有渲染的宿主上
 *   并把整条收集协程堵住（后续撤销条全都不出现）。队列化正好复现旧行为：事件排队等用户回到首页；
 * - [UiSurface.ConsumptionLog]：消耗记录页（带 `onBack` 的二级页）自己的宿主。
 *
 * **事件里只放数据、不放回调**（不放 `onUndo: () -> Unit`）：收集端拿到事件后回调 VM 的方法。
 * 这样事件可以被单测断言，也不会把 VM 作用域泄进 UI 层。
 */
sealed interface UiEvent {
    /** 这条事件该落在哪个 Snackbar 宿主上 —— 也就是由哪一份收集协程接手。 */
    val surface: UiSurface

    /** 列表页步进器减号：已减少一件并计入消耗，可撤销（撤销 = 删消耗记录 + 数量回滚）。 */
    data class UndoConsumption(val itemId: String, val consumptionId: String) : UiEvent {
        override val surface: UiSurface = UiSurface.AppShell
    }

    /** 列表页搜索结果里恢复归档：可撤销（撤销 = 按原原因重新归档）。 */
    data class UndoRestoreArchived(
        val item: FoodItem,
        val reason: ArchiveReason,
        val merged: Boolean,
    ) : UiEvent {
        override val surface: UiSurface = UiSurface.AppShell
    }

    /** 消耗记录页删除一条记录：可撤销（撤销 = 按原下标插回，避免被提到列表最前）。 */
    data class UndoDeleteConsumption(val record: ConsumptionRecord, val index: Int) : UiEvent {
        override val surface: UiSurface = UiSurface.ConsumptionLog
    }

    /** 只报信、不带撤销动作的提示（当前只有「已自动同步到坚果云」）。 */
    data class Notice(val message: String) : UiEvent {
        override val surface: UiSurface = UiSurface.Home
    }
}

/** 一次性事件的落点。划分理由见 [UiEvent] 的类注释。 */
enum class UiSurface { AppShell, Home, ConsumptionLog }
