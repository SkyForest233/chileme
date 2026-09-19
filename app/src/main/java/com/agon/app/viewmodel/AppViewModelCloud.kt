package com.agon.app.viewmodel

import androidx.lifecycle.viewModelScope
import com.agon.app.data.NutstoreSync
import com.agon.app.data.OpFailure
import com.agon.app.data.buildBackupJson
import com.agon.app.data.importBackupJson
import com.agon.app.data.previewBackup
import com.agon.app.data.setLastSync
import com.agon.app.data.setNutstoreCredentials
import com.agon.app.data.toOpFailure
import kotlinx.coroutines.launch

/**
 * ViewModel 的**坚果云同步领域：凭据保存、上传、云端备份列表、下载恢复**（路线图 #10b-2，2026-09-19）。
 *
 * 形状为什么是「同包 `internal` 扩展函数」而不是类成员、以及为什么不选「门面转发」和「领域对象」，
 * 完整取舍写在 `data/RepositoryCore.kt` 的文件 KDoc 里（#5c 一次说清，这里只指路 —— 本文件与那边、
 * 与 `AppViewModelBackup.kt` 同形）。
 *
 * 三条落地约束（#10b 规划里写死的，本领域逐条对过）：
 *
 * 1. 用 `stateIn` 出来的属性与所有 `MutableStateFlow` **留在类里**：所以本领域的 `_cloudBackups` /
 *    `cloudBackups` 与 `_loadingBackups` / `loadingBackups` 两对都没动，
 *    `// ---- 云端备份列表（恢复时选择版本） ----` 那条段标题也留在类里给它们当标题。
 *    反过来，`// ---- 坚果云同步 ----` 那条**整段都搬来了本文件**，留在类里就是个没有内容的孤儿标题
 *    ⇒ 删掉，由本 KDoc 接替它的作用（这是本领域唯一一处「删」，不是搬）。
 * 2. 类里**不留同名转发**：成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译期不报（#5c 已否决）。
 * 3. 于是这几个函数要用到的类成员从 `private` 放宽成 `internal`：`_syncing` / `_cloudBackups` /
 *    `_loadingBackups`（放宽的 3 行都在 `AppViewModel.kt` 里；`repo` / `clock` / `emit` 在 #10b-1 已放宽）。
 *    `internal` = **模块内**可见，不出 App 模块，与 #5c 在 data 层的口径一致。
 *
 * 两个文件级常量各归各位（Kotlin 的顶层 `private` 是**文件私有**，跨文件够不着）：
 *
 * - [NO_CREDENTIALS_MESSAGE] **跟着搬进本文件** —— 它的 3 个使用者（上传 / 拉列表 / 下载）全在这里，
 *   搬完在 `AppViewModel.kt` 里一个使用者都不剩；「一句话三处共用、改一处不会忘两处」的意图原样保留。
 *   KDoc 里那句提到 `TAG` 的话改了措辞（原来写「本文件的 [TAG]」，搬过来之后 `TAG` 不在本文件了）。
 * - `TAG` **留在 `AppViewModel.kt`** —— 用它的只有类里 `init` 那段孤儿封面清理；本领域 4 个函数
 *   一处 `Log` 都没有（实测）。若将来某个领域文件要打日志，照 `data/FoodBackup.kt` 的先例在该文件里
 *   各自声明一份同字面量的 file-private `TAG`；「升成包级常量」那条路 #5c 已否决（本包同名 `TAG` 已有多个）。
 *
 * 对外调用写法一个字没变：同包内用隐式接收者就能解析 —— [syncDownload] 仍要调 `AppViewModelBackup.kt` 的
 * [snapshotBeforeRestore]，两个都是同包扩展 ⇒ 不需要 import；#10b-1 那处「类成员调同包扩展」的形状
 * 随本次搬运消失了（正如那份 KDoc 预告的）。它体内的 `repo.previewBackup(raw)` / `repo.buildBackupJson()` /
 * `repo.importBackupJson(raw)` 要的是 **data 层的同名扩展** ⇒ 那几条 import 必须带上
 * （`tools/move-importcheck.py` 的判据 7 专治这类「声明名与 import 名相撞」）。
 * 唯一要改的是**别的包**的调用方：`ui/screens/SettingsState.kt` 的适配器 `ViewModelSettingsActions`
 * 为这 4 个函数各加一行 import（本仓禁通配导入），转发写法一字未改。
 *
 * 搬运口径：函数体**逐字未动**，只做了两件事 —— 整体左移 4 空格（脱离类体）、声明行改写成
 * `internal …fun AppViewModel.原名(原参数表)`（接收者加上，**名字与参数一个没改**；本领域 4 个函数原本
 * 都不是 `private`，所以没有可见性放宽，只有第 3 条里那 3 个**成员**被放宽）。
 */

/**
 * 「还没填凭据」这句话在 3 个入口（上传 / 拉列表 / 下载）各写了一遍，且字字相同 ⇒ 抽成常量。
 * 不是为省字：三处若各写一遍，改一处忘两处就会让用户在同一件事上看到三种说法。
 *
 * 放顶层而不是类内：Kotlin 的 `const val` 只能在**顶层或 companion object** 里，
 * 类体内直接写 `private const val` 编译不过（`AppViewModel.kt` 里的 `TAG` 同样是顶层，沿用这个惯例）。
 */
private const val NO_CREDENTIALS_MESSAGE = "请先填写并保存坚果云账号和应用密码"

internal fun AppViewModel.saveNutstoreCredentials(account: String, password: String) =
    viewModelScope.launch { repo.setNutstoreCredentials(account, password) }

/**
 * 上传当前数据到坚果云。成败经 [UiEvent]（落点 [UiSurface.Settings]）报信，不再要回调。
 *
 * 本地数据异常那条走 [OpFailure.Other] 而不是 `toOpFailure()`：它不是同步失败，
 * 用类型归类会把一个本地错误误报成"网络问题"。
 */
internal fun AppViewModel.syncUpload() = viewModelScope.launch {
    val account = nutstoreAccount.value
    val password = nutstorePassword.value
    if (account.isBlank() || password.isBlank()) {
        emit(UiEvent.OpFailed(DataOp.Upload, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
        return@launch
    }
    _syncing.value = true
    // 同上：损坏态下拒绝上传，避免残缺备份覆盖云端完好版本。
    val json = runCatching { repo.buildBackupJson() }.getOrElse {
        _syncing.value = false
        emit(UiEvent.OpFailed(DataOp.Upload, OpFailure.Other(it.message ?: "数据异常，已取消上传")))
        return@launch
    }
    val result = NutstoreSync.upload(account, password, json, clock)
    _syncing.value = false
    result.fold(
        onSuccess = {
            val time = java.time.LocalDateTime.now(clock)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            repo.setLastSync("上传于 $time")
            emit(UiEvent.Notice("已上传到坚果云 ☁️", UiSurface.Settings))
        },
        onFailure = { emit(UiEvent.OpFailed(DataOp.Upload, it.toOpFailure("上传失败"))) },
    )
}

/**
 * 拉取云端备份列表，供用户选择恢复哪一份。
 *
 * 三种结果各走各路（改造前它们被压进一个 Boolean）：
 * - **非空** ⇒ 不发事件：列表由 [cloudBackups] 这个 `StateFlow` 驱动，选择器保持打开
 *   （改造前是 `onResult(true, "")`，界面拿到空字符串什么也不做）；
 * - **空** ⇒ [UiEvent.CloudBackupsEmpty]：请求是成功的，只是没东西可恢复；
 * - **失败** ⇒ [UiEvent.OpFailed] 带分类。
 * 后两种都要关掉选择器 —— 由收集端按事件类型决定，见 `SettingsScreen`。
 */
internal fun AppViewModel.loadCloudBackups() = viewModelScope.launch {
    val account = nutstoreAccount.value
    val password = nutstorePassword.value
    if (account.isBlank() || password.isBlank()) {
        emit(UiEvent.OpFailed(DataOp.ListBackups, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
        return@launch
    }
    _loadingBackups.value = true
    val result = NutstoreSync.listBackups(account, password)
    _loadingBackups.value = false
    result.fold(
        onSuccess = { list ->
            _cloudBackups.value = list
            if (list.isEmpty()) emit(UiEvent.CloudBackupsEmpty("云端暂无备份，请先上传"))
        },
        onFailure = { emit(UiEvent.OpFailed(DataOp.ListBackups, it.toOpFailure("获取备份列表失败"))) },
    )
}

/** 从坚果云下载指定备份并恢复（整体替换）。成败经 [UiEvent]（落点 [UiSurface.Settings]）报信。 */
internal fun AppViewModel.syncDownload(fileName: String) = viewModelScope.launch {
    val account = nutstoreAccount.value
    val password = nutstorePassword.value
    if (account.isBlank() || password.isBlank()) {
        emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other(NO_CREDENTIALS_MESSAGE)))
        return@launch
    }
    _syncing.value = true
    val result = NutstoreSync.download(account, password, fileName)
    _syncing.value = false
    val raw = result.getOrNull()
    if (raw == null) {
        val failure = result.exceptionOrNull()?.toOpFailure("下载失败") ?: OpFailure.Other("下载失败")
        emit(UiEvent.OpFailed(DataOp.Download, failure))
        return@launch
    }
    // 与「文件导入」同一套前置校验：必须含 items 键，否则拒绝覆盖（防「合法空备份」清空数据）。
    if (repo.previewBackup(raw) == null) {
        emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other("云端备份格式不正确")))
        return@launch
    }
    val snapshotSaved = snapshotBeforeRestore()
    if (snapshotSaved) loadLocalSnapshots()
    if (repo.importBackupJson(raw)) {
        val time = java.time.LocalDateTime.now(clock)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        repo.setLastSync("恢复于 $time")
        emit(
            UiEvent.Notice(
                if (snapshotSaved) "已从坚果云恢复数据 ✅（已自动留存恢复前快照）"
                else "已从坚果云恢复数据 ✅（恢复前快照未能保存）",
                UiSurface.Settings,
            ),
        )
    } else {
        emit(UiEvent.OpFailed(DataOp.Download, OpFailure.Other("云端备份格式不正确")))
    }
}
