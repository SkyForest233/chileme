package com.agon.app.viewmodel

import android.util.Log
import com.agon.app.data.NutstoreSync
import com.agon.app.data.buildBackupJson
import com.agon.app.data.cleanupOrphanCovers
import com.agon.app.data.isAutoSyncDue
import com.agon.app.data.migrateConsumptionIds
import com.agon.app.data.migrateLegacyCredentials
import com.agon.app.data.migratePlaintextPassword
import com.agon.app.data.seedIfNeeded
import com.agon.app.data.LocalSnapshotStore
import com.agon.app.data.setLastAutoSyncEpochDay
import com.agon.app.data.setLastSync
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * 启动编排（#11f，09-19）：`AppViewModel.init` 里那段一次性「播种 + 迁移 + 清理 + 自动同步 + 自动快照」。
 *
 * 为什么这一段值得单独成文：**它的正确性就是顺序**，而不是行数。三条硬约束——
 *
 * 1. `seedIfNeeded()` 必须最先：后面几步读写的都是它负责建起来的那份数据。
 * 2. `migrateLegacyCredentials()` 必须先于 `migratePlaintextPassword()`（M1-1）：旧版把坚果云三个
 *    key 存在业务数据那份 DataStore 里，而那份被备份规则排除；先搬家，明文加密迁移才能在
 *    `credentials_store` 里找到明文——反过来的话明文永远躺在旧文件里等加密，静默丢凭据。
 * 3. 孤儿封面清理必须被损坏态门拦住：items/archive 解码失败时 `rawFlow` 会**回落空列表**（读路径
 *    的预期行为），照此清理会把 `covers/` 下所有文件当孤儿删掉，而图片无法从 `corrupt/` 的 JSON
 *    留档里恢复 —— 那是 09-15 修过的真事故，不是假想。
 *
 * 这三条此前以注释形式长在 `AppViewModel.kt` 的 `init` 里，夹在 40 多个成员中间，改的人很容易看不见。
 *
 * 形状与 #10b 那批 `AppViewModel<领域>.kt` 一致：**internal + 同包扩展 + 类里不留同名转发**
 * （成员会遮蔽扩展，`fun x() = x()` 是无限递归而编译通过）。`ready`（首帧门控）**留在类里**：
 * 它是「数据到没到」的状态位，不是启动步骤，别跟着搬过来。
 *
 * 搬运口径：三段与原文**逐行相同**，只做了两件机械事 —— 脱离类体整体左移（`init` 体 8 空格、
 * 两个策略函数各 4 空格）、签名从 `private suspend fun 原名()` 改成 `private suspend fun
 * AppViewModel.原名()`（名字与参数一个没改）。`init` 里只剩 `viewModelScope.launch { runPantryStartup() }`。
 *
 * ⚠️ 唯一一处不是纯搬运：`TAG` 在原文件是 **`private const val`**（文件私有 ⇒ 本文件读不到），
 * 这里另立一个**同名同值**的私有常量，于是 `Log.w` 打出的 tag 与迁移前逐字符相同，
 * `adb logcat -s AppViewModel` 的检索习惯不受影响。
 *
 * 判据见 `viewmodel/AppViewModelStartupTest`：顺序、损坏态门、以及「这套顺序只住在本文件」的装配守卫。
 * 一条 ☁️ 提示的文案家也跟着搬过来了 ⇒ `SnackbarCopyTest` 的期望路径同步改（那正是本笔的改前必红项）。
 */

private const val TAG = "AppViewModel"

/**
 * App 启动时跑一次的一次性序列。**调用方只有一处**（`AppViewModel.init` 的 `viewModelScope.launch`）：
 * 顺序不可重排，理由见文件顶。
 */
internal suspend fun AppViewModel.runPantryStartup() {
    repo.seedIfNeeded()
    // 凭据搬家（M1-1）：旧版把坚果云三个 key 存在业务数据那份 DataStore 里，现在它们住在
    // 被备份规则排除的 credentials_store。必须先搬，再跑明文加密迁移 —— 反过来的话
    // migratePlaintextPassword 会在（空的）新文件里找不到明文，明文就永远留在会进备份的那份文件里。
    repo.migrateLegacyCredentials()
    // 安全迁移：旧版明文密码 → Keystore 加密密文
    repo.migratePlaintextPassword()
    // 迁移：旧消耗记录补 id（供删除/撤销定位）
    repo.migrateConsumptionIds()
    // 启动时清理孤儿封面图片（未被库存/归档引用的文件）。
    //
    // 关键：损坏态下**必须跳过**。items/archive 解码失败时 rawFlow 会回落空集
    // （这是读路径的预期行为），若照此清理，covers/ 下的文件会被全部当成孤儿删除——
    // 而图片无法从 corrupt/ 的 JSON 留档里恢复，等于把「保护数据」的机制变成
    // 「销毁数据」。（2026-09-15 修复）
    if (repo.corruptedKeys.value.isEmpty()) {
        val referenced = buildSet {
            repo.itemsFlow.first().forEach { if (it.photoPath.isNotBlank()) add(it.photoPath) }
            repo.archiveFlow.first().forEach { if (it.item.photoPath.isNotBlank()) add(it.item.photoPath) }
        }
        cleanupOrphanCovers(getApplication(), referenced)
    } else {
        Log.w(TAG, "检测到数据损坏（${repo.corruptedKeys.value}），跳过孤儿封面清理以免误删图片")
    }
    // 自动同步：到期且凭据完整时静默上传
    maybeAutoSync()
    // 本地滚动冷备：若今日尚无快照则静默保存一份
    maybeAutoSnapshot()
}

/**
 * 自动同步策略：到期且凭据完整时静默上传。只被 `runPantryStartup` 调用 ⇒ 保持 `private`。
 */
private suspend fun AppViewModel.maybeAutoSync() {
    val days = repo.autoSyncDaysFlow.first()
    if (days <= 0) return
    val account = repo.nutstoreAccountFlow.first()
    val password = repo.nutstorePasswordFlow.first()
    if (account.isBlank() || password.isBlank()) return
    val today = LocalDate.now(clock).toEpochDay()
    val last = repo.lastAutoSyncEpochDayFlow.first()
    // 判定抽成了纯函数（见 isAutoSyncDue 的注释）：这条跨零点边界此前长在 VM 里没法测
    if (!isAutoSyncDue(last, today, days)) return
    // 数据损坏时 buildBackupJson 抛异常：静默跳过本次自动同步，
    // 绝不能把残缺备份推上云端覆盖掉云端的完好版本。
    val payload = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
    val result = NutstoreSync.upload(account, password, payload, clock)
    if (result.isSuccess) {
        repo.setLastAutoSyncEpochDay(today)
        val time = java.time.LocalDateTime.now(clock)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        repo.setLastSync("自动同步于 $time")
        emit(UiEvent.Notice("已自动同步到坚果云 ☁️"))
    }
    // 失败静默忽略，下次启动重试；不打扰用户
}

/**
 * 本地滚动冷备：今日尚无快照则静默存一份。同样只被 `runPantryStartup` 调用。
 */
private suspend fun AppViewModel.maybeAutoSnapshot() {
    val snapshots = LocalSnapshotStore.listSnapshots(getApplication())
    val today = LocalDate.now(clock)
    // ⚠️ 这里两种时间来源相遇：`today` 来自注入的时钟，而快照文件的修改时刻按**系统时区**解读。
    // 生产上两者同一个时区（容器给的就是系统时区时钟）⇒ 行为与改造前逐位相同；
    // 但若哪天要单测这个函数并塞一个别的时区的固定时钟，这条比较会偏一天 —— 届时把下面
    // 的时区也改成从时钟取（clock.zone），别只改一半。
    val hasSnapshotToday = snapshots.any {
        Instant.ofEpochMilli(it.modifiedEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate() == today
    }
    if (!hasSnapshotToday) {
        val json = runCatching { repo.buildBackupJson() }.getOrNull() ?: return
        LocalSnapshotStore.saveSnapshot(getApplication(), json, clock = clock)
        loadLocalSnapshots()
    }
}
