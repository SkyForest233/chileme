package com.agon.app.ui.components.app

// App 级页面骨架：Scaffold + 顶栏。09-19 #11b ① 已把撤销条宿主拆去 AppSnackbar.kt。
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
 * @param onClose 多选态左侧的「退出多选」关闭键（字形 MD3 `Close` / Miuix `Close`），与 [onBack]
 *   互斥、优先级更高；都不传即无左侧图标。
 * @param selectionMode 顶栏进入多选态。**只影响 MD3 侧**：底色从 `background` 转 `surfaceContainer`
 *   （合并前就是这样）；Miuix 侧两态同色（都用库默认 `colorScheme.surface`），所以这个参数在 Miuix
 *   分支没有可见效果 —— 不是静默忽略，是原版两态本来就同色。
 * @param scrollBehavior 只由 [AppScaffold] 传，屏幕层别碰。非 null 时 MD3 侧走折叠式
 *   `LargeTopAppBar`；Miuix 侧不传（上游 `TopAppBar` 也有 `largeTitle` + `scrollBehavior` 可做折叠，
 *   但合并前的 Miuix 首页没用，替它开就是视觉改动）。
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
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixTopAppBar(
            title = title,
            subtitle = subtitle ?: "",
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
        onClose != null -> AppBarIconButton(onClose, "退出多选", Icons.Rounded.Close, MiuixIcons.Close)
        onBack != null -> AppBarIconButton(
            onBack,
            "返回",
            Icons.AutoMirrored.Rounded.ArrowBack,
            MiuixIcons.Back,
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

/** 顶栏「归档历史」入口：MD3 `History` / Miuix `Recent`，primary 色（列表页在用）。 */
@Composable
fun AppArchiveAction(onClick: () -> Unit, contentDescription: String = "归档历史") {
    AppBarIconButton(onClick, contentDescription, Icons.Rounded.History, MiuixIcons.Recent, appPrimaryColor())
}

/**
 * 顶栏「全选 / 取消全选」入口（列表页多选态），primary 色。
 *
 * ⚠️ **两主题的字形逻辑不同，合并前就是这样，照抄不统一**：MD3 恒用 `SelectAll` 字形、只换
 * contentDescription（「全选」/「取消全选」）；Miuix 在已全选时把字形换成 `Close`。
 * 与 [AppEditButton]（MD3 有铅笔图标、Miuix 没有）同一类刻意保留的不对称。
 */
@Composable
fun AppSelectAllAction(allSelected: Boolean, onClick: () -> Unit) {
    val description = if (allSelected) "取消全选" else "全选"
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixIconButton(onClick = onClick) {
            MiuixIcon(
                if (allSelected) MiuixIcons.Close else MiuixIcons.SelectAll,
                contentDescription = description,
                tint = appPrimaryColor(),
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.SelectAll, contentDescription = description, tint = appPrimaryColor())
        }
    }
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
 * `ManageScreens.kt` / `MiuixManageScreens.kt` 里那两份私有的 `*ManageScaffold` 已由本组件取代，
 * 并在 2026-09-16 第 6 对（管理页合并）时随之删除 —— 当初留的「等管理页那一对合并时删掉」已兑现。
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
    onClose: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    snackbar: AppSnackbarHostState? = null,
    snackbarPlacement: AppSnackbarPlacement = AppSnackbarPlacement.SystemBars,
    snackbarForm: AppSnackbarForm = AppSnackbarForm.UndoCountdown,
    snackbarModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
        MiuixScaffold(
            modifier = modifier,
            snackbarHost = {
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement, snackbarForm)
            },
            topBar = {
                AppTopBar(
                    title = title,
                    onBack = onBack,
                    onClose = onClose,
                    selectionMode = selectionMode,
                    subtitle = subtitle,
                    actions = actions,
                )
            },
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
                if (snackbar != null) AppSnackbarHost(snackbar, snackbarModifier, snackbarPlacement, snackbarForm)
            },
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
