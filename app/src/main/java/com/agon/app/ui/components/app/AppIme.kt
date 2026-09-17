package com.agon.app.ui.components.app

// 带输入框的 MD3 弹窗专用的「粘性」键盘避让。
//
// 为什么需要（2026-09-17 真机复测问题 ①）：焦点在同一个弹窗的两个输入框之间切换时，平台会 restartInput，
// 输入法窗口可能整个消失再出现 —— 用户实机确认：不只「账号 → 密码」这种输入法类型不同的会弹，
// 「分类名称 → Emoji」这种类型完全相同的也一样弹，所以**诱因是焦点切换本身，不是 keyboardOptions**。
// 这期间 `WindowInsets.ime` 会瞬时归零，而 MD3 弹窗是独立浮动窗口、由窗口管理器**居中**摆放：
// `imePadding()` 让它在「屏幕减掉键盘」的剩余空间里居中，inset 一掉弹窗就往下坠、inset 回来又往上顶，
// 这就是用户看到的「弹一下」。
//
// 修法：inset 变大时逐帧跟随（与系统键盘动画同步，表现和 `imePadding()` 一致）；inset 变小时**先按住**
// `holdMillis` 毫秒，到期仍然更小才认定「键盘真的收起了」，再用 `releaseMillis` 平滑落回。
// 于是焦点切换的瞬时塌陷（通常几百毫秒内回来）→ 弹窗一动不动；真收起键盘 → 只多等 `holdMillis` 才落回居中。
//
// **为什么不修 Miuix 侧**（用户 2026-09-17 决定：Miuix 不动，只修 MD3）：库的 `imePadding()` 在
// `layout/DialogContentLayout.kt` 的 `DialogContent` 内部，应用层唯一的开关是
// `WindowDialog(defaultWindowInsetsPadding = false)`，而它是**三件套一起关**
// （`.imePadding().navigationBarsPadding().captionBarPadding()`）—— 关掉后弹窗底边会掉到导航栏/手势条下面、
// squircle 圆角被压住，是所有 Miuix 弹窗的视觉回退。查过上游 pinned tag v0.9.4-rc01：全库只有两处用到 ime，
// 即 `DialogContentLayout` 的 `.imePadding()` 与「弹窗关闭时 `keyboardController.hide()`」
// （`DialogContentLayout` + `utils/MiuixPopupUtils.kt:332`），**没有任何 IME 平滑/粘性能力**；
// `.claude/skills/miuix` 里也没有（release note 提到的 "search-bar inset timing" 明确标注为 Example-only、
// 不是库的公开 API，且嘱咐不要照抄）。Miuix 侧要根治只能等上游，可考虑提 issue。
//
// 适用范围：**只有带输入框的 MD3 弹窗**（三处：`AppFormDialog` / `AppDialogs` 批量移动位置 /
// `SettingsScreen` 坚果云，正是 `ImeHandlingTest` 第 3 条点名那三个文件）。
// 屏幕级的 `AppScaffold(modifier = Modifier.imePadding())` **刻意不改**：屏幕内容高、下坠幅度小得多，
// 用户也没报；改它要动 `ImeHandlingTest` 第 1 条的整份屏幕清单，风险与收益不成比例。

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.delay

/**
 * 粘性键盘避让：等价于 `Modifier.imePadding()`，但**不会跟随 IME inset 的瞬时归零**。
 *
 * 用在带输入框的 MD3 弹窗上（配合 `DialogProperties(decorFitsSystemWindows = false)`，
 * 否则 inset 根本传不进来）。两点与 `imePadding()` 的差异，都是刻意的：
 *
 * 1. inset 变小时不立即跟随，而是先按住 [holdMillis] 毫秒 —— 这就是「不弹」的来源；
 *    代价是用户主动收起键盘时，弹窗会晚 [holdMillis] 毫秒才开始落回居中（落回本身有 [releaseMillis] 的动画，
 *    不是硬跳）。弹窗内点「取消 / 确定」关闭时看不出来（弹窗自己正在消失）。
 * 2. 只补 bottom 与 start/end 三段 padding，**不消费 insets**（`imePadding()` 会消费）。
 *    弹窗内容里没有第二个避让位点，所以不会有双重 padding；若将来在弹窗内容里再加 `imePadding()`，会叠加。
 *
 * @param holdMillis inset 归零后按住多久才认定「键盘真的收起了」。要大于输入法 restartInput 的塌陷时长
 *   （实机观察是「整个消失再出现」，故取 300ms 起）；调大更稳但收起键盘时更拖，调小反之。**真机可调参数**。
 * @param releaseMillis 认定收起后，弹窗落回居中的动画时长。取 220ms 与 MD3 弹窗自身的动效量级相当。
 */
@Composable
fun stickyImePadding(holdMillis: Long = 300L, releaseMillis: Int = 220): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val ime = WindowInsets.ime
    val imeBottom = ime.getBottom(density).toFloat()
    val held = remember { Animatable(0f) }

    LaunchedEffect(imeBottom) {
        val target = imeBottom
        if (target > held.value) {
            held.snapTo(target)
        } else if (target == 0f) {
            delay(holdMillis)
            held.animateTo(0f, tween(releaseMillis))
        } else {
            // 变矮但没收起（换输入法面板高度 / 分屏改尺寸）：平滑跟随，不留空隙
            held.animateTo(target, tween(releaseMillis))
        }
    }

    val bottom = with(density) { held.value.toDp() }
    val start = with(density) { ime.getLeft(density, layoutDirection).toDp() }
    val end = with(density) { ime.getRight(density, layoutDirection).toDp() }
    return Modifier.padding(start = start, end = end, bottom = bottom)
}
