package com.agon.app.viewmodel

import com.agon.app.data.ArchiveReason
import com.agon.app.data.OpFailure
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
 * **为什么按 [surface] 分成多条队列、而不是共用一条**：`Channel` 是**单接收方**语义 ——
 * 多个收集协程挂在同一条队列上会互相抢事件（谁先 `receive` 谁拿到），结果不确定。
 * 而本仓有多个互不相干的 Snackbar 宿主，各自只在自己那个界面被组合时才应该弹
 * （全仓共 **6 处**宿主站点，其中 **4 处**收事件；另外两处 —— 食品详情页与归档页 —— 的提示
 * 由屏幕自己的本地流程弹出，不经事件系统）：
 *
 * - [UiSurface.AppShell]：`MainApp` 的自定义覆盖层（动画偏移 + `imePadding`），全 App 常在；
 * - [UiSurface.Home]：首页 `AppScaffold` 内的宿主（`FloatingNav` 落位）。自动同步提示必须落在这里，
 *   且**只在首页组合期间**才弹 —— 用户停在统计页时若照弹，`showSnackbar` 会挂在一个没有渲染的宿主上
 *   并把整条收集协程堵住（后续撤销条全都不出现）。队列化正好复现旧行为：事件排队等用户回到首页；
 * - [UiSurface.ConsumptionLog]：消耗记录页（带 `onBack` 的二级页）自己的宿主；
 * - [UiSurface.Settings]：设置页自己的宿主（`FloatingNav` 落位 + `Plain` 形态）。同步/还原的
 *   成败提示落在这里（#4c 之前是 4 个 `(Boolean, String)` 回调，见下）。
 *
 * **事件里只放数据、不放回调**（不放 `onUndo: () -> Unit`）：收集端拿到事件后回调 VM 的方法。
 * 这样事件可以被单测断言，也不会把 VM 作用域泄进 UI 层。
 *
 * **#4c 补的两类事件（2026-09-18）**：设置页的同步/还原本来是 **4 个** `(Boolean, String)` 回调
 * （`syncUpload` / `loadCloudBackups` / `syncDownload` / `restoreLocalSnapshot`）——
 * 其中 3 个连 Boolean 都不看，只用那句话弹提示；第 4 个（拉云端备份列表）用 Boolean 决定
 * "要不要关掉选择器"。现在统一走事件：[OpFailed] 带 [OpFailure] 分类（不再只剩一句话），
 * [CloudBackupsEmpty] 单独一档（"请求成功但云端没东西"既不是失败也不是成功加载，
 * 混进 Boolean 里正是当初语义被压平的地方）。**文案逐字未改**，由 `OpFailureTest` 钉住。
 *
 * 第 5 条同步路径 `maybeAutoSync()` **不在此列**：它对失败是"静默忽略、下次启动重试"
 * （代码里那句注释就是设计意图），没有回调也没有提示 ⇒ 无事件可发。
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

    /**
     * 只报信、不带撤销动作的提示。
     *
     * #4c 之前它只有一种用法：启动时自动同步成功后那句「已自动同步到坚果云 ☁️」，落 [UiSurface.Home]。
     * 现在设置页的同步 / 还原 / 导入**成功**提示也走这里（落 [UiSurface.Settings]），
     * 失败走 [OpFailed]（带分类），「拉到了但云端是空的」走 [CloudBackupsEmpty]。
     *
     * [surface] 的默认值仍是 [UiSurface.Home]，好让启动自动同步那条调用点保持原样、不必跟着改。
     *
     * ⚠️ 上面那句文案是**照着原文抄进注释的**，重写本段时别把它写没：`SnackbarCopyTest`
     * 的阳性对照（「注释剥离本身有效」）拿它当样本 —— 这句话在本文件里只存在于注释中，
     * 剥掉注释后必须查不到，才能证明那些「某句话只出现在某文件」的分布断言数的是代码而不是注释。
     * 2026-09-18 改 #4c 时就把它写丢了，CI 红在单测那一步；本地没有编译器、Actions 的日志端点
     * 又被墙（只取得到 annotations），最后是把那份守卫逐条镜像成脚本才定位到的。
     */
    data class Notice(
        val message: String,
        override val surface: UiSurface = UiSurface.Home,
    ) : UiEvent

    /**
     * 同步 / 还原失败。[failure] 带分类（凭据错 / 网络 / 其它），不再只剩一句话 ——
     * UI 现在**可以**按种类做事（例如凭据错时引导重填），本轮暂未这么做（属行为变更）。
     *
     * [op] 用来让收集端复现改造前的差别：只有「拉云端备份列表」失败时要顺手关掉备份选择器，
     * 上传/下载/还原失败时不关（那时选择器根本没开，但"顺手关一下"会让语义变模糊）。
     */
    data class OpFailed(val op: DataOp, val failure: OpFailure) : UiEvent {
        override val surface: UiSurface = UiSurface.Settings
    }

    /**
     * 云端备份列表**拉成功了但是空的**。不是失败（不该按失败处理），也不是加载完成（没东西可选）
     * ⇒ 收集端关掉选择器并提示"先上传"。改造前这一档被塞进 `onResult(false, …)`，
     * 与真正的失败共用一个 Boolean。
     */
    data class CloudBackupsEmpty(val message: String) : UiEvent {
        override val surface: UiSurface = UiSurface.Settings
    }
}

/**
 * 哪一次数据操作失败了（上传 / 拉列表 / 下载 / 还原快照 / 导入备份）。
 * 划分理由见 [UiEvent.OpFailed] 的注释 —— 收集端只靠它区分「要不要顺手关掉备份选择器」。
 */
enum class DataOp { Upload, ListBackups, Download, RestoreSnapshot, ImportBackup }

/** 一次性事件的落点。划分理由见 [UiEvent] 的类注释。 */
enum class UiSurface { AppShell, Home, ConsumptionLog, Settings }
