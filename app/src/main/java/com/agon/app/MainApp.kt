package com.agon.app

// App 外壳：状态与副作用（backStack / pagerState / 多选 / Snackbar 收集 / nestedScroll）+
// Scaffold（底栏槽位、FAB、内容槽位一次 AppNavHost 调用）+ Snackbar 覆盖层。
//
// ⚠️ 硬约定（docs/ARCHITECTURE.md §3、devlog/2026-09-15.md）：
//   · rememberNavBackStack **只在这一处**（导航状态源唯一）；全 App 唯一的 NavDisplay 在
//     AppNavGraph.kt 的 AppNavHost 里，由本文件的内容槽位调用一次；二级页一律走回调
//     （navigate / popRoute），不得把 backStack 再往下传给屏幕；
//   · 外层 Scaffold 的 contentPadding **刻意不消费**（底栏是浮层，inset 由各屏自己的 Scaffold +
//     LazyColumn.contentPadding 处理），故函数上有 @Suppress("UnusedMaterial3ScaffoldPaddingParameter")，
//     该注解与其解释注释必须跟着 MainApp 一起搬；
//   · EmphasizedDecelerate / EmphasizedAccelerate 是 MD3 motion token（来自 ui/theme/Motion.kt），
//     目前只有 FAB 动画用 → 保持 private。
//
// 2026-09-16 由 MainActivity.kt 拆分而来（纯搬运：除 private→internal 外，签名与实现逐字节未改）。
// 同日拆分 ④：路由表（NavDisplay + 8 个 entry）搬去 AppNavGraph.kt，弹窗搬去 AppDialogs.kt
// （2026-09-17 再搬去 ui/components/app/AppBatchMoveDialog.kt，故本文件多了那条 import），
// 批量操作栏搬去 BatchBars.kt，底栏搬去 NavChrome.kt —— 本文件只剩 App 外壳本身。

import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.data.ArchiveReason
import com.agon.app.ui.navigation.AppRoute
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.components.app.BatchMoveLocationDialog
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.MotionSpring
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import com.agon.app.viewmodel.UiEvent
import kotlin.math.abs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

// MD3 motion easing tokens 统一从 ui/theme/Motion.kt 引用
private val EmphasizedDecelerate = MotionEasing.EmphasizedDecelerate

private val EmphasizedAccelerate = MotionEasing.EmphasizedAccelerate

// 外层 Scaffold 的 contentPadding 由各屏自行处理，见下方 content lambda 处注释。
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainApp(viewModel: AppViewModel) {
    val backStack = rememberNavBackStack<AppRoute>(AppRoute.Main)
    val currentRoute = backStack.lastOrNull()
    val onTabs = currentRoute is AppRoute.Main
    fun navigate(route: AppRoute) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
    }
    fun popRoute() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    val pagerState = rememberPagerState(pageCount = { MainTabs.size })
    val selectedTabIndex = pagerState.currentPage
    var listFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // 下滑隐藏底栏与 FAB，上滑恢复：监听子屏幕列表的 nested scroll 事件
    var scrollChromeVisible by remember { mutableStateOf(true) }
    val chromeScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) scrollChromeVisible = false
                else if (available.y > 8f) scrollChromeVisible = true
                return Offset.Zero
            }
        }
    }
    // 切换 Tab / 进出二级页时恢复底栏
    LaunchedEffect(currentRoute, selectedTabIndex) { scrollChromeVisible = true }
    // 离开食品列表页时清除多选，避免批量操作栏残留到其他页面
    LaunchedEffect(onTabs, selectedTabIndex) {
        if (!onTabs || selectedTabIndex != 1) viewModel.clearSelection()
    }
    val showChrome = onTabs && scrollChromeVisible
    // Snackbar 展示“撤销”期间隐藏 FAB，避免挡住撤销按钮
    val fabSuppressed by viewModel.fabSuppressed.collectAsStateWithLifecycle()
    // 悬浮导航开关 + 主题风格：决定底栏与 FAB 用哪套组件
    val floatingNav by viewModel.floatingNav.collectAsStateWithLifecycle()
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    // 多选模式：选中状态提升到 VM，多选时用批量操作栏替换底部导航
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionMode = selectedIds.isNotEmpty()
    var showMoveLocationDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val miuixSnackbarHostState = remember { MiuixSnackbarHostState() }

    // 一次性 UI 事件（路线图 #4a）：主壳覆盖层这一路。此前是两条 LaunchedEffect 各收一个
    // 可空 StateFlow 并手工 consume；现在收一条 Channel —— 接收即出队，没有 consume 可调，
    // 也不会向新订阅者重放最后一个值。分流成三条队列的理由见 viewmodel/UiEvent.kt 的类注释。
    // 仍用 LaunchedEffect(Unit)+collect 而非 LaunchedEffect(key)：key 变化会取消协程、中断
    // showSnackbar（MD3 撤销不出现的根因）。
    val currentIsMiuix by rememberUpdatedState(isMiuix)
    LaunchedEffect(Unit) {
        viewModel.appShellUiEvents.collect { event ->
            when (event) {
                // 列表页步进器减号触发的「撤销消耗」：弹撤销 Snackbar（MD3 / MIUIX 两套样式）。
                is UiEvent.UndoConsumption -> {
                    val undone = if (currentIsMiuix) {
                        miuixSnackbarHostState.showUndoSnackbar("已减少一件并计入消耗") ==
                            MiuixSnackbarResult.ActionPerformed
                    } else {
                        snackbarHostState.showUndoSnackbar("已减少一件并计入消耗") ==
                            SnackbarResult.ActionPerformed
                    }
                    if (undone) {
                        viewModel.undoConsumption(event)
                    }
                }
                // 列表页搜索结果中恢复归档：弹撤销 Snackbar
                is UiEvent.UndoRestoreArchived -> {
                    val msg = if (event.merged) "库存中已有同批次「${event.item.name}」，已合并数量"
                              else "已恢复「${event.item.name}」到零食柜"
                    val undone = if (currentIsMiuix) {
                        miuixSnackbarHostState.showUndoSnackbar(msg) ==
                            MiuixSnackbarResult.ActionPerformed
                    } else {
                        snackbarHostState.showUndoSnackbar(msg) ==
                            SnackbarResult.ActionPerformed
                    }
                    if (undone) {
                        viewModel.archiveBatch(setOf(event.item.id), event.reason)
                    }
                }
                // 另两类事件走各自宿主的队列，不会流到这里；when 对 sealed 必须穷尽，故显式列出。
                is UiEvent.UndoDeleteConsumption, is UiEvent.Notice -> Unit
            }
        }
    }

    fun selectTab(index: Int) {
        if (index == pagerState.currentPage) return
        scope.launch {
            val distance = abs(index - pagerState.currentPage)
            pagerState.animateScrollToPage(
                index,
                animationSpec = MotionSpring.page<Float>(distance),
            )
        }
    }

    fun openList(filter: String?) {
        listFilter = filter
        selectTab(1)
    }

    fun archiveSelected() {
        val ids = selectedIds
        if (ids.isEmpty()) return
        viewModel.clearSelection()
        viewModel.archiveBatch(ids, ArchiveReason.DELETED)
        scope.launch {
            viewModel.setFabSuppressed(true)
            try {
                val undone = if (isMiuix) {
                    miuixSnackbarHostState.showUndoSnackbar("已将 ${ids.size} 件食品移入归档") ==
                        MiuixSnackbarResult.ActionPerformed
                } else {
                    snackbarHostState.showUndoSnackbar("已将 ${ids.size} 件食品移入归档") ==
                        SnackbarResult.ActionPerformed
                }
                if (undone) {
                    viewModel.restoreArchivedBatch(ids)
                }
            } finally {
                viewModel.setFabSuppressed(false)
            }
        }
    }

    // Snackbar 底部偏移：跟随底栏可见状态平滑过渡（不瞬移）。
    val snackbarOffset by animateDpAsState(
        targetValue = if (showChrome) 84.dp else 8.dp,
        animationSpec = tween(250, easing = MotionEasing.Standard),
        label = "snackbarOffset",
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box {
                AnimatedVisibility(
                    visible = selectionMode,
                    enter = slideInVertically(MotionSpring.expand<IntOffset>()) { it } + fadeIn(MotionSpring.expand<Float>()),
                    exit = slideOutVertically(MotionSpring.collapse<IntOffset>()) { it } + fadeOut(MotionSpring.collapse<Float>()),
                ) {
                    BatchActionBar(
                        count = selectedIds.size,
                        isMiuix = isMiuix,
                        floating = floatingNav,
                        onCancel = { viewModel.clearSelection() },
                        onMoveLocation = { showMoveLocationDialog = true },
                        onArchive = { archiveSelected() },
                    )
                }
                AnimatedVisibility(
                    visible = !selectionMode && showChrome,
                    enter = slideInVertically(MotionSpring.expand<IntOffset>()) { it } + fadeIn(MotionSpring.expand<Float>()),
                    exit = slideOutVertically(MotionSpring.collapse<IntOffset>()) { it } + fadeOut(MotionSpring.collapse<Float>()),
                ) {
                    when {
                        isMiuix && floatingNav -> MiuixFloatingNav(selectedTabIndex, ::selectTab)
                        isMiuix -> MiuixBottomNav(selectedTabIndex, ::selectTab)
                        floatingNav -> FloatingPillNav(
                            pagePosition = selectedTabIndex + pagerState.currentPageOffsetFraction,
                            onSelect = ::selectTab,
                        )
                        else -> Md3BottomNav(selectedTabIndex, ::selectTab)
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showChrome && !fabSuppressed && !selectionMode && selectedTabIndex != 2 && selectedTabIndex != 3,
                enter = scaleIn(tween(250, easing = EmphasizedDecelerate)) +
                    fadeIn(tween(250, easing = EmphasizedDecelerate)) +
                    slideInVertically(tween(250, easing = EmphasizedDecelerate)) { it / 2 },
                exit = scaleOut(tween(200, easing = EmphasizedAccelerate)) +
                    fadeOut(tween(200, easing = EmphasizedAccelerate)) +
                    slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it / 2 },
            ) {
                if (isMiuix) {
                    MiuixFloatingActionButton(onClick = { navigate(AppRoute.Edit()) }) {
                        MiuixIcon(Icons.Rounded.Add, contentDescription = "添加食品")
                    }
                } else {
                    FloatingActionButton(
                        onClick = { navigate(AppRoute.Edit()) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加食品")
                    }
                }
            }
        },
    ) { _ ->
        // 刻意不消费外层 Scaffold 的 contentPadding：底栏是浮层
        // （AnimatedVisibility 显隐，还可能是悬浮胶囊导航），inset 由各屏自己的
        // Scaffold + LazyColumn.contentPadding 处理（见 HomeScreen 的
        // calculateBottomPadding() + 96.dp）。在此再消费一次会把内容重复下推。
        // 对应函数上的 @Suppress("UnusedMaterial3ScaffoldPaddingParameter")。
        AppNavHost(
            backStack = backStack,
            chromeScrollConnection = chromeScrollConnection,
            viewModel = viewModel,
            pagerState = pagerState,
            listFilter = listFilter,
            // 4 个导航动作压成一个持有者（AppNavHost 形参 9 → 6）；FAB / 底栏 / MiuixFloatingNav
            // 仍直接用这几个局部函数，未受影响。
            callbacks = AppNavCallbacks(
                navigate = ::navigate,
                popRoute = ::popRoute,
                openList = ::openList,
                selectTab = ::selectTab,
            ),
        )
    }

    // Snackbar 覆盖层（自定义定位：底栏可见→悬浮导航上方，隐藏→贴底，平滑过渡不瞬移）
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            // 键盘打开时也要能看见/点到「撤销」：两段式书写 = max(导航栏, 键盘)，
            // 内层只补差额，不会叠加成一条大空隙（等价于旧的 navigationBarsWithImePadding）。
            .imePadding()
            .padding(bottom = snackbarOffset),
    ) {
        if (isMiuix) {
            MiuixSnackbarHost(miuixSnackbarHostState)
        } else {
            SwipeDismissSnackbarHost(snackbarHostState)
        }
    }

    // ---- 批量修改存放位置弹窗（实现在 ui/components/app/AppBatchMoveDialog.kt）----
    BatchMoveLocationDialog(
        show = showMoveLocationDialog,
        locationsFlow = viewModel.locations,
        selectedCount = selectedIds.size,
        onDismiss = { showMoveLocationDialog = false },
        // VM 与 Snackbar 逻辑回到调用方（2026-09-17 按该组件文件头既定方案收窄，形参 8 → 5）。
        // 顺序与收窄前逐句一致：先记住件数（clearSelection 之后 selectedIds 就空了）→ 改数据 → 清选择
        // → 关弹窗 → 弹提示；提示文案两主题本来就相同，只有宿主不同。
        onConfirm = { target ->
            val count = selectedIds.size
            viewModel.updateLocationBatch(selectedIds, target)
            viewModel.clearSelection()
            showMoveLocationDialog = false
            scope.launch {
                val message = "已将 $count 件食品移动到「$target」"
                if (isMiuix) {
                    miuixSnackbarHostState.showSnackbar(message)
                } else {
                    snackbarHostState.showSnackbar(message)
                }
            }
        },
    )
    }
}
