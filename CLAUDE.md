# CLAUDE.md — 家庭零食柜（Pantry App）项目工作指引

本文件是 AI 助手与开发者在本项目中工作的总入口。**每次开始工作前先阅读本文件，再按需查阅对应标准文档。**

---

## 1. 项目概况

- **应用名**：吃了么（app_name）；applicationId `com.chileme.pantry`；代码包名仍为 `com.agon.app`（namespace 不变，两者分离是有意为之）
- **定位**：本地优先的家庭食品库存管理 App，自动计算过期日期、临期提醒、减少食物浪费
- **技术栈**：Kotlin 2.4.10 / Compose BOM 2026.08.00 + Material 3 与 Miuix（HyperOS），单 Activity + miuix-nav `NavDisplay`（底栏 Tab 仍是 HorizontalPager），DataStore 持久化。OCR 已移除
- **设计风格**：薄荷绿单色系（参考 Focus 类 App 截图），详见 `docs/DESIGN_SPEC.md`
- **当前版本**：功能演进见 `docs/REQUIREMENTS.md`；设置页展示版本固定 **v1.0**

## 2. 标准文件索引（docs/）

| 文件 | 内容 | 何时查阅 |
|---|---|---|
| `docs/REQUIREMENTS.md` | 需求规格：功能清单、需求边界（明确不做的功能）、验收标准 | 新增/修改功能前必读；判断需求是否越界 |
| `docs/ARCHITECTURE.md` | 技术架构：分层结构、数据模型、数据流、导航路由表、依赖清单 | 改动数据层/导航/新增依赖前必读 |
| `docs/DESIGN_SPEC.md` | 设计规范：配色、圆角、间距、字体层级、组件规范、动效规范 | 新建/修改任何 UI 前必读 |
| `docs/WORKFLOW.md` | 开发流程：从需求到交付的标准执行步骤、代码规范、构建与验证、**CI 静态门禁（ktlint + detekt）**、常见错误处理 | 每次开发任务开始前必读；提交前跑 `bash tools/ci-gates.sh` |
| `docs/MIUIX_UPGRADE.md` | Miuix 版本升级操作手册（上游基线查询、Version Catalog 单一版本位点、API 核对、常见坑） | 只在升级 Miuix / 工具链时读 |
| `docs/ROADMAP.md` | **结构性改造的执行顺序 + 每项的证据/验收/风险**（第三批 #1–#9；#1–#3 已完成） | 决定「下一项做什么」前必读；开工前核对该项的风险与守卫清单 |
| `tools/doc-metrics.sh` | **所有文档数字的测量口径**（一键复跑；含作用域、计数单位与零结果的阳性对照） | 文档里要写任何数字前先跑它；不要再手抄第二份口径 |

## 2.5 已安装 Skill 与审计

- `.claude/skills/material-3/`：Material Design 3 实现与审计 skill（hamen/material-3-skill v1.1.1）。做 UI 相关任务时可参考其 SKILL.md 与 references/。
- `.claude/skills/miuix/`：Miuix（HyperOS）Compose UI skill（limczhh/miuix-skill，证据基线 v0.9.4-rc01 @ 4a6b750b）。做「主题风格切换 / Miuix 组件」相关任务时参考其 SKILL.md 与 references/（组件 API 一律以 pinned source 为准，禁止凭 MD3 记忆臆造 Miuix 参数）。
- 审计报告存放于 `docs/audits/`（10 份，如 `2026-07-31-md3-audit.md`）。修复审计问题时对照报告的 file:line 引用与优先级列表。
  ⚠️ **这些是历史快照，`file:line` 一律按当时基线读**（`Common.kt` 与 8 个 `Miuix*Screen.kt` 等文件此后已删除）。
  10 份全部已在标题下加「状态批注」——**覆盖率不在此处手抄份数**，由 `bash tools/doc-metrics.sh` 的
  「顶部无任何状态批注的报告 = **0** 份」这条守着（上一版写「09-17 那轮 6 份」，实际是 **9 份**、横跨 3 个提交，
  写下它的下一个提交就让这个数字过期了 —— 这正是本仓禁止手抄计数的原因）；
  按本仓约定**只加批注、不改写原文**，故正文里的过期数字是刻意保留的，别当现状引用。

## 3. 开发日志（devlog/）

- 位置：`devlog/`，按日期命名：`devlog/YYYY-MM-DD.md`
- 索引：`devlog/INDEX.md` 汇总所有日志与当前待办状态
- **记录规则（AI 助手必须遵守）**：
  1. 每次完成一轮开发任务（构建成功后），在当天日志文件中追加记录；当天文件不存在则新建
  2. 日志内容包含：✅ 已完成事项（具体到功能点和改动文件）、📋 待办事项、⚠️ 已知问题/技术债、💡 决策记录（为什么这么做）
  3. 同步更新 `devlog/INDEX.md`：**日志列表加一行**（一天一行，只写主题与状态，细节留在当日日志）+「当前待办」节
  4. 待办完成后：从 INDEX「当前待办」移除，并把**一句话结论**追加到 INDEX「已完成里程碑」；
     若属结构性改造（`docs/ROADMAP.md` 里的 #1–#9），同步更新那份的进度总览与该项状态
  5. **写数字前先跑 `bash tools/doc-metrics.sh`**，文档里注明「实测 N（口径见该脚本）」。
     禁止在多处手抄同一个数字 —— 本仓曾因此让单测数在 91→116→117→119→120→121 之间被反复加注修正
  6. CI 结果按 run 记进当日日志（一个 push 里的多个提交共用一次 run ⇒ 记一行、其余标「同上」；
     补记台账这件事本身不再追记自己的 CI 结果，否则无限递归）

## 4. AI 助手工作说明（每次任务的标准动作）

1. **读指引**：先读本文件，确认任务涉及哪些标准文档并阅读
2. **对需求**：对照 `docs/REQUIREMENTS.md` 确认需求在范围内；超出范围的需求先与用户沟通确认，确认后更新需求文档再开发
3. **按规范开发**：遵循 `docs/ARCHITECTURE.md`（分层/数据流）与 `docs/DESIGN_SPEC.md`（UI 规范），流程按 `docs/WORKFLOW.md` 执行
4. **验证**：每轮改动完成后必须 `./gradlew assembleDebug` 通过（沙箱无 JDK/SDK 时靠 GitHub Actions）；失败则修复后重新构建，不得以失败状态结束
5. **记日志**：构建成功后按第 3 节规则写开发日志、更新 INDEX
6. **同步文档**：若本次改动影响了需求范围、架构、设计规范或流程，同步更新对应 docs 文件，保持文档与代码一致

## 5. 关键约束（速查）

> **本节每条一行、可扫读**；需要理由与细节的跟着指针去 `docs/` 对应章节读。
> **数字一律以 `bash tools/doc-metrics.sh` 的输出为准**（该脚本是全部文档数字的唯一测量口径）。

### 5.1 包名与标识

- namespace 固定 `com.agon.app`（所有 Kotlin 文件必须在此包或子包下）；applicationId 是 `com.chileme.pantry` —— **两者不同，不要混淆**
- FileProvider authority 一律用占位符：Manifest 写 `${applicationId}.fileprovider`、代码写 `${context.packageName}.fileprovider`，**禁止硬编码**
- `strings.xml` 的 `app_name` 必须是「吃了么」，构建前确认，不得回退为占位名
- **版本号锁定**：设置页「关于」里显示的版本号固定为 **v1.0**，未经用户明确指示不得更改（用户 2026-07-31 要求，此后新增功能不自行递增）。⚠️ 该约束**只**针对那个硬编码字符串；`build.gradle.kts` 的 `versionCode` / `versionName` 由 CI 注入（2026-08-21 起），两者互不影响，**不要因此把 versionCode 改回恒定值**

### 5.2 数据层

- 持久化统一走 `FoodRepository`（DataStore + kotlinx-serialization），**UI 不直接碰 DataStore**
- 删除类操作一律走归档（Archive），**不直接物理删除**库存记录
- **写守卫按 key 粒度**（2026-09-15 起）：写「用户资产型」key（items / archived / consumption / history）前**必须先 `isCorrupt(...)`**，但守卫**只覆盖本次写入真正会覆盖的 key**，主数据先判、辅助数据按需判（例：`upsert` 在 `history_entries` 损坏时跳过历史、库存照常保存）。**禁止退回 `isCorrupt(a, b, c)` 式一起判** —— 辅助数据损坏会连带锁死核心功能（`CorruptGuardTest` 静态拦截）
- **新增读 flow 一律走 `rawFlow()` / `lightFlow()`**（内含 `resilientRead()` 兜底），**禁止直接 `dataStore.data`**。理由与细则见 `docs/ARCHITECTURE.md` §5「数据完整性守卫」「读流兜底」「Flow 读取规约」

### 5.3 UI 与双主题

- 只用 **material3 + Miuix** 两套 UI 体系（v2.8 起），**禁止混入 material2 导入**；风格由 `ui/theme/ThemeStyle.kt` 的 `ThemeStyle` 枚举（MATERIAL3 / MIUIX）控制、经 `LocalThemeStyle` 下发，设置页据此切换两套实现
- 配色是 MD3 种子色方案（MaterialKolor 生成）：方案定义在 `ui/theme/Palettes.kt`（`AppPalette` 枚举）；`Color.kt` **仅**保留状态语义色；状态色（安全/临期/过期）通过 `rememberStatusUi()` 获取
- 所有布尔开关一律用 `ui/components/Controls.kt` 的 `CheckSwitch`（打勾/打叉样式），**禁用 material3 `Switch`**
- **新屏幕一律写单文件双主题**：外壳用 `ui/components/app/` 的组件 —— **清单与每个组件的关键约定见 `docs/DESIGN_SPEC.md` §4.1（唯一事实源，本节不再复述）**；**不要再新建 `Miuix*Screen.kt`**；**别在屏幕里写 `if (isMiuix)`**，缺哪块就按同一套分流样板补进组件层
- 屏幕文件（`*Screen.kt` / `*Screens.kt`）**必须调 `remember*UiState`** 复用状态容器，**禁止在 UI 文件里重写业务/聚合计算**（`ScreenParityTest` 静态拦截，原名 `MiuixParityTest`、已扩围到全部屏幕文件；表单类本地编辑态的豁免位是该测试里的 **`NoSharedStateScreens`**，现只有 `EditFoodScreen.kt`）
- 已合并的 8 个屏幕由 `ScreenParityTest.MergedScreens` 守着：既拦「双胞胎被加回来」，也拦「合并后把屏幕本体删掉」
- 含输入框的屏幕**必须消费 IME inset**（`ImeHandlingTest` 拦）：屏幕级用 `Scaffold(modifier = Modifier.imePadding())`；**MD3 输入弹窗用 `stickyImePadding()`**（`ui/components/app/AppIme.kt`）；**Miuix 弹窗不要传 `defaultWindowInsetsPadding = false`**（库已处理 IME，传了会连带关掉 `navigationBarsPadding`）。细则见 `docs/ARCHITECTURE.md` §5「键盘避让」
- Miuix 弹窗两条铁律（`MiuixDialogContentTest` 拦）：`content` **必须单一根节点**（库把 title/summary/content 放进不带 `verticalArrangement` 的 Column，平级节点之间是 **0dp**）；动作按钮一律 `TextButton`，**主要动作传 `textButtonColorsPrimary()`**（不传就和「取消」同为浅灰）
- **动屏幕文件前先 `grep -rn "<屏幕名>" app/src/test/`** —— 点名屏幕文件的静态守卫不止 `ScreenParityTest` / `ImeHandlingTest`，还有 `CorruptGuardTest`、`CompactConsumptionTest`（2026-09-16 第 4 对合并就撞上过；`CorruptGuardTest` 是**按 4 空格缩进截函数体**的，函数一搬家就红）
- **刻意保留 MD3 的只有三处，勿擅自迁移**：编辑页（`DatePicker` 无 Miuix 对应）、`CheckSwitch`（项目特色打勾/打叉）、设置页 body（`Md3SettingsBody` / `MiuixSettingsBody`，两版排版习语根本不同）
- 统计页图表是 `Canvas` + `layout` 自绘、与主题无关，只此一屏用 ⇒ **不进组件层**；`appChartColors()` 是「屏幕侧取色」**唯一**被承认的例外
- Miuix 组件 API 一律以 `.claude/skills/miuix` 的 pinned source（**v0.9.4-rc01**）为准，**不得凭 MD3 记忆臆造**参数或颜色 token
- 迁移进度、已知缺口（MIUIX 侧无配色入口，用户已指示暂缓）与导航双形态见 `docs/DESIGN_SPEC.md` §7

### 5.4 构建、门禁与签名

- **提交前跑静态门禁**：`bash tools/ci-gates.sh`（ktlint 与 detekt **都是 block**；`GATES_MODE=report` 只看报告不拦）。规则边界与理由见 `.editorconfig` 与 `detekt.yml` 文件头，流程见 `docs/WORKFLOW.md` §3
- CI 在每个 PR 上跑同一脚本，外加 `assembleDebug` / `assembleRelease`（R8 验证）/ 单测 / lint。**沙箱无 JDK/SDK 时，CI 是唯一的编译裁判** —— ktlint 绿 ≠ 能编译
- **release 签名**：凭据缺失时构建应当**失败**而非回退 debug 签名。看到 `Release 签名凭据缺失` 报错是**预期行为**，不要通过恢复静默回退来「修复」它
- **写数字进文档前先跑 `bash tools/doc-metrics.sh`**，并注明「实测 N（口径见该脚本）」；**禁止在多处手抄同一个数字** —— 本仓曾因此让单测数在 91→116→117→119→120→121 之间被反复加注修正
