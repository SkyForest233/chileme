package com.agon.app

// App 路由表：NavDisplay 的 8 个 entry<AppRoute.*>，以及它们的转场 / 圆角裁剪 / 视差配置。
//
// 硬约定（与 docs/ARCHITECTURE.md 一致）：
//   · **全 App 只有一个 NavDisplay**，就在这里；`rememberNavBackStack` 仍留在 MainApp（状态源唯一）。
//   · 二级页一律走回调（navigate / popRoute），**不得**把 backStack 继续往下传给屏幕。
//   · 外层 Box 把内容限宽 840dp 居中（MD3 大屏可读性）；手机上无变化。
//
// 形参曾一度是 9 个、且名字与被捕获的 MainApp 局部**完全一致**（navigate / popRoute / openList /
// selectTab / chromeScrollConnection …），那是忠实搬运的刻意选择：搬过来的 101 行 entry 代码因此
// 一个字都不用改，只整体反缩进 4 空格 —— 对这一段做 `git diff -w` 是空的。
//
// 2026-09-17 按本文件当时写下的计划做了窄化：4 个导航动作压成 [AppNavCallbacks] 数据类，**形参 9 → 6**。
// 本轮另将移动与归档多选操作沿同一回调持有者传入食品页；路由 entry 仍通过局部函数转发，不将业务逻辑搬进页面。
//
// 2026-09-16 由 MainActivity.kt（拆分中途在 MainApp.kt）搬出；MainActivity.kt 的 1,123 行至此拆完。

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.agon.app.ui.navigation.AppRoute
import com.agon.app.ui.screens.ArchiveScreen
import com.agon.app.ui.screens.CategoryManageScreen
import com.agon.app.ui.screens.ConsumptionLogScreen
import com.agon.app.ui.screens.EditFoodScreen
import com.agon.app.ui.screens.LocationManageScreen
import com.agon.app.ui.screens.ThresholdManageScreen
import com.agon.app.ui.screens.FoodDetailScreen
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import androidx.compose.foundation.pager.PagerState

/**
 * [AppNavHost] 需要的导航动作，以及食品列表多选的移动、归档动作。
 *
 * 2026-09-17 的 4 个路由动作由平铺 lambda 收窄而来；本轮把 2 个批量动作一并放进该持有者，
 * 供食品页调用 MainApp 中既有的弹窗与归档/Snackbar 流程。
 *
 * 用 data class 而不是接口：路由与归档动作可直接装 MainApp 的 `::局部函数` 引用，移动动作则捕获弹窗状态；
 * 统一持有这些回调可避免把批量业务实现搬进食品页。
 */
internal data class AppNavCallbacks(
    val navigate: (AppRoute) -> Unit,
    val popRoute: () -> Unit,
    val openList: (String?) -> Unit,
    val selectTab: (Int) -> Unit,
    val moveSelection: () -> Unit,
    val archiveSelection: () -> Unit,
)

/**
 * 唯一的 NavDisplay：按 backStack 顶端路由渲染 8 个页面（主页 Pager + 7 个二级页）。
 *
 * **本文件已不含任何主题分支**（2026-09-16 第三批 #3 第 6 对合并管理页后达成）：8 个页面全是
 * 单文件双主题，MD3 / MIUIX 的差异只在 `ui/components/app/` 的骨架组件里分流。
 * 原来这里是「每个二级页按 LocalThemeStyle 分流两套实现」，故 `LocalThemeStyle` / `ThemeStyle`
 * 两个 import 与三处 if/else 一并删除（`ScreenParityTest.MergedScreens` 守着不许把双胞胎加回来）。
 */
@Composable
internal fun AppNavHost(
    backStack: NavBackStack,
    chromeScrollConnection: NestedScrollConnection,
    viewModel: AppViewModel,
    pagerState: PagerState,
    selectedTabIndex: Int,
    listFilter: String?,
    callbacks: AppNavCallbacks,
) {
    // 逐个取出路由动作，避免超过 detekt 解构声明默认 3 项上限；多选动作随 callbacks 持有者转发给 Pager。
    val navigate = callbacks.navigate
    val popRoute = callbacks.popRoute
    val openList = callbacks.openList
    val selectTab = callbacks.selectTab

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
                    selectedTabIndex = selectedTabIndex,
                    listFilter = listFilter,
                    onOpenList = { openList(it) },
                    onOpenItem = { navigate(AppRoute.Detail(it)) },
                    onOpenArchive = { navigate(AppRoute.Archive) },
                    onOpenConsumption = { navigate(AppRoute.Consumption) },
                    onOpenThresholds = { navigate(AppRoute.ManageThresholds) },
                    onOpenCategories = { navigate(AppRoute.ManageCategories) },
                    onOpenLocations = { navigate(AppRoute.ManageLocations) },
                    onBackToHome = { selectTab(0) },
                    callbacks = callbacks,
                )
            }
            entry<AppRoute.Consumption> {
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                ConsumptionLogScreen(viewModel = viewModel, onBack = { popRoute() })
            }
            entry<AppRoute.Detail> { route ->
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                FoodDetailScreen(
                    viewModel = viewModel,
                    itemId = route.id,
                    onEdit = { navigate(AppRoute.Edit(it)) },
                    onBack = { popRoute() },
                )
            }
            entry<AppRoute.Edit> { route ->
                EditFoodScreen(
                    viewModel = viewModel,
                    editId = route.id,
                    onBack = { popRoute() },
                )
            }
            entry<AppRoute.Archive> {
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                ArchiveScreen(viewModel = viewModel, onBack = { popRoute() })
            }
            entry<AppRoute.ManageThresholds> {
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                ThresholdManageScreen(viewModel = viewModel, onBack = { popRoute() })
            }
            entry<AppRoute.ManageCategories> {
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                CategoryManageScreen(viewModel = viewModel, onBack = { popRoute() })
            }
            entry<AppRoute.ManageLocations> {
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                LocationManageScreen(viewModel = viewModel, onBack = { popRoute() })
            }
        }
    }
}
