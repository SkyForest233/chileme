package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.ArchiveReason
import com.agon.app.data.ArchivedItem
import com.agon.app.data.ConsumptionRecord
import com.agon.app.data.addConsumption
import com.agon.app.data.archiveItems
import com.agon.app.data.deleteConsumption
import com.agon.app.data.restoreArchived
import com.agon.app.data.undoConsumption
import kotlinx.coroutines.launch

/**
 * ViewModel 的**归档与消耗撤销领域：单件归档 / 恢复归档 + 消耗记录的删除与撤销**（路线图 #10b-3，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与
 * `AppViewModelBackup.kt` / `AppViewModelCloud.kt` 同形）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. Flow 属性**留在类里**：本领域用到的 `consumption`（消耗记录列表）是 `StateFlow`，没动。
 *    本领域自己**一个属性都没有** ⇒ 也没有段标题需要跟着搬或删：这 6 个函数原本散在两处
 *    （4 个在「一次性 UI 事件」那条段标题之后、2 个在无标题的食物操作区），搬完两处都还有别的内容。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 这几个函数要用到的类成员得从 `private` 放宽成 `internal` —— **本轮一个都不用放**：
 *    `repo` 与 `emit` 在 #10b-1 已经放宽，`consumption` 本来就是 public（放宽总数仍停在 7 个）。
 *
 * ⚠️ 本领域最锋利的一处：**3 个函数名与 data 层的同名扩展一字不差**（`undoConsumption` /
 * `restoreArchived` / `deleteConsumption`；那边是 `internal suspend fun FoodRepository.同名`）。
 * 于是本文件**既声明** `internal fun AppViewModel.同名`、**又必须** `import com.agon.app.data.同名`：
 * 两者不冲突（函数体里写的是 `repo.同名(...)`，要解析的正是 data 那份），但漏掉 import 就是编译错，
 * 而「本文件已经声明了同名函数」恰好是最容易让工具误判成「不用 import」的形状 ⇒
 * `tools/move-importcheck.py` 的判据 7（带接收者的声明不压制同名 import）专治这个，#10b-1 开工前补的。
 *
 * 对外调用写法一个字没变，但**跨包调用方比前两个领域多**：5 个文件、6 处，各加一行 import
 * （本仓禁通配导入）—— `MainApp.kt`、`ui/screens/ConsumptionLogScreen.kt`、`ConsumptionLogState.kt`、
 * `FoodDetailState.kt`，以及 `FoodListState.kt`（两处）。`FoodListScreen.kt` 写的是
 * `state.restoreArchivedWithUndo(entry)`（状态类自己的成员）⇒ 它不需要 import。
 * ⚠️ `FoodListState.kt` 自己**也声明了同名的** `fun restoreArchived` / `fun restoreArchivedWithUndo`
 * （它是把 VM 转发给界面的适配器）⇒ 声明照样不压制 import，那两行必须加（与判据 7 同一形状）。
 *
 * 领域边界（哪些「看着像」却不在本文件）：`archiveBatch` / `restoreArchivedBatch` / `restoreArchivedSmart` /
 * `deleteArchived` / `clearArchive` / `cleanExpired` 是**批量与自动**归档，归 #10b-4「食物 CRUD 与批量」；
 * `maybeAutoSync` / `maybeAutoSnapshot` 是自动同步策略，不属云端同步领域也不属这里（09-19 #11f 起在类里）。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal …fun AppViewModel.原名(原参数表)`（接收者加上，**名字与参数一个没改**；这 6 个原本
 * 都不是 `private` ⇒ 连可见性都没变）。本领域不带任何中文字面量（实测 6 块里 0 处）⇒
 * `SnackbarCopyTest` 的期望 map 不用改键。
 */

/** 撤销最近一次减少消耗：删消耗记录 + 数量回滚。 */
internal fun AppViewModel.undoConsumption(event: UiEvent.UndoConsumption) = viewModelScope.launch {
    repo.undoConsumption(event.itemId, event.consumptionId)
}

internal fun AppViewModel.restoreArchivedWithUndo(entry: ArchivedItem) = viewModelScope.launch {
    val merged = repo.restoreArchived(entry.item.id)
    emit(UiEvent.UndoRestoreArchived(entry.item, entry.reason, merged))
}

/** 删除单条消耗记录（修正统计），并记下原位置供撤销插回。 */
internal fun AppViewModel.deleteConsumption(record: ConsumptionRecord) = viewModelScope.launch {
    val sorted = consumption.value.sortedByDescending { it.epochDay }
    // 优先按 id 精确定位；id 为 null 的旧记录按内容匹配，避免删除静默失效
    val index = sorted.indexOfFirst {
        if (record.id != null) it.id == record.id else it == record
    }
    val target = sorted.getOrNull(index) ?: return@launch
    repo.deleteConsumption(target)
    emit(UiEvent.UndoDeleteConsumption(target, index.coerceAtLeast(0)))
}

/** 撤销删除：按原下标插回，避免被提到列表最前。 */
internal fun AppViewModel.undoDeleteConsumption(record: ConsumptionRecord, index: Int) = viewModelScope.launch {
    repo.addConsumption(record, index)
    // 原先这里还有一句「若待处理的撤销事件正是这条记录就清空它」的防御性代码：
    // 收集端一直是「先 consume 再弹条」，弹条期间那个状态早已是 null，故那句永远不成立；
    // 改用 Channel 后事件接收即出队，也没有「待处理的事件」可清 ⇒ 一并删掉。
}

internal fun AppViewModel.archive(id: String, reason: ArchiveReason) =
    viewModelScope.launch { repo.archiveItems(setOf(id), reason) }

internal fun AppViewModel.restoreArchived(id: String) = viewModelScope.launch { repo.restoreArchived(id) }
