/*
 * App 级撤销/提示条宿主（09-19 #11b ① 从 AppChrome.kt 拆出）。
 *
 * 为什么单独一个文件：这几样回答的是同一个问题——「一条要弹的消息，在两套互不相干的 Snackbar API 上
 * 怎么走完一轮」。`AppSnackbarHostState` 把 MD3 与 Miuix 的两个 `SnackbarHostState` 合进一个容器、
 * 把两个 `SnackbarResult` 枚举挡在组件层内侧；`AppSnackbarPlacement` / `AppSnackbarForm` 是同一件事的
 * 位置与外观维度。它们跟「顶栏 / 外壳」无关，留在 AppChrome.kt 里只会让人觉得 chrome 是个杂物抽屉。
 *
 * 屏幕侧只该看到 `rememberAppSnackbarHostState()` + `AppSnackbarHost(...)`；
 * 「落点随我们的 Scaffold 走、不跟系统 imePadding」那条约定见下面 `AppSnackbarPlacement` 的注释。
 *
 * 与 `ui/components/UndoSnackbar.kt` 的分工：那边是「怎么把一条 snackbar 做成可滑动关闭的撤销条」
 * （挂在 SnackbarHostState 上的扩展 + 装饰用宿主），是主题无关的叶子件；这里是双主题宿主本身。
 * 两者同名相邻是 #11b 顺手记下的事实，合并要改 6 处 import，故不动（docs/ROADMAP.md #11 备注）。
 */
package com.agon.app.ui.components.app

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult

/**
 * 双主题撤销条状态容器。
 *
 * MD3 与 Miuix 的 `SnackbarHostState` 是两个互不相干的类型，合并后的屏幕不能直接持有其中之一，
 * 所以这里把「该往哪个宿主弹条」和「用户有没有点撤销」都收进来，对外只给一个 Boolean——
 * 否则两个主题的 `SnackbarResult` 枚举会顺着签名泄漏回屏幕层，双实现就又回来了。
 *
 * `isMiuix` 参与 `remember` 的 key：切换主题时换一个新容器，屏幕里 key 在它上面的
 * `LaunchedEffect` 随之重启，不会把撤销条弹到已经卸载的那个宿主上。
 */
@Stable
class AppSnackbarHostState internal constructor(private val isMiuix: Boolean) {
    internal val md3 = SnackbarHostState()
    internal val miuix = MiuixSnackbarHostState()

    /**
     * 只报信、不带撤销动作的提示条（自动同步完成、放弃损坏数据这类）。
     * 两主题的 `showSnackbar` 签名一致，默认时长也同为库默认值，直接转发。
     */
    suspend fun showMessage(message: String) {
        if (isMiuix) miuix.showSnackbar(message) else md3.showSnackbar(message)
    }

    /** @return true = 用户点了「撤销」动作。 */
    suspend fun showUndoSnackbar(message: String): Boolean =
        if (isMiuix) {
            miuix.showUndoSnackbar(message) == MiuixSnackbarResult.ActionPerformed
        } else {
            md3.showUndoSnackbar(message) == SnackbarResult.ActionPerformed
        }
}

@Composable
fun rememberAppSnackbarHostState(): AppSnackbarHostState {
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    return remember(isMiuix) { AppSnackbarHostState(isMiuix) }
}

/**
 * 撤销条落位。合并前两版的抬升值不是随手写的，两种各有各的道理，所以做成具名枚举而不是一个 Dp：
 *
 * - [SystemBars]：二级页（消耗记录 / 归档）。MD3 侧 `navigationBarsPadding() + 24dp` 避开系统手势区，
 *   Miuix 侧不额外抬（原版就没抬）。
 * - [FloatingNav]：带悬浮导航栏的 Tab 页（首页 / 设置）。两版都抬 `84dp` 到悬浮栏之上，且 MD3 侧
 *   **不再**叠加 `navigationBarsPadding()` —— 原版本来就没叠，叠上去会把条推得更高。
 *
 * 第 8 对（设置页）核过了：两版设置页都抬 84dp，正是 [FloatingNav]；**但那条 ⚠️ 警告不能「默认套用就算完」** ——
 * MD3 设置页用的是普通 `SnackbarHost`，而 MD3 侧默认宿主是可滑掉的 `SwipeDismissSnackbarHost`。
 * 处置是给宿主**形态**另开一个维度 [AppSnackbarForm]：落位与形态是两件正交的事，塞进同一个枚举会变成
 * `SystemBarsPlain` / `FloatingNavUndo` 这种四个字的名字，而且四种组合里有三种本来就合法。
 */
enum class AppSnackbarPlacement { SystemBars, FloatingNav }

/**
 * 撤销条宿主**形态**（与 [AppSnackbarPlacement] 的「落位」正交）。
 *
 * - [UndoCountdown]：可滑掉 + **单行正文（超出走省略号）** + 右侧 History 倒计时圆环（点了执行 action）。
 *   首页 / 消耗记录页 / 归档页用这个 —— 它们的条都带「撤销」。
 * - [Plain]：MD3 侧改用 material3 原生 `SnackbarHost`（正文可换行、没有圆环、不可滑掉）；
 *   **Miuix 侧两种形态没有区别**（本来就是库的官方 `SnackbarHost`），故本枚举只在 MD3 侧生效
 *   （按 `docs/ARCHITECTURE.md` 约束 ⑥ 点名）。
 *
 * 为什么设置页必须 [Plain]：它的消息全是「备份导出成功 ✅」「坚果云账号已保存」这类**没有 action** 的提示，
 * 其中「导入成功，数据已恢复 ✅（已自动留存导入前快照）」有 24 个字 —— 套 [UndoCountdown] 会把它截成一行
 * 省略号，并在右侧挂一个点了没有任何反应的倒计时圆环。合并前 MD3 设置页本来就是普通 `SnackbarHost`，
 * 所以这是照抄，不是统一。
 */
enum class AppSnackbarForm { UndoCountdown, Plain }

/**
 * 撤销条宿主：抬升见 [AppSnackbarPlacement]，形态见 [AppSnackbarForm]（形态只影响 MD3 侧）。
 * Miuix 侧恒为库的官方 `SnackbarHost`。
 */
@Composable
internal fun AppSnackbarHost(
    state: AppSnackbarHostState,
    modifier: Modifier = Modifier,
    placement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars,
    form: AppSnackbarForm = AppSnackbarForm.UndoCountdown,
) {
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    // 落位先算一次：原本三个分支各写一遍，再加形态维度就成 2×2 四份，分散写容易漏改其中一份
    val placed = when {
        placement == AppSnackbarPlacement.FloatingNav -> modifier.padding(bottom = 84.dp)
        isMiuix -> modifier
        else -> modifier.navigationBarsPadding().padding(bottom = 24.dp)
    }
    when {
        isMiuix -> MiuixSnackbarHost(state.miuix, modifier = placed)
        form == AppSnackbarForm.Plain -> SnackbarHost(hostState = state.md3, modifier = placed)
        else -> SwipeDismissSnackbarHost(state.md3, modifier = placed)
    }
}
