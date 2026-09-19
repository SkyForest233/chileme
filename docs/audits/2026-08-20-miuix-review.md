# Miuix 设计合规性审查报告

> ## ⚠️ 2026-09-17 状态批注（读本文前必看）
>
> 本文基线是 **v2.8 刚迁完、8 对屏幕还是双实现**的时代（点名的 `MiuixHomeScreen.kt` 等 8 个文件已于
> 2026-09-16 合并时**全部删除**）。正文原样保留，逐条现状如下。
>
> 今日实测口径：`git grep -o` 数**出现次数**（不是命中行数）、作用域 `app/src/main`，
> 一键复核跑 `bash tools/doc-metrics.sh`（该脚本末尾有阳性对照：`@Composable` 158 处、虚构符号 0 处）。⚠️ **本文当年只数 8 个 Miuix 页面，今天是全仓（含 MD3 分支与组件层）
> ⇒ 绝对值不可与本文直接相比，只看方向与「修没修」。**
>
> | 本文条目 | 2026-09-17 现状 |
> |---|---|
> | **P0 颜色语义错位**（桥接 54 处 vs `MiuixTheme` 41 处；摘要色应用 `onSurfaceVariantSummary`） | ❌ **未系统性修**，仍是主要取色方式：`MaterialTheme.colorScheme` **184** 处 vs `MiuixTheme.colorScheme` **75** 处。合并后 MD3 分支用 MD3 token 是设计本身，但「Miuix 侧仍靠桥接近似映射（`tertiary = tertiaryContainer` 这类）」这条**没解决**；摘要色也没换成 `onSurfaceVariantSummary` |
> | **P0 字体层级未生效**（32 处显式 `fontSize`、**0** 处 `MiuixTheme.textStyles`） | 🟡 **部分已修，但走的是另一条路**：`MiuixTheme.textStyles` 现 **29** 处、显式 `fontSize` 降到 **24** 处；09-16 另建了跨主题的**语义字号档位表** `AppTextScale`（13 档，**96** 处引用，`ui/components/app/AppText.kt`）。即「统一字号节奏 + 随大字体缩放」的目标是用自建档位表达成的，不是全量换成 Miuix token |
> | **P1 图标体系未对齐**（**0** 处 `MiuixIcons`） | 🟡 **部分已修**：`MiuixIcons` 现 **57** 处；material icons 仍 **64** 处（MD3 分支 + 部分两套图标库无对应关系的字形） |
> | **P1 主操作按钮未用 Primary 色**（**0** 处 `buttonColorsPrimary` / `textButtonColorsPrimary`） | ✅ **已修**：`buttonColorsPrimary` **6** 处 + `textButtonColorsPrimary` **11** 处。其中弹窗那批是 **2026-09-17 当天才修的**（用户指出「Miuix 里确定/保存/添加不该是灰底」，4 处偏离改蓝底白字），并由 `MiuixDialogContentTest` 静态守卫。本文判断与上游一致：pinned tag v0.9.4-rc01 的 `DialogSection.kt` 7/7 弹窗都用 `TextButton` + `textButtonColorsPrimary()` |
> | **P1 MD3 组件残留**（MD3 `Text` / `Surface` / `LinearProgressIndicator` / `FilterChip` / `OutlinedTextField`） | ⚠️ **判定口径已失效**：09-16 合并后是「单文件双主题」，**MD3 分支里用 MD3 组件是设计本身，不再叫残留**；要判断真残留必须按分支看，数总数没意义（例：`LinearProgressIndicator` 真正的**调用**只有 4 处 —— `AppInfo.kt:83`(Miuix)/`:93`(MD3) 与 `FoodCard.kt:161`(Miuix)/`:251`(MD3)，恰好是两对分支；按字符串数会得 11，多出来的是 import 与 KDoc 提及）。本文声明的两处妥协**仍成立**：坚果云密码框用 MD3 `OutlinedTextField`（Miuix `TextField` 无密码遮蔽）、筛选 chip 无 Miuix 等价物 |
> | **P2-1 Snackbar 定位硬编码 84dp，建议按 `floatingNav` 分档** | ✅ **已做且更细**：`AppSnackbarPlacement`（`FloatingNav` → `bottom = 84.dp`；`SystemBars` → `navigationBarsPadding()` + 24dp）+ `AppSnackbarForm`（`UndoCountdown` / `Plain`）两个维度，落位只算一次（`ui/components/app/AppChrome.kt:150-165`） |
> | **P2-2 装饰性图标 `contentDescription` 给/不给不一致（装饰图标应为 null）** | ❌ **仍未逐条判定，且两份报告口径相反**：本文按 Miuix 规范说装饰图标应为 `null`，而 08-22 的无障碍批把 `contentDescription = null` 当缺陷一次补了 112 处。今日实测 **50** 处 `null`、`semantics` 仅 **1** 处 ⇒ 现状是「刻意留空」与「漏填」的混合体，**没人按规范逐条区分过**。这一条与 `devlog/INDEX.md` 待办 #7（无障碍）是同一件事的两面 |
> | **P2-3 FoodCard 选中描边手写 `Modifier.border`，未走 Miuix squircle** | 🟡 **原处已不在，同类问题换了地方**：`FoodCard` 在 09-16 合并中重构过，全仓现只剩 **1** 处 `Modifier.border`，在 `ui/components/ExpiryCalendar.kt:352`（日历选中日描边）—— 与本文那条不是同一处，squircle 能力仍未用上 |
> | **「已符合标准的部分」5 条**（页面结构 / `OverlayDialog` / Preference 组件 / 单一 `MiuixRootTheme` / 状态提升到 VM） | ✅ 判定仍成立，但第 1 条点名的文件名已全变（`Miuix*Screen.kt` 已删除，现为单文件双主题 + `ui/components/app/` 外壳） |
> | **改进优先级建议表（6 项）** | 第 3 项 ✅ 已做、第 1/2/4 项 🟡 部分、第 5 项口径已失效、第 6 项 ✅ 已做（squircle 除外）。现状待办以 `devlog/INDEX.md` 为准，本文这张表**不要再当 backlog 用** |
>
> **本文顺带暴露的一处新重复（2026-09-17 记录，未修）**：组件层已有 `AppLinearProgress`（`AppInfo.kt:76`，
> Miuix 侧走 `MiuixLinearProgressIndicator` + `ProgressIndicatorDefaults`，高度用常量 `LinearProgressHeight = 8.dp`、
> 宽度 `fillMaxWidth()`），但 `FoodCard.kt:161/251` 仍自己分流了一份进度条，且参数不同（高度 **6.dp**、
> 宽度用 `weight(1f)`）。⇒ 属**未收编的重复**，但收编会改变视觉（6dp→8dp、weight→fillMaxWidth），
> 是行为改动而非纯重构，需两主题真机复测，故未在本轮文档整理中顺手做；已登记进 `devlog/INDEX.md` 待办。


> 日期：2026-08-20
> 依据：`.claude/skills/miuix`（v0.9.4-rc01 @ 4a6b750b）的 `ui-review-workflow.md` / `design-language.md` / `color-lookup.md`
> 范围：MIUIX 模式下全部已迁移页面 + 共享组件 + 桥接层

---

## 结论摘要

MIUIX 模式已完成**功能级**迁移（页面结构、组件选择基本正确），但**视觉级**仍大量依赖「MaterialTheme 桥接 + 显式字号 + material 图标」，未真正对齐 Miuix 的**语义 token / 字体层级 / 图标体系**。核心问题是：**页面结构对了，但颜色、文字、图标仍停留在 MD3 的用法上**。

---

## 按影响排序的发现

### P0 — 颜色语义错位（影响所有页面的观感统一性）

**证据**：8 个 Miuix 页面共 **54 处** `MaterialTheme.colorScheme`（桥接），而 `MiuixTheme.colorScheme` 仅 41 处；部分页面（FoodList 12 / Stats 17 / Manage 9）几乎完全靠桥接取色。

| 问题 | 现状 | Miuix 标准 | 影响 |
|---|---|---|---|
| 摘要/辅助文字色 | 桥接 `onSurfaceVariant` → 映射成 `onSurfaceSecondary`（80% 黑，偏深） | `onSurfaceVariantSummary`（60% 黑，更浅的摘要色） | 摘要文字比 Miuix 规范偏重，层次不分明 |
| 选中态容器色 | 手动传 `primaryContainer`/`secondaryContainer` | 用对应组件 `*Defaults`（如 Dropdown 选中用 `tertiaryContainer`+`onTertiaryContainer`） | 部分选中态容器语义错位 |
| 桥接近似映射 | `tertiary = tertiaryContainer`、`onSecondaryContainer = onTertiaryContainer` 等 | 精确语义 token | 动态取色/深色下可能产生非预期的色调 |

**依据**：`color-lookup.md` §Component Defaults first——「摘要文字 → `onSurfaceVariantSummary`，优先 `BasicComponentDefaults.summaryColor()`」；「若公共组件拥有该视觉，用其 `*Defaults` 颜色工厂」。

**修复方向**：Miuix 页面内的取色从 `MaterialTheme.colorScheme` 逐步改为 `MiuixTheme.colorScheme` + 组件 `*Defaults`；桥接层只作为「未迁移页面」的过渡，不作为 Miuix 页面自身的取色来源。

---

### P0 — 字体层级未生效（全项目）

**证据**：8 个页面共 **32 处**显式 `fontSize = N.sp`，**零处**使用 `MiuixTheme.textStyles.*`。

| 页面 | 显式 fontSize 数 |
|---|---|
| MiuixHomeScreen | 9 |
| MiuixManageScreens | 9 |
| MiuixSettingsScreen | 5 |
| MiuixConsumptionLogScreen | 5 |
| MiuixFoodDetailScreen | 2 |
| MiuixStatsScreen | 2 |

**影响**：Miuix 的字体层级（`title1–title4` / `main` / `body1/body2` / `subtitle` / `footnote1/2` / `button`）完全没用上，页面用的是手写 sp，深浅色/大字体（无障碍）下不会随 `TextStyles` 缩放，且与 HyperOS 的字号节奏不一致。

**依据**：`design-language.md` §Typography Roles——「让 Miuix 组件选择 typography；显式 `Text` 从 `MiuixTheme.textStyles` 选，不要硬编码字号」。

**修复方向**：把显式 `fontSize` 替换为 `MiuixTheme.textStyles` 对应角色（卡片标题→`body1`/`title4`、摘要→`footnote2`、正文→`main`/`body2` 等）。

---

### P1 — 图标体系未对齐

**证据**：全部页面用 `material-icons-extended` + MD3 `Icon`，**零处** `MiuixIcons`。

**影响**：Material 圆角图标与 HyperOS 图标风格不同；且 MD3 `Icon` 读 `LocalContentColor`，在 Miuix 组件内需要频繁显式 `tint`（已在多处手动处理，如 `MiuixFoodListScreen` 的 FilterToggle），既啰嗦又易漏。

**依据**：`SKILL.md`——`miuix-icons` 提供 100+ 图标（Light/Normal/Regular/Medium/Demibold 5 权重）；`design-language.md` §Decision Order——优先用 Miuix 组件。

**修复方向**：逐步换 `MiuixIcons.Regular.*`（或按需 Demibold），Miuix 组件内用 Miuix `Icon`（自动读 Miuix `LocalContentColor`）。

---

### P1 — 主操作按钮未用 Primary 色

**证据**：全项目**零处** `buttonColorsPrimary()` / `textButtonColorsPrimary()`。

| 位置 | 现状 | 应 |
|---|---|---|
| 首页「一键清理 N 件过期食品」 | `Button` 默认（secondaryVariant 灰） | `buttonColorsPrimary()`（这是主操作） |
| 详情页「吃掉一份！」 | 自定义 `secondaryContainer` | 主操作，至少 `buttonColorsPrimary()` |
| 详情页「编辑食品信息」 | 自定义 `secondaryVariant` | 次要操作可保留 |

**依据**：`color-lookup.md` §Component Defaults——「蓝色实心按钮 → `ButtonDefaults.buttonColorsPrimary()`」。

**修复方向**：识别各页唯一主操作，用 primary 色突出；次要/中性操作才用默认灰。

---

### P1 — MD3 组件残留（Miuix 页面内）

| 残留 | 位置 | 说明 |
|---|---|---|
| MD3 `Text` | 详情页 13 处、统计页 14 处、管理页 9 处 | 应换 Miuix `Text` |
| MD3 `Surface` | 列表/详情/归档各 1 处 | 应换 Miuix `Surface`/`Card` |
| MD3 `FilterChip` | 列表/归档筛选、编辑页 | Miuix 无等价 chip，属**已声明妥协**，可保留但需确认颜色 |
| MD3 `LinearProgressIndicator` | 详情页、FoodCard | Miuix 有 `ProgressIndicator`，应换 |
| MD3 `OutlinedTextField` | 设置页坚果云密码框 | Miuix `TextField` 无密码遮蔽，**安全必要妥协**，保留 |

---

### P2 — 其它细节

1. **Snackbar 定位硬编码 84dp**（MainActivity）：悬浮/全宽底栏未分档，全宽态可能偏高。建议按 `floatingNav` 分两档。
2. **装饰性图标 contentDescription**：部分图标（如箭头、状态图标）给/不给 `contentDescription` 不一致，需按 `design-language.md` 规范核对（装饰图标应 `null`）。
3. **FoodCard 的 Squircle**：Miuix `Card` 自带 squircle（API 33+），但 FoodCard 里选中描边用了 `Modifier.border` 手动模拟，未走 Miuix 的 squircle 描边能力。

---

## 已符合标准的部分（无需改）

- ✅ **页面结构**：设置/统计用 `SmallTitle + Card` 分组；详情/列表/归档/管理用 Miuix `Scaffold/TopAppBar`——符合 `usage-patterns.md`
- ✅ **弹窗**：用 `OverlayDialog`（页面内）而非 `WindowDialog`——符合 `overlays-and-windows.md`
- ✅ **Preference 组件**：`SwitchPreference/ArrowPreference/RadioButtonPreference/OverlayDropdownPreference` 用法正确
- ✅ **主题归属**：根级单一 `MiuixRootTheme`，未重复包 `MiuixTheme`——符合 `setup-and-theme.md`
- ✅ **状态提升**：多选/撤销状态在 ViewModel，符合 `design-language.md` §State ownership

---

## 改进优先级建议

| 优先级 | 项 | 工作量 | 收益 |
|---|---|---|---|
| 1 | Miuix 页面取色改为 `MiuixTheme.colorScheme` + 组件 `*Defaults` | 大（逐页） | 高（语义正确 + 动态取色正确） |
| 2 | 字体层级改为 `MiuixTheme.textStyles.*` | 中（32 处） | 高（字号节奏 + 无障碍） |
| 3 | 主操作按钮改 `buttonColorsPrimary` | 小（几处） | 中（主次分明） |
| 4 | 图标换 `MiuixIcons` | 大（全项目） | 中（风格统一） |
| 5 | MD3 组件残留替换（Surface/Text/ProgressIndicator） | 中 | 中 |
| 6 | Snackbar 分档、squircle 描边等细节 | 小 | 低 |

---

*（本报告为 review-only，未改动代码。是否按上述优先级实施改进，请确认。）*
