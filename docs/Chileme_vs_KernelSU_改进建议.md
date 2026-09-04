# Chileme 对照 KernelSU 的 UI 改进清单

## 现状优势（保留）
- 已有 `ThemeStyle.MATERIAL3 / MIUIX` 双主题分发，`LocalThemeStyle` + `AppShapes 8/12/16/24/28` 圆角体系比 KernelSU 更一致
- `MotionEasing` / `MotionSpring.folmeSpring` 统一动效，`filterPanelEnter/Exit` 已对齐 Miuix
- `StatusUi` 语义色 + `dot` 小圆点高饱和区分，比 KernelSU 的 `WarningLevel` 更细
- `ExpiryCalendar` 自研日历 + 4档 `ExpiryUrgency`，信息密度高于 KernelSU
- `DataCorruptBanner` 置顶不可忽略，工程健壮性好

---

## 一、主题引擎：从 Boolean 升级到 ColorMode 7档

**KernelSU**
```kotlin
enum ColorMode { SYSTEM/LIGHT/DARK/MONET_SYSTEM/MONET_LIGHT/MONET_DARK/DARK_AMOLED }
+ PaletteStyle 15种 + ColorSpec 2021/2025 + keyColor 15选1 + AMOLED纯黑
LocalEnableBlur / FloatingBottomBar / NavigationBadge 开关
```

**Chileme现状**
```kotlin
darkMode: Int (0跟随/1浅/2深) + dynamicColor Boolean + AppPalette 15种
```

**差距**：没有 Monet 显式模式、没有 AMOLED、没有 Spec 2025、没有 Blur 开关，Miuix 侧 `rememberMiuixController` 只处理了 MonetSystem/Light/Dark 三种，缺 `toNonMonetMode()` 互转逻辑。

**改进**
1. 新建 `ColorMode` 枚举，复用 KernelSU 的 `isSystem/isDark/isAmoled/isMonet/toNonMonetMode/toMonetMode`
2. DataStore 增加 `paletteStyle: PaletteStyle`、`colorSpec: SpecVersion`、`amoled: Boolean`、`enableBlur: Boolean`、`floatingNavBlur: Boolean`、`enableBadge: Boolean`
3. `AgonAppTheme` 和 `MiuixRootTheme` 都走 `rememberDynamicColorScheme(seed, isDark, isAmoled, paletteStyle, colorSpec)`，`animateColorScheme` 保留
4. 设置页新增「主题实验室」入口，复用 KernelSU 的 `ColorPaletteScreen` 逻辑：15色网格 + PaletteStyle 下拉 + Spec 切换 + 实时预览

优先级：P0，1天可完成，收益最大。

---

## 二、导航与自适应：从 NavBackStack 到 Navigation3 + SideRail

**KernelSU**
- `HorizontalPager` + `MainPagerState.springAnimateToPage` 自定义弹簧
- `useNavigationRail() = shouldShowSplitPane() && !(Miuix && floatingBar)` 平板自动侧边栏
- `Navigation3 NavDisplay` + `NavKey` 类型安全
- `BackHandler`：Main栈不在Home时先回Home

**Chileme现状**
- `rememberNavBackStack<AppRoute>` + `pagerState.animateScrollToPage` 用 `MotionSpring.page()`
- 没有 SideRail，平板仍是底栏
- 没有 `springAnimateToPage` 的 `Animatable` 逐帧控制
- `chromeScrollConnection` 下滑隐藏底栏/FAB，但阈值 -8/+8 硬编码

**改进**
1. 拷贝 KernelSU 的 `MainPagerState` + `springAnimateToPage` + `PagerNavigationSpringSpec`，替换 Chileme 的直接 `animateScrollToPage`，解决快速切Tab冲突（KernelSU 用 `isNavigating` + `navJob?.cancel()`）
2. 实现 `shouldShowSplitPane()`：`WindowWidthSizeClass >= Medium` 时用 `NavigationRail`，参考 `ui/component/bottombar/NavigationRailMiuix/Material`
3. 考虑迁移到 `navigation3`，至少把 `AppRoute` 改成 `@Parcelize` NavKey，`Navigator` 封装 `push/pop`
4. `scrollChromeVisible` 阈值改为 `8.dp.toPx()`，并增加 `LaunchedEffect(currentRoute)` 恢复，和 KernelSU 一致

---

## 三、底栏：从悬浮胶囊到液态玻璃

**KernelSU**
- 普通：`ShortNavigationBar` (Material) / `NavigationBar` (Miuix) + `BlurredBar(backdrop)`
- 悬浮：`FloatingBottomBar` 自研，`lens + innerShadow + vibrancy + BloomStroke + 重力感应高光 + DampedDragAnimation拖动切换`
- `backdrop = rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }` + `CombinedBackdrop`

**Chileme现状**
- 已有 `MiuixFloatingNav` / `FloatingPillNav` / `MiuixBottomNav` / `BottomNav`，但：
  - 没有 blur backdrop，`surfaceContainer` 不透明，悬浮感弱
  - 没有拖动切换，`DampedDragAnimation` 缺失
  - 没有高光，液态感不足
  - `BatchActionBar` 多选栏和底栏切换用 `slideInVertically`，但 KernelSU 的 Badge 系统更完善

**改进**
1. 拷贝 `ui/component/liquid/` 4文件 + `FloatingBottomBar.kt` + `miuix/animation/DampedDragAnimation.kt` + `effect/BgEffect*`，封装 `BlurredBar` 组件（Chileme已有类似但未用到 backdrop）
2. `MiuixFloatingNav` 内部用 `rememberBlurBackdrop(enableBlur)`，`containerColor = if (blurEnabled) surfaceContainer.copy(0.4f) else surfaceContainer`
3. 增加 `Badge`：食品总数/临期数/过期数映射到 Tab，`badgeFor(index, state)`，临期用 Alert 红，安全用 Accent 主色
4. `BatchActionBar` 增加 `PressFeedbackType` 和 `overScroll`，和 Miuix 一致

P1，视觉提升最明显。

---

## 四、搜索与筛选：从 OutlinedTextField 到 SearchStatus 全屏

**KernelSU**
- `SearchStatus { isCollapsed, EXPANDING, EXPANDED, offsetY, label }`
- `SearchBarFake` collapsed时是假搜索框，`detectTapGestures` 展开 `SearchPager` 全屏搜索
- `TopAppBarAnim(backgroundColor)` 背景色随滚动变化
- 筛选：`OverlayListPopup + ListPopupColumn + DropdownImpl`

**Chileme现状**
- `OutlinedTextField + FilterToggle + AnimatedVisibility filterPanel`，筛选面板在列表上方，占用空间，滚动时不收起
- 搜索和筛选分离，没有全屏搜索
- `FilterChip` 选中色 `primaryContainer/secondaryContainer`，但未区分 Alert/Accent

**改进**
1. 实现 `SearchStatus`，`FoodListScreen` TopBar 增加 `bottomContent = { SearchBarFake }`，`onGloballyPositioned` 记录 offsetY
2. 搜索展开时用 `SearchPager` / `SearchBox` 全屏，复用 KernelSU 的 `SearchBox` + `SearchPager` 结构，IME padding 处理
3. 筛选按钮从 `FilterToggle` 改成 `OverlayListPopup`，状态/分类/位置用 `DropdownImpl`，和 SuperUser 页一致
4. 增加 `SearchStatus.TopAppBarAnim`，blurActive时 `barColor = Transparent`

P0，列表页体验提升最大。

---

## 五、状态与警告：从 StatusBadge 到 WarningCard 分级

**KernelSU**
- `WarningCard(message, level: WarningLevel.Notice/Warning/Error)` + `AnimatedVisibility expandVertically`
- `StatusTag` 胶囊标签，统一颜色
- `UpdateCard` 有更新时 `fadeIn + expandVertically`

**Chileme现状**
- `StatusBadge` 已有，但 `FreshnessBanner` 只是普通 Card，没有分级色
- `DataCorruptBanner` 已有，但过期清理按钮 `FilledTonalButton` 在 Material 侧，Miuix 侧用 `ButtonPrimary`，不一致
- 缺少「版本不匹配」「GKI警告」这类分级警告栈

**改进**
1. 新建 `WarningCard` 组件，`level` 映射 `containerColor`：Notice=secondaryContainer, Warning=tertiaryContainer, Error=errorContainer
2. Home页 `StatCard` 三个卡片改成 KernelSU 的 StatusCard 样式：大装饰图标 110dp 右下角，LKM/GKI 模式标签左下角，标题 22sp SemiBold
3. `FreshnessBanner` 根据 `expired/expiring` 切换 `WarningLevel`，`containerColor` 随之变红/黄/绿
4. `CleanExpired` 按钮用 `AnimatedVisibility` 包裹，已有但可增加 `PressFeedbackType`

---

## 六、动效与触感

**KernelSU**
- `PagerNavigationSpringSpec` 自定义弹簧
- `overScrollVertical() + scrollEndHaptic()` 果冻回弹+震动
- `InteractiveHighlight` 按压高亮
- `rememberContentReady()` 延迟加载 Pager 内容，避免首帧卡顿

**Chileme现状**
- `MotionSpring.page(distance)` 已用，但缺少 `isNavigating` 标志
- 没有 `overScrollVertical`，Miuix 列表滚动到头无回弹
- 没有 `scrollEndHaptic`
- 没有 `rememberContentReady`，Home页首次进入会一次性加载所有

**改进**
1. 所有 `LazyColumn` 增加 `.overScrollVertical().scrollEndHaptic()`，Miuix 侧已依赖 miuix库，直接可用
2. `StatCard` / `FoodCard` 增加 `pressFeedbackType = PressFeedbackType.Tilt/Sink`
3. 实现 `rememberContentReady()`：首帧只渲染当前页，`beyondViewportPageCount` 延迟到 `contentReady` 后设为3
4. 统一 `animateItem` 用 `MotionEasing.EmphasizedDecelerate/Accelerate`，已有但部分 `tween(280)` 硬编码

---

## 七、组件一致性

| 问题 | KernelSU 做法 | Chileme 现状 | 改进 |
|------|--------------|-------------|------|
| Scaffold | `ExpressiveScaffold` 统一 `surfaceContainer` + `contentWindowInsets = safeDrawing` | 各屏自行 `TopAppBarDefaults.largeTopAppBarColors(background/surfaceContainer)`，不一致 | 封装 `ChilemeScaffold` 统一 |
| TopAppBar | Miuix 用 `MiuixScrollBehavior` + `BlurredBar` | Miuix 侧 `TopAppBar(title, subtitle)` 无 scrollBehavior | 增加 `MiuixScrollBehavior` + `nestedScroll` |
| Card | Miuix `Card(onClick, pressFeedbackType)` | Miuix `Card` 有 `pressFeedbackType` 但 Material 侧 `CardDefaults` 未统一 | 统一 `AppShapes` 已做，补 `CardDefaults` |
| EmptyState | 居中 + emoji + title + subtitle | 已有 `EmptyState`，但 Stats 页空状态是手写 `Box` | 统一用 `EmptyState` |
| Snackbar | `SnackbarHost` + `SwipeDismissSnackbarHost` + `MiuixSnackbarHost` 两套 | 已有两套，但 `snackbarOffset` 84dp/8dp 硬编码，未跟随 `floatingNav` | 用 `animateDpAsState` 已有，补 `bottomInnerPadding` |
| FAB | `FloatingActionButton` + `border(0.05.dp, outline.copy(0.5))` | `FloatingActionButton` 无边框，阴影 0 | 增加边框，Miuix 风格 |

---

## 八、设置页组织

**KernelSU SettingsMiuix**：
```
Card { 检查更新开关 x2 }
Card { UI模式下拉 + 主题箭头 }
Card { Su兼容/卸载/隐藏SELinux等开关 x8 }
Card { 卸载LKM箭头 }
Card { 发日志/关于箭头 }
```

**Chileme Settings**：
- 51K 的 `SettingsScreen.kt`，所有逻辑堆一起，`AlertDialog` 多达8个，`rememberSaveable` 散落
- 缺少分组标题，Card 间距 12dp 但无 Section Label

**改进**
1. 按 KernelSU 分组：外观/通知/备份/关于，`Card` 间 12dp，组内用 `SwitchPreference` / `ArrowPreference`
2. 把 `showExportFormatDialog / showRestoreSourceDialog / showClearDialog / showNutstoreDialog / showBackupPicker / showSnapshotPicker` 拆成独立 `Dialog` 组件，像 KernelSU 的 `UninstallDialog` / `SendLogDialog`
3. 增加 `OverlayDropdownPreference` 替代现在的 `DropdownMenu`，Miuix 侧统一
4. 增加「主题实验室」入口，跳转到 `ColorPaletteScreen`

---

## 九、细节清单（可直接提 PR）

**P0 必须做**
- [ ] `ColorMode` 7档 + `enableBlur` 开关
- [ ] `SearchStatus + SearchBarFake + SearchPager` 重构 FoodList 搜索
- [ ] `overScrollVertical + scrollEndHaptic` 加到所有 LazyColumn
- [ ] `MainPagerState.springAnimateToPage` 替换 `animateScrollToPage`

**P1 体验提升**
- [ ] 移植 `FloatingBottomBar` 液态玻璃 + `DampedDragAnimation`
- [ ] `WarningCard` 分级 + Home StatusCard 大卡片
- [ ] `NavigationBadge` 临期数角标
- [ ] `SideRail` 平板适配

**P2  polish**
- [ ] `BlurredBar` 统一 TopAppBar
- [ ] `rememberContentReady` 首帧优化
- [ ] `ChilemeScaffold` 封装
- [ ] 设置页分组 + Dialog 拆分
- [ ] `PaletteStyle / ColorSpec` 主题实验室

---

## 十、一句话总结

KernelSU 的 UI 精髓是 **“用 CompositionLocal 控制一切开关，用 Card 分组压扁复杂度，用液态玻璃和弹簧动效提升质感”**。Chileme 已具备双主题骨架，缺的是 **主题深度、搜索范式、回弹触感、液态底栏** 四块。补完这四块，Chileme 就能从“功能完整”进阶到“设计系统完整”。
