# 路线图（docs/ROADMAP.md）

> **本文件是活的**：只记「接下来做什么、按什么顺序、做到什么程度算完」。做完的移出去 ——
> 落地过程归 `devlog/` 各日日志，一句话结论归 `devlog/INDEX.md`「已完成里程碑」，本文件只留一行指针。
>
> **来源与分工**（刻意分三份、各管一件事，避免又变成互相抄的重复）：
> | 文件 | 管什么 | 不管什么 |
> |---|---|---|
> | **本文件** | 结构性改造的**执行顺序 + 证据 + 验收 + 风险** | 零碎待办、体验项、需用户决策项 |
> | `devlog/INDEX.md`「当前待办」 | **完整 backlog**（含代码级小项 / 体验 / 决策 / 不做） | 执行顺序与验收口径 |
> | `tools/doc-metrics.sh` | 所有数字的**测量口径**（一键复跑） | 结论与判断 |
>
> 本文件的证据数字全部可用 `bash tools/doc-metrics.sh` 复核（2026-09-17 实测）。
> 原始出处是 `devlog/2026-09-16.md`「🧭 拆分路线图」第三批（#1–#9），**那份原文按本仓约定保留不动**
> （历史快照，含当时的证据、预测与预测漂移的复盘）；两边不一致时**以本文件为准**。

## 进度总览

| # | 事项 | 状态 | 落地记录 |
| --- | --- | --- | --- |
| — | 第二批：`MainActivity.kt`(1,123 行) → 6 个文件 | ✅ 2026-09-16 | 09-16 §13（含计划与实测的三处差别） |
| 1 | detekt 由 `report` 切 **block** + `detekt_selftest` 防空转 | ✅ 2026-09-16 | 09-16 §14 |
| 2 | 开启 detekt 复杂度规则，按实测清单收敛（4 条体量规则显式关闭） | ✅ 2026-09-16 | 09-16 §14 + `detekt.yml` 文件头 |
| 3 | 双主题去重 + App 级组件层（最大的一项） | ✅ 2026-09-16 ⚠️ 原验收未达成 | 09-16 §15–§22；口径见下方「#3 收官」 |
| **4** | **错误模型统一**（⚠️ 证据行 09-18 复核修正：不是「正在重复消费」，是 4 个手工 `consume` + 3 个 `(Boolean, String)` 回调） | 🔶 **4a ✅ · 4b ✅（2026-09-18）** · 4c 未做（3 个 `(Boolean, String)` 回调）；单测 121→127→**135** | 本节「4a/4b 落地结果」 |
| 5 | Repository 拆分 + `Clock` 注入 + 轻量 DI（⚠️ 证据行 09-18 复核修正：跨零点**早已可注入且已被测**，只剩 12 处硬调） | ⏳ 未开始（前置 #4）；分 5a/5b/5c | — |
| 6 | 派生数据下沉 VM + `WhileSubscribed` | ⏳ 未开始（前置 #5）；**范围已缩小**，见下 | — |
| 7 | 字符串资源化 + 无障碍补全 | ⏳ 未开始（前置 #3 已满足 ⇒ **随时可插队做**） | — |
| 8 | 诊断包 + 许可清单（⚠️ 09-18 复核：健康告警条**自 09-15 已在首页运行**，原「没有任何页面消费它」是错的） | ⏳ 未开始（**无前置**，可随时做）；**范围已缩小** | — |
| 9 | CI 加固 | 🔶 用户已否掉大半，剩 4 个小项 | 09-17 §10 |

**排序原则（原文照录，对剩余项仍适用）**：**先能拦、再去重、后补体验**。
**为什么是这个顺序**：#1/#2 先把「已经为零的基线」变成拦截，之后任何一步的回归都会被 CI 当场抓住
（否则 #3 那种七千行量级的改动没有安全网）；#3 收益最大也最险，必须建立在组件层模板 + 守卫改写之上；
**#5 排在 #4 之后，是因为错误模型会改 Repository 的返回类型 —— 先拆文件再改签名等于同一批代码搬两次。**

---

## #4 错误模型统一 —— 🔶 4a 已落地（2026-09-18），4b/4c 未做

> ⚠️ **2026-09-18 复核修正（核查第 12 处）**：本节原证据行写「错误提示目前靠**散落的** `MutableStateFlow<String?>` +
> Snackbar 文案：**多个订阅方会重复消费同一条**」。逐条实测后不成立，已按代码实情重写 ——
> 原句把「一次性事件用状态建模」这个**机制隐患**说成了「正在发生的重复消费 bug」。

- **证据（改造前基线，2026-09-18 逐条实测；4a 之后的现值见本节末「4a 落地结果」）**：
  `Channel<` 0 处、`UiEvent` 0 处、`Result<` **3** 处（全在 `data/CloudSync.kt`，4a 未动）。
  错误与一次性提示当时走**两套各自的土办法**：
  1. ~~**4 个「可空 StateFlow + 手工 `consume`」** 承载一次性事件~~（**4a 已消除**）：`_undoRequest`（`AppViewModel.kt:162`）、
     `_deletedConsumption`（`:177`）、`_restoredArchivedEvent`（`:187`）、`_autoSyncMessage`（`:249`，
     唯一的 `String?` 型，装的是**成功**提示「已自动同步到坚果云 ☁️」）。每条**只有 1 个订阅方**，
     且 **4/4 都调了 `consumeXxx()`**（`MainApp.kt:145`/`:162`、`ConsumptionLogScreen.kt:50`、`HomeScreen.kt:105`）
     ⇒ **目前没有正在发生的重放 bug**，靠的是人肉纪律而非机制：新增第 5 个事件时忘调 `consume` 就会静默重放。
  2. **3 个 `(Boolean, String)` 回调** 承载失败提示（**4c 待做**）：`syncUpload` / `loadCloudBackups` / `syncDownload`。
     这一套的问题更实际 —— ① 布尔 + 字符串不如 `Result` 自解释，且把 `NutstoreSync` 已经分好类的失败原因
     **压平**了（09-15 那轮做的「错误分类 / 诊断 / 失败留档」到 VM 这层就丢了类型）；② 回调捕获的是
     **当时那个界面的** Snackbar 宿主，旋屏后提示可能落到已销毁的宿主上；③ 三处各自重复「账号密码为空 ⇒ 同一句话」的样板。

  另：**自动同步失败是刻意静默的**（`AppViewModel.kt:277` 注释原文「失败静默忽略，下次启动重试；不打扰用户」）——
  这是产品决定不是缺陷，但意味着今天**没有一个正确的载体**能承接「哪天想让用户知道同步失败了」。
- **本项的真实性质**：**预防性改造**（把纪律换成机制 + 给失败原因保住类型），不是救火。
  它排在 #5 之前只有一个理由：#5 会改 Repository 的返回类型，先拆文件再改签名等于同一批代码搬两次。

### 做法：分三阶段，每阶段一个提交、各自过 CI

| 阶段 | 做什么 | 验收 | 风险与注意 |
|---|---|---|---|
| **4a** ✅ 已做（2026-09-18） | 新建 `viewmodel/UiEvent.kt`（`sealed interface UiEvent` 4 类 + `UiSurface` 3 个落点）；`AppViewModel` 里**三条** `Channel<UiEvent>` + **一个** `emit()` 发送点；4 个可空 StateFlow、4 个 `consumeXxx()`、3 个嵌套数据类全删；3 个收集点改收 Channel；新增 `UiEventTest`（6 例）钉住分流 | 可空事件状态 **0** 个；`fun consume` 型清空函数 **0** 个（只剩业务方法 `consumeOne`）；单测 121 → **127**；ktlint/detekt 待 CI 验 | ⚠️ **实施时推翻了本行原先的两处设想**：① 落点是**三个**不是两个（漏了首页 `AppScaffold` 的宿主）；② `Channel` 是单接收方语义，**一条队列挂多个收集协程会互相抢事件** ⇒ 只能按落点分队列，见下方「4a 落地结果」 |
| **4b** ✅ 已做（2026-09-18） | `ui/components/UndoSnackbar.kt` 新增 `internal suspend fun showUndoSnackbarAcrossThemes(isMiuix, md3Host, miuixHost, message): Boolean`；`MainApp` 三处形状相同的 if/else 各收成一次调用（含 `archiveSelected()` 那条非事件流），随之失效的 3 条 import 删掉；新增 `SnackbarCopyTest`（8 例）钉住文案与落位 | 主壳里 `SnackbarResult` 字样 **0** 处；分流实现 **1** 处；单测 127 → **135**；CI ✅ | ⚠️ 三处期望值最初是**猜的**、且第一遍测量脚本按 `startswith(文件名)` 匹配带目录前缀的相对路径 ⇒ 6 项假零；两处坑（子串陷阱 / 路径匹配）都记在测试类注释里 |
| **4c** | 3 个 `(Boolean, String)` 回调改成 `Result` / `sealed` 返回，失败经 `UiEvent.Notice` 报信；`NutstoreSync` 的错误分类不再被压平 | `(Boolean, String)` 回调 **0** 处；设置页 3 处调用点改完；失败原因可分类（凭据缺失 / 网络 / 格式 / 损坏态拒绝） | 会碰 `SettingsScreen.kt`（`ImeHandlingTest` 点名的 10 个文件之一）⇒ 只改回调、不动 IME 相关行；`CorruptGuardTest` 逐字断言 `syncDownload` 体内的 `snapshotBeforeRestore()` 与 `previewBackup(raw) == null` ⇒ **这两个子串必须留在原函数体内**，否则同批改测试 |

### 4a 落地结果（2026-09-18，提交见台账）

- **现值（可复跑：`bash tools/doc-metrics.sh`）**：`Channel<` **3** 处（= 三个落点各一条队列）、
  可空事件状态 **0** 个、事件型 `consume` 函数 **0** 个、单测 **127** 例（121 → 127，新增 `UiEventTest` 6 例）。
- **实施时发现的两个设想错误**（都已按代码实情改，不是照原计划硬做）：
  1. **落点是三个不是两个**：除主壳覆盖层与消耗记录页，**首页 `AppScaffold` 也有自己的宿主** ——
     自动同步提示一直落在那里。规划时只读了 `MainApp` 与消耗记录页，漏了首页。
  2. **一条 `Channel` 挂多个收集协程是错的**：`Channel` 是**单接收方**语义，多个收集协程会互相抢事件
     （谁先 `receive` 谁拿到，结果不确定）。所以「一个 Channel + 一份收集实现挂三处」这个原设计不成立，
     实际做法是**按落点分三条队列**：发送端仍只有一处（`emit()` 按 `UiEvent.surface` 分流），
     每条队列恰好一个接收方，且接收方的生命周期与该宿主一致。
- **为什么必须按落点分（而不是统一到主壳）**：① 落位会变（主壳覆盖层是「动画偏移 + `imePadding`」的自定义定位，
  与二级页 `AppScaffold` 内的 `SystemBars`/`FloatingNav` 落位不同）⇒ 那是视觉改动；② **更硬的理由是生命周期** ——
  `showSnackbar` 会挂起直到提示被关掉，若在用户离开首页时往首页那个**没有渲染**的宿主上发提示，
  收集协程会被永久堵住，后续所有撤销条都不再出现。队列化正好复现旧行为：事件排队等用户回到那一页。
- **纯搬运判据**：4 条提示的文案、落位、主题分流逻辑逐句未改；`undoDeleteConsumption` 里那句
  「若待处理事件正是这条记录就清空」的防御代码删掉了（收集端一直是「先 consume 再弹条」，弹条期间状态早已是 null，
  那句永远不成立；改用 Channel 后也没有「待处理事件」可清）—— 这是本次唯一的逻辑删除，已在代码注释里写明理由。
- **待真机复测（与 4b 一起过一次）**：见本节末的复测口径。

### 4b 落地结果（2026-09-18，提交见台账）

- **收了什么**：`MainApp` 里三处**形状完全相同**的 if/else（撤销消耗、恢复归档、批量归档）→ 一次
  `showUndoSnackbarAcrossThemes(...)` 调用；两主题的 `SnackbarResult` 枚举不再出现在主壳（实测 **0** 处）。
  第三条不是事件流（`archiveSelected()` 里 `scope.launch` 直接调），一并收了 —— 重复的是「分流」，与走不走事件无关。
- **现值（可复跑：`bash tools/doc-metrics.sh`）**：单测 **135** 例（4a 后 127 → 4b 新增 `SnackbarCopyTest` 8 例）。
- **一个被否掉的更「干净」方案**：让主壳直接用二级页那个 `AppSnackbarHostState` 容器（它已经把分流收在容器里了）。
  否掉的理由是生命周期，不是审美：那容器是 `remember(isMiuix)` 建的，切主题会换**新**容器与新宿主，
  而主壳的收集协程是 `LaunchedEffect(Unit)`（key 一变就取消协程、中断 `showSnackbar` —— 当年 MD3 撤销条不出现的
  根因），协程捕获的还是旧容器 ⇒ 提示弹到**没有渲染**的宿主上，而 `showSnackbar` 挂起到关闭为止
  ⇒ 收集协程永久堵住，之后所有撤销条都不再出现。所以主壳这两个宿主的身份必须跨主题稳定，分流只能收在自由函数里。
  理由写进了 helper 的 KDoc，并由 `SnackbarCopyTest` 钉住「主壳不再出现结果枚举」。
- **`SnackbarCopyTest` 钉了什么**（8 例）：5 条提示文案在全仓的**分布**（哪个文件、几处）、
  恢复归档那两句在主壳与归档页**两处一致**（全仓唯一故意重复的文案：归档页走本地回调、不经事件系统）、
  分流只有一处实现、两主题的「撤销」标签与 6 秒自关、主壳覆盖层落位 7 个标记
  （底部对齐 / 导航栏避让 / 键盘避让 / 动画偏移 84-8dp / 两主题各自宿主形态）、二级页落位
  （首页 `FloatingNav`、消耗记录页与归档页走 `AppScaffold` 默认 `SystemBars`）。
  首例是**注释剥离的阳性对照**：`UiEvent.kt` 的 KDoc 里也写着这些文案，剥注释后必须查不到，
  否则「只出现在 X 文件」这类断言全是假的。
- **顺带查清的一件事**：全仓其实有 **6 处** Snackbar 宿主站点（主壳覆盖层 + 5 个二级页各自持有
  `AppSnackbarHostState`：首页 / 消耗记录 / 归档 / 食品详情 / 设置），其中只有 **3 处**收 `UiEvent`。
  此前文档里「三个宿主」的说法容易被读成「全仓只有三个宿主」，已按此改写。

- **沿用的两条既有约束**（原风险项，09-18 逐条实测仍成立；其中 ② 已由 4b 的 `SnackbarCopyTest` 钉进 CI）：
  ① `AppViewModel` 在 detekt `TooManyFunctions` 的显式豁免清单里（**53** 个函数，09-16 快照，`detekt.yml:85`）⇒
  4a 新增 `emit()` 不会撞门禁，但**别再往里堆** —— #5 拆完 Repository 后要回去重评那 4 条体量规则（`detekt.yml:88` 已登记）；
  ② **跨主题宿主对象不得漏回屏幕层**（`docs/ARCHITECTURE.md:157`）：MD3 与 Miuix 的 `SnackbarHostState` 是两个
  不相干的类型，`AppSnackbarHostState` 对外只暴露 `showUndoSnackbar(): Boolean` ⇒ 4b 的 `UiEventHandler`
  必须在 App 层内部消化 `SnackbarResult` / `MiuixSnackbarResult`，交给 VM 的只能是「用户点没点撤销」这个布尔。
- **全项验收**：① 一次性事件只剩一种建模方式；② 失败提示有类型、可分类；③ 撤销条与提示条的**出现位置和文案逐条不变**
  （这是「纯重构」的判据 —— 任何位置变化都要单独提交 + 真机复测；**4b 已把它钉进 CI**：`SnackbarCopyTest`）；④ 单测数只增不减；⑤ ktlint / detekt 0。
- **守卫交接**：✅ 4a 落地时已交接 —— `tools/doc-metrics.sh` 那条「一次性事件必须有 `consume` 配对」的临时守卫
  已改成「**禁止再出现可空 StateFlow 型一次性事件**（目标 0 个）」，同一次提交里完成，没留两套。
  它的阳性对照也跟着换了：临时造一个 `MutableStateFlow<X?>(null)` 就必须报警。
- **真机复测口径（4a+4b 都已做完 ⇒ 现在就可以测）**：两主题 × 四处提示 —— 列表页减号（撤销消耗）、
  列表页搜索里恢复归档、消耗记录页删除记录、启动时自动同步成功提示。每处确认：① 提示出现在**原来那个位置**；
  ② 点「撤销」真的回滚；③ 旋屏一次不重复弹；④ 停在别的 Tab 时触发的事件，**回到该页才弹**（队列语义）。
  文案与落位已由 `SnackbarCopyTest` 在 CI 上钉住 ⇒ 真机这一轮只需确认「看起来对、点得动」，不必逐字比对。

## #5 Repository 拆分 + `Clock` 注入 + 轻量 DI

> ⚠️ **2026-09-18 复核修正（核查第 13 处）**：本节原证据行写「`now()` 直接调用 **34** 处 ⇒ **时间不可注入，
> 跨零点逻辑无法单测**」。后半句**是错的，而且写下来那天就错** —— 跨零点逻辑在 08-21 那轮（fix-plan 阶段 6）
> 就已经做成可注入 `today` 的纯函数，并且**真的有单测在测**。前半句的「34」也是个会误导的口径（含注释与默认参数）。

- **前置**：#4（错误模型会改 Repository 的返回类型；先改签名再搬文件，避免同一批代码搬两次）。
- **证据（2026-09-18 逐条实测）**：
  - `FoodRepository.kt` **950** 行 / **47** 个类级函数（另有 1 个局部函数 `FoodRepository.kt:420`；47 与 detekt 09-16
    `TooManyFunctions` 快照一致），一个类管着库存、归档、消耗、录入历史、阈值分类位置、
    全部设置项、坚果云凭据、备份导入导出 —— 改任一领域都要先读懂其余六个。
  - `Application` 子类 **0** 个；仓库在 `AppViewModel.kt:44` 用 `private val repo = FoodRepository(application)` 现场构造。
  - `now()` **34** 处的真实构成：**注释/KDoc 5 + 默认参数 3 + 便捷属性委托 5 + 函数体硬调 21**。
    硬调 21 处里**该修的只有数据层与 VM 的 12 处**：`FoodRepository.kt` 7 处（损坏留档时间戳、归档上限裁剪的 today、
    消耗压缩的 today、CSV 导出的 today 等）、`AppViewModel.kt` 5 处（自动同步间隔天数、每日快照判定、
    三处「上传于 / 恢复于 …」时间戳）。其余 9 处在 UI 侧，多为文件名与显示初值（`SettingsScreen` 导出文件名 2 处、
    `MainActivity` 的 `LocalToday` 刷新 3 处、`EditFoodScreen` 生产日期初值 1 处、`TodayProvider` 默认值 1 处、
    `CloudSync`/`LocalSnapshotStore` 文件名与时间戳各 1 处），注入价值低，**刻意不动**。
  - ✅ **已经可注入、且已被测的**（不要再当待办）：`daysLeftAt` / `statusForAt` / `freshnessAt` / `elapsedRatioAt` /
    `remainingTextAt`（`FoodModels.kt`）与 `compactConsumptionAt` / `buildCsvExport(…, today)` 都收 `today: LocalDate`；
    `FoodModelsTest` 传固定日期断言剩余天数（含过期负数）、`CompactConsumptionTest` 用固定 `today = 2026-08-21`
    测 90 天压缩与跨月聚合、`CsvExportTest` 同理；界面侧有 `LocalToday` CompositionLocal + `MainActivity` 在
    `ON_RESUME` 刷新。⇒ **本项的时间部分只剩「把 12 处硬调接到同一个可注入时钟上」。**
- **⚠️ 最大的风险：会撞三个读源码/真跑仓库的测试，且清单已随 09-16 合并变过**（原路线图点名的
  `MiuixHomeScreen.kt` / `MiuixConsumptionLogScreen.kt` **已被删除**，别照抄旧清单）。2026-09-18 实测清单：
  - `CorruptGuardTest` 读 4 个文件（`data/FoodRepository.kt`、`data/SecureStore.kt`、`ui/screens/HomeScreen.kt`、
    `viewmodel/AppViewModel.kt`），对 `FoodRepository.kt` **逐字断言** `upsert` / `changeQuantity` / `discardCorrupt`
    的函数体内容，还断言 `if (enc != null)` 在该文件里**恰好 2 处**；
  - `CompactConsumptionTest` 读 2 个（`data/FoodRepository.kt` 断言含 `if (!record.isDeletable())`、
    `ui/screens/ConsumptionLogScreen.kt`）；
  - `FoodRepositoryGuardTest` 是**真跑** DataStore 的集成测试（8 例），走 `FoodRepository(dataStore, corruptDir)`
    这个 `internal` 主构造 ⇒ 拆分后构造方式一变，这里必须同批改；
  - 且 `CorruptGuardTest.functionBody()` 是**按 4 空格缩进截函数体**的 ⇒ 函数签名一搬家就会
    `assertTrue("源码里找不到 …")` 直接失败。
  - ⇒ **拆分与测试改动必须在同一个提交里**，否则 CI 必红且红得莫名其妙。

### 做法：分三阶段（顺序与旧路线图不同 —— DI 先做，否则 VM 拿不到注入的时钟）

| 阶段 | 做什么 | 验收 | 风险与注意 |
|---|---|---|---|
| **5a** | `Application` 子类 + 轻量容器：`ChiliMeApp`（`AndroidManifest.xml` 挂 `android:name`）持有 `AppContainer`（`clock` / `repo`）；`AppViewModel` 从 `application` 取容器，**构造签名保持 `(Application)` 不变** | `Application` 子类由 0 变 1（容器挂在它上面）；`FoodRepository(application)` 现场构造归零；**不引入 Hilt/Koin** | ⚠️ **不能给 `AppViewModel` 加带默认值的第二参数**：`ViewModelProvider` 的默认工厂用反射找 `(Application)` 构造器，而 Kotlin 的默认参数只生成带 `DefaultConstructorMarker` 的合成构造器 ⇒ 反射找不到、运行时崩。时钟从容器取，不从构造参数取 |
| **5b** | 时钟注入：`FoodRepository` 的 `internal` 主构造加 `clock: Clock = Clock.systemDefaultZone()`，替换数据层 7 处 + VM 5 处硬调；给「跨零点的归档裁剪 / 消耗压缩 / 自动同步间隔」补单测 | 数据层与 VM 的 `now()` 硬调 **0** 处（UI 侧 9 处刻意保留，并在脚本里注明口径）；新增单测**用固定时钟**断言跨零点行为；单测数只增不减 | 行为必须逐位不变：默认值就是系统时钟 ⇒ 生产路径零改动。`FoodRepositoryGuardTest` 已经能用 internal 构造传临时 DataStore，加时钟是同一套路 |
| **5c** | 按领域拆分（**纯搬运**，签名与实现不改）：核心读写与损坏三态（`Decoded` / `DecodeCache` / `rawFlow` / `markCorrupt`）留作共用底座；其余按库存、归档、消耗、备份导入导出、设置与凭据分文件 | `FoodRepository.kt` 不再是 950 行单文件；每个新文件 < 400 行；`git diff --stat` 里**新增行数 ≈ 删除行数**（纯搬运的判据）；三个测试同批改完、CI 绿 | 本项最险：一次要搬 48 个函数。建议**一个领域一个提交**（库存 → 归档 → 消耗 → 备份 → 设置），每个提交自带守卫改动；每搬完一个领域立刻 `git diff -w --stat` 确认没顺手改实现 |

- **全项验收**：① 跨零点相关逻辑有**用固定时钟**跑的单测；② 依赖只有一个构造点；③ 单文件不再超过 400 行；
  ④ 全程行为不变（`git diff` 里除 `package` / `import` / 构造注入外没有逻辑改动）；⑤ 单测数只增不减；⑥ ktlint / detekt 0。
- **顺带**：`ImeHandlingTest` 点名 10 个文件（`MainActivity.kt` / `MainApp.kt` / `BatchBars.kt` / `NavChrome.kt`
  + 4 个屏幕 + `AppFormDialog.kt` / `AppBatchMoveDialog.kt`）。拆 Repository 正常碰不到它，5a 也不需要动
  `MainActivity.kt`（容器挂在 `Application` 上，从 VM 里取）⇒ **清单不动**。

## #6 派生数据下沉 VM + `WhileSubscribed`（**范围比原路线图小**）

- **前置**：#5。
- **证据**：`stateIn(` **20** 处，`WhileSubscribed` **0** 处 ⇒ 全部 `SharingStarted.Eagerly`，后台仍在算。
- **⚠️ 原路线图的另一半已经不成立了**：它写的「屏幕仍在组合期筛选/排序」—— 今日实测**被禁的聚合模式在屏幕文件里是 0 处**
  （`sumOf {` / `groupBy {` / `count {` / `.sortedByDescending`，`ScreenParityTest` 静态拦截；
  阳性对照：同一模式在 8 个 `*State.kt` 里命中 **18** 处，证明测量本身有效）。
  宽口径（把 `filter` / `map` 也算上）只剩 **5** 处，且**都不是业务聚合**：
  `EditFoodScreen.kt` 4 处（3 处是输入框只留数字字符的 `filter { ch -> ch.isDigit() }`，1 处是 `historyEntries.filter`）、
  `StatsScreen.kt:232` 1 处（把状态层算好的 `categoryShare` 转成图表要的 `Float`，属绘制适配）。
  `ScreenParityTest` 的注释也写明「只拦聚合，布局相关的 map/filter 不在此列」。
- **⇒ 本项现在只剩两件事**：① `stateIn` 加 `WhileSubscribed(5_000)`；② 派生数据（还在屏幕里现算的少量筛选）下沉到 `*State.kt` / VM。
- **风险**：`Eagerly → WhileSubscribed` 是**行为改动**（后台不再预热，冷进页面首帧可能等一次解码）。
  `DecodeCache` 已让同一份 JSON 只解一次，但首帧延迟仍需真机确认；建议**一个屏幕一个提交**、每个提交单独真机过一遍。

## #7 字符串资源化 + 无障碍补全（**可随时插队**，前置 #3 已满足）

- **为什么现在能做**：原路线图要求「#3 去重后再做，否则同样的文案要改两遍」—— 09-16 八对已全数合并，
  双胞胎归零 ⇒ 这个前置已经满足，且**不必等 #4/#5**。
- **证据（资源化）**：`strings.xml` **1** 条（只有 `app_name`）vs 含中文的字符串字面量 **583** 处
  （口径：剥注释后统计，见 `tools/doc-metrics.sh`；不剥注释会因注释里的引号虚高到 591）。
  ⚠️ 历史报告里的「672 处中文硬编码」（`2026-08-21-fix-plan.md` 阶段 6）没记测量方法，**与 583 不可比，别混用**。
- **证据（无障碍）**：`contentDescription = null` **50** 处；`Modifier.semantics { }` 真调用 **1** 处
  （`ui/components/UndoSnackbar.kt:174`）。⚠️ 多份历史报告写的「7 处 semantics」是**把 6 行 import 算进去了**
  （`chileme-review.md` 已就此追加批注）。另：统计图表无 semantics ⇒ 屏幕阅读器读不出数据。
- **做法与顺序**：先补无障碍（改动小、收益直接、可用守卫拦），再做资源化（583 处，机械但量大，建议按屏幕分批）。
  两项都**必须同批更新守卫清单**：屏幕文件一旦新增 `stringResource`，`ScreenParityTest` 的「逐字相同」类断言不受影响，
  但 `ImeHandlingTest` / `CorruptGuardTest` 那类按文件点名的守卫要确认路径没变。
- **验收**：`contentDescription = null` 逐条判定为「刻意留空（装饰性）」或「补上描述」，**不允许有未判定的**；
  图表补 `semantics` 后能被 TalkBack 读出数据；资源化的验收按屏幕给（如「设置页 184 处全部入 `strings.xml`」）。

## #8 诊断包 + 许可清单（**范围已缩小**：健康提示那半已经有了）

> ⚠️ **2026-09-18 复核修正（核查第 11 处）**：本节原证据行写「`corruptedKeys` 被引用 32 处，但**没有任何页面消费它** ——
> 用户看不到「哪些数据坏了」」。**这句是错的，而且写下来那天就错**：首页自 2026-09-15 起就有一条置顶告警条在消费它。
> 标题、前置与验收随之下调。

- **前置**：~~#4/#5~~ ⇒ **无**（告警条已在运行，加诊断包不必等错误模型与 Repository 边界定型）。
- **证据（2026-09-18 逐条实测）**：
  - ✅ **已经有的**：`ui/components/DataCorrupt.kt` 的 `DataCorruptBanner` 在 `HomeScreen.kt:126` 置顶显示，
    用人话报「库存、归档 数据读取失败」+「这部分数据的写入已暂停，其余数据不受影响（2026-09-15 起按 key 粒度降级）」
    +「原始内容已留档到应用私有目录 corrupt/ 下」，并给两个出路：导入此前的备份恢复 / 「放弃这部分数据」
    （`HomeScreen.kt:233` 有二次确认弹窗）；`corruptKeyNames()` 把 key 翻成中文名，横幅与弹窗共用；
    `CorruptGuardTest` 静态断言这条链路接上了（`onDiscard = {` + 「放弃损坏的数据？」）。
  - ❌ **仍然缺的**：诊断包 **0** 处、开源许可清单 **0** 处（两者都还不存在）；告警条**只出现在首页**；
    它笼统说「写入已暂停」，**没有逐 key 说清「坏了会影响哪个具体功能」**。
  - `corruptedKeys` 被引用 **32** 处（作用域 `app/src/main`，出现次数口径）—— 这个数本身是对的，
    错的只是「没有页面消费它」那半句。
  - ℹ️ 原文还有一条「`corrupt/` 留档目录相关代码 **14** 处」：该口径**无法复现**（09-18 实测按命中行数 18、
    按出现次数 53），且不影响本项决策 ⇒ **删数不删事实** —— 留档机制在上面 ✅ 那条里已说明。
- **做法**：① **诊断包**（本项对你最有用的部分）：一键导出「版本 / 设备 / 各表条数 / 损坏 key / 最近日志」，
  **不含**用户数据明文与凭据，走既有 SAF 导出通道；② **许可清单**：`play-services-oss-licenses` 之类或自生成
  （上架 Google Play 需要开源库归属声明）；③（可选，价值最低）把告警条升级成整页健康检查、逐 key 说明影响。
- **验收**：诊断包可被用户导出并发给开发者，导出内容经一次人工审查确认**不含凭据**
  （本仓密钥已做过全历史扫描，见 09-17 §10）；许可清单可在应用内打开；
  ①② 两项**都不改变现有告警条的文案与位置**（那是已被守卫钉住的行为）。

## #9 CI 加固 —— 🔶 用户已否掉大半（2026-09-17）

- **用户决定**：只做「零件过期」与「依赖/密钥检查」两类；随后又判定「这些都不重要」⇒ **本项整体降级为可选**。
- **已做但推不上去**：13 处 action 升级（`upload-artifact` v4→v7.0.1 / `cache` v4→v6.1.0 /
  `gradle/actions/setup-gradle` v4→v6.3.0 / `action-gh-release` v2→v3.0.3 / `dependency-review-action` →v5.0.0 /
  `gitleaks-action` →v3.0.0 等）已改好并验证过（`git apply --check` + `git diff` 核对），
  但沙箱的 GitHub App 令牌**缺 `workflows` 权限** ⇒ 存为 `docs/audits/2026-09-17-ci-actions-upgrade.patch` 待用户自己应用。
- **密钥泄漏已查清**：本地全历史扫描 **207** 个提交，**零凭据**（只有 AOSP 公开的 debug keystore 口令 `android`）。
  ⚠️ 那次扫描第一次跑出了**假阴性**：`git grep -E` 是 POSIX ERE，不支持 `(?!…)` 与 `\s`，
  正则报错被 `2>/dev/null` 吞掉 ⇒「零命中」毫无意义。改成 POSIX 安全写法 + **强制阳性对照**后才可信；
  这条教训已固化进 `tools/doc-metrics.sh` 的文件头与末尾两项对照。
- **因此建议不做**（成本收益复评）：gitleaks / zizmor / `verification-metadata.xml` 依赖校验 ——
  已确认无密钥泄漏，加装只是每条 PR 多两处可能红的门禁。
- **仍挂着的 4 个小项**（都便宜、都不需要 `workflows` 权限之外的东西，但同样要用户点头）：
  | 项 | 今日实测 | 价值 |
  | --- | --- | --- |
  | `paths-ignore`（改文档也跑全量构建） | **0** 处配置 | 省 CI 时间；本仓文档提交占比很高（09-17 当天 17 个提交里 6 个是纯文档） |
  | APK 体积基线 | **0** 处 | 防体积无声膨胀（现值 debug 23.1 MB / release 2.3 MB） |
  | `bundleRelease`(AAB) | **0** 处 | 上架 Google Play 需要 AAB，现在只出 APK |
  | `versionCode` 改用仓库内版本文件 | 现为 `github.run_number`（`release.yml` 里 2 处引用） | run number 与语义版本脱钩，回滚/重跑会产生奇怪的 versionCode |

---

## #3 收官：验收口径与偏差（**不要再引用旧数字**）

- **达成的**：`Miuix*Screen.kt` 双胞胎 **8 对 → 0**；屏幕本体 17 文件 7,541 行 → **9 文件 4,209 行（-44%）**；
  `AppNavGraph.kt` 与 `NavChrome.kt` 零主题分支；加一个功能的边际成本从「改两个文件 + 一处 if/else」降到「改一个文件」。
- **⚠️ 未达成的**：原验收写「16 个渲染层文件 → 8 个、7,205 行 → **4,500 量级**（约 -24%~-37%）」，
  实测渲染层合计**不在本文件维护**：现值与口径见 `docs/DESIGN_SPEC.md` §7 的「口径」行（那是这些数字的唯一落点，附复核命令）。
  ⚠️ 09-16 收官时记的 **6,950 行（-8%）** 已不是现值 —— 09-17 的 IME 修复与弹窗搬家让渲染层净增 334 行，降幅收窄到 -3.4%。
  偏差几乎全部来自最后一对（设置页）：估的时候把它当成「外壳重复度高」的屏幕，实际它是八对里唯一
  「body 排版习语分叉」的（664 行里只有 287 行逐字相同），故 body 保留两套。
- **⇒ 三个数字已作废，别再引用**：「-24%」、「4,500 量级」、以及 `detekt.yml` 里「7,205 行 / 16 个渲染层文件」
  与 `docs/DESIGN_SPEC.md` 里「7,541 行 / 17 个文件」—— 后两者是同日**两个不同文件集口径**的快照，彼此不可换算。
  复盘全文见 `devlog/2026-09-16.md` §22「预测漂移」。
- **刻意不做的两件事**（当时的决定，仍然有效）：① 不为达标削组件层注释（KDoc 占 22%，
  但那轮的每条结论都是靠注释才没在下一对里被推翻）；② 不把「同构但不同参」的控件强行合并
  （`MiniStat` vs `AppStatCard`、`QuantityStepper` vs `AppStepperPill`）—— 那会把「只有真机看得出来的差异」变成改动。
- **遗留（已登记进 `devlog/INDEX.md` 待办）**：`FoodCard.kt:161/251` 仍自己分流一份进度条，与组件层
  `AppLinearProgress` 不同构（6dp vs 8dp、`weight(1f)` vs `fillMaxWidth()`）⇒ 收编是**视觉改动**而非纯重构，
  需两主题真机复测。
