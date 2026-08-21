# 设计规范（DESIGN_SPEC）

> 风格：薄荷绿单色系（v2.1 起，参考 Focus 类 App 设计截图）· 干净、克制、大量胶囊圆角

## 0. 风格要点（来自参考设计的分析结论）

1. **单色系**：整个 App 以"深森林绿 → 薄荷绿 → 浅绿白"的同色阶梯为主，仅用少量浅蓝作对比点缀，拒绝多彩
2. **背景是极浅绿白**（非纯白），卡片用近白色浮起，靠色差而非阴影分层
3. **胶囊无处不在**：柱状图是两端全圆的胶囊柱、进度条是胶囊、按钮/Chip/导航全部胶囊
4. **悬浮胶囊导航**：底部导航不是全宽 NavigationBar，而是居中悬浮的薄荷绿胶囊，选中项为深绿胶囊（图标+文字），未选中仅图标
5. **数据可视化风格**：粗胶囊柱状图、分段胶囊条、日历色块热力图；图表色一律使用绿色深浅阶梯

## 1. 配色

**主题采用 MD3 种子色方案（v2.2 起）**：主题不再手写完整 ColorScheme，由 MaterialKolor（`com.materialkolor:material-kolor:4.0.1`）从种子色生成：`rememberDynamicColorScheme(seed, isDark, PaletteStyle.TonalSpot)`。屏幕代码一律引用 `MaterialTheme.colorScheme.*`，不得写死主题色值。

**7 套配色方案（`ui/theme/Palettes.kt` · AppPalette 枚举，食物主题命名）**

| 方案 | 种子色 | 意象 |
|---|---|---|
| 🌿 薄荷 MINT（默认） | `#1F5C46` | 深森林绿，延续 v2.1 参考设计 |
| 🍵 抹茶 MATCHA | `#5C7C2E` | 黄绿色，清新 |
| 🍊 蜜橘 CITRUS | `#B35310` | 暖橙，食欲感 |
| 🍑 蜜桃 PEACH | `#B0455B` | 粉红，温柔 |
| 🫐 蓝莓 BLUEBERRY | `#2F5DA8` | 冷静蓝 |
| 🍠 香芋 TARO | `#6B549E` | 紫色，甜品感 |
| 🍫 可可 COCOA | `#6E4A33` | 大地棕，沉稳 |
| 🍓 草莓 STRAWBERRY | `#A93238` | 鲜红，活泼 |
| 🍯 蜂蜜 HONEY | `#8A6C00` | 金黄，温暖 |
| 🫒 橄榄 OLIVE | `#57652F` | 灰绿，自然 |
| 💜 薰衣草 LAVENDER | `#7D5CB0` | 淡紫，柔和 |
| 🥤 苏打 SODA | `#00696E` | 青色，清爽 |
| 🍣 鲑鱼 SALMON | `#B3543F` | 橘粉，温润 |
| 🍇 葡萄 GRAPE | `#5F3D8C` | 深紫，浓郁 |
| ⚫ 黑芝麻 SESAME | `#474A45` | 中性灰，极简 |

（v2.2 后共 15 套方案）

规则：
- 种子色一律选中深色调，MD3 tonal palette 自动保证浅/深两套主题的对比度与易读性
- 优先级：动态取色（Android 12+，用户开启） > 用户选择的 AppPalette > 默认 MINT
- 新增方案：只需在 AppPalette 枚举加一行（名称/emoji/种子色），设置页预览与切换自动生效
- 设置页预览（PaletteSwatch）：用同一 API 实时生成预览色板，三色拼盘 = primary / primaryContainer / tertiaryContainer，选中态描边 + 打勾角标；动态取色开启时置灰

**状态色是硬编码的语义色（刻意为之，不是技术债）**：安全/临期/过期必须在任何主题（含动态取色）下保持稳定可辨，因此不走种子色生成，直接在 `Color.kt` 定义固定色值。这是 MD3 官方同款做法（error 色也不随动态取色变化）。

**深浅色判定**：`rememberStatusUi()` / `urgencyDotColor()` 用 `MaterialTheme.colorScheme.background.luminance() < 0.5f` 判断当前主题深浅，**禁止用 `isSystemInDarkTheme()`**——App 支持强制浅色/深色，与系统设置可能不一致。

**日历圆点四档紧急度（ExpiryUrgency，v2.4）**：三态 status 在日历上区分度不足（阈值内全是一片黄），小圆点按剩余天数分四档：🔴 已过期 → 深橙 ≤3 天 → 🟡 琥珀黄 阈值内临期 → 🟢 安全。圆点色用高饱和专用色（SafeDot/WarnDot/UrgentDot/DangerDot），不用为文字设计的 content 色（小面积下深色调难辨）。

**状态色（三色标签，只能通过 `rememberStatusUi(status)` 获取）**

StatusUi 提供三个颜色槽位，按用途严格区分：
- `container` / `content`：徽章、文字、进度条等**大面积/文字**场景（content 保证 WCAG 文字对比度）
- `dot`：日历标记、图例等 **≤8dp 小圆点**专用高饱和色（content 色调过深，在小圆点上红/棕/绿不可辨——v2.4.1 修复）
  - 浅色：鲜绿 `#2E9E5B` / 琥珀橙 `#F59E0B` / 鲜红 `#E5484D`；深色：`#4ADE80` / `#FBBF24` / `#FF6B6B`
  - 规则：小面积状态色块一律用 `dot`，禁止用 `content` 给小圆点上色

| 状态 | 语义 | 浅色容器/内容 | 深色容器/内容 |
|---|---|---|---|
| SAFE 安全 | 薄荷绿 | `#BDEBD1` / `#12503A` | `#1C4A38` / `#A5E8C6` |
| EXPIRING 临期 | 橙黄 | `#FFE1B3` / `#7A4A00` | `#5A4218` / `#FFDDA8` |
| EXPIRED 过期 | 红 | `#FFD9D4` / `#8C1D18` | `#5C2320` / `#FFB4AB` |

**统计图表调色板（StatsScreen.rememberChartColors）**：v2.3 起全部取自 `MaterialTheme.colorScheme` 语义角色（primary/tertiary/secondary/inversePrimary/各 container/outline），随主题种子色与动态取色自动适配，**禁止硬编码 hex**。

动态取色开启时主题色跟随壁纸，但状态色保持固定（语义色不可变）。

## 2. 形状与间距

| 元素 | 规范 |
|---|---|
| 主卡片（FoodCard、设置分组、统计卡、次级卡片） | `MaterialTheme.shapes.large`（24dp，定义于 Theme.kt AppShapes） |
| Hero 卡（详情页状态卡） | `MaterialTheme.shapes.extraLarge`（28dp） |
| 输入框 | `MaterialTheme.shapes.medium`（16dp） |
| （v2.3 起形状一律引用 shapes token，仅胶囊 RoundedCornerShape(50) 与装饰性小圆角例外） | |
| 按钮、Chip、步进器、搜索框 | 胶囊（RoundedCornerShape(50)） |
| 页面水平内边距 | 20dp |
| 列表项间距 | 10~12dp；分组间距 16dp |
| 卡片内边距 | 16~20dp |
| 列表底部 contentPadding | +96dp（避免 FAB/底栏遮挡） |
| 阴影 | 主卡片 elevation ≤ 1dp，其余 0dp（靠色彩分层）；悬浮导航栏 shadowElevation 6dp |
| 柱状图柱子 | 胶囊形（RoundedCornerShape(50)），宽度约列宽 62%，最小高度 14dp（无数据 8dp 底座） |
| 排行榜进度条 | 胶囊，高 10dp，primaryContainer 色 |

## 3. 字体层级

- 页面标题：TopAppBar title + FontWeight.Bold
- 统计数字：headlineMedium/Small + ExtraBold
- 卡片标题（食品名）：titleMedium + SemiBold
- 正文：bodyMedium；辅助信息：bodySmall + onSurfaceVariant
- 标签/徽章：labelSmall~labelLarge + SemiBold/Bold
- 中文日期格式统一用 `LocalDate.cn()`（yyyy年M月d日），不自行拼接

## 4. 组件规范（优先复用 `ui/components/Common.kt`）

| 组件 | 用途 | 规则 |
|---|---|---|
| `StatusBadge` | 状态徽章 | 胶囊 + 图标 + 文字；颜色来自 StatusUi |
| `FoodAvatar` | 食品头像 | 有照片显示圆角图，否则 emoji 圆形底 |
| `FoodCard` | 列表主卡片 | 头像+名称+位置标签+日期+新鲜度条+数量步进器 |
| `QuantityStepper` | 数量增减 | 胶囊容器；仅数字 AnimatedContent 竖直滑，单位固定 |
| `EmptyState` | 空态 | 大 emoji + 标题 + 副标题，居中 |
| `LocationTag` | 位置标签 | 📍 + 文字小胶囊；location 为空时不渲染 |

新建可复用 UI 时先查本表，避免重复实现；新组件加入 Common.kt 并更新本表。

**悬浮胶囊导航（FloatingPillNav，MainActivity.kt，v2.6 规范）**：居中悬浮、primaryContainer 胶囊容器；内部 4 个等宽槽位（76dp × 48dp，满足最小触摸目标）；背后一枚 primary 胶囊指示器用 spring(dampingRatio=0.8) 滑到选中槽位；**所有 Tab 常显标签**（MD3 always show labels）：图标 20dp 上、labelSmall 标签下竖排，选中项加粗；颜色用 MotionEasing.Standard 250ms 渐变。FAB 为 primary 实心圆胶囊（仅图标）。

**动效缓动（v2.6 起强制）**：全项目 tween 一律引用 `ui/theme/Motion.kt` 的 MotionEasing token，禁止无缓动 tween 与散落 CubicBezierEasing 字面量。约定：进入 = EmphasizedDecelerate（250~400ms）、退出 = EmphasizedAccelerate（200ms）、屏内状态变化 = Standard；指示器/物理位移可用 spring。

**Shape token（v2.6 起强制）**：屏幕代码禁止 `RoundedCornerShape(N.dp)` 魔法数字（全圆 `RoundedCornerShape(50)` 除外），一律引用 `MaterialTheme.shapes.*`（AppShapes：extraSmall 8 / small 12 / medium 16 / large 24 / extraLarge 28dp）；细进度条统一胶囊形。

## 5. 动效规范

| 场景 | 实现 | 时长 |
|---|---|---|
| 底栏 Tab 连滑（v2.8） | `HorizontalPager` + `MotionSpring.page`（folmeSpring 0.95）；主页→统计经过食品列表 | 约 340ms 起，跨页加长 |
| 二级页转场 | miuix-nav `NavDisplay` + `NavTransitions.MiuixDefault`（两主题共用）：全宽跟手滑出 + 下层 1/4 视差；`NavDisplayEffects` 系统圆角（Leading）+ 0.5 dim | 跟手 / 弹簧 settle |
| 筛选面板/箭头 | `filterPanelEnter/Exit` + ExpandMore `rotationZ` 同一套 expand/collapse 弹簧 | 0.2s / 0.3s |
| 底栏/FAB/批量栏显隐 | 弹簧滑入滑出；多选时批量栏与底栏交叉过渡 | 弹簧 |
| 数量变化 | 仅数字 AnimatedContent 竖直滑+淡入（单位固定），方向随增减 | 180/140ms |
| 消耗记录删除 | LazyColumn `animateItem` fade，与食品列表退场同曲线 | 280/200ms |
| 配色渐变 | `animateColorScheme` + MotionEasing.Standard | 450ms |
| 到期日历换月 | slide+fade + MotionEasing | 280/220ms |
| 空态出现 | fade + scaleIn 0.96 | 280ms |
| 新鲜度条 | `animateFloatAsState` Standard | 400ms |
| 柱状图/环形图入场 | animateFloatAsState | 600~800ms |
| 吃掉一份 | 封面 scale 1→1.25→1 + emoji 上浮 72dp 渐隐 | 120/220/700ms |
| 滑动删除 | SwipeToDismissBox，仅 EndToStart，背景 errorContainer | 默认 |

## 6. 交互与反馈原则

- 破坏性操作（清空、彻底删除）必须 AlertDialog 二次确认，确认按钮用 error 色
- 可逆操作（滑删归档）用 Snackbar + “撤销”
- 异步结果（OCR、导入导出）用 Snackbar 告知成功/失败
- 所有数据屏必须处理空态；禁用态按钮置灰（如数量为 0 时的“吃掉一份”）
- 页面主内容用 LazyColumn / verticalScroll，适配小屏与折叠屏
- 大屏适配（v2.3 起）：NavHost 外层约束内容最大宽 840dp 居中（MainActivity），平板/折叠屏展开态不拉伸
- 触摸目标（v2.3 起强制）：所有可点击元素 ≥48dp——IconButton 不得用 Modifier.size 缩小容器（只缩小内部 Icon）；CheckSwitch 已内置 minimumInteractiveComponentSize
- 无障碍语义：CheckSwitch 用 toggleable(Role.Switch)；底栏 Tab 用 selectable(Role.Tab)；批量操作按钮的 contentDescription 需含目标名称（如"增加 零食 阈值"）

## 7. 主题风格切换（v2.8 起）

- App 支持两套「主题风格」：**Material 3**（默认，现状）与 **MIUIX**（小米 HyperOS 风格）。
- 状态：`ThemeStyle` 枚举（`ui/theme/ThemeStyle.kt`）+ DataStore key `theme_style`；`LocalThemeStyle` CompositionLocal 由 MainActivity 下发。
- 切换入口：设置页「外观」分组的「主题风格」——MD3 侧用 SegmentedButton，Miuix 侧用 RadioButtonPreference。
- **根级主题切换 + MaterialTheme 桥接（阶段二起）**：MainActivity 在 MIUIX 模式下包 `MiuixRootTheme`（`MiuixTheme` + 桥接 `MaterialTheme`），让未迁移的 MD3 页面与 Common.kt 复用组件仍可经 `MaterialTheme.colorScheme` 取到 Miuix 配色；桥接映射见 `ui/theme/MiuixRootTheme.kt`（缺失角色用最接近角色近似）。
- 迁移进度（v2.8）：
  - **已 Miuix 化**：设置页、首页、食品列表、食品详情、归档页、管理三页（阈值/分类/位置），以及底部导航（悬浮/全宽）、FAB、Common.kt 复用组件（StatusBadge/LocationTag/QuantityStepper/EmptyState/FoodCard）。
  - **刻意保留 MD3+桥接**：编辑页（`DatePicker` 为 MD3 特有、无 Miuix 对应）、统计页（图表自绘、Miuix 化收益低）、`CheckSwitch`（项目特色打勾/打叉，规范禁止 material3 Switch，自绘且颜色桥接）。
  - **导航双形态**：新增「悬浮导航」开关（`floating_nav`，默认 true）。MD3 悬浮=自绘 `FloatingPillNav`（图标+标签）、非悬浮=MD3 `NavigationBar`；MIUIX 悬浮=Miuix `FloatingNavigationBar`（仅图标）、非悬浮=Miuix `NavigationBar`（全宽图标+文字）。
- Miuix 主题由 `ThemeController` 驱动，语义对齐 MD3 侧：动态取色→Monet（keyColor=null 跟随壁纸），否则按 darkMode 映射 System/Light/Dark。
- Miuix 组件 API 一律以 `.claude/skills/miuix` pinned source（v0.9.4-rc01）为准，禁止凭 MD3 记忆臆造参数/颜色 token。
