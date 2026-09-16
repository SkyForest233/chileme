package com.agon.app

// App 入口：Activity 本体（深浅色 / 主题风格分流、启动放行超时、splash、CompositionLocalProvider）。
//
// 原 MainActivity.kt 有 1,123 行，2026-09-16 起按职责分步拆到同包（com.agon.app）的兄弟文件：
//   MainApp.kt（App 外壳：状态 + Scaffold + Snackbar 覆盖层）· AppNavGraph.kt（路由入口）
//   AppDialogs.kt（弹窗）· BatchBars.kt（多选批量操作栏）· NavChrome.kt（底栏与 Tab Pager）
// 跨文件复用的顶层声明由 private 放宽为 internal —— Kotlin 顶层 private 是**文件级**作用域，
// 不放宽就看不见；internal 只是模块内可见（app 模块没有第二个消费方，R8 照常裁剪），不是公开 API。
// 代价：detekt 的 UnusedPrivateMember 从此不再覆盖它们。理由与取舍见 devlog/2026-09-16.md「🧭 拆分路线图」。
//
// 硬约定（拆分不得破坏）：rememberNavBackStack + NavDisplay 只在 MainApp 一处；二级页一律走回调
// （navigate / onTabs），**不得**把 backStack 往下传给屏幕；外层 Scaffold 的 contentPadding 刻意不消费。

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import com.agon.app.ui.components.MiuixDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.data.ArchiveReason
import com.agon.app.ui.navigation.AppRoute
import com.agon.app.ui.screens.ArchiveScreen
import com.agon.app.ui.screens.CategoryManageScreen
import com.agon.app.ui.screens.ConsumptionLogScreen
import com.agon.app.ui.screens.EditFoodScreen
import com.agon.app.ui.screens.LocationManageScreen
import com.agon.app.ui.screens.ThresholdManageScreen
import com.agon.app.ui.screens.FoodDetailScreen
import com.agon.app.ui.screens.MiuixArchiveScreen
import com.agon.app.ui.screens.MiuixCategoryManageScreen
import com.agon.app.ui.screens.MiuixConsumptionLogScreen
import com.agon.app.ui.screens.MiuixFoodDetailScreen
import com.agon.app.ui.screens.MiuixLocationManageScreen
import com.agon.app.ui.screens.MiuixThresholdManageScreen
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.LocalToday
import com.agon.app.ui.theme.MiuixRootTheme
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.MotionSpring
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import kotlin.math.abs
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 启动放行超时：`ready`（DataStore 首发）在此时间内未达成也强制渲染首帧。
 * 见 `onCreate` 中 `contentReady` 的注释——宁可闪一帧，也不能变砖。
 */
private const val READY_TIMEOUT_MS = 3_000L

// MD3 motion easing tokens 统一从 ui/theme/Motion.kt 引用
private val EmphasizedDecelerate = MotionEasing.EmphasizedDecelerate

private val EmphasizedAccelerate = MotionEasing.EmphasizedAccelerate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 持住 SplashScreen 直到 DataStore 首次发出数据，
        // 避免启动时先用默认绿主题/空内容渲染一帧再闪成真实内容。
        //
        // 用 MutableState 而非普通 var：composition 里读它，超时兜底翻转后要触发重组，
        // 否则 `if (!contentReady) return@setContent` 会一直停在空白帧。
        var contentReady by mutableStateOf(false)
        splash.setKeepOnScreenCondition { !contentReady }
        setContent {
            val viewModel: AppViewModel = viewModel()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
            val paletteName by viewModel.palette.collectAsStateWithLifecycle()
            val themeStyleName by viewModel.themeStyle.collectAsStateWithLifecycle()
            // 跨零点刷新：每次回到前台用最新日期提供 LocalToday。
            // 日期未变（同日多次 resume）时值相等，不会触发重组；跨过午夜则值变化，
            // 所有读取 LocalToday 的屏幕（剩余天数/状态/新鲜度）随之刷新。
            var today by remember { mutableStateOf(LocalDate.now()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
            // 周期性检查：每 30 秒比对一次当前日期，变了就更新 today。
            // 覆盖所有「日期变化」场景（自然跨午夜、手动拨时钟前进/后退、时区变化），
            // 比「一次性睡到下一个午夜」更稳健——后者在时钟被改动后会失效。
            // 与 ON_RESUME 互补（后台跨午夜由后者即时兜底，这里兜前台）。
            LaunchedEffect(Unit) {
                while (true) {
                    val now = LocalDate.now()
                    if (now != today) today = now
                    delay(30_000)
                }
            }
            // 放行条件 = ready（正常路径）或超时兜底。
            // 兜底必不可少：异常/读阻塞会让 ready 永不发射，没有超时就是
            // 「启动画面永久停留、只能杀进程」——比多显示一帧默认主题糟糕得多。
            LaunchedEffect(ready) { if (ready) contentReady = true }
            LaunchedEffect(Unit) {
                delay(READY_TIMEOUT_MS)
                contentReady = true
            }
            if (!contentReady) return@setContent
            val darkTheme = when (darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val themeStyle = ThemeStyle.fromName(themeStyleName)
            CompositionLocalProvider(
                LocalThemeStyle provides themeStyle,
                LocalToday provides today,
            ) {
                if (themeStyle == ThemeStyle.MIUIX) {
                    MiuixRootTheme(darkMode = darkMode, dynamicColor = dynamicColor) {
                        MainApp(viewModel)
                    }
                } else {
                    AgonAppTheme(
                        darkTheme = darkTheme,
                        dynamicColor = dynamicColor,
                        palette = AppPalette.fromName(paletteName),
                    ) {
                        MainApp(viewModel)
                    }
                }
            }
        }
    }
}

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

    // 列表页步进器减号触发的「撤销消耗」：弹撤销 Snackbar（MD3 / MIUIX 两套样式）。
    // 用 LaunchedEffect(Unit)+collect 而非 LaunchedEffect(key)：consume 会改变 key，导致协程被取消、
    // showSnackbar 中断（MD3 撤销不出现的根因）。
    val currentIsMiuix by rememberUpdatedState(isMiuix)
    LaunchedEffect(Unit) {
        viewModel.undoRequest.filterNotNull().collect { request ->
            viewModel.consumeUndoRequest()
            val undone = if (currentIsMiuix) {
                miuixSnackbarHostState.showUndoSnackbar("已减少一件并计入消耗") ==
                    MiuixSnackbarResult.ActionPerformed
            } else {
                snackbarHostState.showUndoSnackbar("已减少一件并计入消耗") ==
                    SnackbarResult.ActionPerformed
            }
            if (undone) {
                viewModel.undoConsumption(request)
            }
        }
    }

    // 列表页搜索结果中恢复归档：弹撤销 Snackbar
    LaunchedEffect(Unit) {
        viewModel.restoredArchivedEvent.filterNotNull().collect { event ->
            viewModel.consumeRestoredArchivedEvent()
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
        // 大屏/折叠屏适配：内容最大宽 840dp 居中（MD3 大屏可读性要求），
        // 手机上无变化；背景由外层 Scaffold 统一铺满。
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val cornerRadius = rememberNavSystemCornerRadius()
            NavDisplay(
                backStack = backStack,
                onBack = { popRoute() },
                // 澎湃记 / HyperOS 设置二级页同款：全宽跟手滑出 + 下层 1/4 视差。
                // 圆角与 dim 在 NavDisplayEffects；不在转场里缩放到中心。
                transition = NavTransitions.MiuixDefault,
                effects = NavDisplayEffects(
                    enableCornerClip = true,
                    cornerClipRadius = cornerRadius,
                    // Leading：全宽滑只圆露出的那条边；All 是给缩放卡片用的。
                    cornerClipMode = NavCornerClipMode.Leading,
                    dimAmount = 0.5f,
                ),
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .nestedScroll(chromeScrollConnection),
            ) {
                entry<AppRoute.Main> {
                    MainTabsPager(
                        viewModel = viewModel,
                        pagerState = pagerState,
                        listFilter = listFilter,
                        onOpenList = { openList(it) },
                        onOpenItem = { navigate(AppRoute.Detail(it)) },
                        onOpenArchive = { navigate(AppRoute.Archive) },
                        onOpenConsumption = { navigate(AppRoute.Consumption) },
                        onOpenThresholds = { navigate(AppRoute.ManageThresholds) },
                        onOpenCategories = { navigate(AppRoute.ManageCategories) },
                        onOpenLocations = { navigate(AppRoute.ManageLocations) },
                        onBackToHome = { selectTab(0) },
                    )
                }
                entry<AppRoute.Consumption> {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixConsumptionLogScreen(viewModel = viewModel, onBack = { popRoute() })
                    } else {
                        ConsumptionLogScreen(viewModel = viewModel, onBack = { popRoute() })
                    }
                }
                entry<AppRoute.Detail> { route ->
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixFoodDetailScreen(
                            viewModel = viewModel,
                            itemId = route.id,
                            onEdit = { navigate(AppRoute.Edit(it)) },
                            onBack = { popRoute() },
                        )
                    } else {
                        FoodDetailScreen(
                            viewModel = viewModel,
                            itemId = route.id,
                            onEdit = { navigate(AppRoute.Edit(it)) },
                            onBack = { popRoute() },
                        )
                    }
                }
                entry<AppRoute.Edit> { route ->
                    EditFoodScreen(
                        viewModel = viewModel,
                        editId = route.id,
                        onBack = { popRoute() },
                    )
                }
                entry<AppRoute.Archive> {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixArchiveScreen(viewModel = viewModel, onBack = { popRoute() })
                    } else {
                        ArchiveScreen(viewModel = viewModel, onBack = { popRoute() })
                    }
                }
                entry<AppRoute.ManageThresholds> {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixThresholdManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    } else {
                        ThresholdManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    }
                }
                entry<AppRoute.ManageCategories> {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixCategoryManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    } else {
                        CategoryManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    }
                }
                entry<AppRoute.ManageLocations> {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixLocationManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    } else {
                        LocationManageScreen(viewModel = viewModel, onBack = { popRoute() })
                    }
                }
            }
        }
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

    // ---- 批量修改存放位置弹窗 ----
    if (showMoveLocationDialog) {
        val locations by viewModel.locations.collectAsStateWithLifecycle()
        var selectedLocation by remember { mutableStateOf(locations.firstOrNull() ?: "零食柜") }
        var customLocation by remember { mutableStateOf("") }

        if (isMiuix) {
            MiuixDialog(
                title = "批量修改存放位置",
                summary = "已选 ${selectedIds.size} 件食品，请选择目标位置：",
                show = showMoveLocationDialog,
                onDismissRequest = { showMoveLocationDialog = false },
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(locations) { loc ->
                            FilterChip(
                                selected = selectedLocation == loc && customLocation.isBlank(),
                                onClick = {
                                    selectedLocation = loc
                                    customLocation = ""
                                },
                                label = { Text(loc, style = MiuixTheme.textStyles.body2) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MiuixTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MiuixTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customLocation,
                        onValueChange = { customLocation = it },
                        label = { Text("或输入新位置（如：书房）") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MiuixTextButton(
                            text = "取消",
                            onClick = { showMoveLocationDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        MiuixButton(
                            onClick = {
                                val target = customLocation.trim().ifBlank { selectedLocation.trim() }
                                val count = selectedIds.size
                                if (target.isNotBlank()) {
                                    viewModel.updateLocationBatch(selectedIds, target)
                                    viewModel.clearSelection()
                                    showMoveLocationDialog = false
                                    scope.launch {
                                        miuixSnackbarHostState.showSnackbar("已将 $count 件食品移动到「$target」")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("确定移动", fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showMoveLocationDialog = false },
                // 键盘避让（2026-09-16 补，与 SettingsScreen 坚果云弹窗 / ManageScreens 两处一致）：
                // MD3 弹窗是独立浮动窗口，默认 DialogProperties（decorFitsSystemWindows = true）不会把
                // IME inset 透给内容 —— 下面「或输入新位置」这个输入框弹出键盘时，「确定移动」按钮会被盖住。
                // 关掉 decorFits 拿到 inset，再由 imePadding 把弹窗整体上移到键盘之上。
                // Miuix 分支不需要：WindowDialog 的 DialogContent 由库自理 IME（见 MiuixDialog 的 KDoc）。
                properties = DialogProperties(decorFitsSystemWindows = false),
                modifier = Modifier.imePadding(),
                title = { Text("批量修改存放位置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "已选 ${selectedIds.size} 件食品，请选择目标位置：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(locations) { loc ->
                                FilterChip(
                                    selected = selectedLocation == loc && customLocation.isBlank(),
                                    onClick = {
                                        selectedLocation = loc
                                        customLocation = ""
                                    },
                                    label = { Text(loc) },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = customLocation,
                            onValueChange = { customLocation = it },
                            label = { Text("或输入新位置（如：书房）") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = customLocation.trim().ifBlank { selectedLocation.trim() }
                            val count = selectedIds.size
                            if (target.isNotBlank()) {
                                viewModel.updateLocationBatch(selectedIds, target)
                                viewModel.clearSelection()
                                showMoveLocationDialog = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("已将 $count 件食品移动到「$target」")
                                }
                            }
                        },
                    ) {
                        Text("确定移动", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMoveLocationDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
    }
}
