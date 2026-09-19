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
| **4** | **错误模型统一**（⚠️ 证据行 09-18 **两次**复核修正：不是「正在重复消费」，是 4 个手工 `consume` + **5 个回调**；回调数原写「3 个 `(Boolean, String)`」，实测 **4 个 `(Boolean, String)` + 1 个 `(Boolean, Boolean)`** ⇒ 核查第 15 处） | ✅ **4a / 4b / 4c 全部落地（2026-09-18）** · **真机复测已通过**；单测 121→127→135→**143** | 本节「4a/4b/4c 落地结果」 |
| 5 | Repository 拆分 + `Clock` 注入 + 轻量 DI（⚠️ 证据行 09-18 **两次**复核修正：跨零点**早已可注入且已被测**；该接时钟的是**数据层 9 + VM 5 = 14 处**，原写 12，见核查第 17 处） | ✅ **全项收官（2026-09-18）**：5a+5b+5c 落地 · 用户真机复测通过（debug 包、两主题、按清单全过）| 本节「5a 落地结果」+「5b 落地结果」+「#5c 收官后的现值」（含 8 个文件的行数表）|
| 6 | 派生数据下沉 VM + `WhileSubscribed` | ⏳ 未开始 ⇒ **执行并入 #10c**（与 VM 拆分同批、共用一轮真机复测）；**范围已缩小**，见下 | #10 节 |
| 7 | 字符串资源化 + 无障碍补全 | ⏳ 未开始（前置 #3 已满足 ⇒ **随时可插队做**） | — |
| 8 | 诊断包 + 许可清单（⚠️ 09-18 复核：健康告警条**自 09-15 已在首页运行**，原「没有任何页面消费它」是错的） | ⏳ 未开始（**无前置**，可随时做）；**范围已缩小** | — |
| 9 | CI 加固 | 🔶 用户已否掉大半，剩 4 个小项 | 09-17 §10 |
| **10** | **UI/VM 层拆分收尾**（设置页 1,705 行（规划时）+ `AppViewModel` 700 行；**含 #6 的执行**） | 🔨 **10a 已落地**（2026-09-18）：10a-1 弹窗区（352–986，跨 635 行）搬到同包 3 个文件、10a-2 两套 body 与两个 MD3 专用小组件搬到 4 个文件，设置页入口 1,705 → **297** 行；🔨 **10b-1 … 10b-5 已落地**（2026-09-19）：备份领域 9 个函数搬到同包 `AppViewModelBackup.kt`、云端同步 4 个函数 + 1 个顶层常量搬到 `AppViewModelCloud.kt`、归档与消耗撤销 6 个函数搬到 `AppViewModelArchiveUndo.kt`、食物 CRUD 与批量 11 个函数搬到 `AppViewModelFood.kt`、分类与位置 7 个函数搬到 `AppViewModelCategoryLocation.kt`，`AppViewModel` 700 → 590 → 469 → 431 → 363 → **322** 行（🎯 **验收① 的 viewmodel 那一半自 10b-4 起已达标 < 400**；10b 剩 2 个领域 13 个函数，**不再是达标所需**） | 本节「#10」+「10a-1 / 10a-2 / 10b-1 … 10b-5 落地结果」+ 09-18 §13.7–§13.9 + 09-19 §1–§8 |

**排序原则（原文照录，对剩余项仍适用）**：**先能拦、再去重、后补体验**。
**为什么是这个顺序**：#1/#2 先把「已经为零的基线」变成拦截，之后任何一步的回归都会被 CI 当场抓住
（否则 #3 那种七千行量级的改动没有安全网）；#3 收益最大也最险，必须建立在组件层模板 + 守卫改写之上；
**#5 排在 #4 之后，是因为错误模型会改 Repository 的返回类型 —— 先拆文件再改签名等于同一批代码搬两次。**

---

## #4 错误模型统一 —— ✅ 4a / 4b / 4c 全部落地（2026-09-18）

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
  2. ~~**3 个 `(Boolean, String)` 回调**~~ ⇒ **实测 5 个回调**承载失败提示（**4c 已全部消除**）：
     **4 个** `(Boolean, String)`（`syncUpload` / `loadCloudBackups` / `syncDownload` / `restoreLocalSnapshot`）
     + **1 个** `(Boolean, Boolean)`（`importBackupWithSnapshot`：界面拿「成功了吗 / 前置快照存下了吗」
     两个布尔自己拼三句话）。⚠️ 原写「3 个」是**核查第 15 处**，与第 11/12/13 处同一类
     （文档里的数与代码不符，而没有任何东西会因此变红）：漏掉的 `restoreLocalSnapshot` 与另外三个形状相同
     （属数漏），`importBackupWithSnapshot` 形状不同（属整个漏掉，也正因形状不同，改造时最容易被落下）。
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
| **4c** ✅ 已做（2026-09-18） | `data/CloudSync.kt` 新增 `sealed interface OpFailure`（`Auth` / `Network` / `Other`）+ `Throwable.toOpFailure(fallback)`，401 那句抽成 `NUTSTORE_AUTH_MESSAGE`（4 个抛出点共用）；`UiEvent` 新增 `OpFailed(op, failure)` / `CloudBackupsEmpty(message)`、`Notice` 的落点改为可指定、加 `UiSurface.Settings` 与 `DataOp`（5 值）；`AppViewModel` 加第 4 条 `Channel` 与 `settingsUiEvents`；**5 个**回调全部去掉回调参数、改发事件；`SettingsState` 三层 ×5 + `SettingsScreen` 5 处调用点 + 1 个收集器 | 回调 **0** 处（两种形状都清零）；设置页 **5** 处调用点改完；失败可分类（凭据 / 网络 / 其它）；**文案逐字未改**（机械验证：diff 中消失的字符串字面量 **0** 条）；单测 135 → **143** | 计划里那两条硬约束都守住了：只改回调、没动 IME 相关行；`CorruptGuardTest` 逐字断言的 `snapshotBeforeRestore()` 与 `previewBackup(raw) == null` 仍在原函数体内（改完把该守卫镜像成脚本在本地跑过）。**实施中另撞两件编译级的事**：`UiEvent` 从 4 类变 6 类 ⇒ `MainApp` 的 `when` 不再穷尽（sealed 不穷尽直接编译失败）；`private const val` 被我插在类体内（Kotlin 只允许顶层或 companion object）⇒ 挪到顶层。详见「4c 落地结果」 |

### 4a 落地结果（2026-09-18，提交见台账）

- **4a 当时的现值**：三个落点各一条队列（共 3 条）、可空事件状态 **0** 个、事件型 `consume` 函数 **0** 个、
  单测 **127** 例（121 → 127，新增 `UiEventTest` 6 例）。**4c 之后的现值见本节末「4c 落地结果」**
  （`bash tools/doc-metrics.sh` 可复跑）。这里刻意不再用「`Channel<` **N** 处」那个写法：
  `doc-metrics.sh` 会把它当**当前值**与实测比对，历史数字写成那个形式就会天天报不符。
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

### 4c 落地结果（2026-09-18，提交 `a6b67d7` + 修复 `2fd6089`）

- **现值（可复跑：`bash tools/doc-metrics.sh`）**：`Channel<` **4** 处（= 四个落点各一条队列）、
  `receiveAsFlow()` **4** 处、`UiEvent` **6** 类 / `UiSurface` **4** 个落点、`(Boolean, String)` 与
  `(Boolean, Boolean)` 型回调 **0** 处、单测 **143** 例（135 → 143：新增 `OpFailureTest` 6 例、
  `UiEventTest` +2 例、`SettingsStateTest` 的转发测试补上这 5 个动作）。
- **范围比计划大**：计划写的是 3 个回调，实测 **5** 个（见上方证据第 2 条的修正）。第 5 个
  `importBackupWithSnapshot(raw, (ok, snapshotSaved) -> Unit)` 与「从快照还原」那套**完全同构**
  （都是"成功了吗 + 前置快照存下了吗"⇒ 三句话），留下它验收 ① 就不成立 ⇒ 一并收。
  它原来那三句话是**界面拼的**（在 `SettingsScreen` 里），现在搬到 VM，逐字未改。
- **失败分三档，不是四档**：`OpFailure.Auth`（401）/ `Network`（`IOException` 及其子类：DNS、超时、连接重置）/
  `Other`（HTTP 状态码类、格式不对、云端为空…）。**没有**给状态码单列一档：状态码本来就在文案里、用户看得见，
  而 UI 目前对 507 与 500 没有任何不同处理 —— 为一档没人区分的情况加类型，只是让 `when` 多一个分支。
- **命名当天改过一次**（趁引用还少）：最初叫 `SyncFailure` / `SyncOp`，但「导入备份」与「快照损坏」都不是同步操作，
  装不进这个名字 ⇒ 改成中性的 `OpFailure` / `DataOp`。
- **`CloudBackupsEmpty` 为什么必须单独一档**：`loadCloudBackups` 改造前用 `onResult(true, "")` 表示"成功且非空"、
  用 `onResult(false, "云端暂无备份，请先上传")` 表示"成功但是空的" —— 一个布尔同时背着"失败"与"成功但没东西"两种语义。
  天真地换成 `Result` 会把这一档丢进失败里（界面就会当成错误处理）。所以：成功且非空**不发事件**
  （列表本来就由 `cloudBackups` 这个 `StateFlow` 驱动），成功但为空发 `CloudBackupsEmpty`，真失败发 `OpFailed`。
- **「关掉备份选择器」搬进收集器，且只在 `op == DataOp.ListBackups` 时关**：改造前只有 `loadCloudBackups`
  的回调里写了 `setShowBackupPicker(false)`，上传 / 下载 / 还原失败都不碰选择器 ⇒ 无条件关会改变那三条的行为。
- **`syncUpload` 本地打包失败刻意用 `OpFailure.Other`，不走 `toOpFailure()`**：`buildBackupJson` 抛的是本地数据异常，
  用 `toOpFailure()` 会因为「不是 `IOException`、也不等于凭据那句话」而落到 `Other` —— 结果一样，但**显式写 `Other`
  才表达得出"这是本地数据问题、不是网络问题"**；把本地错误分类成网络错会误导用户去重试。
- **文案零漂移是机械验证的**：`git diff -U0` 里所有被删行的字符串字面量与新增行比对，**消失 0 条**
  （新增的 8 条全是注释里的中文短语）。`OpFailureTest` 另把改造前三个调用点各自的兜底话
  （"上传失败" / "获取备份列表失败" / "下载失败"）逐个写死，包括"消息为空白等同没有消息"。
- **⚠️ 一处「故意没修」**：网络类异常的 `message` 是 OkHttp 的**英文技术串**，改造前就被原样甩给用户，
  本轮照旧透出（改文案属用户可见的行为变更，要单独提交 + 真机复测）。`OpFailureTest` 里有一条断言专门钉住
  "现状如此"，并在注释里写明真要改时该怎么处理（改断言 + devlog 记一条行为变更）。
  **用户已决（2026-09-18）：保持英文、不改** ⇒ 那条断言长期有效，作用变成"防止有人无意改掉"。
- **顺带查出、本轮刻意没动**：`saveLocalSnapshot(onDone: ((Boolean) -> Unit)?)` 在接口 / 包装 / 实现 / VM
  四层管道齐全，但**全仓没有任何调用点**（`grep` 实证）⇒ 疑似死 API（核查第 16 处：不是文档错，是代码里的死管道）。
  **用户已决（2026-09-18）：留着不删** ⇒ 从此它是一处「已知的死代码」，别当成遗漏再查一遍。
- **`maybeAutoSync()` 不在范围内**：它是第 5 条同步路径，但对失败**刻意静默**（代码里那句注释就是产品决定），
  没有回调也没有提示 ⇒ 无事件可发。等哪天要让用户知道自动同步失败了，`OpFailed` 已经是现成的载体。
- **守卫同步升级**（`tools/doc-metrics.sh`，与代码同提交）：那条"替代机制在场"检查原本把队列数写死成 3，
  加第 4 个落点就红 ⇒ 改成**从 `UiSurface` 枚举反推期望值**（落点数 = 队列数 = 对外 Flow 数），
  于是它能抓两种真错误：「有落点没队列」（事件发出去没人收 ⇒ 提示静默消失）与「有队列没落点」。
  两个方向都反证过（临时加第 5 个落点 / 临时删掉 `Settings`，各自报出不符后还原）。
- **CI 红过一次，根因与定位过程见 `devlog/2026-09-18.md` §9–§10**（一句话版：重写 `Notice` 的 KDoc 时把
  `SnackbarCopyTest` 阳性对照所依赖的那句文案写没了；本地无编译器、Actions 日志与产物端点都被墙，
  靠把三份守卫逐条镜像成脚本才定位到）。

- **沿用的两条既有约束**（原风险项，09-18 逐条实测仍成立；其中 ② 已由 4b 的 `SnackbarCopyTest` 钉进 CI）：
  ① `AppViewModel` 在 detekt `TooManyFunctions` 的显式豁免清单里（**53** 个函数，09-16 快照，`detekt.yml:85`）⇒
  4a 新增 `emit()` 不会撞门禁，但**别再往里堆** —— #5 拆完 Repository 后要回去重评那 4 条体量规则（`detekt.yml:88` 已登记）；
  ② **跨主题宿主对象不得漏回屏幕层**（`docs/ARCHITECTURE.md:157`）：MD3 与 Miuix 的 `SnackbarHostState` 是两个
  不相干的类型，`AppSnackbarHostState` 对外只暴露 `showUndoSnackbar(): Boolean` ⇒ 4b 的 `UiEventHandler`
  必须在 App 层内部消化 `SnackbarResult` / `MiuixSnackbarResult`，交给 VM 的只能是「用户点没点撤销」这个布尔。
- **全项验收**：① 一次性事件只剩一种建模方式；② 失败提示有类型、可分类；③ 撤销条与提示条的**出现位置和文案逐条不变**
  （这是「纯重构」的判据 —— 任何位置变化都要单独提交 + 真机复测；**4b 已把它钉进 CI**：`SnackbarCopyTest`）；④ 单测数只增不减；⑤ ktlint / detekt 0。
  **4c 之后逐条对照**：① ✅ 回调清零，一次性事件只剩 `UiEvent` + `Channel` 一种建模方式；
  ② ✅ `OpFailure` 三档，凭据错与网络错在类型上分得开（UI 想据此引导重填凭据，现在有依据了）；
  ③ ✅ 机制上成立（文案零漂移经机械验证 + `SnackbarCopyTest` 钉住分布），**但仍要真机过一轮**（见下方复测口径）；
  ④ ✅ 121 → 127 → 135 → **143**；⑤ ✅ CI run [35297362614](https://github.com/SkyForest233/chileme/actions/runs/35297362614) 三 job 全绿。
- **守卫交接**：✅ 4a 落地时已交接 —— `tools/doc-metrics.sh` 那条「一次性事件必须有 `consume` 配对」的临时守卫
  已改成「**禁止再出现可空 StateFlow 型一次性事件**（目标 0 个）」，同一次提交里完成，没留两套。
  它的阳性对照也跟着换了：临时造一个 `MutableStateFlow<X?>(null)` 就必须报警。
- **真机复测口径（✅ 2026-09-18 用户实机复测已通过、无问题 ⇒ #4 验收 ①–⑤ 全部达成）**：两主题 × 四处提示 —— 列表页减号（撤销消耗）、
  列表页搜索里恢复归档、消耗记录页删除记录、启动时自动同步成功提示。每处确认：① 提示出现在**原来那个位置**；
  ② 点「撤销」真的回滚；③ 旋屏一次不重复弹；④ 停在别的 Tab 时触发的事件，**回到该页才弹**（队列语义）。
  文案与落位已由 `SnackbarCopyTest` 在 CI 上钉住 ⇒ 真机这一轮只需确认「看起来对、点得动」，不必逐字比对。
- **4c 追加的复测面（设置页，两主题 × 5 条流程）**：立即上传、拉取云端备份列表（含「云端暂无备份」这一档）、
  从云端恢复、从本地快照还原、导入 JSON 备份。每条确认：① 成功与失败提示都出现在**设置页自己的宿主**上
  （不是首页、也不是主壳覆盖层）；② 文案与改造前一致；③ **只有「拉列表失败 / 列表为空」会关掉备份选择器**，
  上传 / 下载 / 还原失败时选择器不动（这是改造前的差别，用 `OpFailed.op` 复现的）；
  ④ 未填凭据就点上传 / 拉列表 / 下载 ⇒ 提示「请先填写并保存坚果云账号和应用密码」。
  ⚠️ 网络类失败仍会显示 OkHttp 的**英文技术串**（如 `Unable to resolve host …`）—— 改造前就这样、本轮刻意未改，
  真机看到它**不算回归**；要不要换成中文 ⇒ **用户已决（2026-09-18）：保持英文不改**（见 `devlog/2026-09-18.md` §11）。

## #5 Repository 拆分 + `Clock` 注入 + 轻量 DI —— ✅ **全项收官**（2026-09-18）：5a+5b+5c 落地 + 用户真机复测通过

> ⚠️ **2026-09-18 复核修正（核查第 13 处）**：本节原证据行写「`now()` 直接调用 **34** 处 ⇒ **时间不可注入，
> 跨零点逻辑无法单测**」。后半句**是错的，而且写下来那天就错** —— 跨零点逻辑在 08-21 那轮（fix-plan 阶段 6）
> 就已经做成可注入 `today` 的纯函数，并且**真的有单测在测**。前半句的「34」也是个会误导的口径（含注释与默认参数）。

- **前置**：#4（错误模型会改 Repository 的返回类型；先改签名再搬文件，避免同一批代码搬两次）。
- **证据（2026-09-18 逐条实测）**：
  - `FoodRepository.kt` 在 **#5 开工时是 965 行 / 47 个类级函数**（#5b 结束时实测；这是**历史快照**，
    说的是"为什么非拆不可"，不是现值）。另有 1 个局部函数（种子数据里的 `id()`）；47 与 detekt 09-16
    `TooManyFunctions` 快照一致。当时一个类管着库存、归档、消耗、录入历史、阈值分类位置、
    全部设置项、坚果云凭据、备份导入导出 —— 改任一领域都要先读懂其余六个。
  - **#5c 收官后的现值（2026-09-18）**：`FoodRepository.kt` **252** 行、**9** 个类级函数
    （7 个 `decode*` 包装 + `nutstoreCredentialKeysFlow` + `discardCorrupt`），外加 19 个 key 与 19 条对外读取流；
    搬出去的是 **38 个类级函数**（47 → 9），加上原本就是文件级顶层函数的 `pruneCorruptDir`，共 **39 个顶层函数**分在 7 个领域文件里；`data/` 下 15 个文件**最大 293 行、无一超 400**：

    | 文件 | 行数 | 管什么 |
    |---|---|---|
    | `FoodRepository.kt` | 252 | 核心：key、对外读取流、解码包装、放弃损坏数据的入口 |
    | `RepositoryCore.kt` | 244 | 共用底座：三态解码、损坏留档、读取兜底、损坏目录清理 |
    | `FoodConsumption.kt` | 177 | 消耗与库存变动（改数量含临期自动归档、增删消耗、撤销、补 id、90 天外聚合） |
    | `FoodBackup.kt` | 124 | 备份、CSV 导出、导入预览与写入、整体清空（都要一次性读写全部 key） |
    | `FoodItems.kt` | 106 | 库存：首次示例数据、新增/编辑、批量改存放位置 |
    | `FoodArchive.kt` | 100 | 归档、单条与批量恢复、删除归档条目、清空归档 |
    | `FoodSettings.kt` | 65 | 设置写入：外观 5 个、同步节奏 3 个、分类与位置 3 个（都只写自己那一两个 key） |
    | `FoodCredentials.kt` | 49 | 坚果云凭据的加密写入与旧版明文密码迁移 |

    这两个数（252 / 9）在 `tools/doc-metrics.sh` 的「文档写死的关键数 vs 实测」比对清单里；
    全部文件的行数与「`data/` 最大文件 < 400 行」判据在同脚本的「规模与结构」段实测。
  - `Application` 子类 **1** 个（`ChiliMeApp`，#5a 起）。改造前是 0 个，仓库在 `AppViewModel` 里
    现场 `FoodRepository(application)` 构造 ⇒ 现已归零：构造点只剩容器里那 1 处，
    由 `tools/doc-metrics.sh` 的「依赖构造点」守卫看着（含"Manifest 必须挂同名 `android:name`"这一条，
    因为漏挂只会在**运行时**炸，编译器与单测都发现不了）。
  - `now()` **34** 处的真实构成：**注释/KDoc 5 + 默认参数 3 + 便捷属性委托 5 + 函数体硬调 21**。
    硬调 21 处里**该修的只有数据层与 VM 的 12 处**：`FoodRepository.kt` 7 处（损坏留档时间戳、归档上限裁剪的 today、
    消耗压缩的 today、CSV 导出的 today 等）、`AppViewModel.kt` 5 处（自动同步间隔天数、每日快照判定、
    三处「上传于 / 恢复于 …」时间戳）。其余 9 处在 UI 侧，多为文件名与显示初值（`SettingsScreen` 导出文件名 2 处、
    `MainActivity` 的 `LocalToday` 刷新 3 处、`EditFoodScreen` 生产日期初值 1 处、`TodayProvider` 默认值 1 处、
    `CloudSync`/`LocalSnapshotStore` 文件名与时间戳各 1 处），注入价值低，**刻意不动**。
    ⚠️ **2026-09-18 复核修正（核查第 17 处，#5b 动手量代码时发现）**：上一句把两处**数据层**的算进了「UI 侧」——
    `CloudSync`（云端备份文件名时间戳）与 `LocalSnapshotStore`（快照文件名时间戳）都在 `data/` 下，
    且**只被 VM 调用**（`upload` 2 处、`saveSnapshot` 3 处）。按原口径不动它们的话，5b 验收
    「数据层与 VM 的硬调 **0** 处」就是假的（会剩 2 处）⇒ 本次一并注入：两处都加带默认值的 `clock` 参数
    （默认值 = 系统时钟 ⇒ 行为逐位不变），VM 把容器的时钟传进去。**真正刻意保留的是 UI/主壳 7 处**：
    `SettingsScreen` 导出文件名 2、`MainActivity` 的 `LocalToday` 刷新 3、`EditFoodScreen` 生产日期初值 1、
    `TodayProvider` 默认值 1 —— 理由是界面「现在几点」没有值得测的跨零点逻辑，而把时钟穿进 Compose 要多传好几层。
  - ✅ **已经可注入、且已被测的**（不要再当待办）：`daysLeftAt` / `statusForAt` / `freshnessAt` / `elapsedRatioAt` /
    `remainingTextAt`（`FoodModels.kt`）与 `compactConsumptionAt` / `buildCsvExport(…, today)` 都收 `today: LocalDate`；
    `FoodModelsTest` 传固定日期断言剩余天数（含过期负数）、`CompactConsumptionTest` 用固定 `today = 2026-08-21`
    测 90 天压缩与跨月聚合、`CsvExportTest` 同理；界面侧有 `LocalToday` CompositionLocal + `MainActivity` 在
    `ON_RESUME` 刷新。⇒ **本项的时间部分只剩「把 12 处硬调接到同一个可注入时钟上」。**
- **⚠️ 开工前判定的最大风险：会撞三个读源码/真跑仓库的测试** —— ✅ **这条风险真的兑现了，而且红了两次**
  （都在 5c-4，两个根因都写在提交信息里：一个是搬运脚本漏改声明行、一个是守卫仍指着旧文件）。
  开工前的清单（原路线图点名的 `MiuixHomeScreen.kt` / `MiuixConsumptionLogScreen.kt` **已被删除**，别照抄旧清单）
  与收官后的实际落点：
  - `CorruptGuardTest`：开工前读 4 个文件，**收官后读 7 个**（`data/FoodRepository.kt`、`data/FoodItems.kt`、
    `data/FoodConsumption.kt`、`data/FoodCredentials.kt`、`data/SecureStore.kt`、`ui/screens/HomeScreen.kt`、
    `viewmodel/AppViewModel.kt`）—— 逐字断言的 `upsert` / `changeQuantity` 与计数断言
    `if (enc != null)` **恰好 2 处**都跟着搬到了各自的新文件，`discardCorrupt` 与
    `nutstorePlaintextFallbackFlow` 仍读核心文件；
  - `CompactConsumptionTest`：`if (!record.isDeletable())` 那条改读 `data/FoodConsumption.kt`
    （**这就是 5c-4 第二次红的真因**：搬了代码没搬守卫）；
  - `FoodRepositoryGuardTest` / `FoodRepositoryClockTest`：真跑 DataStore 的集成测试，走
    `FoodRepository(dataStore, corruptDir)` 这个 `internal` 主构造 —— 构造方式 5a 起就没再变，
    而领域函数变成**同包 internal 扩展函数**后，同包测试**无需 import 即可调用** ⇒ 这两个文件一行未改；
  - `CorruptGuardTest.functionBody()` 原本**按 4 空格缩进截函数体**，而搬出去的函数是顶层的（缩进 0）
    ⇒ 已加 `indent` 参数（成员 4 / 顶层 0）。实测：截断点会落在函数体里第一个内部 `}` 上，
    正向断言假红、`!contains(...)` 反向断言假绿；`upsert` 差 1 行（30 vs 31）、`changeQuantity` 差 2 行（50 vs 52），
    这两个函数**碰巧**不至于假绿，但内部块靠前一点的函数会被截掉大半 ⇒ 参数是原则上必要，不是摆设。
  - ⇒ **拆分与测试改动必须在同一个提交里**（已遵守）；并且每搬一个领域都先跑
    `tools/guard-mirror.py`（#5c-4 之后入库的工具，本地几毫秒查出"守卫指着旧文件"）。

### 做法：分三阶段（顺序与旧路线图不同 —— DI 先做，否则 VM 拿不到注入的时钟）

| 阶段 | 做什么 | 验收 | 风险与注意 |
|---|---|---|---|
| **5a** ✅ 已做（2026-09-18） | `Application` 子类 + 轻量容器：`ChiliMeApp`（`AndroidManifest.xml` 挂 `android:name`）持有 `AppContainer`（`clock` / `repo`）；`AppViewModel` 从 `application` 取容器，**构造签名保持 `(Application)` 不变** | `Application` 子类由 0 变 1（容器挂在它上面）；`FoodRepository(application)` 现场构造归零；**不引入 Hilt/Koin** | ⚠️ **不能给 `AppViewModel` 加带默认值的第二参数**：`ViewModelProvider` 的默认工厂用反射找 `(Application)` 构造器，而 Kotlin 的默认参数只生成带 `DefaultConstructorMarker` 的合成构造器 ⇒ 反射找不到、运行时崩。时钟从容器取，不从构造参数取 ⇒ **实施时已遵守**（VM 构造签名一字未动；容器里先放好 `clock`，#5b 再接线） |
| **5b** ✅ 已做（2026-09-18） | 时钟注入：`FoodRepository` 的 `internal` 主构造加 `clock: Clock = Clock.systemDefaultZone()`，生产用的次构造由 `(context)` 改成 `(context, clock)`（容器传入）；替换**数据层 9 处 + VM 5 处**硬调（⚠️ 原计划写「数据层 7 处」，实测 `CloudSync`/`LocalSnapshotStore` 还各有 1 处，见上方核查第 17 处）；VM 里的自动同步间隔判定抽成纯函数 `isAutoSyncDue`（`data/CloudSync.kt`）才测得到；新增 `FoodRepositoryClockTest` 6 例 + `AutoSyncDueTest` 4 例 | ✅ 数据层与 VM 的**函数体硬调 0 处**、14 处已注入时钟（UI/主壳 7 处刻意保留；另有默认参数 3 + 便捷属性委托 5，逐条登记在脚本的豁免清单里并写明理由）；✅ 新单测用固定时钟断言跨零点行为；✅ 单测 143 → **153** | 行为逐位不变：默认值就是系统时钟 ⇒ 生产路径零改动。⚠️ 固定时钟刻意选在**离当天半年远**的 2026-03-05 —— 若有人把时钟改回系统时钟，按「固定的今天」摆好的数据会落到完全不同的相对位置 ⇒ 断言必红；选个等于真实日期的值，这种回归会静默通过 |
| **5c** ✅ 已做（2026-09-18，7 个提交） | 按领域拆分（**纯搬运**，签名与实现不改）：核心读写与损坏三态（`Decoded` / `DecodeCache` / `rawFlow` / `markCorrupt`）搬去 `RepositoryCore.kt` 作共用底座；其余按库存、归档、消耗、备份导入导出、设置、凭据分 6 个文件。形状 = **同包 `internal` 扩展函数**（`internal suspend fun FoodRepository.upsert(…)`），对外调用写法一字未变，只有跨包的 `AppViewModel` 为搬走的函数逐个加 import（本仓禁通配导入） | ✅ 965 → **252** 行、47 → **9** 个类级函数；✅ 8 个文件**全部 < 400 行**（最大 293 是原有的 `FoodModels.kt`）；✅ 纯搬运判据：每笔 `git diff -w --stat` 的**新增行数恰好等于放宽可见性的行数**（5c-2 = 7、5c-3 = 2、5c-4 = 2、5c-5 = 5、5c-6 = 8、5c-7 = 3），其余全是删除；✅ 守卫同批改完（`CorruptGuardTest` 3 条 + `CompactConsumptionTest` 1 条 + `functionBody` 加 `indent` 参数）；✅ 5c-1/2/3/5/6/7 六轮 CI 三项全绿，5c-4 红两次后修绿 | 本项最险（要搬 48 个函数），实际也**红了两次**：① 搬运脚本认声明行的正则容不下 `private ` ⇒ 一个辅助函数搬成了**没有接收者**的顶层函数，体内却用着仓库成员（编译错，ktlint/detekt 看不出语义错）；② `CompactConsumptionTest` 仍读旧文件 ⇒ 断言失败。两次都因**自校验豁免了出错的那一行**而没被本地拦住 ⇒ 改成按行号定位改写 + 加"无接收者却用成员"断言 + 守卫镜像工具入库。**否决过的方案**：类里留一行转发（成员遮蔽扩展 ⇒ 无限递归且编译期不报）、把流搬成扩展属性（`get() =` 每次访问新建 Flow 实例 ⇒ 行为差异）、包级共享 `TAG`（本包已有多个文件级 private TAG，本地无编译器验证不了撞名）⇒ 每个文件一份 TAG 副本 |

- **全项验收（2026-09-18 收官逐条对账）**：
  ① ✅ 跨零点逻辑有用**固定时钟**跑的单测（5b：`FoodRepositoryClockTest` 6 例 + `AutoSyncDueTest` 4 例，
  固定时钟刻意选在离当天半年远的 2026-03-05）；
  ② ✅ 依赖只有一个构造点（`ChiliMeApp` → `AppContainer`，由 `tools/doc-metrics.sh` 的「依赖构造点」守卫看着，
  含"Manifest 必须挂同名 `android:name`"）；
  ③ 🔶 **在 #5 范围内达成**：`data/` 下 15 个文件最大 **293** 行、无一超 400（已加进 doc-metrics 当判据）。
  ⚠️ 但**全仓还有 6 个文件超 400 行**，都在 UI/VM 层、不属于 #5 的范围 ⇒ 这条按原样留着当待办，
  别因为 #5 收官就当成全仓达成。**清单与行数改成指针**（跑 `bash tools/doc-metrics.sh` 看
  「主代码超 400 行的文件（全清单）」那行），因为抄在这里的数**已经腐烂过一次**：
  ⚠️ **核查第 18 处** —— 原文写 `AppViewModel` 668 行，实测 **700** 行；`git show` 各提交里该文件的行数是
  656（5a）→ 668（5b）→ 700（5c-7），`git diff e054b32 a0a44ea` 显示 +32 行（其中 31 行是 5c 给 VM 加的 import）
  ⇒ 收官记账抄的是 5b 时的值，而那时 5c 已经搬完。这个数不是 doc-metrics 的比对格式，所以腐烂了 6 天没人报警。
  这 6 个文件的处置见 **#10**（拆 2 个、可选 1 个、明确不动 7 个）；
  ④ ✅ **行为不变已由用户真机复测确认**（2026-09-18，debug 包、MD3 与 Miuix 两主题、按 8 类操作清单全过；
  清单与答复原文见 `devlog/2026-09-18.md` §12.7）。代码层面每笔 `git diff -w` 也核过：
  新增行只有可见性放宽与 import。⚠️ 唯一未实机确认的是 **release 包**（这次只装了 debug）⇒
  release 侧只有 CI 的 R8 + `lintRelease` 保障，比 #4 那轮"debug 主测 + release 冒烟"少了后一半；
  ⑤ ✅ 单测数只增不减：143 →（5b）**153** →（5c）**153**（拆分不新增测试，但 4 条源码守卫跟着搬了家；
  原始 `grep @Test` 会数出 154，多的那处在 `ImeHandlingTest.kt:218` 的 KDoc 里，doc-metrics 剥注释后是 153）；
  ⑥ ✅ ktlint / detekt 0（每轮 CI 的「静态门禁」job 全绿；detekt 仍是 block 模式）。
- **顺带**：`ImeHandlingTest` 点名 10 个文件（`MainActivity.kt` / `MainApp.kt` / `BatchBars.kt` / `NavChrome.kt`
  + 4 个屏幕 + `AppFormDialog.kt` / `AppBatchMoveDialog.kt`）。拆 Repository 正常碰不到它，5a 也不需要动
  `MainActivity.kt`（容器挂在 `Application` 上，从 VM 里取）⇒ **清单不动**。

### 5a 落地结果（2026-09-18，提交见台账）

- **新增 `ChiliMeApp.kt`**（本仓第一个 `Application` 子类）：`AppContainer(context: Application)` 持有
  `clock`（`Clock.systemDefaultZone()`，**#5b 才接线**）与 `repo`（`by lazy { FoodRepository(context) }`）；
  `ChiliMeApp` 用 `val container by lazy { AppContainer(this) }` 持有容器；`AndroidManifest.xml` 挂
  `android:name=".ChiliMeApp"`；`AppViewModel` 那行现场构造改成 `(application as ChiliMeApp).container.repo`
  （构造签名保持 `(Application)` 不变，理由见上表的风险列）。参数类型刻意收成 `Application` 而不是 `Context`：
  容器是进程级的，若收 `Context`，某天有人把 Activity 传进来就会被容器一直握着 ⇒ 整个界面泄漏。
- **两个刻意的设计选择**：
  1. **容器用 `by lazy`，不用「`onCreate` 里赋值 + `lateinit var`」那个常见写法**：`ContentProvider.onCreate()`
     的执行时机**早于** `Application.onCreate()`（本仓注册了 `FileProvider`）⇒ `lateinit` 版本会让
     "在 provider 里（或它拉起的东西里）取容器"变成 `UninitializedPropertyAccessException`，
     而这种崩溃只在真机上出现、还离原因很远。`by lazy` 把"必须先 onCreate"这个时序假设整个去掉，
     顺带没有可写的 `var` 暴露出去（容器一旦建好就换不掉）。
  2. **VM 里用硬转型，不用 `as? … ?: FoodRepository(application)` 兜底**：兜底会悄悄造出**第二个**仓库实例
     （各带一份损坏状态与解码缓存），比启动即崩更难查。转型失败只可能是 Manifest 少挂 `android:name`
     这类配置错 ⇒ 让它响，而且响在启动第一行。
- **共享一个实例比改造前更安全**：改造前每次构造 VM 都现场 new 一个仓库，各自持有独立的损坏 key 集合与
  解码缓存 —— 真出现两个 VM 实例时，一个看到的损坏状态另一个看不到。现在全进程一份，不存在这种分叉。
- **新增守卫「依赖构造点（目标：只在容器里 1 处）」**（`tools/doc-metrics.sh`，与代码同提交）：
  ① 现场构造只允许出现在 `ChiliMeApp.kt`（验收 ②「依赖只有一个构造点」）；
  ② **`Application` 子类必须恰好 1 个、且 Manifest 里挂着同名的 `android:name`** —— 这条守的是
  **编译器与单测都发现不了**的错：Manifest 漏挂、或类名改了没同步，`(application as ChiliMeApp)`
  会在**运行时** ClassCastException，而单测里没人构造 VM ⇒ CI 能一路绿到用户手里。自带阳性对照（内嵌样本）。
- **顺带修掉 `doc-metrics.sh` 一个会静默偏低的 bug**：`occ()` 用的 `git grep` **默认只搜已跟踪文件**，
  新建但还没 `git add` 的文件对它是隐形的 ⇒ 开发中量到的数偏低，而"偏低"正好等于"新写的东西没被算进去"，
  最难察觉。本次当场撞上：新建 `ChiliMeApp.kt` 后「Application 子类」那行报 **0**，
  而同一次运行里用 glob 走文件系统的新守卫报 **1** ⇒ 同一件事两个答案。已加 `--untracked`
  （仍尊重 `.gitignore`，不会把 `build/` 算进来）。修完立刻多揪出 3 处文档不符，其中 **2 处是本次新写的
  KDoc 散文把被追踪的计数撑大了**：连写「类名点 now()」让「now() 直接调用」34→36、写出字段名让
  「`corruptedKeys` 出现次数」32→33 ⇒ 已把两处散文改成不触发正则的写法，并在代码注释里写明为什么，
  免得后人"顺手改回更自然的写法"。
- **现值**：`Application` 子类 **1** 个；仓库现场构造 **1** 处（全在容器里）；`AppViewModel` 构造签名未变；
  单测 **143** 例（本阶段不新增：容器只是把构造点搬了个家，没有可测的新逻辑，而 `AppContainer` 要真跑起来
  需要 Android 环境 ⇒ 守卫做在 `doc-metrics.sh` 里而不是单测里）。⇒ **#5b 之后为 153**（见下节）。
- **真机复测**：本阶段理论上零行为变化（同一个仓库、同一个 DataStore、同一套调用），
  但"App 能不能正常启动"这件事只有装上才知道 ⇒ 与 5b/5c 一起在 #5 收官时复测了一轮：
  ✅ **2026-09-18 用户实机通过**（冷启动与各页正常，含在内；详见 `devlog/2026-09-18.md` §12.7）。

### 5b 落地结果（2026-09-18，提交见台账）

- **接线方式**：`FoodRepository` 的 `internal` 主构造多收一个 `clock: Clock = Clock.systemDefaultZone()`；
  生产用的次构造由 `(context)` 改成 `(context, clock)`（**没有保留只收 `Context` 的那个重载** ——
  留着就等于留一条"可以不传时钟"的生产路径，而容器是唯一的生产构造点，让它必须显式给）。
  `AppContainer.repo` 于是写成 `FoodRepository(context, clock)`；`AppViewModel` 把 5a 那次转型收成
  一个 `private val container`，`repo` 与 `clock` 都从它取（少一次转型，也少一处"两个依赖各转各的"的漂移面）。
- **注入点 14 处**（不是计划写的 12）：`FoodRepository.kt` 7（损坏留档时间戳、种子数据与归档条目的 today、
  消耗记录的 epochDay、消耗压缩的 today、CSV 导出的 today）+ `AppViewModel.kt` 5（自动同步间隔、每日快照判定、
  三处「上传于 / 恢复于 …」时间戳）+ **`CloudSync.kt` 1（云端备份文件名时间戳）+ `LocalSnapshotStore.kt` 1
  （快照文件名时间戳）**。后两处是动手量代码时才发现的：计划把它们归给"UI 侧刻意保留"，实际它们在 `data/` 下
  且只被 VM 调用 ⇒ 不接的话本阶段验收「数据层与 VM 的硬调 **0** 处」就是假的（核查第 17 处，见上方证据行）。
  两处都用**带默认值的新参数**接（默认值 = 系统时钟）⇒ 生产行为逐位不变；VM 的 5 个调用点把容器的时钟传进去。
- **刻意不注入的三类**（都登记在守卫的豁免清单里，逐条写了理由，不是"忘了"）：
  ① UI/主壳 **7** 处函数体调用（`SettingsScreen` 导出文件名 2、`MainActivity` 的 `LocalToday` 刷新 3、
  `EditFoodScreen` 生产日期初值 1、`TodayProvider` 默认值 1）—— 界面"现在几点"没有值得测的跨零点逻辑，
  而把时钟穿进 Compose 要多传好几层参数；② 默认参数 **3** 处（`CsvExport` 的 `today`、`BackupData` 的
  `exportedEpochDay`、`FoodListState` 的 `today`）—— 生产的调用点一律显式传注入的今天，默认值只服务
  `@Preview` 与老备份反序列化；③ 便捷属性委托 **5** 处（`daysLeft` / `statusFor` / `freshness` /
  `elapsedRatio` / `remainingText`）—— 可测路径是同名的 `*At(today)` 纯函数，08-21 那轮就是这么拆的，
  这批委托正是那次拆分**留下的**便利层，不该再往回改。
- **顺带抽了一个纯函数**：自动同步的间隔判定原先长在 `AppViewModel.maybeAutoSync()` 里
  （`if (today - last < days) return`），而 VM 需要 Android 环境才构造得起来 ⇒ 这条最典型的"差一天"逻辑
  **从来没被测过**。抽成 `data/CloudSync.kt` 的 `isAutoSyncDue(last, today, days)`（取反即原式，行为逐位不变）。
  ⚠️ 「间隔 <= 0 = 关闭自动同步」那条短路**留在 VM**、刻意不并进来：它必须早于"读凭据、算今天"发生
  （省掉两次 DataStore 读），并进来就改变了读取顺序 —— 结果虽一样，但那就不是纯搬运了。
- **新增 10 例单测（143 → 153）**：
  - `FoodRepositoryClockTest`（6 例，真跑 DataStore + 固定时钟，架子照抄 `FoodRepositoryGuardTest`，纯 JVM）：
    种子数据的生产日期、归档条目记下的那天、消耗记录的 epochDay 与"吃完自动归档"、
    **90 天压缩分界**（界内留逐笔 / 界外聚合到当月 1 号）、损坏留档文件名里的时间戳
    （断言到完整文件名 `food_items-20260305_070809.json`）、CSV 导出的剩余天数与状态。
  - `AutoSyncDueTest`（4 例）：隔满间隔即到期 / 差一天不到期、从未同步过（fallback 0）必到期、
    间隔 1 天时只有同一天不同步、系统日期被往回调时不同步（把原式的负数差行为钉住）。
  - ⚠️ **固定时钟刻意选 2026-03-05，离写测试当天（2026-09-18）半年多**：万一有人把时钟调用改回系统时钟，
    按"固定的今天"摆好的数据会落到完全不同的相对位置（例如界内那条会变成 198 天前 ⇒ 被聚合）⇒ 断言必红。
    若固定日期恰好等于真实日期，这种回归会**静默通过**，守卫等于没写。
- **计划 vs 实测的一处漂移，如实记**：计划里写的「跨零点的**归档裁剪**」在代码里其实是 `.take(200)` 这个
  **按条数**的上限，与日期无关（归档条目上的日期才是时钟相关的，已测）⇒ 没有"按日期裁剪归档"这回事可测，
  不是漏测。
- **新增守卫「数据层+VM 函数体硬调 now()（#5b：0）」**（`tools/doc-metrics.sh`，与代码同提交）：
  ① 只扫 `data/` 与 `viewmodel/` 的**代码行**，命中豁免清单的按类打印理由，其余一律 ✗ 并给出文件:行号；
  ② **双向阳性对照**：先证明"硬调"正则抓得到 `now()` 样本、且不误抓 `now(clock)`，反过来也证一遍
  （两条正则形状只差一个实参，最容易互相误判）；③ **机制是否还在**：容器里必须仍有
  `FoodRepository(context, clock)`、VM 里必须仍有 `container.clock`、注入点计数必须 > 0 ——
  防止"0 处硬调"是靠把取时间的代码整个删掉换来的。
- **顺带修掉守卫自身的两个 bug**（都是这次写守卫时当场撞上的）：
  1. **报的行号是剥完注释后重排的号**（`CsvExport` 报 9、真实 13）⇒ 拿它去翻源码会翻到无关的行。
     改成"跳过注释行但不重排"，行号与 `grep -n` 逐一对上。**守卫指错地方比不指更坏。**
  2. **文档数字守卫的正则漏吃数字后面的星号**：原式只吃数字**前面**的星号，于是「文件名 + 加粗的旧行数 + 行」
     这种写法（数字**后面**也带着加粗标记）根本匹配不上 ⇒ 同一次运行里 `devlog/INDEX.md` 的旧行数被抓到、
     `docs/ROADMAP.md` 里同一个旧行数**漏网**，汇总还报"不符 1 处"，看着像只有一处要改。给数字后面也补上
     "星号至多两个"那段之后，比对数 17 → 18、不符 1 → 2，两处都改成了现值 965。
     ⚠️ 这几行刻意**不原样引用**那串被追踪的写法（文件名、加粗数字、"行"字连在一起写）：本脚本的口径**含散文**，
     举例引一次就等于自己造一个过期数字给守卫抓 —— 写这段的当场就撞上了（守卫把这句举例当成 ROADMAP 里
     真的写死的旧行数，报了 ✗）。与 5a 那两处 KDoc 改写是**同一个教训的第三次出现**。
- **5b 结束时的快照（⚠️ 不是现值）**：`FoodRepository.kt` 当时是 965 行 / 47 个类级函数（5c 的拆分对象还没动），
  5c 收官后已拆到 **252** 行 / **9** 个类级函数（见上方「#5c 收官后的现值」那张表）；数据层 + VM 函数体硬调
  **0** 处、已注入 **14** 处、豁免 **7** 处；`now()` 直接调用总数仍 **34** 处（该指标的正则不含实参，
  注入不减计数；其中空括号的 `LocalDate.now()` 由 26 → **18**）；单测 **153** 例。
- **真机复测**：本阶段同样理论上零行为变化（默认值就是系统时钟，且生产的每条调用链都显式传了同一个时钟）。
  ✅ **2026-09-18 用户实机通过** —— 这道复测原本是 #5 唯一未勾掉的验收项，现在 ①–⑥ 全达成
  （③ 仅在 #5 范围内达成，见上）。复测口径、用户答复原文、以及"release 包这次没装"这一条
  都记在 `devlog/2026-09-18.md` §12.7。

## #6 派生数据下沉 VM + `WhileSubscribed`（**范围比原路线图小**）

- **执行并入 #10c**（2026-09-18 规划）：本项剩下的两件事都要动 `AppViewModel.kt`，而 #10b 正好按领域拆这个文件
  ⇒ 同一批做、共用一轮真机复测。本节的证据与风险条目**原样有效**，下面不再重复。
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
- **证据（资源化）**：`strings.xml` **1** 条（只有 `app_name`）vs 含中文的字符串字面量 **576** 处
  （口径：剥注释后统计，见 `tools/doc-metrics.sh`；不剥注释会因注释里的引号虚高到 **618**）。
  ⚠️ **核查第 19 处（2026-09-18 复跑）**：本节原写「583 / 虚高到 591」是 09-17 的快照，今日实测 **576 / 618**。
  两个口径的差从 8 处涨到 **42** 处 ⇒ 增量全在注释里（#4c/#5 的 KDoc 大量引用中文文案），这正是必须剥注释的原因；
  剥注释后反而少 7 处，**未逐条定位差异来源 ⇒ 只改数、不编原因**。这一项已加进 `doc-metrics.sh` 的比对清单
  （第 21 条），再腐烂会当场报 ✗。⚠️ 历史报告里的「672 处中文硬编码」（`2026-08-21-fix-plan.md` 阶段 6）
  没记测量方法，**与 576 不可比，别混用**。
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

## #10 UI/VM 层拆分收尾（`SettingsScreen.kt` + `AppViewModel.kt`）—— 🔨 **10a 已落地（10a-1 + 10a-2；10a-1 首轮 CI 红一次、已修：别名 import 漏带 9 条，见 devlog §13.8；10a-2 见 §13.9）**（2026-09-18）；**10b-1 已落地（2026-09-19，见本节末「10b-1 落地结果」与 09-19 §2）**，10b 剩 6 个领域

> **为什么新开一项、而不是并进 #6**：#6 剩下的两件事（加 `WhileSubscribed`、少量派生下沉）是**行为改动**，
> 本项的 10a/10b 是**结构搬运**（不改行为、不需真机复测）。混在一个提交里，坏了的时候无法判断是"搬错了"还是
> "生命周期改了"—— 与 #5 拆成 5a/5b/5c 同理。**#6 的执行并入本项 10c**，共用同一轮真机复测。

- **前置**：无（#5 已收官；不依赖 #7/#8）。
- **证据（2026-09-18 实测；行数判据落在 `tools/doc-metrics.sh` 的三行新指标里，一键复跑）**：
  - 主代码超 400 行的文件 **6** 个，全在 UI/VM 层。**清单与行数刻意不抄进本文件** ——
    跑 `bash tools/doc-metrics.sh` 看「主代码超 400 行的文件（全清单）」那行；抄过的下场见下方「核查第 18 处」。
    最大的两个是 `SettingsScreen.kt`（规划时 1,705 行；10a-1 后 1,079 行；**10a-2 后 297 行** ⇒ 已达标，
    超 400 行清单从 6 个减到 5 个）与 `AppViewModel.kt`（700 行；10b-1 后 590、10b-2 后 469、10b-3 后 431；**10b-4 后 363 行 ⇒ 已达标** 🎯；10b-5 后 **322** 行）⇒ 本项就是冲这两个去的，**两个现在都达标了**（`doc-metrics` 的「viewmodel/ 最大文件」那行报 `OK 全部 < 400`）。
    `ExpiryCalendar.kt` 正好 400 行，卡线不计入。
  - `SettingsScreen.kt` 内部（**行号是 10a-1 搬运前的**，搬后弹窗区已在同包三个新文件里）：
    入口 `SettingsScreen` 176–994 = **819 行**，其中
    **弹窗区 350–994 ≈ 645 行**（`AppOptionDialog` ×2 + `AppConfirmDialog` ×3 + 4 对手写的
    `MiuixDialog`/`AlertDialog`：导入预览 416/462、坚果云账号 524/573、云端备份选择 623/745、本地快照列表 832/916）；
    `Md3SettingsBody` 996–1380 = 385 行；`MiuixSettingsBody` 1382–1566 = 185 行；
    `SettingsNavRow` 45 行与 `PaletteSwatch` 92 行（这两个**只被 MD3 body 调用**）。全文 **55** 处主题分支。
  - `AppViewModel.kt`：**50** 个函数（跨 9 个领域）+ **44** 个状态属性；`stateIn(` **20** 处**全在这一个文件**、
    `WhileSubscribed` **0** 处 ⇒ #6 的两件事都落在这里。
  - **调用面实测（决定 10b 的爆炸半径）**：VM 之外真正以 VM 实例为接收者去调用/引用这些函数的是
    **10 个文件 / 56 个站点**（`SettingsState.kt` 19、`FoodListState.kt` 7、`MainApp.kt` 6、`ManageState.kt` 6、
    `ArchiveState.kt` 4、`FoodDetailState.kt` 3、`HomeScreenState.kt` 3，`ConsumptionLogScreen.kt` /
    `ConsumptionLogState.kt` / `EditFoodScreen.kt` 各 1）⇒ 搬成同包扩展函数后这 10 个文件约需新增 **51** 行 import
    （本仓禁通配导入）。#5c 是同一件事的反向版本（当时 `AppViewModel` 为搬走的仓库函数加了 31 行 import）。
    ⚠️ 别用「**31** 个文件引用 `AppViewModel`」那个粗口径估工作量：多数只是 import 类型名，
    或经 `SettingsActions` 这类窄接口间接使用，不用改。
  - **4 个函数从不被外部调用**（`emit` / `maybeAutoSync` / `maybeAutoSnapshot` / `snapshotBeforeRestore`，全是 `private`）
    ⇒ 搬走后只有 VM 文件自己需要 import。
- **⚠️ 对 09-18 晚那次口头评估的两处更正**（都是读了 `SettingsScreen.kt` 文件头 KDoc 才发现的）：
  - ① 那 4 对手写弹窗**不是遗漏，是 09-16 评估后刻意不并**，理由写在文件头「照抄而非统一的地方」三条：
    坚果云账号弹窗要硬套 `AppFormDialog` 就得改它的字段所有权、还得加密码/说明文槽位（风险大于收益）；
    导入预览的摘要文本**已经只写一份**，两套的只是排版；云端备份/本地快照列表是两套行样式与角标口径。
    ⇒ **10a 是搬运，不承诺减少重复**。真要去重得先立 App 级新组件，属行为可见改动 ⇒ 记为 **10d（延后）**。
  - ② **核查第 21 处（本次自查）**：上面证据行原把 623/745 那对写成「应用密码」，实为**云端备份选择**
    （应用密码是坚果云弹窗里的第二个输入框，同一个 MD3 `AlertDialog` 里）；已改。同类错误也出现在
    10a-1 那一行的切法描述里，一并改成实测的三段。
  - ③ 设置页两版**没有功能缺口**（逐个入口核过：Miuix body 里主题切换在 1401–1410、动态取色 1421、
    修改账号 1489、上传云端 1516、清空库存 1549 都在，只是改用 `RadioButtonPreference` / `SwitchPreference` /
    `ArrowPreference` 表达，中文字面量少，粗看像缺）。**已知的刻意非对等只有一处**：配色方案入口只在 MD3 分支
    （`MiuixRootTheme` 没有种子色通道），用户已指示暂缓（见 `devlog/INDEX.md`）。
- **外部对照（`tiann/KernelSU` 的 manager @ main，2026-09-18 实测）**：219 个 `.kt`、**0 个测试文件**、
  **10** 个 ViewModel（一屏一个）、`ui/screen/<feature>/` 按功能分包（每包 = 分岔用的 `XxxScreen.kt` 3.5 KB +
  `XxxUiState.kt` 1.9 KB + `XxxUtils.kt` + 两版完整实现）。双主题走**每屏两份完整文件**，
  同一功能两版的体积（字节）：Home 23,530 vs 29,222 · Module 43,549 vs 47,506 · Settings 19,383 vs 25,240 ·
  SuperUser 24,467 vs 30,805 ⇒ **每一对里 Miuix 版都大 4–6 KB，已经在漂移**（改一处要改两遍的必然结果）。
  - **不学**：每屏两份实现 —— 本仓 09-16 走的正是相反方向（单文件双主题 + App 级组件层 + `ScreenParityTest` 钉住）；
    他们 0 测试，本仓 153 例 + 源码守卫，拆大文件没有测试网等于硬拆。
  - **学**：一屏一个 VM ⇒ 但本项不做，记为 **10d**（理由见表内）。
  - **按功能分包也不采纳**（实测守卫成本）：`ImeHandlingTest.dialogFiles` 按**完整路径**点名，清单里的文件不存在
    **直接红**；`ScreenParityTest` 只遍历 `ui/screens/` **一层**（非递归），且反向断言 `SettingsScreen.kt`
    **必须还在** ⇒ 搬包会让这一屏**静默失去覆盖**；`doc-metrics.sh` 另有 3 处按路径引用 VM。
    代价是 7 个测试文件 + 3 处工具路径 + 一处静默失覆盖，换来的只是目录形状 ⇒ 判定不做（17 个文件平铺仍在可读范围）。

### 做法：10a/10b 结构搬运（不复测）→ 10c 生命周期（一轮复测）

| 步 | 做什么 | 验收 | 风险与必须同批改的守卫 |
|---|---|---|---|
| **10a-1** ✅ 已做（2026-09-18） | 弹窗区 **352–986（跨 635 行，git 记为删 632 行；差额见「10a-1 落地结果」）**按领域抽到同包 3 个文件：备份与导入 352–520 → `SettingsBackupDialogs.kt`；坚果云账号 + 云端备份选择 + 恢复二次确认 522–828 → `SettingsCloudDialogs.kt`；本地快照列表 830–986 → `SettingsSnapshotDialogs.kt`。3 个 SAF 启动器**留在入口**（它们的结果回调要用 `scope` / `snackbar` / `pendingImport`，搬走反而要多传三样），以 `ActivityResultLauncher<String>` / `<Array<String>>` 传参 | 三个新文件 234 / 372 / 211 行（⚠️ 首轮 CI 红：别名 import 漏带 9 条 ⇒ 已修，行数含补进的 import），全部 < 400 ✓；`SettingsScreen.kt` 1,705 → **1,079** 行（⚠️ 不是规划时预计的 ~350 —— 两套 body 还在里面，那是 10a-2 ⇒ 已做完，入口现 **297** 行）；逐字校验 0 缺失；`kt-lexcheck` 96 份 0 问题 | 已同批改的守卫：`ImeHandlingTest.dialogFiles`（`SettingsScreen.kt` → `SettingsCloudDialogs.kt`）+ 它两处 KDoc 的 MIME 行号；`MiuixDialogContentTest` 的调用点分布（总数仍 **8**）与「覆盖导入」引用；`ScreenParityTest` 第 8 对注释 **+ 规则 2 的扫描面加宽**（新增 `uiFiles()`：目录内除 `*State.kt` 的所有 `.kt`；原 `screenFiles()` 只收 `*Screen.kt` / `*Screens.kt`，三个 `*Dialogs.kt` 搬出后会让「禁止内联聚合」**静默失覆盖** —— 加宽前实测那 4 个禁用模式在目录内的命中全在 `*State.kt`，故只补覆盖、不改判定）。⚠️ 规划里「`SettingsActions` 可直接当参数包」被实测推翻：弹窗区用的是入口的局部回调与启动器，实测参数面 10 个（三块各 7 / 4 / 3） |
| **10a-2** ✅ 已做（2026-09-18） | 两套 body + 两个 MD3 专用小组件抽到同包 **4** 个文件（规划写的是 2 个；MD3 侧 522 行 + 文件头必然超 400，规划已预见「还得按分区再切一刀」，实做切的是四节里最大的「备份与数据」171 行 ⇒ 抽成新函数 `Md3BackupSection`，那是本次**唯一**新增的组合边界）：`SettingsBodyMd3.kt`(260) / `SettingsBackupMd3.kt`(222) / `SettingsBodyMiuix.kt`(219) / `SettingsMd3Widgets.kt`(184)；搬走 717 行，4 个函数 `private` → `internal`（名字一个没改） | 5 个文件全部 < 400 行 ✓（入口 1,079 → **297**，import 96 → **22** 条）；入口仍含 `rememberSettingsUiState(`（规则 1）✓；逐字校验 **703** 行 0 缺失 ✓；超 400 行主代码 **6 → 5** 个；`kt-lexcheck` 100 份 0 问题（⚠️ CI 红了**两轮**：首轮 5 个文件漏 41 条 import、次轮入口漏 `getValue`/`setValue` 2 条**委托算子** ⇒ 都已修，行数与 import 数含补进的；`move-importcheck` 0 缺失）| `ScreenParityTest` 禁的是 `Miuix` + 屏幕名这种**前缀式**文件名（`MiuixSettingsScreen.kt`），`SettingsBodyMiuix.kt` 不触雷（已写进注释）；⚠️ 规划担心的「`remember` 跨组合边界搬家」实测**没有发生**（去注释口径：抽出的备份节 0 处、Miuix body 0 处、MD3 body 那 1 处 `rememberScrollState()` 留在 body）⇒ 风险从「必测」降为「顺带过一眼」，仍纳入 10c 那轮复测；**本次没有任何守卫判定需要改**（只给 `ScreenParityTest` 加注释，规则 2 的 `uiFiles()` 自动覆盖 4 个新文件、当天实测 4 个禁用模式在里面 0 命中）；⚠️ 新踩的 2 个自坑都在 import 计算里，10b 沿用同一套算法 ⇒ 见「10a-2 落地结果」 |
| **10b** 🔨 进行中（**10b-1 … 10b-5 ✅ 2026-09-19**：备份 9 + 云端同步 4 + 归档与消耗撤销 6 + 食物 CRUD 与批量 11 + 分类与位置 7 个函数；🎯 `AppViewModel.kt` **322 行**，自 10b-4 起已达标） | `AppViewModel` 的 50 个函数按领域搬成**同包 `internal` 扩展函数**（与 #5c 完全同形），一个领域一个提交：备份导入导出 9 / 云端同步 4 / 归档与消耗撤销 6 / 食物 CRUD 与批量 11 / 分类与位置 7 / 设置 6 / UI 状态与事件 5（余 2 个按实际归类） | 类本体 < 400 行；10 个调用方文件只加 import、**调用写法一字不变** | ① 20 处 `stateIn(` 属性**留在类里**（搬成扩展属性 = `get() =` 每次新建 Flow ⇒ #5c 已否决）；② 类里**不留同名转发**（成员遮蔽扩展 ⇒ 无限递归且编译期不报，#5c 已否决）；③ ⚠️ **`CorruptGuardTest` 会红**：它用 `functionBody(src, "fun syncDownload(")` 这类**按 4 空格缩进签名**抽函数体、还断言字面量 `private suspend fun snapshotBeforeRestore()` 与 `vm.contains("fun discardCorruptData()")` ⇒ 函数一旦变成 0 缩进的扩展函数，这几处全失配，必须同批改**读取路径 + 缩进参数 + 签名字面量**（✅ 已照此落地：10b-1 改了前两条、10b-2 改了 `syncDownload` 那条、**10b-4 改了 `discardCorruptData` 那条**（改读 `AppViewModelFood.kt` + 断言带接收者的声明行）⇒ 该测试方法现已不读 `AppViewModel.kt`，规划点名的三处守卫改动**全部落地**）；④ `SnackbarCopyTest` 按**全仓递归**统计文案片段落在哪些文件（期望 map 逐键相等）⇒ 带文案的函数搬家后要改期望 map 的**键**（⚠️ **10b-2 实测不受影响**：它唯一那条 VM 期望在 `maybeAutoSync` 里，属自动同步策略、不属云端同步领域 ⇒ 留在 VM）；⑤ `tools/guard-mirror.py` **查不到 ③④**（它把这类断言归入「作用域受限跳过」，今日 18 处里有 11 处正是它们）⇒ 必须人工核 |
| **10c** | = **#6**：20 处 `stateIn(` 加 `WhileSubscribed(5_000)`，**一屏一个提交** | `WhileSubscribed` 由 **0** 处 → 20 处；用户真机复测一轮（两主题，含设置页） | **行为改动**：后台不再预热，冷进页面首帧可能等一次解码（#6 原风险条目照旧）；逐屏提交 ⇒ 逐屏可回滚 |
| **10d** | **延后、不排期**：① 真·一屏一 VM（KernelSU 那套）；② 把两版 body 的分区抽成 App 级「设置行」组件；③ 那 4 对手写弹窗收敛进组件层 | — | 三条都是**行为可见的架构改动**：① 要动 10 个调用方 + 导航作用域（批量选择、撤销、snackbar 通道是跨屏共享的，拆错了会丢状态）；②③ 与 09-16「不硬并」同类（两套设计语言：MD3 用 `Text`/`Surface`/`SegmentedButton`，Miuix 用 `ArrowPreference`/`SwitchPreference`/`OverlayDropdownPreference`）。等 10a/10b 落地后重新量收益再定 |
| **10e** | 可选、低风险：`EditFoodScreen.kt` 660 行（**1 个** Composable、**0** 处主题分支）按表单区块抽子组件 | 每文件 < 400 行 | 纯结构、无主题分岔 ⇒ 守卫面比 10a 小得多（只需核 `ImeHandlingTest` 清单里的路径没变） |
| — | **明确不动**：`AppChrome.kt` 479 / `AppListRow.kt` 406 / `StatsScreen.kt` 409 / `ExpiryCalendar.kt` 400 / `AppControls.kt` 376 / `AppText.kt` 349 / `AppSurface.kt` 353 | — | 后三个是 **App 级组件层**（各含 12–35 处主题分支），吸收主题差异正是它们的职责，消灭它等于把差异推回屏幕；前四个已各只剩 0–1 处主题分支，只是长 ⇒ 等真要改它们时顺手拆 |

- **全项验收**：
  ① **靶子文件**无一超 400 行 —— 判据是 `bash tools/doc-metrics.sh` 的「屏幕目录最大文件」与「viewmodel/ 最大文件」
  两行（两行都要做阳性对照：把阈值临时收紧到 100 行必须点名超限文件，否则判据是摆设）。
  ⚠️ **本条口径在 10a-2 落地时改正**（原文是「`ui/screens/` 屏幕本体与 `viewmodel/` 无一文件超 400 行」）：
  那个写法与下方「明确不动」表**自相矛盾** —— `StatsScreen.kt` 409 行既在 `ui/screens/` 里、又被判定不拆
  （它已只剩 1 处主题分支，只是长），`EditFoodScreen.kt` 660 行则属**可选**的 10e ⇒ 照原文口径，本项永远无法收官。
  现口径 = 只认两块靶子（设置页入口与 `AppViewModel`），并把两个已知例外显式记在这里：
  **`StatsScreen.kt` 409（不拆，超 9 行，理由见「明确不动」表）**、**`EditFoodScreen.kt` 660（10e，未排期；659 是 #10b-4 前，那轮它多了 1 行 import）**；
  主代码超 400 行的**全清单**仍由 doc-metrics 那行给出（10a-2 后是 5 个、**10b-4 后是 4 个**：`EditFoodScreen.kt` 660 / `AppChrome.kt` 479 / `StatsScreen.kt` 409 / `AppListRow.kt` 406 —— 后三个在下方「明确不动」表里），不写死在本文件；
  🎯 **10b-4 落地后本项两块靶子都已满足**（设置页入口 297 行、`AppViewModel.kt` 363 行）⇒ 验收① 达成，剩下的 10b-5/6/7 是用户选的「搬完」，不再是达标所需；
  ② 10a/10b **不改行为**：10b 沿用 #5c 的判据（`git diff -w --stat` 的新增行数 == 放宽可见性 + 新增 import 的行数，
  其余全为删除）；10a 因为是**抽子 Composable**（必然新增签名与参数传递行），判据改成「被搬走的每一行都能在新文件里
  逐字找到（缩进除外）+ 新增的只有签名/参数/调用行」，由搬运脚本自校验（反推比对，同 #5c）；
  ③ 守卫不静默少覆盖：`ImeHandlingTest` 清单存在性断言通过、`MiuixDialogContentTest` 解析点数仍 ≥ **8**、
  `ScreenParityTest` 三条全绿、`CorruptGuardTest` 与 `SnackbarCopyTest` 同批镜像；
  ④ 单测数只增不减（现 **153** 例，词法计数：粗 `grep @Test` 会数出 154，多的那处在 KDoc 里）；CI 三 job 全绿；
  ⑤ 10c 完成后用户真机复测**一轮**（两主题；顺带覆盖 10a-2 新增的那**一个**组合边界 —— 见「10a-2 落地结果」，实测没有 `remember` 跨边界搬家，所以这一项从「必测」降为「顺带过一眼」）。
- **风险汇总**：10a/10b 的主要风险不是编译（本地无 JDK，CI 是唯一编译神谕）⚠️ **这半句已被 10a-1 证伪**：
  它红的那轮正是编译错（别名 import），而本地三项检查全绿 ⇒ 编译风险真实存在、且能被本地工具兜住一部分（见下条），
  但**守卫漏改**仍是另一半风险 ⇒
  每步开工前先跑 `tools/guard-mirror.py`，再人工核上面 ③④ 那两类它查不到的断言；
  10c 的风险是首帧延迟（已知、可逐屏回滚）。
  ⚠️ **10a-1 实做时兑现了一条规划里没写的**：搬家除了「把守卫搬红」，还会「把守卫的覆盖面搬窄」
  （按文件名点名、按后缀枚举的清单，搬走的那部分就没人查了，而且**什么都不响**）⇒
  10a-2 与 10b 开工前先列出「哪些守卫按路径或文件名后缀枚举」，逐个问一句「搬走后它还扫得到吗」。
  ⚠️ **10a-1 落地后又兑现一条（编译侧）**：按名字算依赖的脚本对 `import … as X` 是瞎的 ⇒ 红了一轮 CI。
  现在有两道防线：`kt-lexcheck.py` 判据 4（本地、几毫秒）+ CI 的 Kotlin 编译；
  10a-2/10b 的搬运脚本仍必须把「别名」与「项目自己的同名声明」一起收进依赖表 —— 别只靠事后检查。
  另一条流程教训：**CI 红了先请用户给日志签名 URL（网页抓取工具能读），别先按排除法猜**
  （10a-1 那次先猜了 5 个方向全不中，日志到手 30 秒定位）—— 办法已写进 `WORKFLOW.md` §3。

### 10a-1 落地结果（2026-09-18，提交见台账）

| 新文件 | 行数 | 原行号 | 装了什么 | 参数（= 实测引用面） |
|---|---|---|---|---|
| `SettingsBackupDialogs.kt` | 234 | 352–520 | 导出格式选择 / 恢复来源选择 / 导入前预览与二次确认 / 清空库存确认 | `state` `isMiuix` `pendingImportState` 3 个 launcher `confirmImport`（7） |
| `SettingsCloudDialogs.kt` | 372 | 522–828 | 坚果云账号（账号 + 密码两个输入框）/ 云端备份选择 / 恢复二次确认 | `state` `isMiuix` `saveNutstore` `confirmRestore`（4） |
| `SettingsSnapshotDialogs.kt` | 211 | 830–986 | 本地历史快照列表（含还原确认） | `state` `isMiuix` `confirmSnapshotRestore`（3） |

- **入口**：1,705 → **1,079** 行。账按 `git diff --numstat` 记（−658 / +32，`1705 − 658 + 32 = 1079`）：
  删的 658 = 弹窗区内 632 行 + 失效 import 24 行 + 被改写的 2 行（`var pendingImport by remember { … }`、
  头 KDoc 里核查第 20 处那句）；加的 32 = 3 处调用 17 行 + `pendingImportState` 声明 2 行 + 行内注释 3 行
  + 头 KDoc 10 行（#10a-1 落点 6 + 核查第 20 处勘误 4）。
  ⚠️ 三块跨度 169 / 307 / 157（合计 633）比 git 记的 632 多 1：区域里 3 行单独的 `)`（旧行 409 / 828 / 986）
  被最小 diff 配成了「未变上下文」，不是留在原地的代码 ⇒ **搬走的量按跨度说、删掉的量按 git 说**。失效 import 是 `tools/kt-lexcheck.py` 查出来的
  （其中 3 行是带别名的 `… as MiuixSurface/MiuixText/MiuixTextButton`）。
  ⚠️ 中途一度按「搬完就 1,070」记账，那是 KDoc 批注**之前**的数；10a-1 收官时 `bash tools/doc-metrics.sh`
  的「屏幕目录最大文件」行报 1,079 —— 现值一律以那行为准。（⚠️ **10a-2 之后那行报的已不是设置页**：
  入口降到 297 行、不再是屏幕目录里最大的，那行现在报 `EditFoodScreen.kt` 659。）
- **唯一一处非逐字改动（2 行）**：`pendingImport` 在弹窗区里被赋值 4 次（导入预览的关闭/取消），
  故入口的 `var pendingImport by remember { mutableStateOf<PendingImport?>(null) }` 拆成「显式 `pendingImportState`
  + 同一行委托」，把 State 对象传给弹窗 —— 读写的是同一个 `MutableState`，语义不变；
  变化只有「读它的重组作用域从入口挪到弹窗文件」（该状态本来只有弹窗在读）。
- **搬运自校验（脚本自带，不过就不落盘）**：① 搬走的每一行都能在新文件里逐字找到（只允许整体左移 4 空格），
  三块跨度 169 / 307 / 157 行、**0 行找不到**；② 参数表与实测引用面逐个比对（多一个少一个都不落盘）；
  ③ 落盘前查 ktlint 那 6 条（连续空行、右花括号前空行、行尾空白、末尾恰好一个换行）。
- **守卫覆盖面（规划时漏掉的一处）**：`ScreenParityTest.screenFiles()` 只收 `*Screen.kt` / `*Screens.kt`，
  三个 `*Dialogs.kt` 不进清单 —— 规则 1（必须调 `remember*UiState`）本就不该管弹窗（`state` 是传参进来的），
  但规则 2（禁止内联聚合）会因此**静默少覆盖 800 多行 UI 代码**。已加 `uiFiles()`（目录内除 `*State.kt`
  的所有 `.kt`）只给规则 2 用；加宽前实测那 4 个禁用模式在目录内的命中全在 `*State.kt` ⇒ 判定结果不变。
- **行为面**：零改动（弹窗的文案、排版、主题分支、按钮色一律逐字未动）⇒ **不占用真机复测**；
  与 10a-2 的 `remember` 搬家一起，留到 10c 那一轮复测顺带过设置页两主题。
  （⚠️ 后半句的预判已被 10a-2 实做时的实测修正：并没有 `remember` 跨边界搬家 —— 见「10a-2 落地结果」的「风险比规划小」。）
- **⚠️ 落地后 CI 红了一轮（已修，全过程见 `devlog/2026-09-18.md` §13.8）**：搬运脚本按
  「简单名 = import 路径最后一段」算依赖，对**别名 import** 是瞎的（`…basic.Text as MiuixText`，全仓 25 条）
  ⇒ 三个新文件少了 9 条别名 import，`:app:compileDebugKotlin` 报 24 处 `Unresolved reference`
  + 4 处连带的「`@Composable` invocations can only happen from …」（未解析的 `MiuixSurface {}` 尾随 lambda 引起）。
  修法只补那 9 行 import（`git diff --stat` = 9 insertions / 0 deletions ⇒ 行为仍零改动）；
  同批给 `tools/kt-lexcheck.py` 加**判据 4「用了别名却没 import」**（别名表扫全仓建、自检对照 12 → 15），
  并用修复前的文件做端到端阴性对照（点名 9 处、次数 2/15/7 与 CI 逐条对上）。
  **10a-2 与 10b 开工前必读**：搬文件时别名要当「第三个名字」一起收（路径名 / 别名 / 项目自己的同名声明）。
  （10a-2 已照此做 ⇒ 别名 import 一条没漏；但同一片代码上又踩出 2 个**新的**自坑，都在 import 计算里，见下方。）
  **10a-2 修红后追加**：别名只是"第三个名字"问题的**一半** —— 另一半是按「大写开头」算依赖会漏掉小写扩展函数/属性、全大写常量与点号后面的大写成员（`Icons.Rounded.Cloud`）。**10b 开工前先跑 `python3 tools/move-importcheck.py`**（`--old` 指搬家前的 git 引用、`--new` 指搬家后的文件；退出码非 0 = 有缺失），别只靠 `kt-lexcheck`。

### 10a-2 落地结果（2026-09-18，提交见台账）

| 文件 | 行数 | import | 原行号 | 装了什么 |
|---|---|---|---|---|
| `SettingsScreen.kt`（入口，改写） | **297** | 96 → **22** 条 | 1–362 | 状态容器、3 个 SAF 启动器、事件收集、共用动作、9 个弹窗与两套 body 的调用 |
| `SettingsBodyMd3.kt`（新） | 260 | 34 条 | 364–545 + 717–747 | MD3 body：根 `Column`（滚动）+ 外观 / 物品管理 / 关于 三节 + 备份节的调用 |
| `SettingsBackupMd3.kt`（新） | 222 | 32 条 | 546–716 | MD3 body 的「备份与数据」一节（含坚果云云同步、自动同步间隔两个子块）⇒ 新函数 `Md3BackupSection(state, onUpload, onCloudRestore)` |
| `SettingsBodyMiuix.kt`（新） | 219 | 23 条 | 749–938 | Miuix body：`LazyColumn` + 库的 Preference 组件 |
| `SettingsMd3Widgets.kt`（新） | 184 | 31 条 | 940–980 + 982–1079 | `SettingsNavRow` + `PaletteSwatch`（两者都只被 MD3 body 调用） |

- **⚠️ 落地后 CI 红了一轮（已修，全过程见 `devlog/2026-09-18.md` §13.10）**：搬运脚本算依赖的标识符正则只认「大写开头、不含下划线」那一类 ⇒ **小写扩展函数/属性**（`dp` / `padding` / `fillMaxWidth` / `launch`…）、**全大写常量**（`CLOUD_BACKUP_KEEP`）与**点号后面的大写成员**（`Icons.Rounded.Cloud`、`MiuixIcons.CloudFill`）三类全被漏掉，5 个文件少 **41** 条 import ⇒ 两个构建 job 都红在编译（门禁 ✅）。修法只动 import 行（`+41 / −2`，那 2 条是搬完变死的）⇒ 行为仍零改动；上表的行数与 import 数**含这次补进的**。
  与 10a-1 那次是**同一个坑的两半**（那次漏别名 `as`，这次漏"不是别名但也不是大写类型名"的一大片）⇒ 新工具 `tools/move-importcheck.py` 入库：拿**搬家前那个文件的 import 表**当参照物重算，三次历史真红都能逐条复现（1/4/4、41、2），`--selftest` 另有 8 个合成对照。**10b 搬 50 个函数时必须跑它。**
  ⚠️ 同一套判据做成 `kt-lexcheck` 的全仓「判据 5」时 **56/100 份报警**（`list.map` vs `flow.map`、类成员 vs 同包扩展、`AppRoute.Main.Home` vs `Icons.Rounded.Home` 三类词法上不可判定的撞名）⇒ 已撤回：**有参照物才精确**，这份算法的归宿是搬运工具而不是全仓守卫。

- **⚠️ 补完那 41 条，CI 又红了一轮（已修，全过程见 `devlog/2026-09-18.md` §13.11）**：入口那句 `var pendingImport by pendingImportState` 需要 `androidx.compose.runtime.getValue` / `setValue`，而搬运脚本算「入口该保留哪些 import」是**按名字在剩余代码里找用量**的 —— **委托算子的名字在代码里从不以标识符出现**（`by` 后面一个字母都不提 `setValue`），于是这两条被当成死 import 从入口删了；`move-importcheck` 判据 3/4 同样放过（小写名只认调用形，委托一个调用形都没有）。CI 报 `e: SettingsScreen.kt:138:23 Type 'MutableState<PendingImport?>' has no method 'getValue(…)' / 'setValue(…)', so it cannot serve as a delegate` —— **只有这一处**（两行 `e:` 是同一处的读/写两侧），与上一轮 24 处报错 / 41 条缺失不是一个量级。
  修法照旧只动 import 行（`+2 / −0`，入口 295 → **297** 行、20 → **22** 条）⇒ 行为零改动；`move-importcheck` 加**判据 6**（结构判据：代码里有属性委托 ⇒ 需要 `getValue`，其中出现 `var` ⇒ 另需 `setValue`；排除 `by lazy` / `by Delegates.`，并照旧受参照物约束），自检 4 → **8** 个对照（新增：var 委托 / 只 val 委托 / `by lazy` 反面 / 齐了不报），再用**修前状态**当阳性对照复跑 ⇒ 点名恰好这 2 条、其余 4 个文件仍 0。
  这轮的定位**没打扰用户**：向 `api.github.com` 要日志接口的 302 `location:`（就是那条签名 URL），交给网页抓取工具读 ⇒ 拿到编译器原文（做法与三个坑写进 `docs/WORKFLOW.md` §3）。
  **10b 开工前照旧跑 `move-importcheck`**：它现在覆盖**五类**（别名 `as` / 小写扩展与全大写常量与点号后成员 / 委托算子 /
  **小写扩展属性当接收者用**（`viewModelScope.launch { }`，判据 4 第 4 形状）/ **带接收者的声明不压制同名 import**
  （`internal fun AppViewModel.buildCsvExport()` 体内 `repo.buildCsvExport()` 要的是 data 层那个同名扩展，判据 7）。
  后两类是 2026-09-19 为 10b 开工做纸上演练时逼出来的，**都是「本地四项全绿、CI 必红」的方向**；自检对照 8 → **10** 个。
  ⚠️ 同批查明一个记账口径：判据 6 加进来之后，#10a-2 那 5 个文件的对照由 41 条变 **43** 条
  （= 首轮 41 + 次轮 2 条委托算子）—— **同一条命令把两轮真红一起复现了**，不是新判据多点出来的。
  **10b-3 又补第 3 处（2026-09-19，单独一笔提交，详见 09-19 §5）**：判据 7 原先只管「**本文件**的带接收者
  声明」，没管「**同包别的文件**的带接收者声明」—— `same_package_decls()` 把目录里所有顶层声明一律当
  「同包名 ⇒ 不用 import」，于是 `AppViewModelArchiveUndo.kt` 里的 `internal fun AppViewModel.restoreArchived`
  会把 `com.agon.app.data.restoreArchived` 压掉。阳性/阴性对照实测：同目录放一个这样的兄弟文件 ⇒ 目标文件
  明明缺那条 import 却报「缺 0」；把兄弟文件移走 ⇒ 立刻「缺 1」。**这条若不修，#10b-4 落盘必红**（要搬的
  `restoreArchivedSmart` 体内写的正是 `repo.restoreArchived(id)`）。修完自检对照 10 → **11** 个（新对照 ⑪
  用兄弟文件复现同包路径），四个历史对照重跑 **A 9 / B 43 / C 2 / D 0 一字不变**；连带把 #10b-3 新文件的
  「应有 7 · 疑似多余 3」纠正成「应有 **10** · 疑似多余 **0**」（那 3 条假警与这个盲点是同一处压制）。
  ⚠️ 顺带查出自检骨架自己有个坑：所有对照塞在同一个临时目录，而 `_PKG` 按目录缓存 ⇒ 第一个对照之后缓存
  定格、后写的对照（含它自己的声明）不会被重扫 —— 对照 ⑩ 一直是**蒙对**的。改成每个对照一个子目录。

- **规划与实况的差**：规划写的是「抽到两个新文件」，实做是 **4 个** —— 规划已预见「MD3 侧 522 行 + 文件头必然超 400，
  还得按分区再切一刀」，实做选的是四节里最大的一节（备份与数据 171 行）；两个 MD3 专用小组件（NavRow 46 + Swatch 92）
  另立一个文件，因为它们只被 MD3 body 调用，而并进去会到 423 行（又超）。
- **风险比规划小（实测，不是推断）**：规划把「抽子 Composable 会让 `remember` 跨组合边界搬家」列为主要风险；
  落地当天用**去注释**口径量了 5 个文件：入口 7 处 `remember`（状态容器 / scope / snackbar / 3 个 SAF 启动器 /
  `pendingImportState`）**一处没动**；MD3 body 只有根 `Column` 的 1 处 `rememberScrollState()`（留在 body）；
  **抽出的备份节 0 处**；Miuix body 0 处；`PaletteSwatch` 里那处 `rememberDynamicColorScheme`（只为预览色块）
  随函数一起搬、读它的重组作用域没变。⇒ 本次**唯一**新增的组合边界（备份节）内没有任何状态 ⇒ 风险等级从
  「必测」降为「顺带过一眼」。
- **搬运自校验（脚本自带，不过就不落盘）**：18 个行号锚点 · 逐字校验 **703** 行非空代码、**0 行找不到**
  （备份节按「整体左移 4 空格」比对，其余逐字） · 备份节参数表与实测引用面逐个相等（从 8 个形参里实测出
  `state` / `onUpload` / `onCloudRestore` 三个，多一个少一个都不落盘） · 每个文件 < 400 行 ·
  4 个搬走的函数可见性放宽成功且不再 `private`（放宽是本批**唯一**的非逐字改动，名字一个没改）。
- **import 计算的 2 个自坑（都被本地 `kt-lexcheck` 抓到，没到 CI）**：① 算「入口该保留哪些 import」时，
  用来比对的文本**含 import 行本身**，于是 `… as MiuixIcon` 里的别名自己把自己证明成在用 ⇒ 6 处用法随 Miuix body
  搬走后那条死 import 删不掉（判据 3 抓到）；② 带别名的 import 若同时匹配「简单名」会误留 ——
  `…basic.Icon as MiuixIcon` 的简单名 `Icon` 与 `material3.Icon` 撞名。规则改成「**带 `as` 的只认别名**」，
  并用 `git show HEAD:` 的原文重算 4 个新文件的 import 集，与脚本落盘结果逐个一致。**10b 沿用同一套算法**。
- **守卫（逐个问过，本次没有任何判定需要改）**：`ScreenParityTest` 只加注释 —— 规则 1 靠入口仍在的
  `rememberSettingsUiState(` 通过；规则 2 的 `uiFiles()` **自动**覆盖 4 个新文件（当天实测 4 个禁用模式在里面 0 命中）；
  规则 4 禁的是 `Miuix<屏幕名>.kt` 这种**前缀式**第二实现，`SettingsBodyMiuix.kt` 是后缀式、不触雷（注释已写明）。
  `ImeHandlingTest` 的 4 份文件名清单里没有 body 文件（body 内 `decorFitsSystemWindows` 0 处）；
  `MiuixDialogContentTest` 走全树，body 内 `MiuixDialog` 0 处 ⇒ 解析点数仍 8；`SnackbarCopyTest` 走全树，
  body 内 snackbar 文案 0 处；`doc-metrics.sh` 的屏幕指标全按 glob 现算（10a-1 加的两行新判据自动反映新文件名）。
- **本地检查（提交前实测）**：`tools/kt-lexcheck.py` 100 份 · 0 问题；`tools/guard-mirror.py` 0 问题；
  `bash tools/doc-metrics.sh` 表格断裂 0、写死的数字不符 0；超 400 行主代码 **6 → 5** 个（屏幕目录最大文件变成
  `EditFoodScreen.kt` 659，viewmodel/ 最大仍是 `AppViewModel.kt` 700 ⇒ 10b 的靶子没变）。
- **行为面**：零改动（文案、排版、主题分支、按钮色、节顺序一律逐字未动）⇒ 不占用真机复测；与 10c 那轮一起过设置页两主题。

### 10b-1 落地结果（2026-09-19，提交见台账）

- **搬了什么**：领域 1「备份导入导出」**9 个函数 / 101 行**（`buildBackupJson` 463 · `buildCsvExport` 465 ·
  `importBackupJson` 467 · `previewBackup` 469–470 · `snapshotBeforeRestore` 472–483 · `importBackupWithSnapshot` 485–519 ·
  `loadLocalSnapshots` 526–534 · `saveLocalSnapshot` 536–545 · `restoreLocalSnapshot` 547–576；行号是搬运前的）
  ⇒ 新文件 `viewmodel/AppViewModelBackup.kt` **148** 行 / **11** 条 import；`AppViewModel.kt` **700 → 590** 行、
  import **74 → 70** 条；`viewmodel/` 2 → **3** 个文件（另一个是 #4a 的 `UiEvent.kt`）。
- **验收②（不改行为）实测**：`git diff --numstat` = VM **+8 / −118** · 新文件 **+148** · 适配器 **+9** · 守卫 **+13 / −6**。
  非逐字的改动**只有三类**：9 条声明行（加 `internal` 与 `AppViewModel.` 接收者、缩进 4 → 0）、4 个成员放宽
  `private → internal`（`repo` / `clock` / `emit` / `_localSnapshots`）、import（新文件 11 条 / VM 删 4 条 / 适配器 +8 条）；
  另加 VM 类声明上方 4 行 `//` 补注。**独立复核**（用 `git show HEAD:` 的原文重算边界，不信搬运脚本自报）：
  9 个块 **101** 非空行与新文件正文**逐行相等、差异 0**（含阴性对照）；VM 剩余 **504** 非空行两边相等、
  差异**恰好 16 条** = 4 删 import + 4 行补注 + 4 处放宽 ×2 ⇒ 其余一字未动。
- **规划的风险①（`stateIn` 属性留在类里）落地**：`_localSnapshots` / `localSnapshots` 与段标题（521–524）没搬，
  判据写进新文件 KDoc 供后面 6 个领域照用。⚠️ 顺带踩到**指标污染**：KDoc 散文里写了字面量 `stateIn(` ⇒
  `doc-metrics` 的计数由 20 变 21、ROADMAP ×2 与 INDEX ×1 写死的「20 处」全报不符 ⇒ 改措辞后回到 **20**
  （领域 1 的代码里一处 `stateIn` 都没有，那 20 处全在 VM）。
- **规划的风险③（`CorruptGuardTest` 会红）落地**：`guard-mirror` **先抓到了**（「正向断言的字面串在 `AppViewModel.kt`
  里找不到：`private suspend fun snapshotBeforeRestore()`」⇒ 有问题 1 个），改法 = **读两个文件** + 新签名字面量 +
  两处 `functionBody(…, indent = 0)`；`syncDownload` 那条仍读 VM —— 它是「类内代码调**搬出去的同包扩展**」的**首例形状**
  （同包 ⇒ 不需 import）。改完用 Python 忠实复刻 `functionBody` 的截断规则把 **6 条断言**跑了一遍：全过（含阴性对照）。
  ⚠️ 规划里同批点名的 `SnackbarCopyTest` **本轮不用动**（它唯一那条 VM 期望属领域 2 的云端同步）；
  `SettingsStateTest` 走 `SettingsActions` 接口、也不用动。规划说「`guard-mirror` 查不到这两类」**只对了一半**：
  字面量那条它查得到（这次就是它报的），按缩进截函数体的那类它查不到（靠 CI）。
- **调用面比规划小得多**：规划的「10 个调用方文件 / 56 个站点 / 约 51 行 import」是 **50 个函数全搬**的爆炸半径；
  领域 1 这 9 个函数实测只有 **1 个**跨包调用方（`ui/screens/SettingsState.kt` 里的 `ViewModelSettingsActions` 适配器，
  **8** 条 import —— `snapshotBeforeRestore` 是内部前置、不外露）。全仓复核（`app/src` 去掉 `viewmodel/` 与 `data/`）：
  其余命中全是 `state.<名>()`（走接口）或测试里实现接口的 `override` ⇒ 无漏网。
- **开工前修了工具的两个盲点**（单独一笔提交，详见 09-19 §1）：判据 4 的第 4 种形状（`viewModelScope.launch { }` 里
  点号**前**那个名字也要 import；VM 去注释实测 58 处、`.launch` 38 处、领域 1 占 4 处）与判据 7（带接收者的声明
  不压制同名 import；本领域实测撞上 4 条 `com.agon.app.data.*`，新文件原本只算出 7 条、应为 11 条）。
  **两个都是「本地四项全绿、CI 必红」的方向** ⇒ 不修就是又一次修红。
- **本地四项（提交前实测）**：`kt-lexcheck` **101** 份 0 问题 · `move-importcheck` 自检 **10/10**、新文件 **11/11 缺 0** ·
  `guard-mirror` **0 问题**（18 处作用域受限跳过）· `doc-metrics` 表格断裂 0 / 写死的数不符 0。⚠️ 本地全绿 ≠ 能编译。
- **进度**：领域 **1 / 7**（函数 **9 / 50**）；`AppViewModel.kt` **590** 行仍 > 400 ⇒ 靶子未达标，继续一领域一提交。

### 10b-2 落地结果（2026-09-19，提交见台账）

- **搬了什么**：领域 2「坚果云同步」**4 个函数 / 101 行**（`saveNutstoreCredentials` 470–471 · `syncUpload` 473–504 ·
  `loadCloudBackups` 514–541 · `syncDownload` 543–581；行号是搬运前的）**＋顶层常量 `NO_CREDENTIALS_MESSAGE`
  （77–84，8 行）** ⇒ 新文件 `viewmodel/AppViewModelCloud.kt` **167** 行 / **10** 条 import；
  `AppViewModel.kt` **590 → 469** 行（非空 **504 → 389**）、import **70 → 65** 条；`viewmodel/` 3 → **4** 个文件。
- **3 处非函数改动（都是实测逼出来的）**：① `NO_CREDENTIALS_MESSAGE` **跟着搬**（3 个使用者全在本领域，
  搬完 VM 实测 0 处；顶层 `private const val` 是文件私有，留下就够不着），其 KDoc 里"本文件的 [TAG]"改措辞为
  "`AppViewModel.kt` 里的 `TAG`"（登记）；② `TAG` **留在 VM**（唯一使用者是 `init` 里的孤儿封面清理，
  4 个云端函数零 `Log`）⇒ 教训：常量去留必须**全文扫**（含 `init` 与属性），只扫函数体会得出假结论；
  ③ `// ---- 坚果云同步 ----` 段标题整段搬空 ⇒ **删**（本轮唯一"删而非搬"的一行）；
  `// ---- 云端备份列表 ----` 与其下 4 个属性按 10b-1 的判据**留着**。
- **验收②（不改行为）实测**：`git diff --numstat` = VM **+3 / −124** · 新文件 **+167** · 适配器 **+5 / −1** ·
  `AppViewModelBackup.kt` **+3 / −2**（修一句它自己预言过、如今已过期的注释）· 守卫 **+6 / −5**。
  非逐字的改动**只有四类**：4 条声明行、3 个成员放宽 `private → internal`（`_syncing` / `_cloudBackups` /
  `_loadingBackups`；**累计 7 个**）、import（新文件 10 / VM 删 5 / 适配器 +4）、2 处登记的注释改写。
  **独立复核**（用 `git show HEAD:` 原文重算边界，不信脚本自报）：搬走的 **109 非空行**与新文件正文
  **逐行相等、差异 0**（含阴性对照）；VM 剩余 **394 → 389 非空行、差异恰好 11 条** = 5 删 import + 3 处放宽 ×2。
  删掉的 5 条里 `previewBackup` / `importBackupJson` 正是 10b-1 **故意留下**的（当时 `syncDownload` 还在类里调它们）
  ⇒ 它一搬走这两条就死了：**判据 7 的两轮结论互相印证**。
- **规划的风险③落地**：`CorruptGuardTest` 的 `functionBody(vm, "fun syncDownload(")` 改读新文件
  （`fun AppViewModel.syncDownload(`、`indent = 0`），该测试方法**不再读 `AppViewModel.kt`**
  （三条恢复路径全在两个领域文件里）；另一方法读的 `fun discardCorruptData()` 没搬 ⇒ 不受影响。
  改完用 Python 复刻截断规则跑 **7 条断言**全过（含阴性对照）。
- **⚠️ 规划的风险④被实测推翻**：`SnackbarCopyTest` **本轮不用动** —— 它唯一那条 VM 期望（"已自动同步到坚果云 ☁️"）
  实测落在 `maybeAutoSync`（搬后 L323），而 `maybeAutoSync` / `maybeAutoSnapshot` 是**自动同步策略**、
  不属云端同步领域 ⇒ 留在 VM。教训：记忆里/规划里的"预期影响"一律实测复核，别照着预期改文件。
- **调用面**：跨包仍只有 `ui/screens/SettingsState.kt` 适配器（**+4** 条 import）。⚠️ 这 4 条与 10b-1 那 8 条
  **ASCII 序交错**（`loadCloudBackups` 要排在 `restoreLocalSnapshot` 前）⇒ 搬运器不能再"追加到 import 块末尾"
  （10b-1 那条 `max(before) < min(ad_need)` 断言本轮必然失败），改成把 `com.agon.app.viewmodel.*` 整组读出、
  合并排序写回，并把组上那行注释泛化成"`AppViewModel<领域>.kt`"（登记）。
- **本地四项（提交前实测）**：`kt-lexcheck` **102** 份 0 问题 · `move-importcheck` VM **65 条缺 0** / 新文件
  **10 条缺 0** · `guard-mirror` **0 问题**（18 处作用域受限跳过）· `doc-metrics` 表格断裂 0 / 写死的数不符 0。
  ⚠️ 本轮沙箱**第三次重建**（`.git` 变回分支起点的全新 clone、工作树无恙），按 `git ls-remote` →
  显式 refspec `fetch` → `reset --mixed` 三步恢复（**绝不用 `--hard`**）；重建也清掉了 ktlint/detekt 的二进制缓存
  与 JDK（下载被墙）⇒ 那两项本地跑不了，CI 仍是唯一编译神谕。
- **进度**：领域 **2 / 7**（函数 **13 / 50**）；`AppViewModel.kt` **469** 行仍 > 400 ⇒ 靶子未达标，继续一领域一提交。

### 10b-3 落地结果（2026-09-19，提交见台账）

- **搬了什么**：领域 3「归档与消耗撤销」**6 个函数 / 29 行**（`undoConsumption` 245–248 ·
  `restoreArchivedWithUndo` 250–253 · `deleteConsumption` 255–265 · `undoDeleteConsumption` 267–273 ·
  `archive` 351–352 · `restoreArchived` 374；行号是搬运前的）⇒ 新文件
  `viewmodel/AppViewModelArchiveUndo.kt` **87** 行 / **10** 条 import；`AppViewModel.kt` **469 → 431** 行
  （非空 **360 → 357**）、import **65 → 62** 条；`viewmodel/` 4 → **5** 个文件。
  这是 7 个领域里**最小**的一个（VM 只掉 38 行 = 29 函数 + 6 折叠空行 + 3 死 import）。
- **本轮三个「零」（都是实测）**：① **零放宽** —— 6 块只用到 `repo` / `emit`（#10b-1 已 `internal`）
  与 `consumption`（本来就 public），逐个成员核可见性：`private` 未放宽的 **0** 个 ⇒ 放宽总数仍 **7**；
  ② **零常量、零段标题** —— 6 块里既无 `Log` 也无中文字面量，两处切除点各自都还有别的内容
  ⇒ 没有一行是「删而不搬」；③ **零守卫改动** —— `SnackbarCopyTest`（6 块中文字面量 0 处 ⇒ 键不用改）、
  `CorruptGuardTest`（它那个 `archive` 是测试自己的私有辅助函数；另两条读 data 层 `changeQuantity`）、
  `CompactConsumptionTest`（确实读 `ConsumptionLogScreen.kt`，但断言的是 `record.isDeletable()` 与「月度合计」
  ⇒ 多一行 import 不影响）、`ScreenParityTest` / `ImeHandlingTest`（规则与 import 行无关）逐个查过。
- **验收②（不改行为）实测**：`git diff --numstat` = VM **+0 / −38** · 新文件 **+87** · 5 个调用方
  **+1/+1/+1/+1/+2（合计 +6）**。**判据这轮算得最干净：新增行 = 放宽 + 新 import ⇒ 6 = 0 + 6，VM 侧一行未增。**
  非逐字的改动**只有两类**：6 条声明行、import（新文件 10 / VM 删 3 / 调用方 +6）—— **没有注释改写**。
  **独立复核**（`git show HEAD:` 原文重算边界）：搬走的 **29 非空行**与新文件正文**逐行相等、差异 0**
  （含阴性对照）；VM 剩余 **360 → 357 非空行、差异恰好 3 条** = 3 条死 import（`addConsumption` /
  `deleteConsumption` / `undoConsumption`，逐个实测搬家后 VM 里 0 处；留下的 `archiveItems` /
  `restoreArchived` / `ArchivedItem` / `ConsumptionRecord` / `ArchiveReason` 逐个实测**仍有活口**）；
  5 个调用方 `difflib` 逐个比对：**只有 import 行**，各组仍 ASCII 有序。
- **⚠️ 最锋利的一处（并因此逼出 §5 那个工具盲点）**：3 个函数名与 data 层扩展**一字不差**
  （`undoConsumption` / `restoreArchived` / `deleteConsumption`）⇒ 新文件既声明 `internal fun AppViewModel.同名`、
  又必须 `import com.agon.app.data.同名`（体内写的是 `repo.同名(...)`，接收者是 `FoodRepository`，
  同包那份接收者类型不对、不参与解析）。
- **调用面比前两轮宽**：跨包 **5 个文件 / 6 处**（`MainApp.kt` · `ConsumptionLogScreen.kt` ·
  `ConsumptionLogState.kt` · `FoodDetailState.kt` · `FoodListState.kt` 两处）。
  ① `FoodListScreen.kt` 写的是 `state.restoreArchivedWithUndo(entry)` ⇒ **不需要** import；
  ② `FoodListState.kt` 自己**也声明了同名**的 `fun restoreArchived` / `fun restoreArchivedWithUndo`（适配器）
  ⇒ 声明不压制 import，那两行照样得加（先例 `SettingsState.kt`，#10b-1/2 已编译通过）。
  兜底：全仓 `viewModel::` 这类**可调用引用实测 0 处** ⇒ 调用点正则没漏形状；搬运器另加断言
  「全仓扫到的调用方文件集合必须正好等于 CALLERS」。
- **本地四项（提交前实测）**：`kt-lexcheck` **103** 份 0 问题 · `move-importcheck` VM **62 条缺 0** /
  新文件 **10/10 缺 0**（§5 修完后「疑似多余 3」的假警也没了）· `guard-mirror` **0 问题**（18 处跳过）·
  `doc-metrics` 表格断裂 0 / 写死的数不符 0 / `@Composable` 仍 **162** 处（阳性对照不变）。
  ⚠️ ktlint / detekt 本地仍跑不了（沙箱第四次重建清了缓存、下载被墙）⇒ CI 仍是唯一编译神谕。
- **进度**：领域 **3 / 7**（函数 **19 / 50**）；`AppViewModel.kt` **431** 行仍 > 400（差 31 行）
  ⇒ 靶子未达标；剩下 4 个领域里「食物 CRUD 与批量」最大（11 个函数），达标大概要落到它之后。

### 10b-4 落地结果（2026-09-19，提交见台账）—— 🎯 验收① 的 viewmodel 那一半达标

- **搬了什么**：领域 4「食物 CRUD 与批量」**11 个函数 / 46 非空行**（`upsert` 316 · `archiveBatch` 318–319 ·
  `restoreArchivedBatch` 321–323 · `restoreArchivedSmart` 325–328 · `cleanExpired` 330–336 · `deleteArchived` 338 ·
  `clearArchive` 340 · `changeQuantity` 342–358 · `consumeOne` 360–361 · `clearAll` 395 · `discardCorruptData` 424–430；
  行号是搬运前的）⇒ 新文件 `viewmodel/AppViewModelFood.kt` **138** 行 / **14** 条 import；
  `AppViewModel.kt` **431 → 363** 行（非空 **357 → 300**）、import **62 → 51** 条；`viewmodel/` 5 → **6** 个文件。
  这是 7 个领域里**最大**的一个，也是唯一有**三个切除点**的。
- **🎯 验收① 达标**：`bash tools/doc-metrics.sh` 的「viewmodel/ 最大文件」那行现在报
  **`AppViewModel.kt` 363 行 OK 全部 < 400** ⇒ 两块靶子（设置页入口 / `AppViewModel`）**都已满足**
  （前者在 10a-1 / 10a-2 达标）。剩下 3 个领域（分类与位置 7 / 设置 6 / UI 状态与事件 5 = 18 个函数）
  **不再是达标所需**，属用户 2026-09-19 选的「搬完」⇒ 继续一领域一提交。
- **三个「零」**：① **零放宽**（连着第三轮）—— 11 块只用到 `repo` / `emit`（#10b-1 已 `internal`）与
  `items`（本来就 public）⇒ 放宽总数仍停在 **7**；② **零常量、零段标题** —— 11 块里 `Log` / `TAG` /
  中文字面量各 **0** 处，三处切除点各自都还有别的内容 ⇒ 没有一行是「删而不搬」；③ **零多余删除** ——
  VM 减少的 **68** 行 = 搬走的 46 行 + 11 条死 import + 11 行折叠掉的空行（含类尾 1 行），逐类闭合。
- **⚠️ 一处守卫同批改（规划风险 ③ 点名的第三处，也是最后一处）**：`CorruptGuardTest`「损坏数据有放弃入口」
  原先读 `AppViewModel.kt` + 断言字面量 `fun discardCorruptData()` ⇒ 改成读 `AppViewModelFood.kt` +
  断言 `fun AppViewModel.discardCorruptData()`（顶层扩展函数的声明行带接收者）。改之前先做**守卫仿真**
  （照抄该测试的判定跑在两棵树上：HEAD 树为真、搬完为假）⇒「会红」是量出来的，不是猜的。
  `SnackbarCopyTest` 同样仿真过：**32** 个中文片段的分布在两棵树里**完全一致**（11 块 0 个中文字面量；
  它唯一那条 VM 期望「已自动同步到坚果云 ☁️」在 `maybeAutoSync` 里 ⇒ 留守）。
- **验收②（不改行为）实测**：`git diff --numstat` = VM **+0 / −68** · 新文件 **+138** · 7 个调用方 **+15**
  （2/4/1/2/2/3/1）。**判据：新增行 = 放宽 + 新 import ⇒ 15 = 0 + 15，VM 侧一行未增。**
  非逐字的改动只有两类：11 条声明行、import（新文件 14 / VM 删 11 / 调用方 +15）—— **没有注释改写**
  （3 处 KDoc 跟着函数一起搬，一字未改）。**独立复核 7 项全过**（边界取自 `git show HEAD:` 快照、
  比对对象是已落盘的真实文件）：搬走的 46 非空行与新文件正文逐行相等（把 11 条声明行**逆变换**回原形状再比；
  差异 0，阴性对照改 1 行 ⇒ 差 11）；VM 减少的非 import 行恰好等于搬走的 46 行原文；删掉的 11 条 import
  逐条在剩余代码里整词 0 出现，留下的 **8** 条「同名遮蔽」假警逐条点名了 `repo.<名>(` 的活口；
  7 个调用方除 import 外一字未动、组内 ASCII 有序、无重名 import。
- **⚠️ 同名相撞四处（比 #10b-3 更多）**：① **6 个函数名与 data 层扩展一字不差**（`upsert` /
  `restoreArchivedBatch` / `deleteArchived` / `clearArchive` / `changeQuantity` / `clearAll`）⇒ 新文件既声明
  `internal fun AppViewModel.同名`、又必须 `import com.agon.app.data.同名`（体内写的是 `repo.同名(...)`）；
  ② 第 7 处走**同包兄弟文件**那一路：`restoreArchivedSmart` 体内 `repo.restoreArchived(id)`，而
  `AppViewModel.restoreArchived` 声明在隔壁 `AppViewModelArchiveUndo.kt` ⇒ **这正是 `92ce809` 修掉的盲点的
  第一个真实用户**（没修就是「本地全绿、CI 必红」）；实测 `move-importcheck` 对新文件报
  **现有 14 · 应有 14 · 缺 0 · 疑似多余 0**；③ 反向：`repo.discardCorrupt(…)` 与它读的那份损坏标记集合是
  `FoodRepository` 的**类成员**（不是扩展）⇒ 一个 import 都不需要；④ `consumeOne` 是唯一的**领域内互调**
  （`changeQuantity(id, -1, onAutoArchived)` 走隐式接收者 ⇒ 解析到本文件那份扩展，同包不需要 import）。
  **手算漏过、工具揪出来的一条**：`cleanExpired` 里 `it.daysLeft` 用的是 data 层**扩展属性** ⇒
  `import com.agon.app.data.daysLeft` 必须带上。
- **调用面（四个领域里最宽）**：跨包 **7 个文件 / 16 处** `viewModel.<名>(` ⇒ **+15 行 import**；
  走 `state.<名>(` 的 5 个屏幕（`ArchiveScreen` / `HomeScreen` / `FoodDetailScreen` / `FoodListScreen` /
  `SettingsBackupDialogs`）不需要 import；7 个里有 **5 个自己声明了同名转发函数**（声明不压制 import，照样要加）。
  ⚠️ `SettingsState.kt` 的 viewmodel import 组里夹着一条 #10b 分组注释 ⇒ 组**不连续**，搬运器从「整组重排」
  改成「按 ASCII 位置逐条插入、绝不重排已有行」；另加**同名 import 冲突预检**（conflicting import = 编译错；
  7 个文件实测 0 处）。兜底：全仓可调用引用（`viewModel::` / `vm::`）实测 **0** 处。
- **本地四项**：`kt-lexcheck` **104** 份 0 问题 · `move-importcheck` 新文件 14/14 缺 0、VM 缺 0（疑似多余 8
  全是类成员遮蔽的已知假警）· `guard-mirror` **0** 问题（18 处跳过）· `doc-metrics` 表格断裂 0 /
  写死的数不符 **0** / `@Composable` 仍 **162** 处（阳性对照不变）。
  ⚠️ 新文件 KDoc 原本写出了那份损坏标记集合的属性名（`doc-metrics` 追踪的数）⇒ 把 **32 顶到 33**、报「不符 1 处」；
  修法是**改 KDoc 措辞**而不是把 33 写进文档（否则那个数就不只反映代码引用了）⇒ 改完回到 32、不符 0。
  ⚠️ ktlint / detekt 本地仍跑不了（沙箱重建清了缓存、下载被墙）⇒ CI 仍是唯一编译神谕。
- **顺带**：`EditFoodScreen.kt` 因多 1 行 import 从 659 → **660** 行（10e 那块**可选**靶子，本就不在范围内）
  ⇒ 本文件验收① 的例外清单与 10e 那行已同步改成 660；历史段落里的 659 留原样。
- **进度**：领域 **4 / 7**（函数 **30 / 50**）；`viewmodel/` 六个文件**全部 < 400**
  （**363** / 167 / 149 / 138 / 119 / 87）；剩 3 个领域 18 个函数（`maybeAutoSync` / `maybeAutoSnapshot`
  两个 private 暂留类里）。

### 10b-5 落地结果（2026-09-19，提交见台账）

- **搬了什么**：领域 5「分类与位置」**7 个函数 / 25 行**（`setCategoryThreshold` 305–306 · `addCategory` 310–313 ·
  `updateCategory` 315–317 · `deleteCategory` 319–322 · `addLocation` 326–331 · `deleteLocation` 333–335 ·
  `updateLocationBatch` 347–349；行号是搬运前的）⇒ 新文件 `viewmodel/AppViewModelCategoryLocation.kt`
  **104** 行 / **8** 条 import；`AppViewModel.kt` **363 → 322** 行（非空 **300 → 268**）、import **51 → 46** 条；
  `viewmodel/` 6 → **7** 个文件（七个全部 < 400）。
- **本轮的看点：删两条段标题**（10b-2 那条「坚果云同步」之后第二次，也是一次删两条）——
  `// ---- 分类管理 ----` 是**真孤儿**（底下 3 个函数全走）；`// ---- 位置管理 ----` 严格说**不是**孤儿
  （底下还留着 5 个主题开关），但那 5 个属 10b-6「设置」领域、跟「位置」无关（本来只是排在那儿）⇒
  位置函数一走它就开始给不属于自己的内容当帽子，而 10b-6 落地时那 5 个也会走 ⇒ 一并删，由新文件 KDoc 接替。
  ⚠️ 这是本轮唯一两处「删而不搬」（都是注释行，与行为无关）。删完做**结果上的孤儿检查**
  （剩余 VM 里每条 `// ---- X ----` 到下一条标题之间必须有非空内容）⇒ 剩下 3 条逐条查过都不是孤儿。
- **三个「零」**：① **零放宽**（连着第四轮）—— 7 块只用到 `repo`（10b-1 已 `internal`）与 `categories` /
  `locations`（本来就 public）⇒ 放宽总数仍停在 **7**；② **零常量随迁** —— `Log` / `TAG` 各 0 处；
  ③ **零守卫改动** —— 全仓测试树里这 7 个函数名 **0** 处命中；`CorruptGuardTest` 的 5 条断言
  （含 10b-4 刚改的那条）**仿真跑过仍全为真**；`SnackbarCopyTest` 的 32 个片段分布搬家前后**完全一致**。
- **跟着搬走的两样小东西**：① 一个字面量 —— `addCategory` 的默认餐具 emoji（10b-2 的 `NO_CREDENTIALS_MESSAGE`
  之后第一个随迁字面量）；仿真量过全仓总数 **3 → 3** 不变（另两处在 `ConsumptionLogScreen.kt` /
  `StatsScreen.kt`），测试树 **0** 处追踪它 ⇒ 无守卫要改；② 一条 import —— `java.util.UUID`
  （VM 里的唯一使用者就是 `addCategory`）；而 `CategoryDef` 是**两边都要**（类里 `categories` 属性的类型
  仍是它、新文件的参数类型也是它，import 按文件算）。
- **验收②（不改行为）实测**：`git diff --numstat` = VM **+0 / −41** · 新文件 **+104** · 2 个调用方 **+7**（6 + 1）。
  **判据：新增行 = 放宽 + 新 import ⇒ 7 = 0 + 7，VM 侧一行未增。** VM 减少的 41 行 = 搬走的 25 行
  + 2 条段标题 + 5 条死 import + 9 行折叠掉的空行，逐类闭合。非逐字的改动只有三类：7 条声明行、import、
  删掉的 2 条段标题（本领域 7 块里一条注释都没有 ⇒ 没有 KDoc 随迁）。
  **独立复核 7 项全过**（边界取自 `git show HEAD:` 快照、比对对象是已落盘的真实文件）：搬走的 25 行与新文件正文
  逐行相等（声明行逆变换后比；差异 0，阴性对照改 1 行 ⇒ 差 11）；VM 减少的非 import 行恰好 = 搬走的 25 行
  + 2 条标题；5 条被删 import 逐条整词 0 出现，留下的 **6** 条「同名遮蔽」假警逐条点名了 `repo.<名>(` 的活口；
  2 个调用方除 import 外一字未动、组内 ASCII 有序、无重名 import。
- **同名相撞 2 处**（比 10b-4 的 7 处少）：`setCategoryThreshold` 与 `updateLocationBatch` 与 data 层扩展一字不差
  （`data/FoodSettings.kt` / `data/FoodItems.kt`）⇒ 新文件既声明 `internal fun AppViewModel.同名`、又必须
  `import com.agon.app.data.同名`；`setCategories` / `setLocations` 只有 import、没有同名声明
  （data 层只暴露「整表覆盖」的两个 setter）。实测 `move-importcheck` 对新文件报 **现有 8 · 应有 8 · 缺 0 · 疑似多余 0**。
- **调用面是五轮里最窄的**：跨包 **2 个文件 / 7 处** ⇒ **+7 行 import**（`ui/screens/ManageState.kt` 6 ·
  `MainApp.kt` 1）；`ManageScreens.kt` 走 `state.<名>(` ⇒ 不需要 import；⚠️ `ManageState.kt` 里有 **5 个同名的
  转发函数** ⇒ 声明不压制 import，那几行照样要加。兜底：全仓可调用引用 **0** 处、同名 import 冲突预检 **0** 处。
- **本地四项**：`kt-lexcheck` **105** 份 0 问题 · `move-importcheck` 新文件 8/8 缺 0、VM 缺 0（疑似多余 6 全是
  类成员遮蔽的已知假警）· `guard-mirror` **0** 问题（18 处跳过）· `doc-metrics` 表格断裂 0 / 写死的数不符 **0** /
  `stateIn(` 仍 **20** 处（**#10c 的靶子一处没碰**）/ `WhileSubscribed` 仍 **0** 处 / `corruptedKeys` 仍 **32** 处
  （本轮 KDoc 没写任何被追踪的标识符 —— 10b-4 那个坑没再踩）。⚠️ ktlint / detekt 本地仍跑不了 ⇒ CI 仍是唯一编译神谕。
- **搬运器两处机制改动**（为剩下 2 个领域留着）：`DROP`（单个）→ `DROPS`（列表）；「切除区间两侧得有内容」那条
  留守断言 → **结果上的孤儿检查**（本轮 7 块 + 2 标题交错，间接判据会失真）。另加两条断言：删掉的标题
  **没有**出现在搬走的代码正文里（是「删」不是「搬」）、但**必须**在新文件 KDoc 里被提到（否则读者不知道它去哪了）。
- **进度**：领域 **5 / 7**（函数 **37 / 50**）；`viewmodel/` 七个文件全部 < 400
  （**322** / 167 / 149 / 138 / 119 / 104 / 87）；剩 2 个领域 13 个函数（设置 6 + UI 状态与事件 5，
  另 `maybeAutoSync` / `maybeAutoSnapshot` 两个 private 暂留类里）。

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
