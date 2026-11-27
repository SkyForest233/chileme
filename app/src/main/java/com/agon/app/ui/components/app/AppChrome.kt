package com.agon.app.ui.components.app

// App 级页面骨架：Scaffold + 顶栏。09-19 #11b 把另外三件事各归各位：
// 撤销条宿主 → AppSnackbar.kt，顶栏的 5 个动作入口 → AppBarActions.kt，整屏消息页 → AppMessageScreen.kt。
//
// 「Miuix / MD3 双实现，靠 LocalThemeStyle 分流」的写法照抄 ui/components/Badges.kt 的 StatusBadge。
// 与 components/ 下的叶子组件不同，本目录（components/app/）放的是**屏幕骨架级**组件：
// 目标是让每个屏幕只写一份业务结构，主题差异全部沉到这里
// （见 docs/audits/2026-09-15-code-review.md §2.1、devlog/2026-09-16.md 第三批 #3）。
//
// 2026-09-16 由 ConsumptionLogScreen + MiuixConsumptionLogScreen 合并时抽出。

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior as MiuixScrollBehaviorType
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Close

/** Create a page-local Miuix scroll behavior; the MD3 branch does not create or consume one. */
@Composable
internal fun rememberAppMiuixScrollBehavior(): MiuixScrollBehaviorType? =
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) MiuixScrollBehavior() else null

/** Attach a page's Miuix bar behavior to the scroll container that actually owns its offset. */
internal fun Modifier.appMiuixNestedScroll(behavior: MiuixScrollBehaviorType?): Modifier =
    if (behavior == null) this else nestedScroll(behavior.nestedScrollConnection)

/**
 * 二级页顶栏：标题 + 可选返回键 + 右侧动作区。
 *
 * 两主题的返回图标字形不同（MD3 `ArrowBack` / Glass `Back`），标题排版也不同
 * （MD3 传 composable 并加粗 / Miuix Glass 直接吃 String），差异都收在这里。
 * `actions` 两主题同为 `@Composable RowScope.() -> Unit`，可直传；Miuix 分支由 `GlassTopAppBar`
 * 提供玻璃按钮材质上下文，动作里的图标请用 [AppDestructiveAction] 这类已分流的组件，别在屏幕里再写
 * `if (isMiuix)`。
 * `subtitle` 两侧都吃得到：Glass 直接传（空串与不传等价）；MD3 的 `TopAppBar` 没有这个参数，
 * 改用 `LargeTopAppBar` 把标题排成两行 —— 这正是合并前 MD3 首页的写法，顺带带来折叠效果
 * （见 [scrollBehavior] 与 [miuixScrollBehavior]）。
 *
 * @param onClose 多选态左侧的「退出多选」关闭键（字形 MD3 `Close` / Miuix `Close`），与 [onBack]
 *   互斥、优先级更高；都不传即无左侧图标。
 * @param selectionMode 顶栏进入多选态。**只影响 MD3 侧**：底色从 `background` 转 `surfaceContainer`
 *   （合并前就是这样）；Miuix 侧两态同色（都用库默认 `colorScheme.surface`），所以这个参数在 Miuix
 *   分支没有可见效果 —— 不是静默忽略，是原版两态本来就同色。
 * @param scrollBehavior MD3 的折叠行为，由 [AppScaffold] 创建；屏幕层别传。
 * @param miuixScrollBehavior 页面拥有的 Miuix Glass 折叠行为；页面将同一个实例传给顶栏，并把它的
 *   nested-scroll connection 接在真实的列表/滚动容器上。每屏单独创建，不复用全局底栏显隐监听。
 * @param miuixContentScrolled 由当前页面的滚动状态显式提供（LazyListState.canScrollBackward 或
 *   ScrollState.value > 0），驱动 Glass 按钮材质；折叠位移由同一页的 nested-scroll behavior 更新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    miuixScrollBehavior: MiuixScrollBehaviorType? = null,
    miuixContentScrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val activeBackdrop = if (LocalMiuixBlurEnabled.current) LocalMiuixPageBackdrop.current else null
        val blurActive = activeBackdrop != null
        GlassTopAppBar(
            title = title,
            isContentScrolled = miuixContentScrolled,
            backdrop = activeBackdrop,
            progressiveBlur = blurActive,
            bandBrush = if (blurActive) {
                GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
            } else {
                SolidColor(MiuixTheme.colorScheme.surface)
            },
            fill = if (blurActive) {
                GlassTopAppBarDefaults.buttonFill()
            } else {
                MiuixTheme.colorScheme.surfaceContainerHigh
            },
            subtitle = subtitle ?: "",
            scrollBehavior = miuixScrollBehavior,
            // Keep unsupported/disabled-blur runtimes on solid, readable surfaces; only shader
            // capable devices with blur enabled draw the Glass bloom stroke and floating shadow.
            stroke = if (blurActive) GlassTopAppBarDefaults.buttonStroke() else null,
            buttonShadow = if (blurActive) GlassShadows.Regular else null,
            navigationIcon = { AppBarNavIcon(onBack, onClose) },
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
            navigationIcon = { AppBarNavIcon(onBack, onClose) },
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = { AppBarNavIcon(onBack, onClose) },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (selectionMode) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.background
                },
            ),
        )
    }
}

/**
 * 顶栏左侧图标：**关闭（多选态）优先于返回**，两者都为 null 就不渲染。
 *
 * 三个分支都无条件传这个 lambda：两主题 `navigationIcon` 的默认值本来就是空 lambda，
 * 渲染一个「什么都不组合」的槽位与不传等价，所以不会给没有左侧图标的页面凭空留出位置。
 * 字形与描述照 [AppBarIconButton] 的规矩按语义传入（`Close` / `ArrowBack`+`Back`）。
 */
@Composable
private fun AppBarNavIcon(onBack: (() -> Unit)?, onClose: (() -> Unit)?) {
    when {
        onClose != null -> AppBarIconButton(onClose, "退出多选", Icons.Rounded.Close, MiuixIcons.Glass.Close)
        onBack != null -> AppBarIconButton(
            onBack,
            "返回",
            Icons.AutoMirrored.Rounded.ArrowBack,
            MiuixIcons.Glass.Back,
        )
    }
}

/**
 * 屏幕骨架：顶栏 + 撤销条宿主 + 内容区。
 *
 * MD3 侧保留原来的 `containerColor = background`（顶栏与内容区同底色，否则会出现色差）；
 * Miuix 侧用它自己的默认底色（`colorScheme.surface`），与合并前一致。
 *
 * `ManageScreens.kt` / `MiuixManageScreens.kt` 里那两份私有的 `*ManageScaffold` 已由本组件取代，
 * 并在 2026-09-16 第 6 对（管理页合并）时随之删除 —— 当初留的「等管理页那一对合并时删掉」已兑现。
 *
 * **弹窗要放在 [content] 里面**。Miuix Glass 弹窗/下拉菜单使用根 Miuix Scaffold 的 popup host；
 * 这个页面级 Scaffold 显式设 `popupHost = {}`，避免把浮层画进页面 backdrop 后自采样闪退。调用方仍在
 * 此 content lambda 内无条件组合对话框、靠 `show` 控制动画生命周期；MD3 的 `AlertDialog` 放里放外
 * 都是独立窗口，统一放里面即可。
 *
 * **滚动行为由每页的真实滚动所有者提供**：MD3 首页在带副标题时继续使用本地
 * `exitUntilCollapsedScrollBehavior()`；Miuix 页面把自己的 `MiuixScrollBehavior` 同时传给顶栏和
 * 实际 LazyColumn/verticalScroll 的 `nestedScroll` modifier。Glass 顶栏另显式接收该所有者的
 * `canScrollBackward`（或 `ScrollState.value > 0`），所以短列表与恢复的滚动位置也能正确显示浮动材质；
 * 外层 `chromeScrollConnection` 仍只负责全局底栏显隐。
 *
 * **键盘避让由调用方决定**：`AppScaffold` 不无条件加 `imePadding()`（没有输入框的屏幕不需要），
 * 需要的屏幕自己传 `modifier = Modifier.imePadding()`，这样「哪一屏要避让」在屏幕文件里看得见，
 * `ImeHandlingTest` 第 1 条也仍能按屏幕文件点名（合并后一个条目就覆盖两套主题）。
 *
 * @param bottomBar 可选的页面底栏，直接交给当前主题的 Scaffold；默认不渲染。
 * @param miuixScrollBehavior 该页的 Miuix 滚动行为；null 表示不折叠。只由屏幕自己的滚动状态创建，
 *   不要把一个行为跨不同页面复用。
 * @param miuixContentScrolled 当前页列表/滚动容器是否已经可向前滚动，供 Glass 按钮材质使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    snackbar: AppSnackbarHostState? = null,
    snackbarPlacement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars,
    snackbarForm: AppSnackbarForm = AppSnackbarForm.UndoCountdown,
    snackbarModifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    miuixScrollBehavior: MiuixScrollBehaviorType? = null,
    miuixContentScrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        val pageSurfaceColor = MiuixTheme.colorScheme.surface
        val pageBackdrop = if (LocalMiuixBlurEnabled.current) {
            rememberLayerBackdrop {
                drawRect(pageSurfaceColor)
                drawContent()
            }
        } else {
            null
        }
        CompositionLocalProvider(LocalMiuixPageBackdrop provides pageBackdrop) {
            MiuixScaffold(
                modifier = modifier,
                // The outer Miuix scaffold in MainApp owns all page/root Glass popups.
                // Local root states still flow into this page scaffold; do not install a second host.
                popupHost = {},
                snackbarHost = {
                    if (snackbar != null) {
                        AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement, snackbarForm)
                    }
                },
                bottomBar = bottomBar,
                topBar = {
                    AppTopBar(
                        title = title,
                        onBack = onBack,
                        onClose = onClose,
                        selectionMode = selectionMode,
                        subtitle = subtitle,
                        miuixScrollBehavior = miuixScrollBehavior,
                        miuixContentScrolled = miuixContentScrolled,
                        actions = actions,
                    )
                },
                content = { innerPadding ->
                    if (pageBackdrop != null) {
                        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
                            content(innerPadding)
                        }
                    } else {
                        content(innerPadding)
                    }
                },
            )
        }
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
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement, snackbarForm)
            },
            bottomBar = bottomBar,
            topBar = {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    onClose = onClose,
                    selectionMode = selectionMode,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    actions = actions,
                )
            },
            content = content,
        )
    }
}
