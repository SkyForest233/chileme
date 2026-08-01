# CLAUDE.md — 家庭零食柜（Pantry App）项目工作指引

本文件是 AI 助手与开发者在本项目中工作的总入口。**每次开始工作前先阅读本文件，再按需查阅对应标准文档。**

---

## 1. 项目概况

- **应用名**：吃了么（app_name）；applicationId `com.chileme.pantry`；代码包名仍为 `com.agon.app`（namespace 不变，两者分离是有意为之）
- **定位**：本地优先的家庭食品库存管理 App，自动计算过期日期、临期提醒、减少食物浪费
- **技术栈**：Kotlin 2.2 + Jetpack Compose + Material 3，单 Activity + Navigation Compose，DataStore 持久化，ML Kit 中文 OCR
- **设计风格**：薄荷绿单色系（参考 Focus 类 App 截图），详见 `docs/DESIGN_SPEC.md`
- **当前版本**：v2.1（功能范围见 `docs/REQUIREMENTS.md`）

## 2. 标准文件索引（docs/）

| 文件 | 内容 | 何时查阅 |
|---|---|---|
| `docs/REQUIREMENTS.md` | 需求规格：功能清单、需求边界（明确不做的功能）、验收标准 | 新增/修改功能前必读；判断需求是否越界 |
| `docs/ARCHITECTURE.md` | 技术架构：分层结构、数据模型、数据流、导航路由表、依赖清单 | 改动数据层/导航/新增依赖前必读 |
| `docs/DESIGN_SPEC.md` | 设计规范：配色、圆角、间距、字体层级、组件规范、动效规范 | 新建/修改任何 UI 前必读 |
| `docs/WORKFLOW.md` | 开发流程：从需求到交付的标准执行步骤、代码规范、构建与验证、常见错误处理 | 每次开发任务开始前必读 |

## 2.5 已安装 Skill 与审计

- `.claude/skills/material-3/`：Material Design 3 实现与审计 skill（hamen/material-3-skill v1.1.1）。做 UI 相关任务时可参考其 SKILL.md 与 references/。
- 审计报告存放于 `docs/audits/`（如 `2026-07-31-md3-audit.md`）。修复审计问题时对照报告的 file:line 引用与优先级列表。

## 3. 开发日志（devlog/）

- 位置：`devlog/`，按日期命名：`devlog/YYYY-MM-DD.md`
- 索引：`devlog/INDEX.md` 汇总所有日志与当前待办状态
- **记录规则（AI 助手必须遵守）**：
  1. 每次完成一轮开发任务（构建成功后），在当天日志文件中追加记录；当天文件不存在则新建
  2. 日志内容包含：✅ 已完成事项（具体到功能点和改动文件）、📋 待办事项、⚠️ 已知问题/技术债、💡 决策记录（为什么这么做）
  3. 同步更新 `devlog/INDEX.md` 的日志列表和“当前待办总览”
  4. 待办事项完成后，在新日志中标记完成并从 INDEX 待办总览中移除

## 4. AI 助手工作说明（每次任务的标准动作）

1. **读指引**：先读本文件，确认任务涉及哪些标准文档并阅读
2. **对需求**：对照 `docs/REQUIREMENTS.md` 确认需求在范围内；超出范围的需求先与用户沟通确认，确认后更新需求文档再开发
3. **按规范开发**：遵循 `docs/ARCHITECTURE.md`（分层/数据流）与 `docs/DESIGN_SPEC.md`（UI 规范），流程按 `docs/WORKFLOW.md` 执行
4. **验证**：每轮改动完成后必须执行 `build_apk()` 确保编译通过；失败则修复后重新构建，不得以失败状态结束
5. **记日志**：构建成功后按第 3 节规则写开发日志、更新 INDEX
6. **同步文档**：若本次改动影响了需求范围、架构、设计规范或流程，同步更新对应 docs 文件，保持文档与代码一致

## 5. 关键约束（速查）

- 代码包名（namespace）固定 `com.agon.app`，所有 Kotlin 文件必须在此包或子包下；applicationId 为 `com.chileme.pantry`，不要混淆
- FileProvider authority 一律用 `${applicationId}.fileprovider`（Manifest）/ `${context.packageName}.fileprovider`（代码），禁止硬编码
- 只用 material3，禁止混用 material2 导入
- 数据持久化统一走 `FoodRepository`（DataStore + kotlinx-serialization），UI 不直接碰 DataStore
- 删除类操作一律走归档（Archive），不直接物理删除库存记录
- 主题为 MD3 种子色方案（MaterialKolor 生成），配色方案定义在 `ui/theme/Palettes.kt`（AppPalette 枚举）；`Color.kt` 仅保留状态语义色；状态色（安全/临期/过期）通过 `rememberStatusUi()` 获取
- 所有布尔开关一律使用 `ui/components/Common.kt` 的 `CheckSwitch`（打勾/打叉样式），禁止使用 material3 Switch
- 构建前确认 `strings.xml` 的 app_name 为“吃了么”，不得回退为占位名
