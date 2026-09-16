# chileme（吃了么）代码审查报告

> ## ⚠️ 2026-09-16 复核批注（读本文前必看）
>
> 本文基线是 `41a728f`，**其后 PR #7（`58534fd`）与 PR #8（`db5ed69`）已修掉本文大部分 P0/P1 条目**，正文按「不改写历史」原则原样保留。逐条现状：
>
> | 本文条目 | 2026-09-16 状态 |
> |---|---|
> | P0-1 IME 遮挡 | ✅ 已修（A 方案，23 处 `imePadding` + MD3 弹窗 `decorFitsSystemWindows=false`；**修法按第三方复核修正为作用于 `Scaffold` 的 modifier 而非只动 content slot**）+ `ImeHandlingTest` 守卫 + 用户真机确认 |
> | P0-2 `MiuixStatsScreen` 绕过状态层 | ✅ 已修（接回 `rememberStatsUiState`）+ `MiuixParityTest` 静态守卫 |
> | P0-3 过期浪费口径 | ✅ 已修（`calculateWastedTotal` 按件数 `sumOf { quantity }`）。**「本周」文案 / TOP5 按单位 / 分类占比三项用户指示暂缓** |
> | P0-4 冷启动主线程读快照 + 正则计数 | ✅ 已修（三方法 `suspend` + `Dispatchers.IO`；条数改为解析 JSON） |
> | P0-5 月度聚合可被一条删除 | ✅ 已修（`aggregated` 标记 + `isDeletable()` + 仓库层拒删 + 两套 UI 不给按钮） |
> | **P0-6 备份/迁移规则** | ❌ **主体论点不成立（误判）**：本文称 `device-transfer` 未排除 `datastore/`，实测该文件明确有 `<exclude domain="file" path="datastore/" />`，注释与代码一致，凭据不会随换机直传外泄。第三方复核（`2026-09-15-third-party-review-verification.md` §6）得出同一结论。**次要点成立**：`filesDir/snapshots/` 两份规则都没排除 → 每日快照会进 Android 系统云备份；另 `device-transfer` 放行 `covers/` 但排除 `datastore/`，换机后会产生一批待清理的孤儿封面。两点已记入 `docs/ARCHITECTURE.md`「备份排除规则」 |
> | P1-1 守卫靠约定 | 🟡 已改按 **key 粒度** + 「放弃损坏数据」入口 + `CorruptGuardTest`/`FoodRepositoryGuardTest`；「模板化成结构性不可能忘记」未做 |
> | P1-3 冷流收集 3 次 | ✅ 已修（`DecodeCache` + 复用 `stateIn` 流） |
> | P1-4 设置页主线程 IO | ✅ 快照/导入路径已下沉 IO |
> | P1-8 组合期 `File.exists()` + `photoPath` 绝对路径 | ❌ **仍未做**（`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`；原文的 `Common.kt` 已于 2026-09-16 拆分） |
> | P1-11 状态容器重组粒度 | ✅ 已修（`SettingsUiState` 19×`State` 精确订阅 + `SettingsActions` 窄接口） |
> | P1-2 / P1-5 / P1-6 / P1-7 / P1-9 | ❌ 仍未做（`beyondViewportPageCount = 3` 在 `NavChrome.kt:125`（原 `MainActivity.kt:923`，2026-09-16 拆分后改址）；`animateColorScheme` 37 个角色动画；无 `Application` 类/无 DI；双主题一致性仍靠人工复测，无自动比对） |
> | **P1-10 双主题渲染层收敛到组件级 style kit** | ✅ **已做完（2026-09-16 第三批 #3，八对收官）**：`Miuix*Screen.kt` 双胞胎 8 → 0，屏幕本体 17 文件 7,541 行 → 9 文件 4,204 行（**-44%**），style kit 落在 `ui/components/app/`（10 文件 2,746 行：`AppScaffold` / `AppTopBar` / `AppSnackbarHost` 骨架 + `AppConfirmDialog` / `AppFormDialog` / `AppOptionDialog` 三类弹窗 + 语义字号档位表 `AppTextScale` 13 档）；`AppNavGraph.kt` 与 `NavChrome.kt` 都已零主题分支。**唯一保留双套 body 的是设置页**（两版排版习语根本不同：MD3 滚动 `Column` + `Surface` 分组卡片 / Miuix `LazyColumn` + 库 Preference 组件；287 行逐字相同，八对最低），其 9 个弹窗里文案与动作逐字相同的 5 个（10 份实现）已收进组件层。逐对过程、验收口径与行数如实记账见 `devlog/2026-09-16.md` §15–§22 |
> | P2-1 静态检查缺位 | ✅ 报告提交当日即落地（`.editorconfig` + `detekt.yml` + `tools/ci-gates.sh` + `static-gates` job）。**注：本文建议的 `LongParameterList`/`TooManyFunctions`/`ReturnCount` 被有意关闭**（`complexity` 规则集 `active: false`，理由见 `detekt.yml` 文件头）；`SetTextI18n` 显式 disable 与 lint `baseline.xml` 仍未做 |
> | P2-2 CI 提速与加固 | ✅ 合并 Gradle 调用 + `concurrency` + `permissions: contents: read` + `release-r8` job；❌ 仍未做：`paths-ignore`、供应链（`verification-metadata.xml` / `dependency-review-action` / `gitleaks` / `zizmor`）、APK 体积基线、`bundleRelease`(AAB) |
> | P2-3 `versionCode = github.run_number` | ❌ 仍未改（`release.yml:112`） |
> | P2-4 `material-icons-extended` | 🟢 **已决策保留** + 注释记录理由（本文所述「已停止维护」成立） |
> | P2-5 高风险处零测试 | 🟡 已补仓储写守卫集成测试 8 例（注入临时 DataStore，**未用 Robolectric**）；ViewModel 撤销状态机、双主题一致性仍缺 |
> | P2-7 无障碍 | ❌ 仍未做（`contentDescription = null` 61 处、`semantics` 7 处，与本文统计一致） |
> | P2-8 字符串资源化 | ❌ 仍未做（`strings.xml` 只有 `app_name`） |
> | P3-2 / P3-4 / P3-5 / P3-6 / P3-7 / P3-9 / P3-11 | ❌ 仍未做（P3-6 MIUIX 配色入口用户指示暂缓，`MiuixSettingsScreen` KDoc 的「功能对等」声明已于 2026-09-16 改正） |
> | P3-3 相机临时文件 / P3-8 CSV 公式注入 / P3-10 归档单条删除确认 / P3-12 `corrupt/` 上限 / P3-13 文档漂移 / P3-14 导出文件名 | ✅ 全部已修 |
> | **P3-1 建议做 Glance 桌面小组件** | ⛔ **撞需求红线**：`docs/REQUIREMENTS.md` §4「明确不做」第 3 条就是「❌ 桌面小组件（Widget）」。要做必须先由用户推翻该边界，不要当普通待办推进 |
>
> **计数订正**（不影响结论）：`animateColorScheme` 是 **37** 个角色动画（本文写 36）；`Icons.*` 去重后 **34** 个（本文写 40）；`isCorrupt` 现出现 **18** 次（本文写 12 处守卫；09-15 加固后增加）；8 对双主题屏幕 16 个文件在本文基线时共 **6,903** 行（本文写 6,583），09-15 加固后现为 **7,205** 行；单测已从 73 例增至 **116** 例。
>
> **当前待办请以 `devlog/INDEX.md`「当前待办总览」与 `docs/audits/2026-09-15-code-review.md` §5 为准。**

> 对象：`SkyForest233/chileme`，commit `41a728f`（devlog 最新 2026-08-22）
> 审查方式：**完整静态走读**——`app/src` 全部 51 个 Kotlin 文件（14,886 行）、4 份 gradle 构建脚本、2 个 GitHub Actions workflow、manifest/res/xml、proguard 规则、10 个单测文件、CLAUDE.md + docs/ 四件套 + devlog + 既有 audits
> **未做的事**：沙箱无 Android SDK / JDK 21（只有 JDK 11），因此**没有执行 `assembleDebug`、单测或 lint**。下面所有结论都来自源码阅读，凡涉及运行时行为的地方我都标了判断依据，不涉及"我跑出来了"。
> 参照系：`docs/audits/2026-08-21-project-review.md` 与 `devlog/INDEX.md` 待办总览——已在 backlog 里的条目我只补充新角度，不重复。

---

## 0. 总体判断

工程质量**明显高于一般个人项目**，而且高于很多中型 App：

| 维度 | 评价 | 依据 |
|---|---|---|
| 数据安全意识 | ✅ 罕见地扎实 | `Decoded` 三态（`Ok/Empty/Corrupt`）把"解析失败"和"没数据"分开；28 处 `dataStore.edit` 中 12 个资产型写方法全部带 `isCorrupt` 守卫，另有 `buildBackupJson` 用 `check` 拒绝导出（我逐个方法核对过，**当前无遗漏**）；损坏原文留档 `filesDir/corrupt/`；损坏态拒绝导出以免生成残缺备份覆盖云端完好版本 |
| 演进纪律 | ✅ 优秀 | 每处修复都带"为什么"的注释（如 `compactConsumptionAt` 分组键必须含 `unit`、聚合记录必须补 `id`）；devlog + audits + 需求边界齐全 |
| 可测性设计 | ✅ 好 | `*At(today)` 注入时钟的纯函数拆分、73 个 JVM 单测、`idFactory` 参数化 |
| 发布工程 | ✅ 已补 | release 签名缺失即失败（不静默降级）+ `apksigner` 产物侧兜底 + `NewApi/InlinedApi` 提为 lint error + versionCode 由 CI 注入 |
| 双主题成本 | 🔴 仍是最大维护税 | 16 个屏幕文件中 8 对是 MD3/Miuix 双实现，共 6,583 行 |
| 输入体验 | 🔴 有硬缺陷 | 全项目 **0 处** `imePadding()` / `WindowInsets.ime`（详见 P0-1） |
| 一致性 | 🟡 出现漂移 | `MiuixStatsScreen` 绕过状态层自己重算统计；设置页 KDoc 声称"功能对等"但 MIUIX 侧无配色入口 |

下面按优先级列。每条给 **证据 file:line** 和**可直接落地的修法**。

---

## 1. P0：会真实影响正确性/体验的缺陷

### P0-1 键盘遮挡输入框与"保存"按钮（edge-to-edge 下 `adjustResize` 失效）

**证据**：`MainActivity.kt:169` 调 `enableEdgeToEdge()`；`AndroidManifest.xml:23` 写了 `android:windowSoftInputMode="adjustResize"`；而全项目对 IME 的处理次数为 0：

```
grep -rn "imePadding"      app/src/main → 0
grep -rn "WindowInsets.ime" app/src/main → 0
grep -rn "contentWindowInsets" app/src/main → 0
```

`enableEdgeToEdge()` 内部会 `setDecorFitsSystemWindows(false)`，此时 **`adjustResize` 不再缩小窗口**，键盘只以 inset 形式下发；Compose 端不消费 `WindowInsets.ime` 就等于没有避让。`EditFoodScreen` 恰好是"长表单 + `bottomBar` 里放保存按钮"（`EditFoodScreen.kt:182` 的 `Scaffold` + `:249` 的 `verticalScroll`），**键盘弹起时"添加到零食柜/保存修改"按钮会被完全盖住**，名称/数量/单位输入框也会落在键盘下。同样暴露的还有搜索框（`FoodListScreen`）和分类/位置管理的新增输入。

**修法**（二选一，建议 A）：

```kotlin
// A. 在每个含输入的 Scaffold 上让 ime 参与 contentWindowInsets
Scaffold(
    contentWindowInsets = WindowInsets.ime
        .union(WindowInsets.systemBarsForVisualComponents),
    ...
)
// B. 只给最外层内容加 imePadding（简单但 topBar 不避让）
Column(Modifier.padding(padding).imePadding().verticalScroll(...))
```

并把 manifest 里的 `adjustResize` 改成 `adjustNothing`（或删掉）——现在这个声明在误导后续维护者"系统已经帮我 resize 了"。加一条 `androidTest`/`lint` 自定义规则不现实，建议在 `docs/DESIGN_SPEC.md` 增加一条硬约定：**任何含 `TextField` 的屏幕必须消费 ime inset**。

### P0-2 `MiuixStatsScreen` 复制了一份统计逻辑，绕过已测的状态层

**证据**：项目给 8 对双主题屏幕各抽了一个 `remember*UiState`（v2.8 的 B-08 去重），我逐文件核对：16 个屏幕文件里 **15 个都调用了 `remember*UiState`，只有 `MiuixStatsScreen` 是 0**。它把 `StatsState.kt` 里的逻辑手抄了一遍：

| 指标 | 已测实现 | Miuix 页内联实现 |
|---|---|---|
| 本周消耗 | `StatsState.kt:42 calculateConsumedThisWeek` | `MiuixStatsScreen.kt:85` 手写 `consumption.filter { it.epochDay >= weekAgo }.sumOf { it.amount }` |
| 本月消耗 | `StatsState.kt:47` | `MiuixStatsScreen.kt:88` |
| 7 日趋势 | `StatsState.kt:52` | `MiuixStatsScreen.kt:94` |
| 分类占比 | `StatsState.kt:61` | `MiuixStatsScreen.kt:103` |
| 过期浪费 | `StatsState.kt:94` | `MiuixStatsScreen.kt:91` |

**影响**：`StatsStateTest`（5 例）测的是**用户切到 MIUIX 主题时根本不会执行的那份代码**。这类"两份实现、一份被测"是最典型的静默分叉温床——下一个改统计口径的人只会改 `StatsState.kt`，Miuix 页永远停在旧口径，而且没有任何检查会发现。

**修法**：`MiuixStatsScreen` 改成 `val state = rememberStatsUiState(viewModel)`，删掉 30 行内联计算（该文件 474 → 约 440 行）。**再加一条防回归测试**：把每个指标的期望值写成表驱动用例，分别调用 `calculate*` 纯函数——并在 `docs/WORKFLOW.md` 代码规范里补一句"Miuix 实现只允许替换外壳（Scaffold/Card/组件），**禁止重新计算业务量**"。这条约定值得机械化：一个扫描脚本/自定义 lint 检查 `Miuix*.kt` 里出现 `sumOf|filter {` 就告警，成本很低。

### P0-3「过期浪费」统计口径错误：数条数而不是数件数

**证据**（两份实现同错，所以修的时候要一起改——正好是 P0-2 的佐证）：

```kotlin
// StatsState.kt:94 与 MiuixStatsScreen.kt:91
val wastedTotal = archived.count { it.reason == ArchiveReason.EXPIRED }
```

`archiveItems()` 归档时保留原 `quantity`（`FoodRepository.kt:335` 存的是 `toArchive` 原对象，只有吃完自动归档才 `copy(quantity = 0)`）。所以"冰箱里 6 瓶冰红茶过期"被统计成 **1**。而同一行的"本周消耗"用的是 `sumOf { it.amount }`——**同一屏里两个卡片，一个数件数一个数条数**。这个指标还是整个 App 的价值主张（减少浪费）唯一量化出口。

**修法**：`archived.filter { it.reason == EXPIRED }.sumOf { it.item.quantity }`；顺手给 `StatsStateTest` 加一条"1 条归档 6 件 → 6"的用例。

补充：`consumedThisWeek/Month` 跨单位求和（`3 瓶` + `2 箱` = `5`）。你们在 `compactConsumptionAt` 的注释里已经明确"分组键必须含 unit，否则单位取第一条得到错误结果"——同一个道理适用于求和。建议要么在 UI 标注口径（"消耗份数"），要么按单位分组展示，要么给 `FoodItem` 增加"1 单位 = N 份"的换算。这是产品决策，但至少该在 `docs/REQUIREMENTS.md` 里写清楚口径。

### P0-4 冷启动主线程读全量快照文件 + 正则计数

**证据**：调用链全程没有切线程，`loadLocalSnapshots` 甚至是非 suspend 函数：

```
AppViewModel.kt:207-224  init { viewModelScope.launch { ... maybeAutoSnapshot() } }   // Main.immediate
AppViewModel.kt:259      val snapshots = LocalSnapshotStore.listSnapshots(getApplication())
AppViewModel.kt:387      fun loadLocalSnapshots() { _localSnapshots.value = LocalSnapshotStore.listSnapshots(...) }  // 非 suspend
LocalSnapshotStore.kt:58-77  dir.listFiles().map { file -> val text = file.readText(); Regex("\"id\"\\s*:").findAll(text).count() ... }
```

`viewModelScope` 默认是 `Dispatchers.Main.immediate`。于是**每天首次启动都会在主线程读最多 3 份完整备份 JSON 并对每份跑一次正则扫描**（重度用户几十上百 KB × 3），正落在你们专门做过"首帧门控 + SplashScreen 不闪帧"优化后的启动路径上。

顺带两个正确性问题：
1. **`itemCount` 语义不对**：正则数的是整个文件里所有 `"id":`，包括 `archived[].item.id` 和 `consumption[].id`。UI 文案是"包含 N 项资产"（`MiuixSettingsScreen.kt:824`、`SettingsScreen.kt:882`），8 件库存 + 30 条消耗会显示"包含 38 项资产"。
2. `readSnapshot(context, fileName)` 直接 `File(dir, fileName)`（`LocalSnapshotStore.kt:82-84`），fileName 目前来自内部列表所以没有实际风险，但没有任何约束；建议 `require(fileName.matches(Regex("snapshot_\\d{8}_\\d{6}\\.json")))`。

**修法**：
- `LocalSnapshotStore` 的三个方法改 `suspend` 并 `withContext(Dispatchers.IO)`；`loadLocalSnapshots()` 也进 `viewModelScope`。
- 计数不要读文件：保存快照时把数量写进文件名（`snapshot_20260822_141530_i23.json`）或写一行 sidecar（`.meta`），列表只 `listFiles()` + `lastModified()`——从 O(文件大小) 降到 O(3)。
- 如果仍要解析，用 `JSONObject(raw).optJSONArray("items")?.length()`，别用正则。

### P0-5 月度聚合记录在消耗记录页被当普通记录展示、且可被"一条删除"抹掉一个月

**证据**：`compactConsumptionAt`（`FoodModels.kt:232`）把 90 天前的流水按「年×月×名称×单位」合并成一条，`epochDay` 归一到当月 1 号。`ConsumptionLogScreen.kt:130-131` 直接 `itemsIndexed(state.sortedRecords, ...)`，每条一行、每行一个删除按钮，页面上唯一的说明是"删除记录仅修正统计，不会回滚库存数量"（`:107`）。全项目没有任何 `isAggregate` 标记（`grep 聚合|isAggregated` 在这两个文件为 0）。

**影响**：用户看到"7月1日 芒果干 × 14"，以为是一天的记录，点删除 → **整个 7 月的芒果干消耗历史一次性消失**（撤销靠的是内存里的 `DeletedConsumption(record, index)`，杀进程后不可撤销）。另外这些假日期会渗进 TOP5 榜（`calculateTopConsumed` 全量 groupBy name，把聚合与明细混在一起）。

**修法**（小改动，收益大）：
1. `ConsumptionRecord` 加 `val aggregated: Boolean = false`（有默认值 → 旧数据自动兼容，符合你们 `ignoreUnknownKeys` + 默认值的约定），聚合时置 `true`。
2. 列表分两段：「最近明细」+「历史月度汇总」；汇总行标注"7 月合计"、**禁用删除按钮**（或不提供）。
3. `calculateTopConsumed` 保持合并口径即可，但 UI 上说明统计区间。

### P0-6 备份/迁移规则：注释与代码不一致，且"数据 100% 本地"的对外表述不准确

`data_extraction_rules.xml` 的文件注释写着"cloud-backup 与 device-transfer **两者都排除** DataStore"，但实际：

```xml
<cloud-backup>     <exclude datastore/> <exclude covers/> <exclude corrupt/> </cloud-backup>
<device-transfer>  <exclude datastore/>                    <exclude corrupt/> </device-transfer>
```

`device-transfer` 里**并没有** `datastore/` 这一条（我读了完整文件，只有 `corrupt/` 两行 exclude）。而 `covers/` 在 device-transfer 中被放行。后果：换机直传时 DataStore 走的是**默认全量包含**，把 `nutstore_password_enc`（Keystore 密文，密钥不跨设备）传过去——这正是你们专门做了 `nutstoreCredentialBrokenFlow`（`FoodRepository.kt:233`）去兜底的那个场景。两处规则意图相同、结果不同，说明是漏改而不是设计。

同时 `android:allowBackup="true"` + 只排除三个路径 ⇒ **`filesDir/snapshots/`（每日全量库存 JSON）会进 Android 云备份**，与 README 的"数据 100% 存储于设备本地、无云端"以及"无需注册账号、无后台追踪"的表述冲突（备份到用户自己的 Google 账号，安全上没问题，**表述上是问题**）。

**建议**（这是唯一能同时保住"换机可用"和"凭据不外泄"的方向，也解决上面那个不一致）：

- **把"设备绑定的秘密"从可备份的数据里拆出去**：`nutstore_password_enc` 不再放 DataStore，改存独立文件 `filesDir/secure/webdav.key`（自己加密写入，不依赖 Keystore 跨设备），或反过来——保持现状但**两条规则都显式 exclude `datastore/`**。
- 前者更好的地方在于：拆分后 DataStore（库存本体）就可以**同时**参与云备份与换机直传，用户的库存数据在换机时自动带走，不用手动导出 JSON。这直接消灭一类"换机后数据没了"的差评来源。
- 无论选哪条：把 README 的"100% 本地"改成"默认 100% 本地；Android 系统备份（若用户开启）会包含每日快照，可在设置页关闭"，并在设置页加一个 `allowBackup` 对应开关（写不进 manifest，那就给一个"关闭每日快照"开关并说明它同时会失去系统备份通道）。

---

## 2. P1：结构与性能（改动可控、回报长期）

### P1-1 数据完整性守卫靠"每人记得写"，应改成结构性不可能忘记

现在 12 处 `isCorrupt` 是手写在各方法体里的，规则在 `CLAUDE.md §5` 和 `ARCHITECTURE §5` 里用加粗警告维持。**今天是对的（我逐条核对过），但它依赖于未来每个改动者都读到并遵守那条约定**——这正是最容易腐化的地方：新增一个方法、多写一个 key，守卫漏了不会有编译错误，只会在用户设备上偶发地"数据被清空"。

**修法**：把解码 + 守卫 + 回写封进一个模板方法，调用方拿到的就是已解码数据，无法跳过守卫：

```kotlin
private suspend fun mutateAssets(
    needs: Set<AssetKey>,                      // ITEMS / ARCHIVE / CONSUMPTION / HISTORY
    block: suspend (MutablePreferences, Assets) -> Unit,
) {
    context.dataStore.edit { prefs ->
        val a = Assets.decode(prefs, needs, json)      // 三态解码，一处实现
        if (a.corrupted.any { it in needs }) { Log.w(TAG, "拒绝写入：${a.corrupted}"); return@edit }
        block(prefs, a)
    }
}
```

`upsert` / `archiveItems` / `changeQuantity` / `undoConsumption` 等各自变成"取数据 → 改 → 写"，守卫与 `needs` 集合天然对齐（现在还有 `updateLocationBatch` 这类同时写资产 key 和配置 key、需要人判断的场景）。附带收益：将来从 DataStore 迁到 Room/单文件 JSON 只改一处——**而这个 App 最合理的中期演进就是迁走**，见 P1-2。

### P1-2 用 DataStore Preferences 存"用户资产型"关系数据，是全部复杂度的根源

现状：4 个大列表（items / archived / consumption / history）各自序列化成 JSON 字符串塞进一个 preference key，于是你们被迫手写了：三态解码与写守卫、`rawFlow` 去重（否则改主题色会全量重解析）、`compactConsumptionAt` 压缩、`take(200)/take(50)` 上限、跨 key 的原子更新（`restoreArchivedBatch` 注释里明确写了"旧实现是 N 次独立 edit，非原子且性能差"）。这些**每一样都是正确的补救**，但它们共同说明底层选型在跟需求打架。

需要说清楚：以当前数据量（几十条库存、上千条流水）DataStore **完全够用**，性能不是理由。真正的理由是：
- **没有部分写**：改 1 件食品的数量 = 重写整份 items JSON。任何未来的"多端合并/冲突解决"都做不了，因为没有记录级粒度。
- **没有约束与迁移**：schema 演进只能靠 `ignoreUnknownKeys` + 字段默认值 + `migrateXxx()` 启动补丁（现在已有 3 个：`migratePlaintextPassword`、`migrateConsumptionIds`、隐式的 seed 守卫）。第 10 个迁移函数开始就会变成负债。
- **上限策略只能"静默丢弃"**：`take(200)` 会把最老的归档记录直接扔掉，用户不可见、不可撤销。
- **消耗记录只能靠 name 关联**（`ConsumptionRecord` 没有 `itemId`）：于是 `StatsScreen.kt:299` 需要 `findItemIdByName(name)` 反查——同名食品会跳错详情页，改名后历史断链；`HistoryEntry` 也以 name 为唯一键（`upsert` 里 `filterNot { it.name == entry.name }`）。

**建议分两步，不要一次性大改**：
1. **先加 `ConsumptionRecord.itemId: String?`（默认 null，旧数据兼容）**，统计与详情页跳转改走 id，name 只作展示回退。这一步不需要换存储，就把最脆的关联修掉，同时让"按食品统计"成为可能。
2. 若还要继续演进（多设备、按天提醒、复杂查询），再上 **Room**：实体 = `FoodItem / ArchivedItem / ConsumptionRecord / HistoryEntry`，把 `Decoded`/守卫层删掉（DB 给你原子性与类型安全），`rawFlow` 换成 `Flow<PagingData>` 或 DAO 的 Flow。迁移用 `AutoMigration` + 一个一次性 JSON 导入器。**明确不建议**半途同时保留两套存储。

### P1-3 同一个冷流被并发收集 3 次，重复解码

```
AppViewModel.kt:42   repo.itemsFlow.stateIn(...)                    // 给 items
AppViewModel.kt:59   combine(repo.historyFlow, repo.itemsFlow, repo.archiveFlow)  // 给 suggestionSource
AppViewModel.kt:120  combine(repo.itemsFlow, paletteFlow, ...) { _,_,_,_,_ -> true } // 给 ready
```

`itemsFlow` 是冷流，三个收集者 ⇒ **每次写 items 就把同一份 JSON 解码 3 遍**，其中第 3 个（`ready`）解完直接丢弃。`rawFlow` 的 `distinctUntilChanged` 只去重"该 collector 自己看到的上游"，不能跨 collector 复用。这是 v2.8 "flow 去重+移出主线程" 优化没覆盖到的另一半。

**修法**：仓库内部把重 key 做成 `shareIn`：

```kotlin
private fun <T> hotFlow(key: Preferences.Key<String>, decode: (String?) -> T): Flow<T> =
    rawFlow(key, decode).shareIn(globalScope, SharingStarted.Eagerly, replay = 1)
```
（`shareIn` 需要一个仓库级 `CoroutineScope`——更干净的做法是在 `AppViewModel` 里 `stateIn` 一次、下游全部复用 VM 暴露的 `StateFlow`，并把 `suggestionSource` 改为 `combine(items, archived, history)` 这三个已热化的 StateFlow。后者改动更小、更贴合你们现有分层，推荐。）
`ready` 则改成只观察一个轻量 key（`lightFlow { it[itemsKey] != null }`），别为它付解码成本。同一处还有第 4、5 次冷收集：`AppViewModel.kt:216-217` 在启动路径上 `repo.itemsFlow.first()` + `repo.archiveFlow.first()` 各起一条独立链、各做一次全量解码，仅为算出"哪些封面还被引用"——这两个集合可以直接从已热化的 `items`/`archived` StateFlow 上取。

### P1-4 设置页导出/导入：主线程做 IO + 全量序列化，且协程挂在 composition 上

```
MiuixSettingsScreen.kt:91-125（SettingsScreen.kt:103-137 同构）
val scope = rememberCoroutineScope()          // Main.immediate，且随屏幕离开而取消
scope.launch {
    val jsonText = state.buildBackupJson()     // dataStore.first() + prettyPrint 全量序列化 → 主线程
    context.contentResolver.openOutputStream(uri)?.use { it.write(jsonText.toByteArray()) }  // 主线程磁盘/IPC IO
}
```
MD3 侧同样（`SettingsScreen.kt`）。三个问题：
1. **ANR 风险**：pretty-print 序列化整份库存 + 归档 + 全部流水在主线程，重度用户（几千条流水）会掉帧甚至 ANR；
2. **写一半**：`rememberCoroutineScope` 绑定组合生命周期，导出过程中切后台/旋转/返回 → 协程取消 → 目标 SAF 文件里留下半截 JSON，用户拿到一份坏备份还以为成功了；
3. **导入无防护**：`input.readBytes()`（Miuix `:120` / MD3 `:132`）无大小上限，选到几百 MB 的文件会 OOM；`importBackupJson` 只做 `runCatching{}.getOrNull()`，一个"合法但空"的 JSON（`{"items":[]}`）会**静默清空全部数据**且直接生效。

**修法**：
- `buildBackupJson/buildCsvExport/importBackupJson` 已经在 VM 上，加 `withContext(Dispatchers.IO)`，并把 UI 侧的 `scope.launch` 全部改成 VM 方法（`viewModel.exportBackup(uri)`），生命周期归 `viewModelScope`；
- `importBackupJson(raw)` 拆成 `previewBackup(raw): BackupSummary?` + `commitBackup(raw)`：弹窗展示"库存 23 件 / 归档 8 条 / 流水 1,342 条 / 导出日期 2026-08-20"再让用户确认，把破坏性操作的可逆性讲清楚（现在只有"整体替换"四个字）；
- 读入前 `if (size > 20 MB) return 拒绝`。

### P1-5 "保存后立刻返回"可能在写盘完成前销毁 ViewModel

`EditFoodScreen.kt:222-223`：`viewModel.upsert(item)`（fire-and-forget）紧跟 `onBack()`。`upsert` 内部 `dataStore.edit{}` 挂起等待落盘；若用户保存后立刻划掉 Activity/进程被杀，`viewModelScope` 被取消，**这次写可能丢失**。同理 `FoodDetailScreen` 的"吃掉一份"。

**修法**：VM 提供 `suspend fun save(item: FoodItem)`，屏幕用 `scope.launch { viewModel.save(item); onBack() }`，并在 await 期间 disable 按钮（顺带解决了"双击重复插入"——现在 `existing?.id ?: UUID.randomUUID()`，连点两次会产生两条同内容记录，因为第二次组合时 `existing` 仍是 null）。

### P1-6 `HorizontalPager(beyondViewportPageCount = 3)` + `userScrollEnabled = false`

`MainActivity.kt:898-901`。四个 Tab 页**全部常驻组合**，而 `MainTabsPager` 里每页都 `collectAsStateWithLifecycle` 订阅 items/archived/consumption/…（`StatsState`/`FoodListState`/`SettingsState` 各订阅 5–18 个 flow）。后果：任何一次数据变化（点一下减号）会让**看不见的三个页面一起重算统计、重跑 filter**。你们已经关掉了滑动手势（`userScrollEnabled = false`），所以 pager 剩下的唯一作用是"保留页状态"——而这正是 `rememberSaveable` 已经在做的事（`FoodListState` 的 query/filter 全用 `rememberSaveable`）。

**修法**：改成 `Crossfade`/`AnimatedContent` + 显式 `Modifier.animateEnterExit`（4 个 tab 状态已由 VM/`rememberSaveable` 承担），或至少 `beyondViewportPageCount = 1`。这一步和 P1-7 是本项目**性价比最高的两处运行时性能改进**。

### P1-7 `animateColorScheme` 用 36 个独立动画，把主题切换变成全屏重组风暴

`Theme.kt:39-87` 对 36 个颜色角色各建一个 `animateColorAsState(450ms)`，返回值喂给 `MaterialTheme(colorScheme = ...)`。动画期间**每一帧都产生新的 `ColorScheme` 实例 ⇒ 所有读 `MaterialTheme.colorScheme` 的 composable 每帧重组一次**，持续 450ms。叠在 P1-6（四页常驻）上，切一次配色的代价是"4 屏 × 全部卡片 × 约 27 帧"。而 `FoodAvatar`（`Common.kt:262`）在**组合同时同步做 `File(...).exists()` 磁盘 stat**（详见 P1-8），于是主题切换会明显掉帧。

**修法**（保留动效但换实现）：只动画 1–2 个驱动量，其余用 `lerp`：

```kotlin
val t = remember { Animatable(1f) }  // 1 → 0 触发一次
LaunchedEffect(targetScheme) { t.snapTo(0f); t.animateTo(1f, tween(450)) }
val scheme = lerpColorScheme(prevScheme, targetScheme, t.value)  // 仍每帧新实例
```
——这治不了根。真正治根的做法是**不要动画整套 scheme**：要么直接瞬切（MD3 官方语义里主题切换本就不承诺颜色补间；`MaterialTheme` 换色时靠各组件自己的 `animateColorAsState` 局部过渡即可），要么对整块内容做 `Crossfade(targetState = scheme)`（一次 alpha 混合，GPU 合成，不重组子树）。我倾向后者：`Crossfade` 一个 `ColorScheme`，代价与内容复杂度无关。同时给"主题切换"加一个 `Macrobenchmark`，把"切换期间掉帧数"变成一个有回归价值的指标。

### P1-8 渲染路径上的同步磁盘访问

```
Common.kt:262      if (item.photoPath.isNotBlank() && File(item.photoPath).exists())   // FoodAvatar，列表每项一次
EditFoodScreen.kt:263  if (photoPath.isNotBlank() && File(photoPath).exists())
```
两处都在**组合期**直接 `File.exists()`（syscall），未 `remember`。列表 50 项、每次滚动/重组都重算。而且语义也不对：文件不存在时静默回落到 emoji，用户不知道为什么封面"又没了"。

**修法**：不要在渲染时判断。照片是 App 自己写到 `filesDir/covers/` 的，删除也只发生在 `cleanupOrphanCovers`，**路径存在性由数据层保证**；渲染侧直接 `AsyncImage`，Coil 加载失败时给 `onError` 回落 emoji。另外 `photoPath` 建议只存文件名（`covers/<uuid>.jpg` 的 basename），运行时用 `context.filesDir` 拼——绝对路径写进备份 JSON 里，换机/换用户 ID 后必然悬空（与 P0-6 的备份话题连在一起）。

### P1-9 凭据处理：与 README 的承诺差一步

```
FoodRepository.kt:602-613  setNutstoreCredentials
    val enc = SecureStore.encrypt(password.trim())
    if (enc.isNotBlank()) { ... } else { prefs[nutstorePasswordKey] = password.trim() }  // Keystore 失败 → 回落明文
SecureStore.kt:38-47   encrypt 失败 return ""，异常被 catch 后丢弃
AppViewModel.kt:91     val nutstorePassword: StateFlow<String> = ...   // 解密后的明文常驻整个会话
```

README 写"WebDAV 应用密码基于 Android Keystore (AES-GCM) 硬件加密存储"，代码里有一条**静默写明文**的兜底分支。这个分支"不应发生"（注释自己说的），但它存在，而且失败原因被吞掉（`catch → ""`，无日志），一旦真发生，用户和你都不会知道。

**修法**：
- Keystore 失败 ⇒ **不保存，抛给 UI 明确报错**（"此设备安全存储不可用，无法保存密码"）。宁可少一个用户用云同步，不能悄悄降级安全承诺。
- `SecureStore.encrypt/decrypt` 的 catch 里加 `Log.e`（你们其它地方都留了日志，这里是漏项）。
- 别把解密后的明文放进常驻 `StateFlow<String>`（当前 `nutstorePasswordFlow` 每次 DataStore 发出都会解密并缓存一份在内存里，UI 还绑着它）。改成：**仅在打开凭据对话框时**解密一次给 `TextField` 初值，关闭即丢；同步/上传时由 VM 在 IO 协程里临时解密、用完不缓存。
- `AppViewModel` 里明文还进了 `remember` 状态对象（`SettingsState.kt:128-129` 的 `accountInput/passwordInput`），配合 P1-4 一并调整。

### P1-10 双主题策略没收敛：两种"分流方式"并存，应统一到组件级

同一个仓库里有两套做法：

| 做法 | 用在 | 结果 |
|---|---|---|
| **屏幕级复制**：`XxxScreen.kt` + `MiuixXxxScreen.kt` | 8 对屏幕 | 6,583 行，改一个业务规则要改两处 |
| **组件内分流**：`if (LocalThemeStyle.current == MIUIX)` | `Common.kt` 的 `FoodAvatar/StatusBadge/QuantityStepper/DataCorruptBanner` | 单一实现，两套外观 |

我按"去掉 import/package/空行后逐行 diff"量化了两者的重复度：

```
ConsumptionLog  vs Miuix:   差异  50 / 合计  272   → 82% 相同
FoodList        vs Miuix:   差异 126 / 合计  680   → 81% 相同
HomeScreen      vs Miuix:   差异 107 / 合计  567   → 81% 相同
FoodDetail      vs Miuix:   差异 145 / 合计  549   → 74% 相同
ArchiveScreen   vs Miuix:   差异 119 / 合计  389   → 69% 相同
ManageScreens   vs Miuix:   差异 307 / 合计  771   → 60% 相同
StatsScreen     vs Miuix:   差异 271 / 合计  791   → 66% 相同
SettingsScreen  vs Miuix:  差异 1328 / 合计 1764   → 25% 相同（外壳范式真的不同，合理）
```

**B-08（状态层抽离）把"逻辑"去重了，但"骨架"还留着**，所以那 6 对 70–82% 相同的文件就是纯重复的排版结构。而且已经出现了代价：`MiuixStatsScreen` 干脆不接状态层（P0-2）——双实现越多，这种"某一侧偷偷没跟上"的概率越高。

**建议：把分流下沉到"组件/外壳"层，屏幕只留一份。** 具体做法是引入一套很小的 **style kit**（你们 `Common.kt` 已经在做，只是没做到底）：

```kotlin
// ui/kit/StyleKit.kt —— 每个都内部按 LocalThemeStyle 分流
@Composable fun ScreenScaffold(topBar, bottomBar, snackbarHost, content)
@Composable fun SectionHeader(text: String)
@Composable fun InfoRow(title, summary, leading, trailing, onClick)   // MD3: TwoLineRow；Miuix: ArrowPreference
@Composable fun ChipRow(...)  @Composable fun Panel(...)  @Composable fun ConfirmDialog(...)
@Composable fun Stepper(...)  @Composable fun ListContainer(contentPadding, key, itemContent)
```

屏幕文件因此变成"数据 + 布局意图"的单一实现，外观差异被关在 kit 内部。以 `ConsumptionLogScreen`（180+195 行 → 合并后约 190 行）和 `ArchiveScreen`（260+254 → 约 290）为试点，**预计可去掉 1,500–1,900 行**，且此后新页面自动获得双主题。`SettingsScreen` 保持两份（25% 相同，强行合并只会变成一堆 `if`），并在 `docs/DESIGN_SPEC.md` 里写明"哪些页面允许双实现、为什么"——你们对 `EditFoodScreen/StatsScreen` 保留 MD3 已经这样做了，把规则一般化即可。

### P1-11 状态容器的写法牺牲了 Compose 的重组粒度

`SettingsState.kt:137-186` 用一个 `remember(28 个 key) { SettingsUiState(...) }` 把 18 个 flow 打包成**一个**对象。副作用是：**任何一个字段变化，整个 `SettingsUiState` 实例就是新的**，所有读 `state.xxx` 的地方一律失效——比直接 `collectAsStateWithLifecycle()` 逐个读的粒度**更粗**。你们为去重引入的这层，实际把 Compose 免费提供的精细重组能力关掉了（在 P1-6 的四页常驻下更明显）。

另外 `SettingsUiState` 里 30 个构造参数 + 10 个 `on*Changed` 回调 + 28 个 remember key：新增一个设置项要同步改 4 处，这类"手工 MVI"迟早会漏。

**修法**（三档，按你们的口味选）：
- **最小**：保留共享逻辑（纯函数 + 派生值），但让叶子 composable 自己读细粒度 flow；`remember*UiState` 只返回**动作集**，不返回数据。
- **推荐**：把派生计算上移到 VM 的 `stateIn { ... }`（`deriveStateFlow`），例如 `val foodListSnapshot: StateFlow<FoodListViewData>` —— 顺带解决 P1-3 的重复解码，且**派生逻辑自动变成可 JVM 单测**（你们 `StatsStateTest` 现在测的是"UI 文件里的纯函数"，位置很别扭）。
- **重构派**：把 `(query, statusFilter, categoryFilter, locationFilter)` 收成一个 `@Immutable data class ListQuery`，一个 `mutableStateOf(query)` 取代 4 个 `var` + 4 个回调。

---

## 3. P2：质量工程与发布

### P2-1 代码风格/静态检查缺位
没有 `.editorconfig`、ktlint、detekt；`WORKFLOW.md §2` 的规范全靠自觉。建议：
- 加 `spotless`（ktlint + `editorConfigOverride`）+ CI 一步 `spotlessCheck`；
- 加 `detekt` 只做**少量高信号规则**（`LongParameterList`——你们有 30 参构造；`ReturnCount`；`TooManyFunctions`；`MagicNumber` 关掉）。`Common.kt` 965 行、`MainActivity.kt` 1,093 行、`SettingsScreen.kt` 1,068 行都会被 `TooManyFunctions`/文件长度规则点名，是有价值的拆分信号；
- lint 目前只 `error += [NewApi, InlinedApi]`（很好，是踩过坑的产物）。建议再补 `SetTextI18n`（显式 `disable`，把"中文单语言"的决定写进配置而不是只写在文档里）、`Compose` 相关检查，并用 `baseline.xml` 固化存量，让**新违规必红**。

### P2-2 CI 可提速与加固
```
build.yml 连续三次独立调用：assembleDebug / testDebugUnitTest / lintDebug，且全部 --no-daemon
```
每次重新配置工程 ⇒ 约 3× 配置开销。**合并成一条** `./gradlew testDebugUnitTest lintDebug assembleDebug --stacktrace`（同一 daemon，配置一次）。另外：
- `build.yml` 没有 `concurrency: cancel-in-progress`（release.yml 有），同分支连续 push 会排队白跑；
- 无 `paths-ignore`：改 `devlog/*.md` 也跑整套构建；
- **供应链**：这个 App 会拿用户凭据连 WebDAV，建议加 `gradle/verification-metadata.xml`（依赖校验和）+ `dependency-review-action`，并把 `gitleaks`/`zizmor` 跑上（workflow 里处理 keystore，值得专门防注入）；
- 产物侧可加一步 APK 体积基线（`apkanalyzer dex packages`）贴到 PR 评论——你们很在意 2.6 MB 这个数，值得自动化守住；
- 没有 `bundleRelease`（AAB），若打算上 Play 需要补。

### P2-3 `APP_VERSION_CODE = github.run_number` 有撞车风险
`release.yml:112` 用 `github.run_number`，它是**按 workflow 文件**计数。目前能单调递增（都走同一个 `release.yml`），但两个前提很脆：任何人新开一条会产物的 workflow，或同一 tag 被重打（`run_number` 变大 → 版本号跳号），或将来改成 `workflow_dispatch` 手动构建不同分支时产生互相"更大"的 versionCode。稳妥做法是把版本号写进仓库：`version.properties`（发版 PR 里 bump，`gradle` 读取）+ CI 校验"tag 版本 == properties 版本"，让 versionCode 与发布节奏一致，而不是与 CI 运行次数一致。

### P2-4 `material-icons-extended` 是 Google 已停止维护的库，也是 debug 体积的主要贡献者
全项目只用到 40 个图标（我按 import 去重统计：`rounded.Add/Check/Close/Delete/Home/...`），却引入整个扩展图标库。Miuix 图标已随主题在依赖里。建议：把 MD3 侧也换到「Miuix icons + 少量自绘 ImageVector」（你们 v2.5 已经手做过 launcher 矢量图标，有现成套路）。收益：debug 包（文档记为 ≈63 MB）与编译/dex 时间下降、少一个 deprecated 依赖、图标风格两套统一。

### P2-5 测试：73 例覆盖的是"纯函数"，风险最高的地方没有测试
现有测试全在 `app/src/test`，只覆盖无 Android 依赖的纯函数（`FoodModelsTest` 29、`CloudBackupTest` 8、`BackupCompatTest` 8…）。**没有任何测试覆盖**：
- `FoodRepository` 的写守卫（本项目最重要的安全机制！）——**Robolectric 完全能测**：`DataStore` 用临时目录、`SecureStore` 用注入接口，测"items 损坏时 `upsert` 不写""`importBackupJson` 后解除损坏""`changeQuantity` 归零自动归档 + 消耗记录 + 撤销"三条链路即可锁定核心不变式；
- `compactConsumptionAt` 与 `deleteConsumption` 的交互（P0-5 就出在这个缝里）；
- `AppViewModel` 的撤销/恢复状态机（`DeletedConsumption`/`RestoredArchivedEvent` 的下标恢复语义）；
- **两套主题的一致性**（P0-2 那类分叉）——建议做成表驱动用例："同一份输入数据 → `calculateXxx` 与 UI 侧调用的入口给出同一结果"。

`SecureStore` 也值得抽个接口出来测（目前是 `object` + `catch → null`，测不到也换不掉，`FoodRepository` 因此不可单测——见 P2-6）。

### P2-6 依赖注入与包结构
`AppViewModel` 里 `private val repo = FoodRepository(application)` 直接 new，`FoodRepository` 内部又直接引用 `object SecureStore` 与 `context.filesDir`。**没有 Application 类、没有 DI**。后果是：可测性受限（P2-5）、`NutstoreSync` 是全局 `object` 持有 OkHttpClient（无法在测试里替换成 MockWebServer——虽然它能跑，但你无法注入失败）。
最小改法（不引 Hilt，保持单模块轻量）：VM 构造参数化 + 一个手写 `AppContainer`（`Application` 里 new，`viewModelFactory` 取用）。`NutstoreSync` 改成 class 并把 `OkHttpClient` 作为构造参数——**顺带能补上 WebDAV 客户端的单元测试**（`parsePropfind` 你们已有，缺的是 HTTP 失败分支：401/404/超时）。

### P2-7 无障碍（沿用 backlog B4，给具体清单）
统计：61 处 `contentDescription = null`、55 处有文字描述、全项目仅 7 处 `semantics`。TalkBack 下"看图说话"的 App 现在等于静默。落地清单（一次 PR 可完成）：
- 图表：`StatsScreen.kt:176` 的柱状图与 `:250` 的环形图容器加 `semantics { contentDescription = "近 7 天消耗趋势：" + trend.joinToString { "${it.first.cnDay()} ${it.second} 件" } }`；
- 卡片：`FoodCard` 合成一条描述（"草莓酸奶，4 杯，还剩 3 天，临期"）并 `hideFromAccessibility` 子节点，避免逐字朗读；
- 步进器：`QuantityStepper`（`Common.kt:374`）当前只靠 `enabled = quantity > 0` 表达状态，补 `stateDescription`/`LiveRegion`，数量变化要被读出来；
- 装饰性 emoji（`EmojiAvatar`、分类 emoji 的 `Text`）应 `clearAndSetSemantics { }` 或给出替代文本，否则 TalkBack 会读"🍪"或什么都不读；
- 加一条 `androidTest` 的 `AccessibilityTestRule`/`Espresso` 扫描，或至少把 `lintDebug` 的 `Accessibility` 组提为 warning 清单跟版。

### P2-8 单语言硬编码在 Kotlin 里
`strings.xml` 只有 `app_name`，其余全部中文字面量散在代码里（`docs/WORKFLOW.md` 已明确承认）。短期可接受，但三处代价：
1. 想上 Play 多语言时要改 1,000+ 处；
2. 文案与逻辑耦合，测试不能断言 UI 文案（P0-3 那种"标签口径"问题就更难发现）；
3. 状态标签、CSV 表头、Snackbar 文案这类"会被产品反复调"的字符串每次改动都要动代码文件。
建议**增量迁移**：新改动的文案一律进资源；优先把 `FoodStatus`/`ArchiveReason` 的 label、CSV 表头、Snackbar 模板抽出去（数量少、复用高）。同时用 `lint { disable += "SetTextI18n" }` 把当前决定显式记录下来（见 P2-1），避免以后有人误以为漏配。

---

## 4. P3：产品与设计层面的建议

1. **README 的"智能临期提醒"与实际能力的落差**。`docs/REQUIREMENTS.md:48` 明确"❌ 推送通知 / 后台提醒（WorkManager）"（用户已确认）。这不是 bug，是范围决定；但 README 首屏把它列在核心特性第 4 条，会形成预期落差——一个保质期 App 最被期待的就是"不打开也能提醒"。两条路：① 把措辞改成"应用内临期标识与处理入口"；② **考虑 Glance 桌面小组件**——它不需要通知权限、不需要后台任务（App Widget 由系统宿主刷新），能覆盖"我不想要推送但不想主动打开 App"这批用户，和"不做通知"的决定并不冲突。这可能是本项目性价比最高的一个新功能。
2. **`take(200)` 静默丢弃最老归档**（`FoodRepository.kt:339`、`:474`）。数据丢失对用户不可见、不可撤销。建议：达到上限时 UI 提示"归档已满，最早 N 条已被移除，建议导出备份"，或改成"归档不限量、只在展示层分页"。
3. **`cleanupOrphanCovers` 只在启动跑**（`AppViewModel.kt:219`）。换封面/移除封面后旧文件要等下次冷启动才回收；相机临时文件 `cacheDir/camera/<uuid>.jpg`（`EditFoodScreen.kt:170-177`）**永远不会被删**（写进的是 cacheDir，靠系统清理，但没人保证时机）。修法：`EditFoodScreen` 的 `TakePicture` 回调里 finally 删临时文件；`upsert` 成功替换照片后立刻删旧文件（`copyImageToCovers` 返回新路径 + 传入旧路径即可，比"启动扫全目录"更直接）。
4. **编辑表单的输入约束太松**：`quantityText.toIntOrNull() ?: 1`、`shelfLifeText.toIntOrNull() ?: 0` 再 `.coerceAtLeast(1)`（`EditFoodScreen.kt:179-180`、`:218`）——粘进 `"1e9"`/空串/`-5` 会被静默改成默认值或 1 天，用户不知道。建议：`TextField` 上加数字 `Filter`（只允许数字、长度 ≤ 5）、非法值时给 `error` 文案并 disable 保存；`shelfLife` 上限（如 3650 天）显式拒绝而不是 `coerce`。同时"返回时若有未保存修改应二次确认"（目前 `onBack` 直接丢弃 14 个字段）。
5. **`existing` 用 `viewModel.items.value` 而非订阅**（`EditFoodScreen.kt:98` `remember(editId) { viewModel.items.value.find {...} }`）：不响应数据变化，且依赖"冷启动 ready 之前进不来编辑页"这一隐含时序。改成 `items.mapNotNull{...}.firstOrNull()` 的 VM 派生 flow，或至少 `remember(editId, items)`。
6. **MIUIX 模式缺"主题配色"入口，但 KDoc 声称功能对等**：`MiuixSettingsScreen.kt:69` 写"与 `SettingsScreen` 功能对等：外观（主题风格/深浅/动态取色/**配色**/悬浮导航）"，实际外观 Card 里（`:164-197`）只有深色模式/动态取色/悬浮导航，**没有 `AppPalette` 选择器**（MD3 侧在 `SettingsScreen.kt:235-247`）。根因是 `MiuixRootTheme`（`MiuixRootTheme.kt:96-107`）只消费 `darkMode`+`dynamicColor`，Miuix 侧没有种子色通道 —— 于是**在 MIUIX 风格下，15 个配色方案（`Palettes.kt` 的 `AppPalette`）一个都选不了，而 MD3 下选好的配色在切到 Miuix 后不可见地失效**。建议要么补上（Miuix 的 `ThemeController` 支持 `keyColor` 时用之；不支持就在页面上明示"配色方案仅对 Material 3 风格生效"），要么把 KDoc 的对等声明改掉。这类"声称对等但不对等"比缺功能本身更伤信任。
7. **`SettingsState.kt:130` 的 `LaunchedEffect(showNutstoreDialog, nutstoreAccount, nutstorePassword)`**：用 effect 把 flow 值灌进两个输入框状态。任何一次 key 变化都会覆盖用户正在输入的内容（凭据保存成功后 flows 更新 → 输入框被回写）。改成对话框自身 `remember(showNutstoreDialog) { 初值 }`，或把对话框提成独立 composable 用参数注入初值。
8. **CSV 导出**：`escapeCsvField`（`CsvExport.kt:42`）正确处理了逗号/引号/换行，但**没有防公式注入**（以 `=`/`+`/`-`/`@` 开头的字段在 Excel 里会被求值——用户食品名/备注可控，属本地可控风险，但该 CSV 会被转发）。加一行 `if (value.firstOrNull() in "=@+-") "\"" + value + "\""`（或前置 `'`）即可。另外 CSV 只导库存，不导归档/消耗——导出格式对话框里（`showExportFormatDialog`）建议明确标注"仅当前库存"，避免用户以为 JSON 与 CSV 等价。
9. **云端备份只能追加不能管理**：`NutstoreSync` 没有对外删除单个备份的 API（只靠 `CLOUD_BACKUP_KEEP=3` 轮转），也没有"测试连接"。用户改密码后想清理旧备份、或想确认凭据是否有效，只能盲试。加 `deleteBackup(fileName)` + "测试连接"按钮很便宜（PROPFIND Depth 0 即可）。
10. **`ArchiveScreen` 的"彻底删除"是物理删**（`deleteArchived`/`clearArchive` 无守卫，`clearArchive` 甚至允许在损坏态执行，注释说明是有意的"清空即丢弃"）。逻辑自洽。但归档页文案"可恢复或彻底删除"（`MiuixSettingsScreen.kt:220`）之后没有二次确认？我确认 `showClearDialog` 存在（`ArchiveState.kt:25`），所以清空有确认；单条 `deleteEntry` 似乎没有——建议单条也加确认或加撤销（你们已经有成熟的 `showUndoSnackbar` 基建，成本极低）。
11. **`consumeOne` 在详情页不暴露撤销**（`FoodDetailState.kt:48-54` → `changeQuantity(id, -1)` 未传 `withUndo`）。"吃掉一份"是最容易误触的操作（大按钮 + 连击动效），而列表页的减号反而有撤销。建议对齐。
12. **`LocalSnapshotStore` 与 `corrupt/` 目录没有总量上限**：`snapshot_*.json` 有 `MAX_SNAPSHOTS=3` 兜住了；但 `markCorrupt`（`FoodRepository.kt:138`，由 `:103` 在解码失败时调用）是"同一 key 每进程只写一次"，跨进程重启可无限累积（每次崩溃前解析失败都留一份完整原文）。建议按 mtime 保留最近 5 份即可。
13. **`docs` 与代码的漂移**（都是小改，但会被下一个改动者当真相读）：
    - `ARCHITECTURE.md:56` 仍写"消耗流水 上限 1000"——v2.4 已换成 90 天明细 + 月度聚合，`take(1000)` 早就不存在了；
    - `ARCHITECTURE.md:58` 写 `category_thresholds` 的"key 为 `FoodCategory.name`"——`FoodCategory` 枚举已删，现在是 `CategoryDef.id`；
    - `ARCHITECTURE.md:63` 写"UI 一律用 `statusFor`"——B3 之后 UI 应该一律用 `statusForAt(today, ...)`，否则跨零点刷新不生效（这正是你们刚修的 bug 类）；
    - `README.md:37` 写"OkHttp 5"，实际 `libs.versions.toml:22` 是 `okhttp = "4.12.0"`；
    - `WORKFLOW.md:31` 的"新增依赖：2. 加入 `app/build.gradle.kts`"——B6 已引入 Version Catalog，应改为"加入 `gradle/libs.versions.toml`"。
    建议在 `WORKFLOW.md §5 文档维护责任` 里把"改实现 → 改对应文档行"变成 PR 检查项，或干脆在 CI 里加个粗糙的 grep 断言（例如禁止文档里出现 `FoodCategory`、`上限 1000`）。

14. **导出的备份文件名只带日期，不带时间**：`MiuixSettingsScreen.kt:391` / `SettingsScreen.kt:569` 都是 `exportLauncher.launch("吃了么备份_${LocalDate.now()}.json")`——同一天导出两次会撞名（SAF 会提示改名或原地覆盖），用户很可能以为"我已经备份过了"。云端那条路径已经用了 `yyyyMMdd_HHmmss`（`CloudSync.kt:90-92`），本地文件名对齐它即可，顺便让文件名可排序、可辨识第几次备份。

---

## 5. 如果只做三件事

| 优先级 | 做什么 | 为什么 |
|---|---|---|
| **1** | P0-1 IME inset + P0-2 Miuix 统计接回状态层 + P0-3 浪费口径 | 一个直接挡住用户保存、一个是静默分叉的源头、一个把 App 的核心价值指标算错。都是小时级改动 |
| **2** | P1-10 抽出 style kit，把 6 对 70–82% 相同的屏幕合成一份 + P1-6/P1-7 去掉 `beyondViewportPageCount=3` 与整套 ColorScheme 补间 | 这三条分别是本项目最大的维护税和最大的运行时开销来源；做完之后新增功能的边际成本明显下降 |
| **3** | P1-1 写守卫模板化 + P1-2 第 1 步（`ConsumptionRecord.itemId`）+ P2-5 用 Robolectric 测住守卫 | 把"靠约定维持的数据安全"变成"靠结构维持"，并给演进留出空间 |

---

## 6. 附：我如何验证的（便于复核）

- 逐行读完 51 个 Kotlin 文件、4 个 gradle 脚本、2 个 workflow、manifest + 3 个 res/xml、proguard 规则、README/CLAUDE/docs 四件套、devlog INDEX、`docs/audits/2026-08-21-project-review.md`；
- 28 个 `dataStore.edit` 逐个核对 `isCorrupt` 守卫（结论：资产型全覆盖，无漏）；
- 16 个屏幕文件逐个核对是否调用 `remember*UiState`（结论：仅 `MiuixStatsScreen` 未用）；
- 8 对双主题屏幕用 `diff`（忽略 import/package/空行）量化重复度，数字见 P1-10；
- `grep` 统计：`imePadding`/`WindowInsets.ime`/`contentWindowInsets` = 0；`GlobalScope`/`runBlocking`/`Thread(` = 0（这点很好）；`Modifier.composed` = 0（好）；`contentDescription = null` = 61；`semantics` = 7；`@Test` = 73；
- 未能验证的：真实运行时行为（构建/单测/lint 均未在本环境执行）、APK 体积数字（引用自你们文档）、Android 15/16 上的 IME 表现（结论基于 `enableEdgeToEdge()` + 无 ime inset 消费的代码事实推得，建议在真机上确认一次"打开添加食品 → 点名称输入框 → 保存按钮是否被键盘遮挡"）。
