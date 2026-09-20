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

**15 套配色方案（`ui/theme/Palettes.kt` · AppPalette 枚举，食物主题命名）**

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

**统计图表调色板（`ui/components/app/AppText.kt` 的 `appChartColors()`，2026-09-16 第 7 对合并统计页时从 `StatsScreen.rememberChartColors` 搬进组件层）**：v2.3 起全部取自语义色角色，随主题种子色与动态取色自动适配，**禁止硬编码 hex**。两主题各一份 8 色清单：MD3 用 primary/tertiary/secondary/inversePrimary/各 container/outline；**Miuix 色板没有 `tertiary` 与 `inversePrimary`**，第 3、4 色用容器色与容器前景色替代（合并前 `rememberChartColorsMiuix` 就是这么写的，照抄不统一）。这是「屏幕侧取色」唯一被承认的例外，故 `appPrimaryColor()` / `appPrimaryContainerColor()` / `appHighestContainerColor()` 一并放宽为 public；弱化色仍走 `AppMutedText`，不开放访问器。

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

## 4. 组件规范（优先复用 `ui/components/`）

| 组件 | 文件 | 用途 | 规则 |
|---|---|---|---|
| `StatusBadge` | `Badges.kt` | 状态徽章 | 胶囊 + 图标 + 文字；颜色来自 StatusUi |
| `LocationTag` | `Badges.kt` | 位置标签 | 📍 + 文字小胶囊；location 为空时不渲染 |
| `FoodAvatar` | `FoodAvatar.kt` | 食品头像 | 有照片显示圆角图，否则 emoji 圆形底 |
| `FoodCard` | `FoodCard.kt` | 列表主卡片 | 头像+名称+位置标签+日期+新鲜度条+数量步进器 |
| `QuantityStepper` | `QuantityStepper.kt` | 数量增减 | 胶囊容器；仅数字 AnimatedContent 竖直滑，单位固定 |
| `SelectIndicator` | `Controls.kt` | 多选勾选指示 | 长按进入多选时列表项左侧的勾选圈 |
| `CheckSwitch` | `Controls.kt` | 布尔开关 | 项目特色打勾/打叉样式；**全项目布尔开关一律用它，禁用 material3 Switch** |
| `EmptyState` | `Controls.kt` | 空态 | 大 emoji + 标题 + 副标题，居中 |
| `rememberStatusUi` / `urgencyDotColor` | `StatusUi.kt` | 状态色源 | 三态与四档紧急度的唯一取色入口，屏幕代码禁止写死状态色 |
| `DataCorruptBanner` | `DataCorrupt.kt` | 数据损坏告警条 | 首页顶部；按 key 粒度说明影响 + 恢复/放弃入口 |
| `MiuixDialog` | `MiuixDialog.kt` | Miuix 弹窗封装 | 基于 `WindowDialog`；**不要传 `defaultWindowInsetsPadding = false`**（库已处理 IME） |

新建可复用 UI 时先查本表，避免重复实现。新组件按职责放进 `ui/components/` 下对应文件（状态色 → `StatusUi.kt`、小胶囊 → `Badges.kt`、通用控件 → `Controls.kt`……），**不要再新建大杂烩文件**（原 `Common.kt` 已于 2026-09-16 拆完），并同步更新本表。

**悬浮胶囊导航（FloatingPillNav，NavChrome.kt，v2.6 规范）**：居中悬浮、primaryContainer 胶囊容器；内部 4 个等宽槽位（76dp × 48dp，满足最小触摸目标）；背后一枚 primary 胶囊指示器用 spring(dampingRatio=0.8) 滑到选中槽位；**所有 Tab 常显标签**（MD3 always show labels）：图标 20dp 上、labelSmall 标签下竖排，选中项加粗；颜色用 MotionEasing.Standard 250ms 渐变。FAB 为 primary 实心圆胶囊（仅图标）。

**动效缓动（v2.6 起强制）**：全项目 tween 一律引用 `ui/theme/Motion.kt` 的 MotionEasing token，禁止无缓动 tween 与散落 CubicBezierEasing 字面量。约定：进入 = EmphasizedDecelerate（250~400ms）、退出 = EmphasizedAccelerate（200ms）、屏内状态变化 = Standard；指示器/物理位移可用 spring。

**Shape token（v2.6 起强制）**：屏幕代码禁止 `RoundedCornerShape(N.dp)` 魔法数字（全圆 `RoundedCornerShape(50)` 除外），一律引用 `MaterialTheme.shapes.*`（AppShapes：extraSmall 8 / small 12 / medium 16 / large 24 / extraLarge 28dp）；细进度条统一胶囊形。

### 4.1 App 级双主题外壳（`ui/components/app/`）—— 组件清单与关键约定

> **本节是这份清单的唯一事实源**（2026-09-17 起）。此前同一份清单在 `CLAUDE.md` §5、
> `docs/ARCHITECTURE.md` §5「App 级组件层」与本文件里各存一份手抄值，三份互相漂移
> （CLAUDE 里 93% 的组件名 ARCHITECTURE 已有，而 ARCHITECTURE 那条还漏了 `AppStatusCard`）。
> 现在：**清单只在这里**，`ARCHITECTURE.md` 只留「为什么要有这一层」，`CLAUDE.md` 只留「必须/禁止」一句话 + 指针。
>
> 组件列由源码生成（2026-09-17 快照：12 文件 / 3,075 行#11a 拆出 `AppColors.kt`、#11b 拆出 `AppSnackbar.kt` / `AppBarActions.kt` / `AppMessageScreen.kt` ⇒ 现 16 文件，`wc -l` 口径；**现值一律见 doc-metrics「App 级组件层」那行** —— 2026-09-19 复核发现这两个数已与实测差 1 行，故此后不再手抄），复核命令：
> ```bash
> for f in app/src/main/java/com/agon/app/ui/components/app/*.kt; do
>   echo "$(basename $f): $(grep -oE '^(internal |public )?(fun|val|class|enum class|data class) [A-Za-z][A-Za-z0-9]*' "$f" \
>     | sed -E 's/^(internal |public )?(fun|val|class|enum class|data class) //' | sort -u | tr '\n' ' ')"
> done
> ```
> **新增/删除组件时同批更新本表**（`CLAUDE.md` §3 记录规则已立此条）。
> ⚠️ 括号里的行数一律 **`wc -l` 口径**（2026-09-19 起与表头那句话统一，此前 #11a 两行写的是 +1 的数，已改）；且它是**快照**：`AppText.kt` / `AppColors.kt` / `AppChrome.kt` / `AppSnackbar.kt` / `AppBarActions.kt` /
> `AppMessageScreen.kt` 六行为 2026-09-19 #11a、#11b 当天实测，其余行仍是 09-17 的数
> ⇒ 全目录现值一律看 `tools/doc-metrics.sh` 的「App 级组件层」那行，本表这一列别拿来当结论引用。

| 文件 | 组件（多数行生成于 2026-09-17；#11a、#11b 碰过的行是当天现跑上面那条命令） | 用途 |
|---|---|---|
| `AppChrome.kt` (224 行) | `AppScaffold` `AppTopBar` | 页面骨架与顶栏（含多选态的关闭/返回分流）。09-19 #11b 把撤销条宿主、顶栏动作族、整屏消息页各拆成一个文件；`AppBarIconButton` 跟着动作族走了 —— 它是 `private fun`，同包跨文件不可见，留在原地就是编译失败 |
| `AppSnackbar.kt` (128 行) | `AppSnackbarForm` `AppSnackbarHost` `AppSnackbarHostState` `AppSnackbarPlacement` `rememberAppSnackbarHostState` | 撤销/提示条：双主题宿主容器、落点与形态。`UndoSnackbar.kt` 的 `SwipeDismissSnackbarHost` **没有**一起搬：它被 `MainApp.kt` 与 4 个屏幕直接 import，搬它要改 6 处 import，与「同包零改动」不是一回事（记在 ROADMAP #11 备注）（09-19 #11b 拆出） |
| `AppBarActions.kt` (123 行) | `AppArchiveAction` `AppBarIconButton` `AppDeleteAction` `AppDestructiveAction` `AppEditAction` `AppSelectAllAction` | 顶栏 5 个动作入口 + 它们共用的 `AppBarIconButton` 底座（`AppChrome.kt` 的 `AppBarNavIcon` 也调它）。行内动作 `AppEditRowAction` / `AppDeleteRowAction` 仍在 `AppListRow.kt`：它们长在卡片行上，不是顶栏槽位（09-19 #11b 拆出） |
| `AppMessageScreen.kt` (76 行) | `AppMessageScreen` | 整屏消息页（自带标题栏 + 一个动作按钮），被当导航目标用。**刻意不与 `EmptyState` 合并** —— 后者是嵌在列表末尾的引导块（判定见 ROADMAP #11「明确不做」）（09-19 #11b 拆出） |
| `AppSurface.kt` (353 行) | `AppCard` `AppPaddedCard` `AppCardTone` `AppStatusCard` `AppStatCard` `AppStatTone` `AppSection` `AppHintText` `AppStatsListMetrics` `appStatsListMetrics` | 卡片与分区外壳、统计卡、列表度量 |
| `AppListRow.kt` (406 行) | `AppListRow` `AppActionRow` `AppCardRow` `AppSectionHeader` `AppLocationIcon` `AppEditRowAction` `AppDeleteRowAction` | 列表行（三种形态）、分区标题、行内动作 |
| `AppControls.kt` (376 行) | `AppSearchField` `AppFilterChip` `AppChipTone` `AppFilterToggle` `AppFilterSectionLabel` `AppStepperPill` | 搜索框、筛选胶囊/开关、数量步进器 |
| `AppText.kt` (212 行) | `AppTextScale` `AppText` `AppEmojiText` `AppMutedText` | 语义字号档位 + 文字组件；**取色访问器已于 09-19 #11a 移出去**，别再往这里加 |
| `AppColors.kt` (164 行) | `appSurfaceColor` `appMutedColor` `appErrorColor` `appPrimaryContainerColor` `appOnPrimaryContainerColor` `appPrimaryColor` `appFaintColor` `appHighestContainerColor` `appChartColors` | 跨主题取色口子（一个语义角色一个函数）；守卫 `AppColorLocationTest` 钉住「只此一个文件」 || `AppButtons.kt` (188 行) | `AppBigButton` `AppEditButton` `AppWideButton` `AppAddItemButton` | 大按钮（emoji + 文案 + 角标）、整宽按钮、新增入口 |
| `AppOptionDialog.kt` (188 行) | `AppOptionDialog` `AppOptionSpec` | 选项弹窗（导出格式选择 / 恢复来源选择） |
| `AppFormDialog.kt` (173 行) | `AppFormDialog` `AppFormFieldSpec` | 带输入框的表单弹窗 |
| `AppConfirmDialog.kt` (134 行) | `AppConfirmDialog` | 确认弹窗（MD3 `AlertDialog` 槽位 / Miuix `WindowDialog` + 等宽两个 `TextButton`） |
| `AppInfo.kt` (133 行) | `AppDetailRow` `AppDivider` `AppLinearProgress` `AppHistoryNote` | 详情行、分隔线、进度条、带图标的弱化说明 |
| `AppIme.kt` (85 行) | `stickyImePadding` | **粘性 IME 避让**（MD3 输入弹窗焦点切换时不坠） |
| `AppBatchMoveDialog.kt` (211 行) | `BatchMoveLocationDialog` | 批量「移动存放位置」弹窗（原包根 `AppDialogs.kt`，09-17 搬入本层） |

**关键约定（为什么这么设计 / 已踩过的坑）** —— 按文件分组，改这些组件前先读对应那条：

- **`AppChrome.kt`**
  - `AppSnackbarHostState`：MD3 与 Miuix 的 `SnackbarHostState` 是**两个不相干的类型**，容器对外只暴露
    `showUndoSnackbar(): Boolean` —— 免得两个主题的 `SnackbarResult` 顺着签名漏回屏幕层。
    `isMiuix` 参与 `remember` key：切主题时换容器并让 `LaunchedEffect` 重启，否则撤销条会弹到**已卸载**的宿主上。
  - `AppSnackbarPlacement`：`SystemBars` = 二级页（`navigationBarsPadding()` + 24dp）；
    `FloatingNav` = 带悬浮导航栏的 Tab 页（抬 **84dp**）。`AppSnackbarForm`：`UndoCountdown` = 可滑掉 + 倒计时环 /
    `Plain` = 朴素 MD3 `SnackbarHost`（`AppScaffold` 另有 `snackbarForm` 参数把它传下去）。落位**只算一次**（原本三个分支各写一遍，再加形态维度就成 2×2 四份，分散写容易漏改）。
  - `AppScaffold` 已取代 `ManageScreens.kt` / `MiuixManageScreens.kt` 里那两份私有的 `*ManageScaffold`（09-16 第 6 对时删除）。
  - `AppTopBar` 的 `onClose` / `selectionMode`（多选态顶栏）：私有 `AppBarNavIcon` 里 **`onClose` 优先于 `onBack`**，
    两者皆 null 时导航槽**不渲染任何东西**（与两版原来的空槽逐字等价）；`selectionMode` **只让 MD3 换底色** `surfaceContainer`。
  - `AppEditAction` / `AppDeleteAction` / `AppDestructiveAction` 共用私有 `AppBarIconButton`，字形按语义传入。
  - `AppSelectAllAction` 的字形**不对称，照抄两版**：MD3 恒为 `SelectAll`、只换无障碍描述；Miuix 已全选时换成 `Close`。
  - `AppMessageScreen` = 无顶栏的兜底屏（错误/空态）。
- **`AppSurface.kt`**
  - `AppPaddedCard` 可传 `onClick`（= 可点卡片），`tone: AppCardTone` 决定 MD3 底色档；`AppCard` 另有 `miuixCornerRadius`。
  - `AppStatCard` + `AppStatTone`：首页三张统计卡，配色按语义在内部取。首页与统计页的统计卡**同构不同参**
    （`onClick` 可空 / `emojiSize` / `valueSpacing` / `valueScale`）；MD3 侧 `onClick` 为 null 时**退回 `Surface`** ——
    保持合并前 `MiniStat` 无水波纹、无按钮语义。
  - `AppSection`（节标题 + 卡片外壳）：MD3 标题在卡片**内**、Miuix 是卡片**外**的库 `SmallTitle` 且文案更短，
    所以是 `cardTitle` / `sectionTitle` **两个参数**；`titleSpacing` 只在 MD3 侧生效。
  - `AppStatsListMetrics` + `appStatsListMetrics()`：两版把列表水平边距放在 `contentPadding` 还是放在 item 上，
    **分工相反**，故三个值必须一起给。
  - `AppHintText` 自 `AppMutedText` 出现后，是它的 `Hint` 档特例。
- **`AppListRow.kt`**
  - `AppListRow`（emoji + 右侧强调文本）**不认识领域类型**：「可删/不可删」由调用方判断后传 `onDelete` 或 `trailingTag`。
  - `AppActionRow` = 头像槽位 + 恢复/删除两个图标按钮；`AppCardRow` = 前导槽 + 标题 + 可选副标题 + 尾部槽
    （Miuix 侧圆角 **24dp**）；`AppDeleteRowAction` 可点时危险色、禁用时最弱色。
- **`AppControls.kt`**
  - `AppSearchField`：MD3 用 `OutlinedTextField`（圆角 50 + 放大镜）/ Miuix 用 `InputField`（走 label）。
  - `AppFilterChip`：两版都用 material3 `FilterChip`，**只分流色板与标签字号**；`AppChipTone` 决定选中态容器色，
    其中**只有 `Primary` 两版都显式指定选中态文字色**，其余吃 material3 默认。
  - `AppFilterToggle`（筛选胶囊：计数文案 + 转 180° 的箭头 + 激活态配色）：**箭头弹簧两主题各一套**
    （MD3 `MotionSpring.expand<Float>()` / Miuix 库的 `folmeSpring<Float>(0.95f, 0.2f|0.3f)`），但 `AnimatedContent` 只写一遍。
  - `AppStepperPill`：减号两版都用 material 字形（两套图标库无对应关系）。
- **`AppText.kt`**
  - `AppTextScale` = **13 档语义字号表**（Hero / Emphasis / Heading / Body / Meta / Hint / Value / Label /
    SectionTitle / Action / ItemTitle / Tag / OptionTitle → 两主题各自的 TextStyle）。
    `Action` 原名 `Link`，第 5 对合并时发现「筛选胶囊文案」与「首页链接」是同一映射后改名。
    `OptionTitle` 是**首个两主题落在不同档位的条目**（MD3 `bodyMedium` = Meta 档 / Miuix `body1` = Body 档）——
    照抄两版原样，**不强行统一**。
  - `AppEmojiText` 刻意**不指定 style**：两主题「不传 style」时的默认正文样式不同，指定反而会丢掉差异。
  - `appChartColors()` / `appHighestContainerColor()`：**图表调色板是「屏幕侧取色」唯一被承认的例外**，
    故 `appPrimaryColor` / `appPrimaryContainerColor` 由 `internal` 放宽为 `public`。
    Miuix 色板**没有** `tertiary` / `inversePrimary`，两份 8 色清单照抄不统一。
- **`AppInfo.kt`**
  - `AppHistoryNote` = 「归档中找到 N 条」这类带图标的弱化说明（图标 MD3 `History` / Miuix `Recent`）。
  - `AppLinearProgress`：**两侧宽度处理方式不同，都按合并前的原样保留** —— MD3 调用处原本显式
    `fillMaxWidth() + height(8.dp) + clip(圆角 50)`，故这三项收进组件；Miuix 上游 `LinearProgressIndicator`
    （v0.9.4-rc01，`ProgressIndicator.kt:88-91`）**内部自带** `.fillMaxWidth().height(height)`，
    调用方不传宽度也是满宽 ⇒ 抽象前后一致，不会变宽变窄。
    ⚠️ `FoodCard.kt` 的两处进度条（Miuix 侧 `MiuixLinearProgressIndicator` / MD3 侧 `LinearProgressIndicator`）仍自己分流且参数不同（**6dp** + `weight(1f)`），属未收编的重复；
    收编会改变视觉，是行为改动而非纯重构，需两主题真机复测（登记在 `devlog/INDEX.md` 待办）。
- **`AppFormDialog.kt` / `AppOptionDialog.kt` / `AppBatchMoveDialog.kt`（弹窗三条铁律）**
  - **Miuix 侧 `content` 必须是单一根节点**：库把 title / summary / `content()` 放进一个**不带
    `verticalArrangement` 的 Column**，两个平级节点之间是 **0dp**（2026-09-17 真机复测踩到）。
  - **Miuix 弹窗动作按钮一律 `TextButton`，主要动作传 `textButtonColorsPrimary()`**（蓝底白字胶囊）：
    不传 colors 会和「取消」同为浅灰。上游 `DialogSection.kt` 7/7 弹窗都是这个写法。
  - 以上两条由 `MiuixDialogContentTest` 静态拦截。
  - `AppFormDialog` + `AppFormFieldSpec`：两主题的**截断时机不同、刻意不桥接**。
  - `AppOptionSpec` 刻意**不是 data class**（它带 lambda，`equals` 无意义）；图标分 `md3Icon` / `miuixIcon`
    两个参数，因为两套图标库没有对应关系。`AppOptionDialog` 一次吸收了「导出格式选择」「恢复来源选择」
    两对共 **4 份**实现（MD3 `AlertDialog` + `Surface(onClick)` 选项行 / Miuix `MiuixDialog` + 36dp 圆形图标徽章行 + 整宽取消按钮）。
- **`AppIme.kt`**：`stickyImePadding()` = 粘性 IME 避让。焦点在同一弹窗的两个输入框之间切换时平台会
  `restartInput`、输入法窗口整个消失再出现，`WindowInsets.ime` 瞬时归零 ⇒ 居中弹窗上下坠一下。
  粘性避让在 inset 变小时先按住 `holdMillis`（默认 300ms）再平滑落回，焦点切换因此零位移；
  代价是主动收起键盘时弹窗晚 300ms 才落回居中。**只用于 MD3 弹窗**（三处：`AppFormDialog` /
  `AppBatchMoveDialog` / `SettingsCloudDialogs` 坚果云 —— #10a-1 前在 `SettingsScreen`）；**Miuix 侧无法同样处理**（上游无任何 IME 平滑能力，
  唯一开关 `defaultWindowInsetsPadding = false` 会连带关掉 `navigationBarsPadding` / `captionBarPadding`）。
  详见 `docs/ARCHITECTURE.md` §5「键盘避让」与 `devlog/2026-09-17.md` §2–§6。

**新屏幕怎么用这一层**（规范性要求，与 `CLAUDE.md` §5.1 一致）：外壳一律用上面的组件，
**不要再新建 `Miuix*Screen.kt`**，**别在屏幕里写 `if (isMiuix)`**；缺哪块就按同一套分流样板补进对应文件并更新本表。

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
| 撤销 Snackbar | 两主题 6 秒后自动消失；MD3 单行正文 + 右侧 History 圆环（去指针、数字居中），MIUIX 库自带滑掉 | 6000ms |

> ⚠️ 本表原有「滑动删除 | `SwipeToDismissBox`，仅 EndToStart」一行，**2026-09-17 核查时删除**：该交互 v2.3 起已被长按多选取代（见 `docs/REQUIREMENTS.md` F5、`docs/ARCHITECTURE.md` §5「批量操作」），实测 `SwipeToDismissBox` **只剩撤销 Snackbar 在用**（6 处全在 `ui/components/UndoSnackbar.kt`）。留着这行会让人以为列表还能滑删。

## 6. 交互与反馈原则

- 破坏性操作（清空、彻底删除）必须 AlertDialog 二次确认，确认按钮用 error 色
- 可逆操作（批量归档、减库存、删消耗记录）用 Snackbar + 撤销；6 秒后自动消失，两主题均可左右滑关掉。MD3 用 `SwipeDismissSnackbarHost`：自绘 Material History 圆环 path（去指针），圆心对齐按钮中心再叠粗倒计时。不用 `Icons.Rounded.History`（指针还在、左边箭头算进 bounds）。消耗记录撤销按删除前下标插回，带 `animateItem` 位移。消耗记录页额外抬高 24dp + nav bar。MIUIX 库自带胶囊「撤销」+ `canSwipeToDismiss`
- 异步结果（OCR、导入导出）用 Snackbar 告知成功/失败
- 所有数据屏必须处理空态；禁用态按钮置灰（如数量为 0 时的“吃掉一份”）
- 页面主内容用 LazyColumn / verticalScroll，适配小屏与折叠屏
- 大屏适配（v2.3 起）：NavHost 外层约束内容最大宽 840dp 居中（`AppNavGraph.kt` 的 `AppNavHost` 外层 Box），平板/折叠屏展开态不拉伸
- 触摸目标（v2.3 起强制）：所有可点击元素 ≥48dp——IconButton 不得用 Modifier.size 缩小容器（只缩小内部 Icon）；CheckSwitch 已内置 minimumInteractiveComponentSize
- 无障碍语义：CheckSwitch 用 toggleable(Role.Switch)；底栏 Tab 用 selectable(Role.Tab)；批量操作按钮的 contentDescription 需含目标名称（如"增加 零食 阈值"）

## 7. 主题风格切换（v2.8 起）

- App 支持两套「主题风格」：**Material 3**（默认，现状）与 **MIUIX**（小米 HyperOS 风格）。
- 状态：`ThemeStyle` 枚举（`ui/theme/ThemeStyle.kt`）+ DataStore key `theme_style`；`LocalThemeStyle` CompositionLocal 由 MainActivity 下发。
- 切换入口：设置页「外观」分组的「主题风格」——MD3 侧用 SegmentedButton，Miuix 侧用 RadioButtonPreference。
- **根级主题切换 + MaterialTheme 桥接（阶段二起）**：MainActivity 在 MIUIX 模式下包 `MiuixRootTheme`（`MiuixTheme` + 桥接 `MaterialTheme`），让未迁移的 MD3 页面与 `ui/components/` 复用组件仍可经 `MaterialTheme.colorScheme` 取到 Miuix 配色；桥接映射见 `ui/theme/MiuixRootTheme.kt`（缺失角色用最接近角色近似）。
- 迁移进度（v2.8 起，2026-09-16 校正）：
  - **已 Miuix 化（全部屏幕除编辑页）**：设置页、首页、食品列表、食品详情、归档页、管理三页（阈值/分类/位置）、**统计页**，以及底部导航（悬浮/全宽）、FAB、`ui/components/` 复用组件（StatusBadge/LocationTag/QuantityStepper/EmptyState/FoodCard/DataCorruptBanner）。统计页的**图表是 `Canvas` + `layout` 自绘**（与主题无关，两版逐字相同，合并后只有一份），外壳与组件（Scaffold/TopAppBar/Card/Text/Icon/SmallTitle）走 Miuix。
  - **已合并为单文件双主题（2026-09-16，第三批 #3；八对收官）**：消耗记录页 / 归档页 / 食品详情页 / 首页 / 食品列表页 / 管理页三合一 / 统计页 / 设置页 —— `Miuix*Screen.kt` 双胞胎 **8 → 0**；`screens/` 下**当时**为 **9 个屏幕文件**（含从未有双胞胎的编辑页）+ **8 个 `*State.kt`**。第 6 对之后 `AppNavGraph.kt` 已不含主题分支，第 8 对之后 `NavChrome.kt` 也不含了。
    - **口径（复核跑 `bash tools/doc-metrics.sh`）**：⚠️ **本行的三个数是 2026-09-17 的快照，#10a-1 后又变了** （10a-1 弹窗抽到同包 3 个新文件 ⇒ 除 `*State.kt` 的文件从 9 个变 12 个；**10a-2 又加 4 个**（两套 body / 备份节 / 两个 MD3 专用小组件）⇒ **#10e 又加 5 个**（编辑页的封面 / 名称 / 字段 / 阈值 / 底栏五个区块文件）⇒ 现 **24 个**（其中 **15 个**不是屏幕））。现值一律看 doc-metrics 的「屏幕目录 ui/screens（不含 *State.kt）」「App 级组件层」两行，本行只作历史快照保留：屏幕本体（不含 `*State.kt`）**9 文件 4,209 行**、组件层 `ui/components/app/` **12 文件 3,075 行**、渲染层合计 **7,284 行**；相对 09-16 之前的基线（17 文件 7,541 行）：屏幕本体 **-44%**、渲染层合计 **-3.4%**（2026-09-17 实测）。
      09-16 收官时记的是 4,204 / 2,746 / 6,950（-8%）—— **当时记得没错**（在 `5788ae2` 上复核即为这三个数），是此后变了：09-17 的 IME 修复与弹窗搬家让渲染层净增 **334** 行（新增 `AppIme.kt` 85 + `AppBatchMoveDialog.kt` 211，改动 `AppFormDialog` / `AppConfirmDialog` / `SettingsScreen`；`git diff --stat 5788ae2 HEAD -- app/src/main/java/com/agon/app/ui/` 可复核）。
⚠️ 原验收「4,500 量级 / -24%」**不成立**；「-24%」「4,500 量级」「7,205 行 / 16 文件」「7,541 行 / 17 文件」四个数字**均已作废且彼此不可换算**（后两者是同日两个不同文件集口径）。复盘见 `devlog/2026-09-16.md` §22 与 `docs/ROADMAP.md`「#3 收官」。
    - **两个刻意的例外（别当缺陷去「修」）**：① **设置页 body 保留两套**（`Md3SettingsBody` → `ui/screens/SettingsBodyMd3.kt`；`MiuixSettingsBody` → `ui/screens/SettingsBodyMiuix.kt`；#10a-2 起各自一个文件）—— 两版排版习语根本不同（MD3 是滚动 `Column` + `Surface` 分组卡片，Miuix 是 `LazyColumn` + 库的 Preference 组件；664 行里只有 **287 行逐字相同**，八对最低），去重发生在 9 个弹窗（其中 5 个收进 `AppConfirmDialog` / `AppOptionDialog`）与骨架上，故该对收缩率只有 **-20%**；② **统计页图表**是 `Canvas` + `layout` 自绘、与主题无关（合并前就是一份语义两份拷贝，合并只删拷贝、无处可缩 ⇒ 该对 **-55%**），且只此一屏用 ⇒ **不进通用组件层**。
    - **逐对过程、行数账、「哪一对带来了哪个组件」都不在本文件维护**：过程见 `devlog/2026-09-16.md` §15–§22（含每对净行数与预测漂移复盘），组件清单与关键约定见**本文 §4.1**（唯一事实源）。
  - **刻意保留 MD3+桥接**：编辑页（`DatePicker` 为 MD3 特有、无 Miuix 对应）、`CheckSwitch`（项目特色打勾/打叉，规范禁止 material3 Switch，自绘且颜色桥接）。
  - **单实现铁律（2026-09-16 起取代「双实现铁律」）**：屏幕文件（`*Screen.kt` / `*Screens.kt`）必须调 `remember*UiState` 复用状态容器，**禁止在 UI 文件里重写聚合计算**（`ScreenParityTest` 静态拦截，原名 `MiuixParityTest`——合并后文件名不再带 `Miuix` 前缀，按前缀枚举会让刚合并的屏幕逃出守卫，故扩到全部屏幕文件；此前 `MiuixStatsScreen` 手抄过一份统计逻辑，导致 `StatsStateTest` 测的是 MIUIX 下不执行的代码）。**新屏幕不再建 `Miuix*Screen.kt` 双胞胎**，主题差异一律走 `ui/components/app/` 的骨架组件；已合并的屏幕由该测试的 `MergedScreens` 守着不许回退。
  - **已知缺口**：MIUIX 风格下**没有配色方案入口**（设置页 Miuix 分支 `MiuixSettingsBody`，`ui/screens/SettingsBodyMiuix.kt`，只有深色模式/动态取色/悬浮导航），15 套 `AppPalette` 选不了，且 MD3 下选好的配色切到 Miuix 后无提示地失效——根因是 `MiuixRootTheme` 只消费 `darkMode` + `dynamicColor`，没有种子色通道。2026-09-16 第 8 对合并后，这条非对等已写进 `SettingsScreen.kt` 的文件头 KDoc（「已知非对等…勿再声明完全对等」），不再靠两份文件各自的措辞表达；功能本身**用户已指示暂缓**（见 `devlog/INDEX.md`）。
  - **导航双形态**：新增「悬浮导航」开关（`floating_nav`，默认 true）。MD3 悬浮=自绘 `FloatingPillNav`（图标+标签）、非悬浮=MD3 `NavigationBar`；MIUIX 悬浮=Miuix `FloatingNavigationBar`（仅图标）、非悬浮=Miuix `NavigationBar`（全宽图标+文字）。
- Miuix 主题由 `ThemeController` 驱动，语义对齐 MD3 侧：动态取色→Monet（keyColor=null 跟随壁纸），否则按 darkMode 映射 System/Light/Dark。
- Miuix 组件 API 一律以 `.claude/skills/miuix` pinned source（v0.9.4-rc01）为准，禁止凭 MD3 记忆臆造参数/颜色 token。
