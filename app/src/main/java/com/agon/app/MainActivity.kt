package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.screens.ArchiveScreen
import com.agon.app.ui.screens.CategoryManageScreen
import com.agon.app.ui.screens.EditFoodScreen
import com.agon.app.ui.screens.LocationManageScreen
import com.agon.app.ui.screens.ThresholdManageScreen
import com.agon.app.ui.screens.FoodDetailScreen
import com.agon.app.ui.screens.FoodListScreen
import com.agon.app.ui.screens.HomeScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.screens.StatsScreen
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.AppPalette
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.viewmodel.AppViewModel

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
            LaunchedEffect(ready) { if (ready) contentReady = true }
            if (!ready) return@setContent
            val darkTheme = when (darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
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

private data class TabSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabRoutes = setOf("home", "list?filter={filter}", "stats", "settings")
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
    // 切换页面时恢复显示
    LaunchedEffect(currentRoute) { scrollChromeVisible = true }
    val showChrome = currentRoute in tabRoutes && scrollChromeVisible
    // Snackbar 展示“撤销”期间隐藏 FAB，避免挡住撤销按钮
    val fabSuppressed by viewModel.fabSuppressed.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(tween(250, easing = EmphasizedDecelerate)) { it } +
                    fadeIn(tween(250, easing = EmphasizedDecelerate)),
                exit = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it } +
                    fadeOut(tween(200, easing = EmphasizedAccelerate)),
            ) {
                FloatingPillNav(navController, currentRoute)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showChrome && !fabSuppressed && currentRoute != "settings" && currentRoute != "stats",
                enter = scaleIn(tween(250, easing = EmphasizedDecelerate)) +
                    fadeIn(tween(250, easing = EmphasizedDecelerate)) +
                    slideInVertically(tween(250, easing = EmphasizedDecelerate)) { it / 2 },
                exit = scaleOut(tween(200, easing = EmphasizedAccelerate)) +
                    fadeOut(tween(200, easing = EmphasizedAccelerate)) +
                    slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it / 2 },
            ) {
                FloatingActionButton(
                    onClick = { navController.navigate("edit") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加食品")
                }
            }
        },
    ) { innerPadding ->
        // 大屏/折叠屏适配：内容最大宽 840dp 居中（MD3 大屏可读性要求），
        // 手机上无变化；背景由外层 Scaffold 统一铺满。
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .nestedScroll(chromeScrollConnection),
            // MD3 进入：emphasized decelerate 400ms；退出：emphasized accelerate 200ms
            enterTransition = {
                slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 4 } +
                    fadeIn(tween(400, easing = EmphasizedDecelerate))
            },
            exitTransition = {
                slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { -it / 6 } +
                    fadeOut(tween(200, easing = EmphasizedAccelerate))
            },
            popEnterTransition = {
                slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 6 } +
                    fadeIn(tween(400, easing = EmphasizedDecelerate))
            },
            popExitTransition = {
                slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { it / 4 } +
                    fadeOut(tween(200, easing = EmphasizedAccelerate))
            },
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenList = { filter ->
                        navController.navigate(if (filter == null) "list" else "list?filter=$filter") {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    },
                    onOpenItem = { id -> navController.navigate("detail/$id") },
                )
            }
            composable("list?filter={filter}") { backStackEntry ->
                FoodListScreen(
                    viewModel = viewModel,
                    initialFilter = backStackEntry.arguments?.getString("filter"),
                    onOpenItem = { id -> navController.navigate("detail/$id") },
                    onOpenArchive = { navController.navigate("archive") },
                )
            }
            composable("stats") {
                StatsScreen(
                    viewModel = viewModel,
                    onOpenItem = { id -> navController.navigate("detail/$id") },
                )
            }
            composable("detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                FoodDetailScreen(
                    viewModel = viewModel,
                    itemId = id,
                    onEdit = { editId -> navController.navigate("edit?id=$editId") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("edit?id={id}") { backStackEntry ->
                EditFoodScreen(
                    viewModel = viewModel,
                    editId = backStackEntry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("edit") {
                EditFoodScreen(
                    viewModel = viewModel,
                    editId = null,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("archive") {
                ArchiveScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenArchive = { navController.navigate("archive") },
                    onOpenThresholds = { navController.navigate("manage_thresholds") },
                    onOpenCategories = { navController.navigate("manage_categories") },
                    onOpenLocations = { navController.navigate("manage_locations") },
                )
            }
            composable("manage_thresholds") {
                ThresholdManageScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("manage_categories") {
                CategoryManageScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("manage_locations") {
                LocationManageScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            }
        }
    }
}

/**
 * 居中悬浮胶囊导航栏（带滑动指示器）：
 * 等宽槽位 + 背后一枚 primary 胶囊指示器，切换 Tab 时用 spring 动画滑到目标槽位。
 * MD3 导航规范：所有项常显标签（always show labels），图标上、标签下竖排；
 * 槽位 48dp 高满足最小触摸目标；选中/未选中颜色用 MD3 standard 缓动渐变。
 */
@Composable
private fun FloatingPillNav(navController: NavHostController, currentRoute: String?) {
    val tabs = listOf(
        TabSpec("home", "首页", Icons.Rounded.Home),
        TabSpec("list?filter={filter}", "食品", Icons.AutoMirrored.Rounded.ListAlt),
        TabSpec("stats", "统计", Icons.Rounded.PieChart),
        TabSpec("settings", "设置", Icons.Rounded.Settings),
    )
    val slotWidth = 76.dp
    val slotHeight = 48.dp
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val indicatorOffset by animateDpAsState(
        targetValue = slotWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "navIndicator",
    )

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
                // 滑动指示器
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
                        val target = if (tab.route == "list?filter={filter}") "list" else tab.route
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
                                    if (!selected) {
                                        navController.navigate(target) {
                                            popUpTo("home") { inclusive = target == "home" }
                                            launchSingleTop = true
                                        }
                                    }
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
