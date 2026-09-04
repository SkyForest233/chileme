# KernelSU Manager UI 设计全拆解

> 源码版本：`tiann/KernelSU` 最新 main（2026-09-03 克隆，`manager` 模块）
> 分析目标：理解这款 Root 管理器的双设计系统、导航架构、主题引擎与交互细节，提炼可复用到 `Chileme` 的设计范式。

---

## 1. 总览：为什么 KernelSU 的 UI 值得拆？

KernelSU Manager 是典型的 **工具型重功能 App**：Home 状态页 / Superuser 授权列表 / Module 模块管理 / Settings 设置，4 个一级 Tab + 约 12 个二级页面（Install、Flash、AppProfile、Template、Sulog、ModuleRepo 等）。但它在视觉上却做到了：

1.  **双设计系统并存**：`UiMode.Miuix` (HyperOS/MIUI) vs `UiMode.Material` (Material 3 Expressive)，运行时一键切换，所有页面都有两套实现 `*Miuix.kt` / `*Material.kt`。
2.  **极致的主题引擎**：`ColorMode` 7 种（SYSTEM/LIGHT/DARK/MONET_SYSTEM/MONET_LIGHT/MONET_DARK/DARK_AMOLED）+ `PaletteStyle` 15 种 + `ColorSpec` 2021/2025 + 15 个预设 keyColor + 动态取色 + AMOLED 纯黑，全部通过 `materialKolor` 生成。
3.  **液态玻璃 FloatingBottomBar**：自研 `FloatingBottomBar`，基于 `miuix-blur` 的 `LayerBackdrop` + `lens` + `innerShadow` + `vibrancy` + 重力感应高光，实现了 iOS 26 风格的液态玻璃底栏。
4.  **工具 App 的信息密度控制**：用 `WarningCard` 分级、`StatusTag`、`TonalCard`、`PullToRefresh`、`SearchPager` 等把复杂状态压扁成可扫视的卡片流。

这正好与 Chileme 的 `MATERIAL3 / MIUIX` 双主题架构同构，拆解价值极高。

---

## 2. 信息架构 & 导航

### 2.1 顶层：Pager + BottomBar + SideRail

```
MainActivity
 ├─ MainScreen (HorizontalPager, pageCount=4)
 │   ├─ 0 HomePager
 │   ├─ 1 SuperUserPager
 │   ├─ 2 ModulePager
 │   └─ 3 SettingPager
 ├─ BottomBar / SideRail (根据 shouldShowSplitPane() 切换)
 └─ NavDisplay (Navigation3)
     ├─ Route.About
     ├─ Route.Sulog
     ├─ Route.Install
     ├─ Route.Flash
     ├─ Route.AppProfile(uid)
     ├─ Route.TemplateEditor
     └─ ...
```

- **HorizontalPager** 是主导航容器，`beyondViewportPageCount=3` 预加载，`userScrollEnabled` 在非完整功能模式下禁用。
- **MainPagerState** 封装了 `springAnimateToPage`，用 `Animatable` + `PagerNavigationSpringSpec` 自定义弹簧，而非默认 `animateScrollToPage`，手感更跟手。`isNavigating` 标志防止快速点击冲突。
- **自适应**：`useNavigationRail()` = `shouldShowSplitPane() && !(Miuix && floatingBar)`。平板/折叠屏横向时自动切 `NavigationRail`，手机竖向用底栏。Chileme 也可以照搬这个 `WindowSize` 判断。

### 2.2 二级导航：Navigation3

使用 `androidx.navigation3` 的 `NavDisplay` + `entryProvider`，所有 Route 是 `@Parcelize @Serializable` 的 `NavKey`。`Navigator` 用 `backStack` 管理，`IntentDispatcher` 处理外部 Intent（如模块快捷方式 `Shortcut.buildShortcutUri`）。返回拦截用 `NavigationBackHandler`：在 Main 栈且不在 Home 时，返回键先回到 Home，而不是退出。

### 2.3 Badge 体系

`NavigationBadgeState(superuserCount, moduleEnabledCount, moduleUpdatableCount)` 通过 `badgeFor()` 映射到 Tab：

- Superuser：有授权数 → `BadgeTone.Accent`（primary 色）
- Module：有可更新 → `Alert`（红色），否则有启用数 → `Accent`

Material 用 `BadgedBox + Badge`，Miuix 用 `top.yukonga.miuix.kmp.basic.Badge`，floating 模式下用 `primaryContainer` 保证液态玻璃上的可读性。

---

## 3. 双设计系统实现

### 3.1 UiMode 分发

```kotlin
enum class UiMode { Miuix("miuix"), Material("material") }
val LocalUiMode = staticCompositionLocalOf { Miuix }
```

`MainActivity` 从 `SettingsRepository` 读取 `uiMode`，通过 `CompositionLocalProvider` 下发。每个 Pager 内部：

```kotlin
when (LocalUiMode.current) {
  Miuix -> HomePagerMiuix(...)
  Material -> HomePagerMaterial(...)
}
```

这种 **文件级分叉** 而非组件级 if-else，保证两套设计可以独立演进，不互相污染。Chileme 已采用相同模式，值得坚持。

### 3.2 Material 3 Expressive 分支

- `MaterialExpressiveTheme` + `MotionScheme.expressive()`
- `ShortNavigationBar`（M3 新的短底栏）替代旧 `NavigationBar`
- `ExpressiveScaffold` 封装 `Scaffold`，统一 `containerColor = surfaceContainer`
- `LargeFlexibleTopAppBar` + `SearchBar`（`ExpandedFullScreenContainedSearchBar`）
- 图标用 `Icons.Filled / Outlined` 区分选中态

### 3.3 Miuix (HyperOS) 分支

- 依赖 `top.yukonga.miuix.kmp`，`MiuixTheme` + `ThemeController`
- 组件：`Card`、`BasicComponent`、`SwitchPreference`、`ArrowPreference`、`OverlayDropdownPreference`、`TopAppBar`、`Scaffold`、`PullToRefresh`、`SuperSearchBar`
- 交互：`overScrollVertical()` 回弹 + `scrollEndHaptic()` 触感 + `PressFeedbackType.Tilt/Sink` 按压
- 视觉：大量 12dp 水平 padding 的卡片分组，`Card` 内部用 `BasicComponent` 统一标题/摘要/首尾 Action 插槽

---

## 4. 主题引擎：比 Chileme 更激进的版本

### 4.1 AppSettings 模型

```kotlin
data class AppSettings(
  val colorMode: ColorMode, // 7 种
  val keyColor: Int,        // 0=动态, 否则15选1
  val paletteStyle: PaletteStyle, // TonalSpot, Neutral, Vibrant, Expressive...
  val colorSpec: SpecVersion // SPEC_2021 / SPEC_2025
)
```

`ThemeController.getAppSettings()` 会根据 `miuixMonet` 开关自动把 Monet ↔ Non-Monet 互转，避免 Miuix 下误用动态色。

### 4.2 颜色生成

- **Material**：`rememberKernelSUColorScheme(seedColor, isDark, isAmoled, paletteStyle, colorSpec)` → `materialKolor` 生成全套 `ColorScheme`，`animateAsState()` 做平滑过渡，AMOLED 模式把 surface 压到纯黑。
- **Miuix**：`ThemeController(ColorSchemeMode.MonetSystem/Light/Dark, keyColor, paletteStyle, colorSpec)` → 内部同样走 MaterialKolor，但映射到 Miuix 的 `colorScheme`。
- **MonetColorsProvider.UpdateCss()**：把 Compose 的 `ColorScheme` 同步到 WebView 的 CSS 变量，供模块 WebUI 使用，这个细节非常工程化。

### 4.3 附加 CompositionLocal

```kotlin
LocalColorMode // int value
LocalEnableBlur // 是否启用模糊
LocalEnableFloatingBottomBar
LocalEnableFloatingBottomBarBlur
LocalEnableNavigationBadge
```

用户可在设置里开关模糊、悬浮底栏、角标，实时生效，无需重启。

---

## 5. 底栏设计：从普通到液态玻璃

这是 KernelSU 最炫技的部分。

### 5.1 普通模式

- Material：`ShortNavigationBar` + `ShortNavigationBarItem`，`containerColor = surfaceContainer`
- Miuix：`NavigationBar` + `NavigationBarItem`，支持 `BlurredBar(blurBackdrop)` 包裹，`barColor = Transparent` 时透出毛玻璃。

### 5.2 Floating Liquid Glass 模式

`FloatingBottomBar` 完全自研，参考 `compose-miuix-ui` 的 iOS 案例，Apache 2.0：

- **布局**：`Row` + `weight(1f)` 的 `FloatingBottomBarItem`，`defaultMinSize(76.dp)`，底部 `padding = navigationBars + 28.dp` 悬浮。
- **背景**：`surfaceContainer.copy(0.4f)` 半透明 + `layerBackdrop(backdrop)` 模糊 + `lens` 折射 + `innerShadow` 内阴影 + `vibrancy` 鲜艳度。
- **指示器**：选中态是一个 pill 形状的 `CircleShape`，内部通过 `LocalContentColor` 染色，文字 11sp。
- **手势**：`DampedDragAnimation` 实现阻尼拖拽，可在 Tab 间拖动切换，`rubberBandPx = 4.dp` 橡皮筋回弹，`spring(1f, 300f, 0.5f)`。
- **高光**：`iosIndicatorSpecular` 用 `BloomStroke` + 双光源，`rememberQuantizedGravityAngle()` 读取 `rememberDeviceTilt()` 重力，量化到 3° 一档，实时旋转高光位置，模拟真实玻璃反光。

这个组件如果要移植到 Chileme，建议直接拷贝 `liquid/` + `FloatingBottomBar.kt` + `DampedDragAnimation`，但要注意性能：`drawBackdrop` 会触发离屏渲染，低端机需可关闭（KernelSU 已有 `LocalEnableFloatingBottomBarBlur` 开关）。

---

## 6. 页面级拆解

### 6.1 Home 首页

**信息层级**：
```
UpdateCard (有更新时 AnimatedVisibility + expandVertically)
WarningCard 栈（PR构建警告、版本不匹配、GKI警告、UAPI不匹配、Root失败）
StatusCard（核心）
InfoCard（内核版本、管理器版本、SELinux等）
DonateCard
LearnMoreCard
```

**StatusCard**：
- 已工作：大卡片，`secondaryContainer` / `Color(0xFF1A3825)` 深绿，右下角 110dp 的 `CheckCircleOutline` 装饰图标，左下角 LKM/GKI 模式标签，左上角 "Working" + 版本。点击跳 Install。
- 未安装：`BasicComponent` + `ErrorOutline` + "Click to install" + Jailbreak 按钮（仅 SELinux Permissive 时显示）
- 不支持：同上，提示原因

**TopBar**：`TopAppBar` + `RebootListPopup`（重启选项菜单），Miuix 下包 `BlurredBar`。

### 6.2 Superuser 超级用户

- **搜索**：`SearchStatus` 封装 `isCollapsed/EXPANDING/EXPANDED`，`SearchBarFake` 是 collapsed 时的假搜索框，点击展开 `SearchPager`（全屏搜索）。`offsetY` 用 `onGloballyPositioned` 计算，保证动画锚点正确。
- **列表**：`LazyColumn` + `PullToRefresh`，`GroupedApps` 按 UID 分组，显示 AppIcon、包名、ownerName（多用户）、`StatusTag`（允许/拒绝）。
- **排序/过滤**：`OverlayListPopup` + `ListPopupColumn` + `DropdownImpl`，支持按名称/UID/授权状态排序，过滤系统应用、仅主用户。
- **空状态**：`AppIconImage` + `ownerNameForUid` 处理多用户。

### 6.3 Module 模块

- **FAB**：`FloatingActionButton` + `Intent.ACTION_GET_CONTENT` 选 zip，支持多选，`confirmDialog` 二次确认。
- **卡片**：每个模块显示图标、名称、版本、作者、描述、`Switch` 启用、`StatusTag`（更新可用），点击展开操作（WebUI、Action、卸载）。
- **搜索 & 排序**：同 Superuser，额外 `sortActionFirst` / `sortEnabledFirst`。
- **冲突处理**：`magiskInstalled` 时全屏提示冲突。
- **下拉刷新**：`rememberPullToRefreshState` + 自定义文案数组 `refresh_pulling/release/refresh/complete`。

### 6.4 Settings 设置

Miuix 下是典型 HyperOS 设置页：

```
Card {
  SwitchPreference(检查更新)
  SwitchPreference(模块更新检查)
}
Card {
  OverlayDropdownPreference(UI模式 Miuix/Material)
  ArrowPreference(主题 -> ColorPaletteScreen)
}
Card {
  SwitchPreference(Su兼容模式)
  SwitchPreference(卸载模块、隐藏SELinux、Sulog、ADB Root等)
}
Card {
  ArrowPreference(卸载 LKM)
}
Card {
  ArrowPreference(发送日志 -> SendLogDialog)
  ArrowPreference(关于)
}
```

- `SwitchPreference` 有 `startAction` 插槽放图标，`ArrowPreference` 右箭头。
- `UninstallDialog` / `SendLogDialog` 是 `ScaleDialog` 封装，带 loading。
- Material 分支用 `SegmentedList` + `ExpressiveSwitch` 实现同款。

### 6.5 其他二级页

- **Install**：`InstallScreen`，展示当前内核、支持刷入方式、选择文件刷入。
- **Flash**：终端风格，实时输出刷入日志，`FlashUtils` 处理。
- **AppProfile**：单个 UID 的 Root 配置，`ProfileConfig` 含 `RootProfileConfig` / `AppProfileConfig` / `TemplateConfig`，支持能力、组、SELinux 上下文等，Miuix 用 `SuperSearchBar` + `MultiSelectDialog`。
- **Template**：AppProfile 模板管理。
- **Sulog**：Superuser 日志，`SulogHelper`。
- **About**：`AboutMaterial/Miuix`，`GithubMarkdown` 渲染 changelog。
- **ColorPalette**：主题实验室，调 `keyColorOptions`（15 色）、`PaletteStyle`、`ColorSpec`，实时预览。
- **ModuleRepo**：模块仓库，`ModuleRepoApi` 拉取，支持搜索、详情 `ModuleRepoDetailScreen`。

---

## 7. 组件库盘点

| 组件 | Material 实现 | Miuix 实现 | 设计要点 |
|------|--------------|-----------|---------|
| Scaffold | `ExpressiveScaffold` (封装 M3 Scaffold) | `Miuix Scaffold` + `overScrollVertical` + `scrollEndHaptic` | 统一 `contentWindowInsets = systemBars+displayCutout` |
| TopAppBar | `LargeFlexibleTopAppBar` + `expressiveTopAppBarColors` | `TopAppBar` + `MiuixScrollBehavior` + `BlurredBar` | Miuix 支持折叠动画 |
| Search | `SearchBar` + `ExpandedFullScreenContainedSearchBar` | `SuperSearchBar` + `SearchBox` + `SearchPager` | 都支持 BackHandler 关闭 |
| Dialog | `DialogMaterial` + `ExpressiveDialog` | `DialogMiuix` + `ScaleDialog` | `ConfirmDialogHandle` 协程化 `awaitConfirm` |
| BottomBar | `ShortNavigationBar` | `NavigationBar` / `FloatingBottomBar` | 液态玻璃仅 Miuix |
| Card | `TonalCard` | `Miuix Card` + `BasicComponent` | Miuix Card 圆角更大，带按压反馈 |
| Switch | `ExpressiveSwitch` | `SwitchPreference` | |
| Badge | M3 `Badge` | Miuix `Badge` | 支持 Alert/Accent 双色 |
| StatusTag | `StatusTagMaterial` | `StatusTagMiuix` | 胶囊标签，显示允许/拒绝/模块状态 |
| WarningCard | 共用 `WarningLevel` 枚举 | `WarningCard` Miuix 版 | Notice/Warning/Error 三级，颜色区分 |
| ListPopup | `ExpressiveMenu` | `OverlayListPopup` + `ListPopupColumn` | 排序/过滤用 |
| Markdown | `MarkdownContent` | 同左，包 `GithubMarkdown` | About/Changelog 用 |

---

## 8. 动效 & 交互细节

1.  **Pager 弹簧**：`PagerNavigationSpringSpec` 自定义，`Animatable` 逐帧 `scrollBy`，阈值 0.5px，异常时 snap 到目标，避免卡在中间。
2.  **FloatingBar 阻尼拖拽**：`DampedDragAnimation` 按压放大 78/56，`visibilityThreshold 0.001f`，`canDrag` 限制在总宽度内，松手回弹用 `spring(1f, 300f, 0.5f)`。
3.  **Blur**：`rememberBlurBackdrop(enableBlur)` + `rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }`，`CombinedBackdrop` 合并两个 backdrop，`layerBackdrop` 修饰符应用。
4.  **OverScroll**：`overScrollVertical()` + `scrollEndHaptic()` 是 Miuix 的特色，拉到头有果冻回弹 + 震动。
5.  **Search 动画**：`SearchStatus.TopAppBarAnim` + `SearchBarFake` 的 `alpha` 随 `collapsedFraction` 变化，`detectTapGestures` 触发展开。
6.  **内容就绪**：`rememberContentReady()` 延迟加载 Pager 内容，避免首帧卡顿。

---

## 9. 对 Chileme 的可复用建议

Chileme 已有 `ThemeStyle.MATERIAL3 / MIUIX` 双主题，与 KernelSU 同构，可直接借鉴：

1.  **ColorMode 7 档**：把 Chileme 现在的 `darkTheme Boolean` 升级为 `ColorMode` 枚举，支持跟随系统/强制浅/深色/Monet/AMOLED，`ThemeController` 逻辑可照搬。
2.  **PaletteStyle & ColorSpec**：Chileme 已用 `PaletteStyle.TonalSpot`，可开放用户选择 `Vibrant/Expressive/Neutral`，并支持 `SPEC_2025`，MaterialKolor 已支持。
3.  **FloatingBottomBar 移植**：把 `liquid/` + `FloatingBottomBar.kt` 拷贝到 `ui/components/`，用 `LocalThemeStyle` 控制开关，低端机默认关闭 blur。
4.  **Badge**：Chileme 的库存页可借鉴 `NavigationBadgeState`，在底栏显示临期数量（Alert 红点）/安全数量（Accent）。
5.  **SearchStatus 模式**：Chileme 的 FoodList 搜索可改成 Fake SearchBar + 全屏 SearchPager，解决现在搜索框占 TopAppBar 空间的问题。
6.  **WarningCard 分级**：Chileme 的过期提醒可复用 `WarningLevel`，临期 Notice 黄、过期 Error 红。
7.  **PullToRefresh 文案**：KernelSU 的 `refreshTexts` 数组可本地化到 Chileme，统一空状态。
8.  **Navigation3**：Chileme 还在用 `AppRoute`，可考虑迁移到 Navigation3 的 `NavKey`，类型更安全。

---

## 10. 视觉语言总结

- **圆角**：Miuix 卡片 16-24dp，Material 用 M3 token，Chileme 的 `AppShapes` 8/12/16/24/28 已对齐。
- **颜色**：Miuix 用 `onBackground` 统一图标色，Material 用 `primary` 强调；警告卡用 `secondaryContainer` 绿（工作正常）vs `errorContainer` 红（异常）。
- **图标**：Miuix 用 `Icons.Rounded`（Cottage/Security/Extension/Settings），Material 用 Filled/Outlined 双态，Chileme 建议统一 Rounded 风格更贴近 HyperOS。
- **排版**：Miuix 偏大标题 22sp SemiBold + 副标题 15sp Medium，卡片内 16sp；Material 用 M3 Typography。
- **留白**：Miuix 12dp 水平 padding + 12dp 卡片间距，Material 16dp + 13dp，Chileme 可取 12dp 统一。

---

## 11. 一句话评价

KernelSU 的 UI 是 **“工具 App 的设计系统天花板”**：用最克制的卡片流承载最复杂的 Root 状态，用双主题 + 液态玻璃底栏 + 完整的主题实验室把“可玩性”拉满，同时通过 `Local` 开关保证性能可控。对 Chileme 来说，抄它的 **主题引擎 + FloatingBar + SearchPager + Warning 分级**，就能让库存管理 App 从“能用”跃升到“好用且好看”。

> 源码路径索引：
> - 主题：`ui/theme/Theme.kt, MaterialTheme.kt, MiuixTheme.kt, Colors.kt`
> - 底栏：`ui/component/bottombar/*, FloatingBottomBar.kt, liquid/*, miuix/effect/*`
> - 导航：`ui/navigation3/Routes.kt, MainActivity.kt`
> - 页面：`ui/screen/home/*, superuser/*, module/*, settings/*`
> - 组件：`ui/component/material/*, miuix/*, dialog/*, statustag/*`

