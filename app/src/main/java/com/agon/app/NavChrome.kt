package com.agon.app

// 底部导航（4 套实现）+ 四个 Tab 的 HorizontalPager 容器 + Tab 定义。
//
// 底栏按「主题风格 × 悬浮开关」四选一：MiuixFloatingNav / MiuixBottomNav / FloatingPillNav /
// Md3BottomNav；MainTabs（MD3 图标）与 MiuixMainTabs（MiuixIcons）顺序即 Pager 页序。
// Tab 用 HorizontalPager 而非 miuix-nav 的 MultiPush：后者是堆栈推进（中间页被盖住），
// Tab 切换要露出中间页；手势翻页关闭，避免和列表里的横向 Chip 抢手势。
//
// ⚠️ A 方案（2026-09-15 产品决定）：底栏**只避让导航栏、不避让键盘** —— 本文件不得出现
// .imePadding()，ImeHandlingTest 直接对本文件断言这一点（不再用「两个计数的大小关系」间接表达）。
// 硬约定：Tab 切换与二级页进出都走回调（onSelect / onTabs），backStack 不得传进来。
//
// 2026-09-16 由 MainActivity.kt 拆分而来（纯搬运：除 private→internal 外，签名与实现逐字节未改）。
// MainTabs / MainTabsPager / 4 套底栏被 MainApp 调用 → internal；TabSpec 因被 internal val MainTabs
// 的推断类型暴露，也必须 internal（见下方声明处注释）；MiuixMainTabs 只在本文件内使用 → 保持 private。

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.screens.FoodListScreen
import com.agon.app.ui.screens.HomeScreen
import com.agon.app.ui.screens.MiuixSettingsScreen
import com.agon.app.ui.screens.MiuixStatsScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.screens.StatsScreen
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.MotionEasing
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import kotlin.math.roundToInt

// internal 而非 private：MainTabs 是 internal val，其推断类型 List<TabSpec> 会**暴露** TabSpec，
// 而 Kotlin 要求「被暴露类型的可见性不得低于声明本身」（否则编译报 exposes its private type）。
// 这是本轮 private→internal 放宽时唯一的连带项 —— 单看 TabSpec 只在文件内用，很容易漏。
internal data class TabSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 底部导航共享 Tab 定义（MD3 / MIUIX 共用标签；图标按主题分流）。顺序即 Pager 页序。 */
internal val MainTabs = listOf(
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
internal fun MainTabsPager(
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
            // 首页已于 2026-09-16 合并为单文件双主题（第三批 #3 第 4 对），两套主题共用一次调用
            0 -> HomeScreen(
                viewModel = viewModel,
                onOpenList = onOpenList,
                onOpenItem = onOpenItem,
            )
            // 食品列表页已于 2026-09-16 合并为单文件双主题（第三批 #3 第 5 对）
            1 -> FoodListScreen(
                viewModel = viewModel,
                initialFilter = listFilter,
                onOpenItem = onOpenItem,
                onOpenArchive = onOpenArchive,
            )
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
internal fun MiuixBottomNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
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
internal fun MiuixFloatingNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
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
internal fun Md3BottomNav(selectedIndex: Int, onSelect: (Int) -> Unit) {
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
internal fun FloatingPillNav(pagePosition: Float, onSelect: (Int) -> Unit) {
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
