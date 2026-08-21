package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult as MiuixSnackbarResult
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
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
import com.agon.app.ui.screens.FoodListScreen
import com.agon.app.ui.screens.HomeScreen
import com.agon.app.ui.screens.MiuixArchiveScreen
import com.agon.app.ui.screens.MiuixCategoryManageScreen
import com.agon.app.ui.screens.MiuixConsumptionLogScreen
import com.agon.app.ui.screens.MiuixFoodDetailScreen
import com.agon.app.ui.screens.MiuixFoodListScreen
import com.agon.app.ui.screens.MiuixHomeScreen
import com.agon.app.ui.screens.MiuixLocationManageScreen
import com.agon.app.ui.screens.MiuixSettingsScreen
import com.agon.app.ui.screens.MiuixStatsScreen
import com.agon.app.ui.screens.MiuixThresholdManageScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.screens.StatsScreen
import com.agon.app.ui.components.SwipeDismissSnackbarHost
import com.agon.app.ui.components.showUndoSnackbar
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MiuixRootTheme
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.MotionSpring
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

// MD3 motion easing tokens 统一从 ui/theme/Motion.kt 引用
private val EmphasizedDecelerate = MotionEasing.EmphasizedDecelerate
private val EmphasizedAccelerate = MotionEasing.EmphasizedAccelerate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 持持 SplashScreen 直到 DataStore 首次发出数据，
        // 避免启动时先用默认绿主题/空内容渲染一帧再闪成真实内容
        var contentReady = false
        splash.setKeepOnScreenCondition { !contentReady }
        setContent {
            val viewModel: AppViewModel = viewModel()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
            val paletteName by viewModel.palette.collectAsStateWithLifecycle()
            val themeStyleName by viewModel.themeStyle.collectAsStateWithLifecycle()
            LaunchedEffect(ready) { if (ready) contentReady = true }
            if (!ready) return@setContent
            val darkTheme = when (darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val themeStyle = ThemeStyle.fromName(themeStyleName)
            CompositionLocalProvider(LocalThemeStyle provides themeStyle) {
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

private data class TabSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

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
            .padding(bottom = snackbarOffset),
    ) {
        if (isMiuix) {
            MiuixSnackbarHost(miuixSnackbarHostState)
        } else {
            SwipeDismissSnackbarHost(snackbarHostState)
        }
    }
    }
}

/** 多选批量操作栏：取消 + 归档 N 项（多选时替换底部导航，MD3 / MIUIX 两套按钮，跟随悬浮/非悬浮）。 */
@Composable
private fun BatchActionBar(
    count: Int,
    isMiuix: Boolean,
    floating: Boolean,
    onCancel: () -> Unit,
    onArchive: () -> Unit,
) {
    if (floating) {
        // 悬浮：仅按钮本身悬浮（无外层胶囊背景），「取消」文字 + 「归档」实心胶囊独立悬浮。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatchCancelButton(isMiuix = isMiuix, onClick = onCancel)
                BatchArchiveButton(isMiuix = isMiuix, count = count, onClick = onArchive)
            }
        }
    } else {
        // 非悬浮：全宽常驻操作栏
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatchCancelButton(isMiuix = isMiuix, onClick = onCancel, modifier = Modifier.weight(1f))
                BatchArchiveButton(isMiuix = isMiuix, count = count, onClick = onArchive, modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun BatchCancelButton(
    isMiuix: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isMiuix) {
        // Miuix 标准文字按钮
        MiuixTextButton(
            text = "取消",
            onClick = onClick,
            modifier = modifier,
            minHeight = 48.dp,
        )
    } else {
        // MD3 实心胶囊（中性色），与归档实心胶囊视觉统一
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("取消")
        }
    }
}

@Composable
private fun BatchArchiveButton(isMiuix: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (isMiuix) {
        // Miuix 标准实心按钮（默认 16dp 圆角）
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            minHeight = 48.dp,
            colors = MiuixButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
                contentColor = MiuixTheme.colorScheme.onError,
            ),
        ) {
            MiuixIcon(
                Icons.Rounded.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onError,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "归档 $count 项",
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onError,
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("归档 $count 项", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 底部导航共享 Tab 定义（MD3 / MIUIX 共用标签；图标按主题分流）。顺序即 Pager 页序。 */
private val MainTabs = listOf(
    TabSpec("home", "首页", Icons.Rounded.Home),
    TabSpec("list", "食品", Icons.AutoMirrored.Rounded.ListAlt),
    TabSpec("stats", "统计", Icons.Rounded.PieChart),
    TabSpec("settings", "设置", Icons.Rounded.Settings),
)

/** MIUIX 主题的底部导航图标（MiuixIcons.Regular；统计用 GridView 替代无对应的 PieChart）。 */
private val MiuixMainTabs = listOf(
    TabSpec("home", "首页", MiuixIcons.Home),
    TabSpec("list", "食品", MiuixIcons.ListView),
    TabSpec("stats", "统计", MiuixIcons.GridView),
    TabSpec("settings", "设置", MiuixIcons.Settings),
)

/**
 * 四个底栏 Tab 用 HorizontalPager 按索引左右连滑。
 * Miuix-nav 的 MultiPush 是堆栈推进（中间页被盖住），Tab 切换要露出中间页，故用 Pager。
 * 关闭手势翻页，避免和列表里横向 Chip 抢手势；点击底栏 / 首页卡片驱动 animateScrollToPage。
 */
@Composable
private fun MainTabsPager(
    viewModel: AppViewModel,
    pagerState: PagerState,
    listFilter: String?,
    onOpenList: (String?) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenConsumption: () -> Unit,
    onOpenThresholds: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenLocations: () -> Unit,
    onBackToHome: () -> Unit,
) {
    val isMiuix = LocalThemeStyle.current == ThemeStyle.MIUIX
    BackHandler(enabled = pagerState.currentPage != 0) { onBackToHome() }
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        beyondViewportPageCount = 3,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> if (isMiuix) {
                MiuixHomeScreen(
                    viewModel = viewModel,
                    onOpenList = onOpenList,
                    onOpenItem = onOpenItem,
                )
            } else {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenList = onOpenList,
                    onOpenItem = onOpenItem,
                )
            }
            1 -> if (isMiuix) {
                MiuixFoodListScreen(
                    viewModel = viewModel,
                    initialFilter = listFilter,
                    onOpenItem = onOpenItem,
                    onOpenArchive = onOpenArchive,
                )
            } else {
                FoodListScreen(
                    viewModel = viewModel,
                    initialFilter = listFilter,
                    onOpenItem = onOpenItem,
                    onOpenArchive = onOpenArchive,
                )
            }
            2 -> if (isMiuix) {
                MiuixStatsScreen(
                    viewModel = viewModel,
                    onOpenItem = onOpenItem,
                    onOpenConsumption = onOpenConsumption,
                )
            } else {
                StatsScreen(
                    viewModel = viewModel,
                    onOpenItem = onOpenItem,
                    onOpenConsumption = onOpenConsumption,
                )
            }
            else -> if (isMiuix) {
                MiuixSettingsScreen(
                    viewModel = viewModel,
                    onOpenArchive = onOpenArchive,
                    onOpenThresholds = onOpenThresholds,
                    onOpenCategories = onOpenCategories,
                    onOpenLocations = onOpenLocations,
                )
            } else {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenArchive = onOpenArchive,
                    onOpenThresholds = onOpenThresholds,
                    onOpenCategories = onOpenCategories,
                    onOpenLocations = onOpenLocations,
                )
            }
        }
    }
}

/** MIUIX：全宽图标+文字底栏（HyperOS 风格）。 */
@Composable
private fun MiuixBottomNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
    MiuixNavigationBar {
        MiuixMainTabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            MiuixNavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(index) },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

/** MIUIX：居中悬浮底栏（仅图标）。 */
@Composable
private fun MiuixFloatingNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
    MiuixFloatingNavigationBar {
        MiuixMainTabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            MiuixFloatingNavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(index) },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

/** Material 3：全宽图标+文字底栏（非悬浮态）。 */
@Composable
private fun Md3BottomNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        MainTabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

/**
 * 居中悬浮胶囊导航栏（带滑动指示器）：
 * 等宽槽位 + 背后一枚 primary 胶囊指示器，位置跟随 Pager 连续偏移。
 * MD3 导航规范：所有 Tab 常显标签（always show labels），图标上、标签下竖排；
 * 槽位 48dp 高满足最小触摸目标；选中/未选中颜色用 MD3 standard 缓动渐变。
 */
@Composable
private fun FloatingPillNav(pagePosition: Float, onSelect: (Int) -> Unit) {
    val tabs = MainTabs
    val slotWidth = 76.dp
    val slotHeight = 48.dp
    val selectedIndex = pagePosition.roundToInt().coerceIn(0, tabs.lastIndex)
    val indicatorOffset = slotWidth * pagePosition

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp, top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .size(width = slotWidth, height = slotHeight)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val contentColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            animationSpec = tween(250, easing = MotionEasing.Standard),
                            label = "navContent$index",
                        )
                        Column(
                            modifier = Modifier
                                .size(width = slotWidth, height = slotHeight)
                                .clip(RoundedCornerShape(50))
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) onSelect(index)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
