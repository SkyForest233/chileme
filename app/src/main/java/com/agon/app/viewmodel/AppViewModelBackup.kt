package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.BackupData
import com.agon.app.data.LocalSnapshotStore
import com.agon.app.data.OpFailure
import com.agon.app.data.buildBackupJson
import com.agon.app.data.buildCsvExport
import com.agon.app.data.importBackupJson
import com.agon.app.data.previewBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel 的**备份领域：导出、文件导入、导入前快照、本地快照管理**（路线图 #10b-1，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与那边同形）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. 用 `stateIn` 出来的属性与所有 `MutableStateFlow` **留在类里**：搬成扩展属性就变成 `get() =` 每次新建
 *    Flow ⇒ 语义变了（#5c 已否决）；所以本领域的 `_localSnapshots` / `localSnapshots` 一对都没动，
 *    `// ---- 本地快照管理 ----` 那条段标题也留在类里给它们当标题。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 于是这几个函数要用到的类成员从 `private` 放宽成 `internal`：`repo` / `clock` / `_localSnapshots` /
 *    `emit`（放宽的那 4 行都在 `AppViewModel.kt` 里）。`internal` = **模块内**可见，不出 App 模块，
 *    与 #5c 在 data 层的口径一致。
 *
 * 对外调用写法一个字没变：同包内用隐式接收者就能解析 —— `AppViewModel.kt` 里留下的 `syncDownload`
 * 仍要调本文件的 [snapshotBeforeRestore]，这处「成员调同包扩展」等 #10b-2 搬云端同步时就消失了；
 * 唯一要改的是**别的包**的调用方：`ui/screens/SettingsState.kt` 的适配器 `ViewModelSettingsActions`
 * 为搬走的每个函数加一行 import（本仓禁通配导入），转发写法一字未改。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal …fun AppViewModel.原名(原参数表)`（`private` 去掉、接收者加上，**名字与参数一个没改**）。
 */

internal suspend fun AppViewModel.buildBackupJson(): String = repo.buildBackupJson()

internal suspend fun AppViewModel.buildCsvExport(): String = repo.buildCsvExport()

internal suspend fun AppViewModel.importBackupJson(raw: String): Boolean = repo.importBackupJson(raw)

/** 解析备份用于导入前预览（不改动数据）。非备份 / 畸形 JSON 返回 null。 */
internal suspend fun AppViewModel.previewBackup(raw: String): BackupData? = repo.previewBackup(raw)

/**
 * 恢复类操作的公共前置步骤：留一份「操作前状态」本地快照，返回是否保存成功。
 *
 * 三条恢复路径（文件导入 / 坚果云整版本恢复 / 本地快照还原）都走它，
 * 保证任何一次整体替换之前都有一份可回退的快照。
 * 快照失败（例如当前数据本身已损坏、无法序列化）不阻断恢复——那种情况正是恢复的用途。
 */
internal suspend fun AppViewModel.snapshotBeforeRestore(): Boolean = withContext(Dispatchers.IO) {
    runCatching { repo.buildBackupJson() }.getOrNull()
        ?.let { json -> LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock) != null }
        ?: false
}

/**
 * 导入备份的推荐入口：**先写一份本地快照兜底，再整体替换**。
 *
 * 导入是不可撤销的破坏性操作，此前点一下文件就直接覆盖。现在：
 * 1. 先 `buildBackupJson()` + `LocalSnapshotStore.saveSnapshot()` 留一份「导入前状态」，
 *    用户可在设置页「本地快照」里一键回到导入前；
 * 2. 再执行 [com.agon.app.data.FoodRepository.importBackupJson]。
 *    （这里写**全限定名**：`FoodRepository` 的 import 在 #5a 之后只剩这一处 KDoc 引用了，
 *    而本仓的口径是 import 只服务代码 —— `tools/kt-lexcheck.py` 会把"只被注释用着的 import"
 *    报成未使用，detekt 的 `UnusedImports` 又是关的（核查第 14 处），所以只能自己守。)
 *
 * 快照失败（例如当前数据本身已损坏、无法序列化）**不阻断导入**——那种情况正是导入的用途。
 *
 * 成败经 [UiEvent]（落点 [UiSurface.Settings]）报信：那三句话此前写在 `SettingsScreen` 里，
 * 由界面拿 `(ok, snapshotSaved)` 两个布尔拼出来 —— 与还原快照/云端恢复的文案是同一套句式，
 * 却分散在两个文件里。#4c 一并收到 VM，句式与用词逐字未改。
 */
internal fun AppViewModel.importBackupWithSnapshot(raw: String) =
    viewModelScope.launch {
        // 快照（含整份 JSON 序列化与落盘）与导入都放 IO 线程，避免主线程卡顿
        val snapshotSaved = snapshotBeforeRestore()
        if (snapshotSaved) loadLocalSnapshots()
        val ok = withContext(Dispatchers.IO) { repo.importBackupJson(raw) }
        if (ok) {
            emit(
                UiEvent.Notice(
                    if (snapshotSaved) "导入成功，数据已恢复 ✅（已自动留存导入前快照）"
                    else "导入成功，数据已恢复 ✅（导入前快照未能保存）",
                    UiSurface.Settings,
                ),
            )
        } else {
            emit(UiEvent.OpFailed(DataOp.ImportBackup, OpFailure.Other("导入失败：文件格式不正确")))
        }
    }

/**
 * 刷新本地快照列表。读取（含逐份解析 JSON 数条数）已下沉到 IO 线程，
 * 这里 fire-and-forget 地更新 UI 状态 —— 调用方无需等待。
 */
internal fun AppViewModel.loadLocalSnapshots() {
    viewModelScope.launch {
        _localSnapshots.value = LocalSnapshotStore.listSnapshots(getApplication())
    }
}

internal fun AppViewModel.saveLocalSnapshot(onDone: ((Boolean) -> Unit)? = null) = viewModelScope.launch {
    val json = runCatching { repo.buildBackupJson() }.getOrNull()
    if (json != null) {
        LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock)
        loadLocalSnapshots()
        onDone?.invoke(true)
    } else {
        onDone?.invoke(false)
    }
}

/**
 * 从本地快照还原。
 *
 * 顺序很重要：**先读出目标快照内容，再写「还原前状态」快照**。
 * 反过来的话，快照份数已达上限（[LocalSnapshotStore.MAX_SNAPSHOTS] = 3）时，
 * 新写的那份会把要还原的旧快照挤掉（按修改时间淘汰），导致「点了还原却报找不到」。
 */
internal fun AppViewModel.restoreLocalSnapshot(fileName: String) = viewModelScope.launch {
    // 两条失败路径（读不出来 / 导不进去）今天是同一句话，故共用一个值 —— 写两遍就有改一处忘一处的风险。
    val corrupt = OpFailure.Other("快照文件损坏或无法还原")
    val raw = LocalSnapshotStore.readSnapshot(getApplication(), fileName)
    if (raw == null) {
        emit(UiEvent.OpFailed(DataOp.RestoreSnapshot, corrupt))
        return@launch
    }
    val snapshotSaved = snapshotBeforeRestore()
    if (snapshotSaved) loadLocalSnapshots()
    if (repo.importBackupJson(raw)) {
        loadLocalSnapshots()
        emit(
            UiEvent.Notice(
                if (snapshotSaved) "已从本地快照还原数据 ✅（已自动留存还原前快照）"
                else "已从本地快照还原数据 ✅（还原前快照未能保存）",
                UiSurface.Settings,
            ),
        )
    } else {
        emit(UiEvent.OpFailed(DataOp.RestoreSnapshot, corrupt))
    }
}
