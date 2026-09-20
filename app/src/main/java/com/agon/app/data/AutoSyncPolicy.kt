/**
 * 自动同步的**间隔判定**（09-19 #11c 从 `CloudSync.kt` 拆出的纯函数）。
 *
 * 为什么单独一个文件：`isAutoSyncDue` 是这段逻辑里唯一**不需要网络、不需要 Android 环境**的部分，
 * 也正是因此它是唯一能被单测钉死的（`AutoSyncDueTest`：跨零点 / 刚好等于间隔 / 从未同步过）。
 * 它此前住在一个网络对象旁边，读代码的人会以为「自动同步」这件事全在 `NutstoreSync` 里，
 * 于是下一次改策略时容易再往那个 object 里塞一个 `if` —— 那就是又一个不可测的分支。
 */
package com.agon.app.data

/**
 * 自动同步的**间隔判定**（#5b 从 `AppViewModel.maybeAutoSync()` 里抽出来的纯函数）。
 *
 * 抽出来只有一个理由：这段逻辑此前**没法测** —— 它长在需要 Android 环境的 VM 里，
 * 而它偏偏是「跨零点」最敏感的一处（差一天就同步、差一天就不同步）。抽成纯函数后
 * `AutoSyncDueTest` 能把边界钉死；行为逐位不变（原来写的是 `if (today - last < days) return`，
 * 取反即此式）。
 *
 * ⚠️ 「间隔 <= 0 表示关闭自动同步」那条判断**留在 VM 里**、刻意不并进来：它必须在读凭据、
 * 算今天之前就先短路（省掉两次 DataStore 读与一次日期计算），并进来就改变了读取顺序 ——
 * 结果虽一样，但那就不是纯搬运了。
 *
 * @param lastSyncEpochDay 上次自动同步那天的 epochDay（从未同步过时仓库给的是 0 ⇒ 必然到期）
 * @param todayEpochDay 今天的 epochDay —— 调用方从注入的时钟取，不再直接问系统
 * @param intervalDays 用户设的间隔天数（调用方已保证 > 0）
 */
internal fun isAutoSyncDue(lastSyncEpochDay: Long, todayEpochDay: Long, intervalDays: Int): Boolean =
    todayEpochDay - lastSyncEpochDay >= intervalDays
