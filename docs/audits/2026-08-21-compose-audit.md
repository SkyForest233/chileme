# Jetpack Compose Audit Report

Target: `/home/user/chileme` (`app` 单模块)
Date: 2026-08-21
Skill: [hamen/compose_skill](https://github.com/hamen/compose_skill) `jetpack-compose-audit` 4.3.2
Scope: `app/src/main/java/com/agon/app/` 生产 Compose（含 MD3 与 MIUIX 双实现）
Excluded from scoring: 无 sample/demo；无 UI 测试源
Confidence: **Medium**（沙箱无 Android 工具链，Compose compiler reports 未生成；稳定性结论为源码推断）
Overall Score: **75/100**

## MIUIX / 导航边界（先读）

本轮 **不与 MIUIX 冲突**，也 **不会建议换成 Navigation 3**：

- skill 明确不打 Material 3 / 设计系统分。MD3 vs MIUIX 组件选型、`MiuixTheme`、桥接 `MaterialTheme` **不进 0–100**。
- 路由是 **miuix-nav 0.9.4** 的 `NavDisplay` / `rememberNavBackStack` / `NavKey`，不是 `androidx.navigation3`。playbook 里的 `dropUnlessResumed`、`entryDecorators`、`ResultEventBus` **不适用**，未当缺陷。
- 同一逻辑的 MD3 / MIUIX 双文件只记一次（两边都有则两处路径都列）。**不建议合并或删掉任一主题。**

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 7/10 | 35% | solid | 列表 key / remember 筛选做得好；动画值多在 composition 读 |
| State management | 8/10 | 25% | solid | VM + `collectAsStateWithLifecycle` 一致；dialog 未 saveable |
| Side effects | 8/10 | 20% | solid | 无 composition 导航/IO；吃一份动画从点击启动，合格 |
| Composable API quality | 7/10 | 20% | solid | 复用件有 `modifier`；`rememberStatusUi` 名不副实 |

`overall = 7*0.35 + 8*0.25 + 8*0.20 + 7*0.20 = 7.45` → **75**

## Critical Findings

无 Blocker（无跨 phase 回写、无 composition 里 `backStack.add`、无 plain `collectAsState()`）。

1. **Performance: 手势/庆祝动画把 `Animatable` / `animate*AsState` 读在 composition**
   - Why it matters: 每一帧走重组，而不是只走 layout/draw。
   - Evidence: `FoodDetailScreen.kt`（`Modifier.scale(bounceScale.value)` / `.offset(y = floatOffset.value.dp)` / `.alpha(floatAlpha.value)`）；`MiuixFoodDetailScreen.kt` 同构；`Common.kt` `CheckSwitch` 的 `.offset(x = thumbOffset)`；`StatsScreen.kt` 柱高 `(84 * animRatio).dp`。
   - Fix direction: `graphicsLayer { scaleX/Y; translationY; alpha }` 或 `Modifier.offset { IntOffset(...) }`；柱状图用 `drawBehind` / `layout` 读进度。
   - References: https://developer.android.com/develop/ui/compose/performance/phases · https://developer.android.com/develop/ui/compose/animation/value-based

2. **Performance: 统计页部分聚合未 `remember`（MD3 + MIUIX）**
   - Why it matters: 每次重组扫一遍消耗列表。
   - Evidence: `StatsScreen.kt` `consumedThisWeek` / `consumedThisMonth`；`MiuixStatsScreen.kt` 同构。同文件的 `dailyTrend` / `categoryShare` / `topConsumed` 已经 `remember` 了。
   - Fix direction: `remember(consumption) { ... }`，与现有聚合对齐。
   - References: https://developer.android.com/develop/ui/compose/performance/bestpractices

3. **Android Launch UX: Android 12+ 静态启动图标可能发糊（不计分）**
   - Why it matters: `windowSplashScreenAnimatedIcon` 解析到 adaptive-icon / PNG，走 108dp 预渲染再放大。
   - Evidence: `app/src/main/res/values/themes.xml` → `@mipmap/ic_launcher`；`mipmap-anydpi-v26/ic_launcher.xml` 是 `<adaptive-icon>`；无 `drawable-v31` 的 `<animated-vector>`。
   - Fix direction: 主题名不变，API 31+ 解析到空 `<animated-vector>` 包一层**另一个名字**的 vector（不要自引用）。
   - References: https://developer.android.com/develop/ui/views/launch/splash-screen · https://issuetracker.google.com/issues/520672537

## Adjacent Findings

### Android Launch UX

- Status: **risky**
- 见 Critical Finding 3。不扣四类分数，但进优先修复。

### Testing / Preview / Focus / KMP / Paging

- UI tests / `@Preview` / screenshot：**none observed**
- Focus / TV / D-pad：**none observed**（手机 App，可接受）
- KMP：**Android-only**，不扣分
- Paging 3：**not present**

## Category Details

### Performance — 7/10

**Ceiling check**

- Strong Skipping: **on**（Kotlin 2.4.10 / Compose BOM 2026.01.01，默认 SSM）
- Compiler diagnostics used: **no**（无 `gradlew`/JDK）
- Unstable shared types from compiler: n/a
- Qualitative 7；ceiling none；applied **7/10**

**What is working**

- 身份列表普遍 `key = { it.id }`（食品、归档、分类管理、首页紧急项）
- 列表筛选/排序大多 `remember(...)`（`FoodListScreen` / `MiuixFoodListScreen` / 首页 `urgent` / 消耗流水 `sorted`）
- 筛选箭头用 `graphicsLayer { rotationZ }`，动画读在 draw
- 无 `onSizeChanged` / `onGloballyPositioned` 回写
- 无 `derivedStateOf` 假优化
- Release：R8 minify + shrink，无 baseline profile（不单独重罚）
- `enableEdgeToEdge()` 已开

**What is hurting the score**

- 吃一份 / CheckSwitch / 统计柱：动画值在 composition 读（见 Critical 1）
- 统计本周/本月消耗未 remember（见 Critical 2）
- `rememberStatusUi` 每次分配 `StatusUi`，函数名有 `remember` 却没有 `remember()`
- `HorizontalPager(beyondViewportPageCount = 3)` 四 Tab 常驻组合——产品决策（连滑要中间页），记一笔成本，不重罚
- `usedLocations` / 若干 Chip `items(...)` 无 key（短列表）
- `FoodItem` 等 data class 无 `@Immutable`（SSM 下惩罚轻；源码推断）

**Animation performance signals**

- Status: **risky** — composition-phase animated reads（详情庆祝、CheckSwitch 滑块、统计柱高）
- 筛选箭头 `graphicsLayer`：clean

**Paging list signals**

- Status: **not present**

**Evidence**

- `app/src/main/java/com/agon/app/ui/screens/FoodDetailScreen.kt` — `scale`/`offset`/`alpha` 读 `Animatable.value` · References: https://developer.android.com/develop/ui/compose/performance/phases
- `app/src/main/java/com/agon/app/ui/components/Common.kt` — `CheckSwitch` `.offset(x = thumbOffset)` · References: https://developer.android.com/develop/ui/compose/performance/bestpractices
- `app/src/main/java/com/agon/app/ui/screens/StatsScreen.kt` — 柱 `height((84 * animRatio).dp)`；`consumedThisWeek` 未 remember · References: https://developer.android.com/develop/ui/compose/performance/bestpractices
- `app/src/main/java/com/agon/app/ui/screens/FoodListScreen.kt` / `MiuixFoodListScreen.kt` — `items(filtered, key = { it.id })` 正确 · References: https://developer.android.com/develop/ui/compose/lists
- `app/src/main/java/com/agon/app/MainActivity.kt` — `beyondViewportPageCount = 3` · References: https://developer.android.com/develop/ui/compose/performance/bestpractices

### State Management — 8/10

**What is working**

- 业务状态在 `AppViewModel` / `FoodRepository`；UI 不碰 DataStore
- 全部 Flow 用 `collectAsStateWithLifecycle()`，**零** `collectAsState()`
- 筛选/搜索 `rememberSaveable`；多选 `selectedIds` 在 VM
- `rememberNavBackStack<AppRoute>(AppRoute.Main)` 显式超类型；`AppRoute` 为 `@Serializable sealed interface : NavKey`（data object / data class）
- 栈在 `MainApp`，ViewModel 不持有 `backStack`
- `navigate` 前 `lastOrNull() == route` 防重复 contentKey

**What is hurting the score**

- 对话框显隐多为 `remember { mutableStateOf(false) }`，旋转丢失（可接受，轻微）
- `FoodItem.daysLeft` 读 `LocalDate.now()`，过 0 点不重组（产品可接受）

**Nav3 playbook（已适配 miuix-nav）**

- 未混用 Nav2 `NavHost`
- 未在 composition body 调 `backStack.add`
- `dropUnlessResumed`：**N/A**（androidx.navigation3 API，miuix-nav 无此符号）

**Paging load-state signals**

- Status: **not present**

**Evidence**

- `MainActivity.kt` — `rememberNavBackStack<AppRoute>` + 事件回调里 `backStack.add` · References: https://developer.android.com/develop/ui/compose/state-saving
- 各 `*Screen.kt` — `collectAsStateWithLifecycle` · References: https://developer.android.com/topic/libraries/architecture/coroutines#lifecycle-aware
- `AppRoute.kt` — 顶层 `@Serializable` keys · References: https://developer.android.com/guide/navigation/navigation-3

### Side Effects — 8/10

**What is working**

- Snackbar / 撤销用 `LaunchedEffect` + 稳定 key；`LaunchedEffect(Unit)+collect` 有注释（consume 会改 key 取消协程）
- `EmptyState` 用 `MutableTransitionState` + `LaunchedEffect(appear)` 入场，合理
- 吃一份动画从 **onClick** 里 `rememberCoroutineScope().launch`，属手势驱动，不是 target-driven 误用
- 无 composition 里导航、无 `DisposableEffect` 泄漏迹象

**What is hurting the score**

- 无明显系统性副作用误用。未给 9：部分 `LaunchedEffect(Unit)` 靠约定而非结构化生命周期（已有注释补偿）

**Animation side-effect signals**

- Status: **clean**（驱动方式正确；性能问题记在 Performance）

**Paging side-effect signals**

- Status: **not present**

**Evidence**

- `MainActivity.kt` — undo `LaunchedEffect(Unit) { filterNotNull().collect }` · References: https://developer.android.com/develop/ui/compose/side-effects
- `FoodDetailScreen.kt` — `playEatAnimation()` 仅从按钮 onClick · References: https://developer.android.com/develop/ui/compose/animation/value-based
- `ConsumptionLogScreen.kt` — 删除撤销 collect 模式 · References: https://developer.android.com/develop/ui/compose/side-effects

### Composable API Quality — 7/10

只评 `ui/components/` 复用件，不评叶子 Screen。

**What is working**

- `StatusBadge` / `FoodAvatar` / `LocationTag` / `QuantityStepper` / `FoodCard` / `CheckSwitch` / `EmptyState` / `ExpiryCalendarCard` 均有 `modifier: Modifier = Modifier` 并落到最外层
- 有意义的默认（头像 size、CheckSwitch enabled）
- 未把 `MutableState<T>` 当复用 API 参数

**What is hurting the score**

- `rememberStatusUi` 不 `remember`，名不副实
- `FoodCard` 参数顺序：回调在 `modifier` 前（skill：数据 → modifier → 其余可选）
- `CheckSwitch` 未暴露 `animationSpec`（内部自绘控件，轻扣）

**Evidence**

- `ui/components/Common.kt` `rememberStatusUi` · References: https://developer.android.com/develop/ui/compose/performance/bestpractices
- `ui/components/Common.kt` `FoodCard` 签名 · References: https://developer.android.com/develop/ui/compose/layouts/basics#modifiers
- `ui/components/Common.kt` 各组件 `modifier` 转发 · References: https://developer.android.com/develop/ui/compose/modifiers

## Prioritized Fixes

1. 详情「吃掉一份」、`CheckSwitch`、统计柱：动画读改到 `graphicsLayer` / lambda `offset` / draw（MD3 与 MIUIX 详情各改一处）。
2. `StatsScreen` / `MiuixStatsScreen`：本周/本月消耗 `remember(consumption)`。
3. Splash：`drawable-v31` 空 `<animated-vector>` 包一层独立 vector（不改 Compose）。
4. `rememberStatusUi` 真正 `remember(status, dark, isMiuix)`。
5. 位置 Chip `items(usedLocations, key = { it })`。

**明确不做（本审计）**

- 不换 miuix-nav → Navigation 3
- 不删/合并 MIUIX 屏幕
- 不引入 Paging / Hilt / 为审计加 `@Stable` 大改模型

## Notes And Limits

- 单模块 Android App；无 KMP
- Compiler reports 未跑 → 稳定性/skippable% 不作为硬证据；confidence Medium
- 未打开 Layout Inspector，重组次数未实测
- 双主题重复实现会放大「同一逻辑改两处」的维修成本，这是产品选择，不扣 Compose 分
- 建议 follow-up：`material-3` skill 已做过视觉审计（`docs/audits/2026-07-31-md3-audit.md`）；本文件只覆盖重组/状态/副作用/复用 API
