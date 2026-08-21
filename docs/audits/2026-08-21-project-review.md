# 项目审查报告与待办 Backlog（2026-08-21）

> 对象：`SkyForest233/chileme`（"吃了么"家庭食品库存 App）
> 审查方式：代码走查 + 既有审计文档（`docs/audits/2026-08-21-fix-plan.md`、`2026-08-21-backlog.md`）核对 + devlog 交叉验证
> 结论：项目工程质量**明显高于一般个人项目**——文档体系完整、数据安全防护到位、CI 已接 lint+单测。剩余待办集中在「测试补全、双主题去重、无障碍、工程门面」四类。

---

## 0. 项目健康度小结

| 维度 | 评价 | 依据 |
|---|---|---|
| 文档 | ✅ 优秀 | CLAUDE.md + docs/（REQUIREMENTS/ARCHITECTURE/DESIGN_SPEC/WORKFLOW）四件套 + devlog + audits 全齐 |
| 数据安全 | ✅ 强 | `Decoded` 三态 + 11 处写守卫 + 损坏留档 + 备份排除 DataStore（修复后） |
| 构建/发布 | ✅ 已修 | Release 签名/env 优先、versionCode 由 CI 注入、apksigner 兜底校验（阶段 1） |
| 兼容性 | ✅ 已修 | minSdk 24→26、`NewApi/InlinedApi` 提为 error + CI lint（阶段 2） |
| 性能 | ✅ 已修 | 列表 flow 走 `rawFlow`（去重+Default 线程），密码流按密文去重（阶段 4） |
| 测试 | 🟡 起步 | 已落 `FoodModelsTest`/`CompactConsumptionTest`（26 例），CI 已接 `testDebugUnitTest`；覆盖面仍窄 |
| 工程基建 | 🟡 欠账 | 无 README/LICENSE、无 version catalog、无 androidTest |
| 维护税 | 🟡 偏高 | MD3 / Miuix 双实现 3209 行，占 screens 约 44%，重复度 67–81% |
| 无障碍 | 🔴 欠账 | 45 处 `contentDescription = null`、统计图表无 semantics |

规模：`app/src` 约 11,919 行 Kotlin；其中 `Miuix*.kt` 3,209 行。

---

## 1. 审查中发现的关键问题（除既有 backlog 外，本轮走查补充）

### 1.1 跨零点剩余天数不刷新（沿用 fix-plan 阶段 6，代码确认存在）
`FoodModels.kt:130` 的 `daysLeft` 是 `ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)`，在组合期求值。进程存活期间**跨午夜不会重算**——用户挂一晚，第二天看到的"还剩 X 天"是旧值。
- 影响：临期/过期判断错一天，首页提醒失真。属真实体验缺陷。
- 建议修法：`MainActivity` 监听 `Lifecycle.ON_RESUME`，日期变化时 bump 一个 `todayEpochDay` 状态触发重组。顺带为 `daysLeft` 注入 `Clock` 便于单测。

### 1.2 `restoreArchivedBatch` 非原子（批量撤销是 N 次独立 DataStore edit）
`AppViewModel.restoreArchivedBatch` 对每个 id 各跑一次 `repo.restoreArchived`，一次编辑触发整份 Preferences 重发。批量较大（几十条）时：
- 非原子：中途失败会留下一半已恢复/一半未恢复的中间态；
- 性能：N 次全量解码 + 重写。
- 影响：低（归档上限 200，实际批量通常个位数）。列为 P3。

### 1.3 `AppViewModel.deleteConsumption` 对 id=null 的旧记录静默跳过
`index = sorted.indexOfFirst { it.id == id }`，旧数据 id=null 时 `getOrNull(-1) → null → return`，删除无效。启动迁移 `migrateConsumptionIds()` 已兜底，故仅剩「迁移前删除」的极窄窗口。列为 P3，可顺手改。

### 1.4 依赖跨 7 个月未升（既有 backlog 1.1，确认为最高性价比技术债）
`compose-bom 2026.01.01 → 2026.08.00` 等 7 项（见下）。无 version catalog，Miuix 三坐标 + BOM 易漏改，**升前应先引入 catalog**。

> 其余既有发现（签名失效、minSdk、解码清空、主线程解码）已在 PR #3 修复，本文不再重复。

---

## 2. 待办 Backlog（按严重程度排序）

严重程度：🔴 P0 发版阻断 / 🟠 P1 高 / 🟡 P2 中 / 🟢 P3 低
难易程度：易 / 中 / 难（相对单人改动成本，含回归风险）

### 🔴 P0 · 发版前必做

| # | 事项 | 严重 | 难度 | 来源 | 状态 |
|---|---|---|---|---|---|
| B1 | 下个 Release notes 注明「签名已变更，请先在设置页导出备份、卸载旧版后再安装」。已发布 v1.0.0/v2.0/v2.0.1 是互不相同的 debug 签名，无法覆盖升级 | 🔴 | 易（纯文案） | 阶段1 | ✅ 已完成 |

### 🟠 P1 · 高价值（测试基线与运行正确性）

| # | 事项 | 严重 | 难度 | 来源 | 状态 |
|---|---|---|---|---|---|
| B2 | ~~补全单测基线~~：新增 `planRestore` 三分支、`CloudBackup.displayTime/parsePropfind`、`BackupData` v1→v2 兼容、`Decoded.orElse`/三态（新增 25 例）；把 `restoreArchived` 纯逻辑抽成无 Context 的 `planRestore` | 🟠 | 中 | 阶段5 | ✅ 已实现（2026-08-21，待 CI 验证） |
| B3 | ~~跨零点剩余天数不刷新~~：新增 `*At(today)` 可注入日期纯函数 + `LocalToday` CompositionLocal，MainActivity 在 `ON_RESUME` 刷新，UI 全部改读 `LocalToday.current`；新增跨零点/`urgencyForAt` 用例 | 🟠 | 低–中 | 阶段6 | ✅ 已实现（2026-08-21，待 CI 验证） |

### 🟡 P2 · 中价值

| # | 事项 | 严重 | 难度 | 来源 | 状态 |
|---|---|---|---|---|---|
| B4 | **无障碍补全**：45 处 `contentDescription=null` 逐一判定（装饰→保留 null，信息型→补文案）；统计柱状图/环形图容器加 `semantics` 汇总文案（MD3 + Miuix 两套） | 🟡 | 中（量大、需逐处判断） | 阶段6 + MD3审计 | 待做 |
| B5 | **升级过期依赖**（单独 PR，勿与功能混）：compose-bom 2026.01.01→2026.08.00、core-ktx 1.15→1.19、core-splashscreen 1.0.1→1.2.0、activity-compose 1.12.2→1.13.0、lifecycle 2.10→2.11、Gradle 9.6.1→9.7.1。升级后重点回归 Miuix 页面（占 44%） | 🟡 | 中（Miuix 依赖 Compose 1.12-rc01，有连带风险需独立回滚） | backlog 1.1 | 待做 |
| B6 | **引入 `gradle/libs.versions.toml`**（version catalog）——升级依赖（B5）的前置，Miuix 三坐标 + BOM 对齐 | 🟡 | 低–中（机械，需小心坐标） | backlog 基建 | 待做 |
| B7 | **README + LICENSE**：仓库公开根目录只有写给 AI 的 `CLAUDE.md`，路人无法理解项目；补带截图 README，选定开源协议 | 🟡 | 低 | 阶段6 | 待做 |
| B8 | ~~CI 首次真跑单测~~：master Build（run 32492016050）的 `Unit tests` 步骤 success，`test-report` 已上传——26 例在 CI 上真实跑绿 | 🟡 | 低 | 阶段5 | ✅ 已完成（2026-08-21 核实） |

### 🟢 P3 · 低价值 / 锦上添花

| # | 事项 | 严重 | 难度 | 来源 | 状态 |
|---|---|---|---|---|---|
| B9 | **MD3 / Miuix 双实现去重**（最大长期维护税）：抽状态层（`rememberXxxState` / VM 派生 Flow），两套 UI 只留纯渲染 | 🟢 | 难（16+ 文件；**须在 B2 测试基线就位后再动**，否则无安全网） | 阶段7 | 待讨论 |
| B10 | **`restoreArchivedBatch` 批量化/原子化**（见 §1.2） | 🟢 | 中 | 本轮走查 | 待做 |
| B11 | **`deleteConsumption` 对 id=null 旧记录兜底**（见 §1.3） | 🟢 | 低 | 本轮走查 | 待做 |
| B12 | **Miuix 0.9.4-rc01 → 稳定版回迁** | 🟢 | 低–中 | 08-20 | 等上游发版 |
| B13 | **targetSdk 36→37**（`OldTargetApi`）——会触发新一档行为变更，需完整实机回归，未上架 Play 无合规压力，**建议暂不升** | 🟢 | 难 | backlog 1.3 | 待决策 |
| B14 | 删 5 个 legacy `mipmap-*/ic_launcher.png`（minSdk 26 后已无设备使用，可减包体）——先确认无他处引用 | 🟢 | 低 | backlog 1.4 | 可选 |
| B15 | 顺手清 `ObsoleteSdkInt`（3 处 `SDK_INT>=S` 动态取色判断，minSdk 26 下仍必要，可留可清） | 🟢 | 低 | backlog 1.5 | 可选 |

### 🟢 P3 · 待评估（既有 backlog 中未定项，非本轮新增）

| 事项 | 严重 | 难度 | 说明 |
|---|---|---|---|
| 归档搜索结果的恢复操作无撤销提示 | 🟢 | 低 | 直接恢复，可接受 |
| 高对比度 / medium、high 对比度档位未提供 | 🟢 | 中 | MD3 三级对比度，非必需 |
| 统计图表无 semantics（并入 B4） | 🟢 | 中 | 并入无障碍 |
| 删除分类后孤儿记录批量重新归类入口 | 🟢 | 中 | 现回退"其他" |
| 日历视图增加周视图密度选项 | 🟢 | 低 | 体验增强 |
| 自动同步增加"仅 Wi-Fi"开关 | 🟢 | 低 | 备份体积小，暂不必要 |

---

## 3. 建议的落地顺序

1. **发版前**：B1（文案）—— 零成本，但漏掉会让老用户数据全丢。
2. **下轮开发前**：B2（测试补全）+ B8（CI 实跑）—— 双主题现状下，这是唯一防"改一处崩另一处"的安全网，也顺带把 B3 的时间可测性做掉（抽 `Clock`）。
3. **近期**：B6 → B5（先 catalog 再升依赖，单 PR 独立回滚）；B3（跨零点）；B7（README/LICENSE）。
4. **中后期**：B4（无障碍，工作量大但无风险，可分页做）；B10/B11（顺手小修）。
5. **单独立项**：B9（双主题去重，需在 B2 后做，并单独评估路径 A 抽状态层 vs B 砍一套）。
6. **明确"暂不做"**：B13（targetSdk 升档，等实机回归条件）；B14/B15（可选清理）。

> 注：本报告为审查产物，仅登记 backlog，未改动任何源码。仓库其余待办（去重、无障碍等）均可在本文件与 `devlog/INDEX.md` 之间交叉核对。
