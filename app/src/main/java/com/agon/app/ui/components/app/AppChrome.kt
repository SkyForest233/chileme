package com.agon.app.ui.components.app

// App 级页面骨架：Scaffold + 顶栏 + 撤销条宿主。
//
// 「Miuix / MD3 双实现，靠 LocalThemeStyle 分流」的写法照抄 ui/components/Badges.kt 的 StatusBadge。
// 与 components/ 下的叶子组件不同，本目录（components/app/）放的是**屏幕骨架级**组件：
// 目标是让每个屏幕只写一份业务结构，主题差异全部沉到这里
// （见 docs/audits/2026-09-15-code-review.md §2.1、devlog/2026-09-16.md 第三批 #3）。
//
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出。

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
 * 撤销条宿主：MD3 用可滑掉的自绘条（`SwipeDismissSnackbarHost`）并避开系统手势区，
 * Miuix 用官方 `SnackbarHost`。MD3 原有的 `navigationBarsPadding() + 24dp` 抬升封在这里。
 */
@Composable
internal fun AppSnackbarHost(state: AppSnackbarHostState, modifier: Modifier = Modifier) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSnackbarHost(state.miuix, modifier = modifier)
    } else {
        SwipeDismissSnackbarHost(
            state.md3,
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        )
    }
}

/**
 * 二级页顶栏：标题 + 可选返回键 + 右侧动作区。
 *
 * 两主题的返回图标字形不同（MD3 `ArrowBack` / Miuix `Back`），标题排版也不同
 * （MD3 传 composable 并加粗 / Miuix 直接吃 String），差异都收在这里。
 * `actions` 两主题同为 `@Composable RowScope.() -> Unit`（上游 v0.9.4-rc01 的
 * `TopAppBar` 签名已核对），可直传；动作里的图标请用 [AppDestructiveAction] 这类
 * 已分流的组件，别在屏幕里再写 `if (isMiuix)`。
 * Miuix 侧的 `subtitle`（首页在用）暂未开口子：MD3 `TopAppBar` 没有对应参数，
 * 等首页那一对合并时连 MD3 的第二行一起补，避免「一个主题静默忽略参数」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTopAppBar(
            title = title,
            navigationIcon = {
                if (onBack != null) {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            },
            actions = actions,
        )
    } else {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
    }
}

/**
 * 顶栏里的「危险操作」入口（清空归档、删除全部之类）：两主题各自的删除字形
 * （MD3 `DeleteForever` / Miuix `Delete`）+ error 色，尺寸都用各自 IconButton 的默认值。
 */
@Composable
fun AppDestructiveAction(onClick: () -> Unit, contentDescription: String) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick) {
            MiuixIcon(
                MiuixIcons.Delete,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.error,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 屏幕骨架：顶栏 + 撤销条宿主 + 内容区。
 *
 * MD3 侧保留原来的 `containerColor = background`（顶栏与内容区同底色，否则会出现色差）；
 * Miuix 侧用它自己的默认底色（`colorScheme.surface`），与合并前一致。
 *
 * 取代 `ManageScreens.kt` / `MiuixManageScreens.kt` 里那两份私有的 `*ManageScaffold`
 * ——等管理页那一对合并时删掉它们。
 *
 * **弹窗要放在 [content] 里面**：Miuix 的 `WindowDialog` 必须在 Miuix Scaffold 的
 * content lambda 内无条件调用、靠 `show` 控制显隐，否则不显示（见 `MiuixDialog.kt` 的 KDoc
 * 与 `docs/MIUIX_UPGRADE.md` §2.3）。MD3 的 `AlertDialog` 放里放外都是独立窗口，渲染无差别，
 * 所以统一放里面 —— 别按 MD3 的习惯写到 Scaffold 外面去。
 *
 * **键盘避让由调用方决定**：`AppScaffold` 不无条件加 `imePadding()`（没有输入框的屏幕不需要），
 * 需要的屏幕自己传 `modifier = Modifier.imePadding()`，这样「哪一屏要避让」在屏幕文件里看得见，
 * `ImeHandlingTest` 第 1 条也仍能按屏幕文件点名（合并后一个条目就覆盖两套主题）。
 */
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    snackbar: AppSnackbarHostState? = null,
    snackbarModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixScaffold(
            modifier = modifier,
            snackbarHost = {
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier)
            },
            topBar = { AppTopBar(title = title, onBack = onBack, actions = actions) },
            content = content,
        )
    } else {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier)
            },
            topBar = { AppTopBar(title = title, onBack = onBack, actions = actions) },
            content = content,
        )
    }
}
