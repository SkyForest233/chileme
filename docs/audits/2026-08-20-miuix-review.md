# Miuix 设计合规性审查报告

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
