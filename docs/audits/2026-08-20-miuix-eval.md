# Miuix 主题风格评估报告

> ## ⚠️ 2026-09-17 状态批注（读本文前必看）
>
> 本文是 **v2.8 立项前的评估**，正文按本仓「只加批注、不改写原文」的约定原样保留。四件事必须先说清：
>
> **① 本文的推荐没有被采纳。** §5 建议「立即落地方案 A」（不引入 miuix 库、只用现有 MaterialTheme/MaterialKolor
> 管线做 HyperOS 风格），实际走的是**先升工具链、再引入真 Miuix 组件**（接近本文列为高代价的方案 B/C）：
> v2.8 起 `top.yukonga.miuix.kmp:miuix-ui:0.9.4-rc01` 已在依赖里，8 个屏幕做过 Miuix 实现。
> 本文 §3.1 判定「直接引入会编译失败、需先大幅升级工具链」是**对的**，升级也确实做了 ——
> 现值 Kotlin **2.4.10** / AGP **9.3.1** / Gradle **9.7.1** / compileSdk **37** / minSdk **26** /
> composeBom **2026.08.00** / targetSdk **36**（`gradle/libs.versions.toml`、`app/build.gradle.kts`）。
> 故 §1 记的 Kotlin 2.2.21 / AGP 8.10.1 / Gradle 8.11.1 / JDK 17 / compileSdk 36 / minSdk 24 / BOM 2026.01.01
> **全部已失效**，只在读「当时为什么这么判」时有意义。
>
> **② 本文 §3.2 的 RC 风险判断仍然成立**：上游至今没有 `v0.9.4` 稳定 tag，仓库仍钉在 `0.9.4-rc01`；
> 「等上游发稳定版再升」这条挂在 `devlog/INDEX.md`「需用户决策」。
>
> **③ 本文最有预见性的一条已经解决。** §3.4 担心「同时维护两套运行时可切换 = 双份组件实现，长期双倍维护成本」
> —— 2026-09-16 已把 `Miuix*Screen.kt` 双胞胎 **8 对全数合并为单文件双主题**（外壳下沉到 `ui/components/app/`，
> `AppNavGraph.kt` / `NavChrome.kt` 零主题分支），加一个功能的边际成本从「改两个文件 + 一处 if/else」降到「改一个文件」。
> 唯一刻意保留双份的是设置页 body（两版排版习语根本不同，664 行里只有 287 行逐字相同，是八对最低）。
>
> **④ 本文的量化基线不可再引用**：「UI 代码约 5448 行」「`MaterialTheme.*` 约 390 处」「10 个 UI 文件」都是
> 双实现之前、且 `Common.kt`（983 行）尚未拆分时的口径。今日实测：包根 + `ui/` 合计 **11,978** 行，
> 其中屏幕本体 9 文件 **4,209** 行、`ui/components/app/` 12 文件 **3,075** 行；`Common.kt` 已于 09-16 拆成 8 个
> 按职责命名的文件（**该文件已不存在**）。`MaterialTheme.colorScheme` 现 **184** 处、`MiuixTheme.colorScheme` **75** 处。
>
> **⑤ §3.3 引的三条规范冲突已由用户决策解除**：`CLAUDE.md`「只用 material3、禁止混用」已改为
> 「只用 material3 + Miuix 两套 UI 体系（v2.8 起）」；工具链升级经用户同意后执行；`DESIGN_SPEC.md` 仍以 MD3 语义为主，
> Miuix 侧规范见其 §7。


> 日期：2026-08-20
> 目标：评估「吃了么」当前项目，判断如何增加「MIUIX（HyperOS）风格」的主题切换。
> 依据：已安装的 `.claude/skills/miuix`（Miuix Skill，证据基线 v0.9.4-rc01 @ 4a6b750b）+ 本仓库 docs/ 与源码。

---

## 1. 现状梳理（Material 3）

- **技术栈**：Kotlin 2.2.21 / AGP 8.10.1 / Gradle 8.11.1 / JDK 17；Compose BOM 2026.01.01（material3 + icons-extended）；compileSdk 36、minSdk 24。
- **主题机制**：单 Activity（`MainActivity`）在 `setContent` 中包一层 `AgonAppTheme`（`ui/theme/Theme.kt`）。
  - `AgonAppTheme(darkTheme, dynamicColor, palette)` → `MaterialTheme(colorScheme = animateColorScheme(targetScheme), shapes = AppShapes)`。
  - 配色由 MaterialKolor 从 `AppPalette` 种子色（15 套食物主题）以 `PaletteStyle.TonalSpot` 生成；深浅模式/动态取色/种子色三态在 DataStore 持久化。
  - 状态语义色（安全/临期/过期 + 日历圆点）在 `Color.kt` 硬编码，不随主题变化。
- **组件**：全项目 10 个 UI 文件 import `androidx.compose.material3`；`MaterialTheme.colorScheme/typography/shapes` 约 **390 处**引用。
- **规模**：UI 代码约 5448 行（9 屏 + Common.kt/ExpiryCalendar.kt + 主题 3 文件 + MainActivity）。

## 2. MIUIX 是什么（对照 Skill）

Miuix 是**独立**的 Compose Multiplatform 组件库（HyperOS 设计语言），与 Material 3 API **完全不同**：

- 依赖坐标：`top.yukonga.miuix.kmp:miuix-ui[-android]:0.9.4-rc01`（另有 miuix-preference / miuix-icons / miuix-blur / miuix-nav）。
- 主题：`ThemeController`（`ColorSchemeMode.System/Light/Dark/Monet*`）+ 根 `MiuixTheme`，提供 `MiuixTheme.colorScheme.*` / `MiuixTheme.textStyles.*` —— **不提供** `MaterialTheme`。
- 组件：`Scaffold/TopAppBar/NavigationBar/Card/Button/Switch/TextField/OverlayDialog/WindowDialog/SwitchPreference/ArrowPreference/Dropdown/…`，各有 `*Defaults`。
- 关键约束：`MiuixTheme` 的 colorScheme 与 `MaterialTheme.colorScheme` 是两套 token，不能互相替换。

## 3. 关键发现（决定方案可行性的硬事实）

1. **工具链冲突**：Miuix 0.9.4-rc01 基线是 Kotlin 2.4.10 / AGP 9.3.1 / Compose Multiplatform 1.12.0-rc01；本项目 Kotlin 2.2.21 / AGP 8.10.1。Kotlin 编译器无法读取更高版本产物的 metadata，直接引入 `miuix-ui` 会**编译失败**，需要先大幅升级工具链。
2. **RC 而非稳定版**：上游尚无 `v0.9.4` 稳定 tag，当前只有 `0.9.4-rc01`；稳定基线是 `v0.9.3`（Compose MP 1.11.1 / Kotlin 相对更旧，但同样可能高于本项目）。
3. **项目规范冲突**：`CLAUDE.md` 明文「只用 material3，禁止混用」；`ARCHITECTURE.md` 明文「技术栈与版本不得随意升级」；`DESIGN_SPEC.md` 整套规范（形状 token、MD3 motion、AppShapes 等）是 MD3 语义。
4. **替换成本巨大**：若把 9 屏 + Common.kt + 导航 + 弹窗全部换成 Miuix 组件，等于重写约 5448 行 UI 代码，且 `MaterialTheme.*` 的 390 处引用需逐一改为 `MiuixTheme.*` / Miuix 组件。同时维护「MD3 与 Miuix 两套运行时可选切换」意味着**双份组件实现**，长期双倍维护成本。

## 4. 三条可行路径对比

| 方案 | 做法 | 优点 | 代价/风险 |
|---|---|---|---|
| **A. 轻量「风格」切换（推荐先做）** | 保留 material3 组件树，新增「主题风格」设置项：Material 3（现状）⇄ MIUIX 风格。MIUIX 风格用一套 HyperOS 化的 ColorScheme（小米蓝种子色）+ 更方正的圆角 + 更紧凑的字体层级，仍通过现有 MaterialTheme/MaterialKolor 管线渲染 | 立即可编译、零破坏、真「切换」、符合项目规范、与 AppPalette/深浅/动态取色无缝兼容 | 是「Miuix 视觉风格」而非「Miuix 官方组件」，组件形态仍是 MD3 组件 |
| **B. 全量 Miuix 组件迁移** | 引入 miuix-ui，根改 `MiuixTheme`+`ThemeController`，逐屏替换为 Miuix 组件 | 真正的 HyperOS 组件与交互 | 需先升级 Kotlin/AGP/Compose（违反「不得随意升级」）、重写 ~5448 行、RC 版不稳定、与「只用 material3」冲突、无运行时「双风格」切换（是替换不是切换） |
| **C. 渐进混合** | 根包 `MiuixTheme`（满足祖先要求），把 Miuix colorScheme 桥接成 MaterialTheme 以维持现有屏可渲染，再挑价值高的页面（如设置页）先换 Miuix Preference 组件 | 逐步用上真 Miuix、风险低于 B | 仍需先解决工具链升级；桥接层本身是「混用」灰色地带；设置页外其余屏仍是 MD3 组件 |

## 5. 建议

1. **立即落地方案 A**：符合「主题风格切换」的字面诉求，风险最低、当轮即可交付并构建通过；实现为「主题风格」枚举（`MATERIAL3` / `MIUIX`）+ DataStore 持久化 + 设置页「外观」分组新增风格切换。
2. **方案 B/C 作为后续独立专项**：若之后确要「真 Miuix 组件」，先单独立项评估 Kotlin/AGP/Compose 升级影响面与 RC 风险，再分页面渐进迁移。

---

*（本报告为评估结论；代码实现待方案确认后执行。）*
