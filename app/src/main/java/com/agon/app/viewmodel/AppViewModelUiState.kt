package com.agon.app.viewmodel

import kotlinx.coroutines.flow.update

/**
 * ViewModel 的**UI 状态与事件领域：FAB 抑制 + 多选集三件套 + 一次性事件的唯一发送点**（路线图 #10b-7，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与
 * `AppViewModelBackup.kt` / `AppViewModelCloud.kt` / `AppViewModelArchiveUndo.kt` / `AppViewModelFood.kt` /
 * `AppViewModelCategoryLocation.kt` / `AppViewModelSettings.kt` 同形）。
 * 这是 #10b 的**第七轮、也是最后一轮**：搬完之后类里只剩 `init`、两个 private 策略函数
 * （`maybeAutoSync` / `maybeAutoSnapshot`）与那一大排对外的 `StateFlow` 属性（**09-19 #11f 又把这两个策略函数连同 `init` 的整段编排搬进了 `AppViewModelStartup.kt`** ⇒ 现在的形状是"类里只剩属性 + 一行 launch"）。
 *
 * 搬走的 5 个：
 *
 * - `setFabSuppressed` 与选择集三件套（`toggleSelection` / `setSelection` / `clearSelection`）是两组**临时 UI 状态**
 *   的写口：前者管「Snackbar 展示撤销时把 FAB 藏起来、别挡住撤销按钮」，后者管多选模式选中的食品 id 集合
 *   （v2.8 提升到 VM，供主壳的批量操作栏与列表页共用）。
 *   ⚠️ 两组**状态本身**（`_fabSuppressed` / `_selectedIds`，以及对外的 `fabSuppressed` / `selectedIds`）
 *   一律**留在类里** —— 搬成扩展属性就等于 `get() =` 每次新建 Flow（#5c 已否决）⇒ 本轮只搬写口、读口一条没动。
 * - `emit` 是一次性事件的**唯一发送点**，按 `UiEvent.surface` 把事件分流到 4 条队列。它原本就是
 *   `internal suspend fun`（不是 `private`）⇒ 声明行改写后可见性一词没变，只是多了接收者。
 *   它那个 `internal` 是 **#10b-1 给的**：那轮搬出去的备份领域要发事件，就得先让同包够得着它
 *   （是 10b 前两轮放宽的 7 处之一）⇒ 所以本轮它不算"新放宽"，只是终于搬到了自己的领域文件里。
 *   它头上那条单行 KDoc 随迁（本轮 5 块里**唯一**一条注释；4 条队列与 4 个对外 Flow 的注释都留在类里）。
 *
 * ⚠️ 本轮是 #10b 七轮里**唯一一次成批放宽**：6 个 `private` 成员必须改成 `internal`（累计 7 → **13**，
 * 与 #10 规划向用户交底时给的数一致）—— `_fabSuppressed` / `_selectedIds` 与 4 条队列
 * （`appShellEvents` / `homeEvents` / `consumptionLogEvents` / `settingsEvents`）。
 * 这 6 个都是「只由本领域写、对外只读派生视图」的内部状态：对外的读口一律是它们旁边的 `StateFlow` 属性
 * 与 `receiveAsFlow()` 出来的 `Flow`（都留在类里）⇒ 放宽只是让**同包**的扩展函数够得着，
 * 模块外仍然看不见（`internal` 的边界是 Gradle 模块）。
 *
 * 4 条队列与 4 个对外 Flow 都留在类里，「一次性 UI 事件（路线图 #4a，2026-09-18）」那条段标题与它底下
 * 6 行历史注释（为什么从「可空 StateFlow + 手工 consume」换成队列）也**一起留着** —— 搬走 `emit` 之后
 * 那节底下仍有 8 个成员，不是孤儿帽子。`UiEvent` 与 `UiSurface` 都在同包的 `UiEvent.kt` 里
 * ⇒ 本文件一条 import 都不用为它们加；本领域唯一要 import 的是 `_selectedIds.update { … }` 那个扩展。
 *
 * 调用面：**跨包 2 个文件 / 9 处 ⇒ +5 行 import** —— `MainApp.kt` 6 处（`clearSelection` ×4 +
 * `setFabSuppressed` ×2）⇒ 2 行；`ui/screens/FoodListState.kt` 3 处 ⇒ 3 行。
 * ⚠️ `FoodListState.kt` 自己声明了 2 个同名成员（`toggleSelection` / `clearSelection`）⇒ 声明不压制 import，
 * 那几行照样要加（判据 7 同形，先例已六轮编译通过）；第 3 处 `viewModel.setSelection(…)` 藏在它自己的
 * `selectAll()` 里（名字不同 ⇒ 不算同名相撞，但同样要 import）。
 * `emit` **没有跨包调用方**：用它的全是同包兄弟文件里的裸调用（`AppViewModelCloud.kt` 12 处 ·
 * `AppViewModelBackup.kt` 5 处 · `AppViewModelArchiveUndo.kt` 2 处 · `AppViewModelFood.kt` 1 处）
 * 加 `maybeAutoSync` 里的 1 处（09-19 #11f 起该函数在 `AppViewModelStartup.kt`）⇒ 同包扩展 + 隐式接收者，一行 import 都不用加、那些文件一个字没动。
 * ⚠️ 那处原本是「类成员调同包扩展」的形状，#11f 之后变成「同包扩展调同包扩展」（接收者都是 AppViewModel、`emit` 也是 suspend
 * ⇒ 调用上下文对得上）；这个形状 #10b-1 起已六轮编译通过，不是本轮的新赌注。
 *
 * 守卫本轮**一处都不用改**（实测）：5 块里 **0** 个字符串字面量（☁️ 那条自动同步提示在 `maybeAutoSync` 里、
 * 留守 VM）⇒ `SnackbarCopyTest` 的片段分布无从变化。**#11f 起这句更正**：☁️ 那句随 `maybeAutoSync` 搬进了 `AppViewModelStartup.kt`，该守卫的期望路径同步改了（它按"文案住哪个文件"断言 ⇒ 搬家必红，正是它该红）；`UiEventTest` 只读同包的 `UiEvent.kt`（断言落点数与
 * 4 条队列一一对应），不读 VM 也不读本文件；`CorruptGuardTest` 读的是 Food / Backup / Cloud 三个兄弟文件。
 *
 * 领域边界：一次性事件的**类型**（`UiEvent` / `DataOp` / `UiSurface`）在 `UiEvent.kt`，本轮一个字没动；
 * 谁去**收集**这些 Flow（4 个宿主各自的 `LaunchedEffect`）在 UI 层，也不在本文件；
 * `fabSuppressed` / `selectedIds` 这两个读口留在类里，与其余对外的 `StateFlow` 属性同一排。
 *
 * 搬运口径：函数体**逐字未动**，只整体左移 4 空格（脱离类体）；声明行改写成
 * `internal fun AppViewModel.原名(原参数表)`（`emit` 那条是 `internal suspend fun AppViewModel.emit(…)` ——
 * 接收者加上，**名字、参数与可见性一个没改**）。
 */

internal fun AppViewModel.setFabSuppressed(suppressed: Boolean) {
    _fabSuppressed.value = suppressed
}

internal fun AppViewModel.toggleSelection(id: String) {
    _selectedIds.update { if (id in it) it - id else it + id }
}

internal fun AppViewModel.setSelection(ids: Set<String>) {
    _selectedIds.value = ids
}

internal fun AppViewModel.clearSelection() {
    _selectedIds.value = emptySet()
}

/** 一次性事件的**唯一发送点**：按 [UiEvent.surface] 分流到对应宿主的队列。 */
internal suspend fun AppViewModel.emit(event: UiEvent) {
    when (event.surface) {
        UiSurface.AppShell -> appShellEvents.send(event)
        UiSurface.Home -> homeEvents.send(event)
        UiSurface.ConsumptionLog -> consumptionLogEvents.send(event)
        UiSurface.Settings -> settingsEvents.send(event)
    }
}
