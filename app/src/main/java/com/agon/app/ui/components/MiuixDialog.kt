package com.agon.app.ui.components

// Miuix（HyperOS）标准弹窗的统一入口。
//
// 存在理由：弹窗必须在 Miuix Scaffold 的 content lambda 内无条件调用、用 show 参数控制，
// 否则不显示（历史踩坑，见 docs/MIUIX_UPGRADE.md §2.3）；把 WindowDialog 收口到一个函数，
// 各屏就不会各写一份、也就不会漏掉这个约束。IME 由库内 DialogContent 自理，
// **不要**传 defaultWindowInsetsPadding = false（详见下方 KDoc）。
//
// 2026-09-16 由 Common.kt 拆分而来（纯搬运，签名与实现未改）。

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Miuix（HyperOS）标准弹窗。
 *
 * 基于 MIUIX 官方 WindowDialog 实现，遵循 HyperOS 规范：
 * - 手机竖屏（常规设备）：标准底部贴合弹出（Bottom-attached），顶部自适应屏幕 Squircle 大圆角；
 * - 大屏/平板/横屏：自动响应式转为屏幕居中卡片（Centered）；
 * - 独立 Window 层：拥有专属系统 Window 图层，不受外部悬浮底栏遮挡；
 * - 自适应软键盘（2026-09-15 核实库源码 pinned 快照 v0.9.4-rc01）：`DialogContentLayout.kt` 的
 *   `DialogContent` 根节点在 `defaultWindowInsetsPadding = true`（默认值）时自带
 *   `.imePadding().navigationBarsPadding().captionBarPadding()`，且 `WindowDialog` 的窗口属性来自
 *   `platformDialogProperties()`（`decorFitsSystemWindows = false`、`usePlatformDefaultWidth = false`），
 *   所以 IME inset 能一路传到弹窗内容、键盘弹出时弹窗整体上移。
 *   **不要传 `defaultWindowInsetsPadding = false`**：那会让键盘盖住弹窗按钮（Miuix 侧弹窗因此无需在本项目里加任何 imePadding）。
 */
@Composable
fun MiuixDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    summary: String = "",
    content: @Composable () -> Unit,
) {
    WindowDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        summary = summary,
        content = content,
    )
}
