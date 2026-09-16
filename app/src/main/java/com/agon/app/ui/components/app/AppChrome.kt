package com.agon.app.ui.components.app

// App 级页面骨架：Scaffold + 顶栏 + 撤销条宿主。
//
// 「Miuix / MD3 双实现，靠 LocalThemeStyle 分流」的写法照抄 ui/components/Badges.kt 的 StatusBadge。
// 与 components/ 下的叶子组件不同，本目录（components/app/）放的是**屏幕骨架级**组件：
// 目标是让每个屏幕只写一份业务结构，主题差异全部沉到这里
// （见 docs/audits/2026-09-15-code-review.md §2.1、devlog/2026-09-16.md 第三批 #3）。
//
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出。

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
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
 * ⚠️ 留给设置页那一对（第 8 对）：MD3 `SettingsScreen` 用的是普通 `SnackbarHost` 而不是可滑掉的
 * `SwipeDismissSnackbarHost`，届时要么给这里加一个「宿主形态」维度，要么接受「设置页的条也能滑掉」
 * 这个行为变化并单独说明 —— 别默认套用 [FloatingNav] 就算完。
 */
enum class AppSnackbarPlacement { SystemBars, FloatingNav }

/** 撤销条宿主：MD3 用可滑掉的自绘条（`SwipeDismissSnackbarHost`），Miuix 用官方 `SnackbarHost`。抬升见 [AppSnackbarPlacement]。 */
@Composable
internal fun AppSnackbarHost(
    state: AppSnackbarHostState,
    modifier: Modifier = Modifier,
    placement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixSnackbarHost(
            state.miuix,
            modifier = if (placement == AppSnackbarPlacement.FloatingNav) {
                modifier.padding(bottom = 84.dp)
            } else {
                modifier
            },
        )
    } else if (placement == AppSnackbarPlacement.FloatingNav) {
        SwipeDismissSnackbarHost(state.md3, modifier = modifier.padding(bottom = 84.dp))
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
 * `subtitle` 两侧都吃得到，不存在「一个主题静默忽略参数」：Miuix 直接传（上游 v0.9.4-rc01
 * `TopAppBar.kt:100` 已核对 `subtitle: String = ""`，所以传空串与不传逐字等价）；MD3 的 `TopAppBar`
 * 没有这个参数，改用 `LargeTopAppBar` 把标题排成两行 —— 这正是合并前 MD3 首页的写法，
 * 顺带带来折叠效果（见 [scrollBehavior]）。
 *
 * @param scrollBehavior 只由 [AppScaffold] 传，屏幕层别碰。非 null 时 MD3 侧走折叠式
 *   `LargeTopAppBar`；Miuix 侧不传（上游 `TopAppBar` 也有 `largeTitle` + `scrollBehavior` 可做折叠，
 *   但合并前的 Miuix 首页没用，替它开就是视觉改动）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTopAppBar(
            title = title,
            subtitle = subtitle ?: ,
            navigationIcon = {
                if (onBack != null) {
                    MiuixIconButton(onClick = onBack) {
                        MiuixIcon(MiuixIcons.Back, contentDescription = "返回")
                    }
                }
            },
            actions = actions,
        )
    } else if (subtitle != null) {
        LargeTopAppBar(
            title = {
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
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
 * 顶栏图标按钮的共用实现：字形由语义入口（下面三个）传进来，两主题各挑各的。
 *
 * `tint` 为 null 时**不传**这个参数，而不是传 `Color.Unspecified` —— 合并前两版在
 * 「非危险操作」上都是整个参数不写（用各自库的默认内容色），照抄最稳。
 */
@Composable
private fun AppBarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    md3Icon: ImageVector,
    miuixIcon: ImageVector,
    tint: Color? = null,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick) {
            if (tint != null) {
                MiuixIcon(miuixIcon, contentDescription = contentDescription, tint = tint)
            } else {
                MiuixIcon(miuixIcon, contentDescription = contentDescription)
            }
        }
    } else {
        IconButton(onClick = onClick) {
            if (tint != null) {
                Icon(md3Icon, contentDescription = contentDescription, tint = tint)
            } else {
                Icon(md3Icon, contentDescription = contentDescription)
            }
        }
    }
}

/** 顶栏「编辑」入口：MD3 `Edit` / Miuix `Edit`，都用默认内容色（详情页在用）。 */
@Composable
fun AppEditAction(onClick: () -> Unit, contentDescription: String = "编辑") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.Edit, MiuixIcons.Edit)
}

/** 顶栏「删除这一条」入口：MD3 `Delete` / Miuix `Delete`，error 色（详情页在用）。 */
@Composable
fun AppDeleteAction(onClick: () -> Unit, contentDescription: String = "删除") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.Delete, MiuixIcons.Delete, appErrorColor())
}

/** 顶栏「清空 / 删除全部」入口：MD3 `DeleteForever` / Miuix `Delete`，error 色（归档页在用）。 */
@Composable
fun AppDestructiveAction(onClick: () -> Unit, contentDescription: String) {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.DeleteForever, MiuixIcons.Delete, appErrorColor())
}

/**
 * 「这一页没东西可显示」的兜底屏：**无顶栏**的空 Scaffold + 居中一句话 + 一个返回按钮。
 * 详情页在食品已归档/移除时用（合并前两版各写一份）。
 *
 * 文案走 [AppText] 的 Emphasis 档位（两版原来就是 MD3 `titleMedium` / Miuix `body1`）；
 * 按钮两版形态不同 —— MD3 是圆角 50 胶囊，Miuix 是库默认按钮 + 显式 `onSecondaryVariant` 文字色，
 * 照原样保留，所以这一处仍是两段分支。
 */
@Composable
fun AppMessageScreen(message: String, actionLabel: String, onAction: () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixScaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(message, AppTextScale.Emphasis)
                Spacer(Modifier.height(12.dp))
                MiuixButton(onClick = onAction) {
                    MiuixText(actionLabel, color = MiuixTheme.colorScheme.onSecondaryVariant)
                }
            }
        }
    } else {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(message, AppTextScale.Emphasis)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(50)) { Text(actionLabel) }
            }
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
 * **带副标题的顶栏（首页）在 MD3 侧是折叠式的**：`subtitle != null` 时 MD3 分支自建
 * `exitUntilCollapsedScrollBehavior()`，并把 `fillMaxSize() + nestedScroll(...)` 加到 Scaffold 上
 * （合并前只有 MD3 首页这么写；Miuix 首页的 Scaffold 没有 modifier，就不替它加）。折叠状态因此
 * 与主题绑定：切主题会重建，和 [AppSnackbarHostState] 换宿主是同一类取舍。
 *
 * **键盘避让由调用方决定**：`AppScaffold` 不无条件加 `imePadding()`（没有输入框的屏幕不需要），
 * 需要的屏幕自己传 `modifier = Modifier.imePadding()`，这样「哪一屏要避让」在屏幕文件里看得见，
 * `ImeHandlingTest` 第 1 条也仍能按屏幕文件点名（合并后一个条目就覆盖两套主题）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    snackbar: AppSnackbarHostState? = null,
    snackbarPlacement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars,
    snackbarModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixScaffold(
            modifier = modifier,
            snackbarHost = {
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement)
            },
            topBar = { AppTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions) },
            content = content,
        )
    } else {
        val scrollBehavior = if (subtitle != null) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else {
            null
        }
        Scaffold(
            modifier = if (scrollBehavior != null) {
                modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement)
            },
            topBar = {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    actions = actions,
                )
            },
            content = content,
        )
    }
}
