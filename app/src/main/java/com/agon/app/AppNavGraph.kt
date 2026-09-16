package com.agon.app

// App 路由表：NavDisplay 的 8 个 entry<AppRoute.*>，以及它们的转场 / 圆角裁剪 / 视差配置。
//
// 硬约定（与 docs/ARCHITECTURE.md 一致）：
//   · **全 App 只有一个 NavDisplay**，就在这里；`rememberNavBackStack` 仍留在 MainApp（状态源唯一）。
//   · 二级页一律走回调（navigate / popRoute），**不得**把 backStack 继续往下传给屏幕。
//   · 外层 Box 把内容限宽 840dp 居中（MD3 大屏可读性）；手机上无变化。
//
// 形参 9 个、且名字与被捕获的 MainApp 局部**完全一致**（navigate / popRoute / openList / selectTab /
// chromeScrollConnection …）是忠实搬运的刻意选择：搬过来的 101 行 entry 代码因此一个字都不用改，
// 只整体反缩进 4 空格 —— 对这一段做 `git diff -w` 是空的。调用点用 `::局部函数` 传引用
// （MainApp 里本来就有 `MiuixFloatingNav(selectedTabIndex, ::selectTab)`，同一手法，已被 CI 证明可编译）。
// 把 navigate/popRoute 的定义也收进本文件、或压成 AppNavCallbacks 数据类，都会动到 MainApp 里
// FAB / 底栏共用的那几个局部函数 —— 属于第三批「结构性」范围，本轮不做。
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
import com.agon.app.ui.screens.MiuixCategoryManageScreen
import com.agon.app.ui.screens.MiuixFoodDetailScreen
import com.agon.app.ui.screens.MiuixLocationManageScreen
import com.agon.app.ui.screens.MiuixThresholdManageScreen
import com.agon.app.ui.theme.LocalThemeStyle
import com.agon.app.ui.theme.ThemeStyle
import com.agon.app.viewmodel.AppViewModel
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import androidx.compose.foundation.pager.PagerState

/**
 * 唯一的 NavDisplay：按 backStack 顶端路由渲染 8 个页面（主页 Pager + 7 个二级页），
 * 每个二级页按 LocalThemeStyle 分流 MD3 / MIUIX 两套实现。
 */
@Composable
internal fun AppNavHost(
    backStack: NavBackStack,
    chromeScrollConnection: NestedScrollConnection,
    viewModel: AppViewModel,
    pagerState: PagerState,
    listFilter: String?,
    navigate: (AppRoute) -> Unit,
    popRoute: () -> Unit,
    openList: (String?) -> Unit,
    selectTab: (Int) -> Unit,
) {
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
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                ConsumptionLogScreen(viewModel = viewModel, onBack = { popRoute() })
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
                // 双主题已合并为一份（外壳差异在 ui/components/app/ 的骨架组件里分流）
                ArchiveScreen(viewModel = viewModel, onBack = { popRoute() })
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
