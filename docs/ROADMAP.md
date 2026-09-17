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
| **4** | **错误模型统一** | ⏳ **未开始 · 下一个该做的** | — |
| 5 | Repository 拆分 + `Clock` 注入 + 轻量 DI | ⏳ 未开始（前置 #4） | — |
| 6 | 派生数据下沉 VM + `WhileSubscribed` | ⏳ 未开始（前置 #5）；**范围已缩小**，见下 | — |
| 7 | 字符串资源化 + 无障碍补全 | ⏳ 未开始（前置 #3 已满足 ⇒ **随时可插队做**） | — |
| 8 | 数据健康检查页 + 诊断包 + 许可清单 | ⏳ 未开始（前置 #4/#5） | — |
| 9 | CI 加固 | 🔶 用户已否掉大半，剩 4 个小项 | 09-17 §10 |

**排序原则（原文照录，对剩余项仍适用）**：**先能拦、再去重、后补体验**。
**为什么是这个顺序**：#1/#2 先把「已经为零的基线」变成拦截，之后任何一步的回归都会被 CI 当场抓住
（否则 #3 那种七千行量级的改动没有安全网）；#3 收益最大也最险，必须建立在组件层模板 + 守卫改写之上；
**#5 排在 #4 之后，是因为错误模型会改 Repository 的返回类型 —— 先拆文件再改签名等于同一批代码搬两次。**

---

## #4 错误模型统一 —— 下一个该做的

- **证据（2026-09-17 实测）**：`Channel<` **0** 处、`UiEvent` **0** 处、`Result<` 仅 **3** 处。
  错误提示目前靠散落的 `MutableStateFlow<String?>` + Snackbar 文案：多个订阅方会重复消费同一条，
  且「一次性事件」被当成「状态」建模（旋转/重组后可能重放或丢失）。
- **做法**：`Result` 承载返回值 + `Channel<UiEvent>` 承载一次性事件 + **一处**全局收集（`MainApp` 的 Snackbar 覆盖层）。
- **验收**：`Channel<UiEvent>` 在 `AppViewModel` 里有且只有一处发送端与一处收集端；
  现有 `MutableStateFlow<String?>` 型的错误提示逐个迁走（迁一个删一个，不留两套）；单测数只增不减。
- **风险**：会碰到 `AppViewModel`（`TooManyFunctions` 清单里的大头，09-16 实测 53 个函数）；
  撤销条那条链路（`AppSnackbarHostState` / `AppSnackbarForm`）已经是容器化的，迁移时**不要**把两个主题的
  `SnackbarResult` 顺着签名漏回屏幕层（这条约定记在 `docs/ARCHITECTURE.md` §5「App 级组件层」）。

## #5 Repository 拆分 + `Clock` 注入 + 轻量 DI

- **前置**：#4（错误模型会改 Repository 的返回类型）。
- **证据**：`FoodRepository.kt` **950** 行；`Application` 子类 **0** 个（DI 靠 `remember { … }` 现场构造）；
  java.time 的 `now()` 直接调用 **34** 处（其中 `LocalDate.now()` **26** 处）⇒ 时间不可注入，跨零点逻辑无法单测。
- **做法**：按领域拆 Repository（库存 / 归档 / 消耗 / 历史 / 备份）+ 注入 `Clock`（或 `today: LocalDate` 参数，
  仓库里已有 `LocalToday` CompositionLocal 与可注入 `today` 的 `*At` 函数，沿用同一套）+ 一个轻量 DI 容器
  （`Application` 子类或手写 `ServiceLocator`，**不引入 Hilt/Koin**：单模块、构造点少，引框架的代价大于收益）。
- ⚠️ **风险：会撞两个「读源码」的静态守卫，且清单已随 09-16 合并变过**（原路线图点名的
  `MiuixHomeScreen.kt` / `MiuixConsumptionLogScreen.kt` **已被删除**，别照抄旧清单）。今日实测清单：
  - `CorruptGuardTest` 读 4 个文件：`data/FoodRepository.kt`、`data/SecureStore.kt`、`ui/screens/HomeScreen.kt`、`viewmodel/AppViewModel.kt`
  - `CompactConsumptionTest` 读 2 个：`data/FoodRepository.kt`、`ui/screens/ConsumptionLogScreen.kt`
  - 且 `CorruptGuardTest.functionBody()` 是**按 4 空格缩进截函数体**的 ⇒ 函数签名一搬家就会
    `assertTrue("源码里找不到 …")` 直接失败。
  - ⇒ **拆分与测试改动必须在同一个提交里**，否则 CI 必红且红得莫名其妙。
- **顺带**：`ImeHandlingTest` 点名 10 个文件（`MainActivity.kt` / `MainApp.kt` / `BatchBars.kt` / `NavChrome.kt`
  + 4 个屏幕 + `AppFormDialog.kt` / `AppBatchMoveDialog.kt`）。拆 Repository 正常碰不到它；若顺手动了屏幕就要同步改清单。

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

## #8 数据健康检查页 + 诊断包 + 许可清单

- **前置**：#4/#5（诊断包要导出的东西会随错误模型与 Repository 边界变化）。
- **证据**：`corruptedKeys` 被引用 **32** 处（作用域 `app/src/main`，出现次数口径），但**没有任何页面消费它** ——
  用户看不到「哪些数据坏了」，出路只有「导入备份 / 放弃损坏数据」两个入口；
  `corrupt/` 留档目录相关代码 **14** 处；许可清单 **0** 处、诊断包 **0** 处（两者都还不存在）。
- **做法**：新页面读 `corruptedKeys` 逐 key 说明影响 + 一键导出诊断包（版本/设备/各表条数/损坏 key/最近日志，
  **不含**用户数据明文与凭据）+ 开源许可清单（`play-services-oss-licenses` 之类，或自生成）。
- **验收**：损坏态下用户能看到「哪几张表坏了、各影响什么功能、能怎么救」；诊断包可被用户导出并发给开发者；
  导出内容经 `tools/ci-gates.sh` 之外的一次人工审查确认不含凭据（本仓密钥已做过全历史扫描，见 09-17 §10）。

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
