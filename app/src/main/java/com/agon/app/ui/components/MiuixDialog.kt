package com.agon.app.ui.components

// Miuix（HyperOS）标准弹窗的统一入口。
//
// 存在理由：弹窗必须在 Miuix Scaffold 的 content lambda 内无条件调用、用 show 参数控制，
// 否则不显示（历史踩坑，见 docs/MIUIX_UPGRADE.md §2.3）；把 WindowDialog 收口到一个函数，
// 各屏就不会各写一份、也就不会漏掉这个约束。IME 由库内 DialogContent 自理，
// **不要**传 defaultWindowInsetsPadding = false；content 也**必须是单一根节点**（库根 Column 无间距，
// 两个平级节点之间会是 0dp）；动作按钮**一律用库的 TextButton，主要动作传 textButtonColorsPrimary()**
// （不传 colors 时它和「取消」同为浅灰）。三条约束详见下方 KDoc。
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
 *
 * **content 必须是单一根节点**（2026-09-17 真机复测后补写，此前踩过）：库的 `DialogContent` 把
 * `title` / `summary` / `content()` 依次塞进一个**不带 `verticalArrangement` 的 Column**
 * （间距靠 title、summary 各自的 `padding(bottom = 12.dp)` 提供，`content()` 后面没有任何补白）。
 * 所以 content 里若写两个平级节点（如「字段 Column」+「按钮 Row」），它们之间是 **0dp**，真机上表现为
 * 输入框下边与按钮上边重合。上游示例的标准写法是单一 `Column(verticalArrangement = Arrangement.spacedBy(12.dp))`
 * （`example/shared/.../component/DialogSection.kt:351`），本仓 `app/AppBatchMoveDialog.kt`（批量移动位置）、
 * `AppFormDialog.kt`、`SettingsScreen.kt`（坚果云）均遵此，按钮区再额外留 4~8.dp。
 *
 * **动作按钮一律 `TextButton`，主要动作传 `ButtonDefaults.textButtonColorsPrimary()`**
 * （2026-09-17 真机复测后补写，此前 4 处偏离）：Miuix 的 `TextButton` **不是** MD3 那种无底文字按钮 ——
 * 它内部就是 `Button`，而 `Button` 用 `.squircleSurface(color = containerColor)` 实心填充
 * （源码 `basic/Button.kt:76`）；默认 `textButtonColors()` 的容器色是 `secondaryVariant`（浅灰 #F0F0F0），
 * 所以「不传 colors」的主要动作会和「取消」完全同色、看不出主次。`textButtonColorsPrimary()` 给的是
 * 容器 `primary`（蓝）+ 文字 `onPrimary`（白）+ 对应 disabled 角色 ⇒ **蓝底白字胶囊**。
 * 依据：上游 `example/shared/.../component/DialogSection.kt` 的 7 个弹窗，主要动作一律这么写；
 * 弹窗里**不要**用实心 `Button` + `buttonColorsPrimary()`（颜色虽同，但要自己补文字色/字重，
 * 也拿不到 `MiuixTheme.textStyles.button` 与 disabled 角色）。危险动作传 error 色是本仓约定。
 * 静态守卫：`MiuixDialogContentTest.dialogActionsFollowMiuixButtonConvention`。
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
