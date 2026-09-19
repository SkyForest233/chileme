package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.ArchiveReason
import com.agon.app.data.FoodItem
import com.agon.app.data.QuantityChangeResult
import com.agon.app.data.archiveItems
import com.agon.app.data.changeQuantity
import com.agon.app.data.clearAll
import com.agon.app.data.clearArchive
import com.agon.app.data.daysLeft
import com.agon.app.data.deleteArchived
import com.agon.app.data.restoreArchived
import com.agon.app.data.restoreArchivedBatch
import com.agon.app.data.upsert
import kotlinx.coroutines.launch

/**
 * ViewModel 的**食物 CRUD 与批量领域：新增/编辑、数量增减、批量归档与恢复、清空与放弃损坏数据**
 * （路线图 #10b-4，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与
 * `AppViewModelBackup.kt` / `AppViewModelCloud.kt` / `AppViewModelArchiveUndo.kt` 同形）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. Flow 属性**留在类里**：本领域只**读** [items]（`cleanExpired` 用它挑过期条目），没动它。
 *    本领域自己**一个属性都没有** ⇒ 也没有段标题需要跟着搬或删：这 11 个函数原本散在三处
 *    （9 个在无标题的食物操作区、`clearAll` 在「位置管理」段里、`discardCorruptData` 在
 *    「云端备份列表」段的两个属性之后），搬完三处都还有别的内容 ⇒ 没有一条段标题变孤儿。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 这几个函数要用到的类成员得从 `private` 放宽成 `internal` —— **本轮又一个都不用放**：
 *    `repo` 与 `emit` 在 #10b-1 已放宽，`items` 本来就是 public（放宽总数仍停在 7 个）。
 *
 * ⚠️ 本领域最锋利的一处（比 #10b-3 的 3 处更多）：**6 个函数名与 data 层的同名扩展一字不差**
 * （`upsert` / `restoreArchivedBatch` / `deleteArchived` / `clearArchive` / `changeQuantity` / `clearAll`；
 * 那边是 `internal suspend fun FoodRepository.同名`）⇒ 本文件**既声明** `internal fun AppViewModel.同名`、
 * **又必须** `import com.agon.app.data.同名`：函数体里写的是 `repo.同名(...)`，接收者是 `FoodRepository`
 * ⇒ 要解析的正是 data 那份，本文件这份接收者类型不对、根本不参与解析。漏掉 import 就是编译错，
 * 而「本文件已经声明了同名函数」恰好是最容易让工具误判成「不用 import」的形状。
 * 还有第 7 处同形状：[restoreArchivedSmart] 体内调 `repo.restoreArchived(id)`，而
 * `internal fun AppViewModel.restoreArchived` 这个**同包兄弟文件里的声明**（`AppViewModelArchiveUndo.kt`，
 * #10b-3 搬的）同样不许压制那条 import ⇒ 这正是 `tools/move-importcheck.py` 判据 7 的「同包路径」
 * （2026-09-19 补上的那一处，本轮是它的第一个真实用户）。
 * 反过来，`repo.discardCorrupt(…)` 与它读的那份损坏标记集合（`FoodRepository` 自己的属性，就在下面
 * `discardCorruptData` 的体内）都是**类成员**、不是扩展函数 ⇒ 这两处一个 import 都不需要。
 * ⚠️ 这句刻意不写出那个属性的标识符：它是 `tools/doc-metrics.sh` 追踪的一个数（#8「被引用很多但
 * 没有页面消费它」，作用域 `app/src/main`、按出现次数），散文里多写一次就会把那个数顶上去 —— 数就
 * 不再只反映代码引用了（2026-09-19 当场撞上：32 → 33）。
 *
 * [consumeOne] 是本领域唯一一处**领域内互调**：它体内写 `changeQuantity(id, -1, onAutoArchived)`
 * （隐式接收者就是 `AppViewModel`）⇒ 解析到本文件自己那份扩展，同包不需要 import；
 * 而上面那条 `import com.agon.app.data.changeQuantity` 的接收者是 `FoodRepository`、形参只有 2 个
 * ⇒ 两者既不同接收者又不同元数，谁也不会跟谁抢。
 *
 * 对外调用写法一个字没变，但**跨包调用方是四个领域里最多的**：7 个文件、16 处 `viewModel.<名>(`
 * ⇒ 共加 15 行 import（本仓禁通配导入）：`MainApp.kt` 2 行、`ui/screens/EditFoodScreen.kt` 1 行、
 * `ArchiveState.kt` 4 行、`HomeScreenState.kt` 3 行、`FoodListState.kt` 2 行、`FoodDetailState.kt` 2 行、
 * `SettingsState.kt` 1 行。走 `state.<名>(` 的那 5 个屏幕（`ArchiveScreen` / `HomeScreen` /
 * `FoodDetailScreen` / `FoodListScreen` / `SettingsBackupDialogs`）用的是状态类自己的成员 ⇒ 不需要 import。
 * ⚠️ 这 7 个文件里有 5 个**自己声明了同名的转发函数**（如 `FoodListState.deleteArchived`、
 * `SettingsState.clearAll`）⇒ 声明照样不压制 import，那些行必须加（与判据 7 同一形状；
 * 先例见 `SettingsState.kt`（#10b-1/2）与 `FoodListState.kt`（#10b-3），都已编译通过）。
 *
 * ⚠️ 守卫同批改一处（#10b 规划的风险 ③ 早点名了它）：`CorruptGuardTest` 的「损坏数据有放弃入口」
 * 原先读 `AppViewModel.kt` 并断言字面量 `fun discardCorruptData()` ⇒ 函数搬走后必须改成读**本文件**、
 * 断言 `fun AppViewModel.discardCorruptData()`，否则那条守卫会红。`SnackbarCopyTest` 不受影响：
 * 11 块里 0 个中文字面量（实测），它唯一那条 VM 期望（「已自动同步到坚果云 ☁️」）在 `maybeAutoSync` 里，
 * 属自动同步策略 ⇒ 留在 `AppViewModel.kt`。
 *
 * 领域边界（哪些「看着像」却不在本文件）：**单件**归档/恢复与消耗记录的删除撤销在
 * `AppViewModelArchiveUndo.kt`（#10b-3）；`setCategoryThreshold` 与分类/位置的增删改在 #10b-5；
 * `setAutoSyncDays` 与 5 个主题开关在 #10b-6；`setFabSuppressed`、选择集三件套与 `emit` 在 #10b-7；
 * `maybeAutoSync` / `maybeAutoSnapshot` 是自动同步策略，暂留类里。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal …fun AppViewModel.原名(原参数表)`（接收者加上，**名字与参数一个没改**；这 11 个原本
 * 都不是 `private` ⇒ 连可见性都没变）。3 处 KDoc（`restoreArchivedSmart` / `changeQuantity` /
 * `discardCorruptData`）跟着各自的函数一起搬，一字未改。
 */

internal fun AppViewModel.upsert(item: FoodItem) = viewModelScope.launch { repo.upsert(item) }

internal fun AppViewModel.archiveBatch(ids: Set<String>, reason: ArchiveReason) =
    viewModelScope.launch { repo.archiveItems(ids, reason) }

internal fun AppViewModel.restoreArchivedBatch(ids: Set<String>) = viewModelScope.launch {
    repo.restoreArchivedBatch(ids)
}

/** 恢复单条归档；回调参数 merged = 是否与现有库存合并（同名同生产日期去重）。 */
internal fun AppViewModel.restoreArchivedSmart(id: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
    onDone(repo.restoreArchived(id))
}

internal fun AppViewModel.cleanExpired(onDone: ((Set<String>) -> Unit)? = null) = viewModelScope.launch {
    val ids = items.value.filter { it.daysLeft < 0 }.map { it.id }.toSet()
    if (ids.isNotEmpty()) {
        repo.archiveItems(ids, ArchiveReason.EXPIRED)
        onDone?.invoke(ids)
    }
}

internal fun AppViewModel.deleteArchived(id: String) = viewModelScope.launch { repo.deleteArchived(id) }

internal fun AppViewModel.clearArchive() = viewModelScope.launch { repo.clearArchive() }

/**
 * 调整数量；吃完（减到 0）时仓库层会自动归档。
 * @param onAutoArchived 自动归档发生时回调（用于 UI 提示）
 * @param withUndo 减少时是否暴露「撤销」请求（列表页步进器减号用，详情页吃掉一份走 consumeOne 不用）
 */
internal fun AppViewModel.changeQuantity(
    id: String,
    delta: Int,
    onAutoArchived: (() -> Unit)? = null,
    withUndo: Boolean = false,
) = viewModelScope.launch {
    val result: QuantityChangeResult = repo.changeQuantity(id, delta)
    if (result.autoArchived) onAutoArchived?.invoke()
    if (withUndo && delta < 0 && result.consumptionId != null) {
        emit(UiEvent.UndoConsumption(id, result.consumptionId))
    }
}

internal fun AppViewModel.consumeOne(id: String, onAutoArchived: (() -> Unit)? = null) =
    changeQuantity(id, -1, onAutoArchived)

internal fun AppViewModel.clearAll() = viewModelScope.launch { repo.clearAll() }

/**
 * 放弃处于损坏态的数据（UI 二次确认后调用）：删除该 key 的内容并解除损坏标记，
 * 让相关写入恢复正常。原文留档保留在 filesDir/corrupt/。
 */
internal fun AppViewModel.discardCorruptData() = viewModelScope.launch {
    repo.discardCorrupt(repo.corruptedKeys.value)
}
