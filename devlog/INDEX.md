# 开发日志索引（devlog/INDEX.md）

> 维护规则见 `CLAUDE.md` 第 3 节：每轮开发完成（构建成功）后写当日日志，并在本索引更新一行。
>
> **本文件是索引，不是副本**（2026-09-17 起严格执行）：日志列表一天一行，细节留在当日日志；
> 待办只在本文件维护一份。此前 09-15 / 09-16 / 09-17 三行被写成了日志的浓缩副本
> （09-17 那格 37 行 ≈ 当日日志篇幅的 24%，07-31 那格 ≈ 50%），单元格里还嵌了换行，
> 导致 markdown 表格从第 10 行起断裂（那些行不以 `|` 开头，GitHub 上渲染是乱的）；
> 同一件事又在「待办总览」散文和「优先级表」两处各写一遍，单测数就因此在
> 91 → 116 → 117 → 119 → 120 之间被反复加注修正。
>
> 压缩前做过**信息保全核对**：原先只存在于本文件、当日日志里没有的 5 个 CI run 号与 6 个提交哈希，
> 已先补进 [2026-09-17 日志](2026-09-17.md) 末节「本日提交与 CI 台账」，本文件此后只留指针。

## 日志列表（新 → 旧）

| 日期 | 主题 | 状态 |
| --- | --- | --- |
| [2026-09-19](2026-09-19.md) | **#10b 开工**：`AppViewModel`（700 行 / 50 函数）按 7 领域搬同包 `internal` 扩展函数，一领域一提交。开工前先补 `move-importcheck` 的两个盲点（判据 4 第 4 形状「小写扩展属性当接收者」`viewModelScope.launch`；判据 7「带接收者的声明不压制同名 import」），自检 8 → **10**；四个历史对照重跑 A/C/D 不变、#10a-2 的 5 个文件 41 → **43**（查明是判据 6 让两轮真红一起复现）。**10b-1 落地**：备份领域 9 个函数 / 101 行 → 同包 `AppViewModelBackup.kt` 148 行，VM **700 → 590**、4 个成员放宽 `internal`、适配器 +8 条 import、`CorruptGuardTest` 同批改 1 个测试方法（`guard-mirror` 先抓到）；逐字复核差异 **0**。**10b-2 落地**：云端同步领域 4 个函数 / 101 行 + 顶层常量 `NO_CREDENTIALS_MESSAGE`（8 行）→ 同包 `AppViewModelCloud.kt` 167 行，VM **590 → 469**、3 个成员放宽（累计 **7**）、适配器 +4 条 import（与 10b-1 那 8 条 ASCII 序交错 ⇒ 改成整组合并排序）、`CorruptGuardTest` 同批改（该测试方法已不再读 VM）、`AppViewModelBackup.kt` 修 1 句过期注释、孤儿段标题删 1 行；规划点名的 `SnackbarCopyTest` **实测不受影响**（那条文案在 `maybeAutoSync`，不搬）；逐字复核差异 **0**（含阴性对照） | ✅ CI 绿（§1 `e371d82`、§2 `e7c89cd` 各一次通过；§3 的 run 号下一笔补记）· 行为零改动 ⇒ 不占用真机复测（与 10c 那轮一起过）· ⚠️ 沙箱**第三次重建**（`.git` 回到分支起点、工作树无恙），按 `ls-remote` → 显式 refspec `fetch` → `reset --mixed` 恢复；重建也清了 ktlint/detekt 缓存 ⇒ 那两项本地跑不了，CI 仍是唯一编译神谕 |
| [2026-09-18](2026-09-18.md) | 结构性重构第一轮 **#4 全项收官（4a+4b+4c）**：一次性事件从「可空 StateFlow + 手工 `consume`」→ `UiEvent` + `Channel`（**4** 条队列、1 个发送点）；主壳三处「按主题分流」→ `showUndoSnackbarAcrossThemes()`；**5 个回调**（4 个 `(Boolean, String)` + 1 个 `(Boolean, Boolean)`）→ `OpFailure` 三档 + `OpFailed`/`CloudBackupsEmpty` 事件；`kt-lexcheck.py` 入库；核查第 14/15/16 处；守卫交接 + 日期纠正 22 处。**+ 结构性重构第二轮 #5 全项落地（5a+5b+5c）**：手写 DI 容器（`Application` 子类 0 → 1、仓库现场构造归零）→ 时钟注入（数据层与 VM 函数体硬调 → **0**、14 处已注入）→ 仓库按领域拆完（`FoodRepository.kt` **965 → 252** 行、47 → **9** 个类级函数，拆出 7 个领域文件，`data/` 15 个文件最大 293 行、无一超 400）；`tools/guard-mirror.py` 入库；核查第 17 处 | ✅ CI 绿（4c 红过一回见 §10；**5c-4 红过两回**见 §12.4 第 3/4 条，两次都是我的搬运工具与守卫没跟上搬家）· 单测 121→127→135→143→**153** · ✅ #4 **真机复测已通过**（4 处提示 + 设置页 5 条流程，各 × 2 主题）· ✅ #5 **真机复测已通过**（debug 包、两主题、按清单全过）⇒ **#5 全项收官**（release 包未实机确认，如实记在 §12.7）|
| [2026-09-17](2026-09-17.md) | 真机复测收尾：① MD3 输入弹窗粘性键盘避让（`stickyImePadding`）② Miuix 表单弹窗输入框与按钮零间距 ③ Miuix 弹窗主要动作按钮改蓝底白字；随后形参窄化、`ImeHandlingTest` 加固、`AppDialogs.kt` 搬进组件层；**+ 文档审计轮 P0–P4**（INDEX 重写 -70%、6 份报告补批注、抽出 `docs/ROADMAP.md`、三份组件清单合一、新增 `tools/doc-metrics.sh` 口径脚本、删 1 个孤儿文件）；**+ 文档事实核查轮**（把 8 份基础文档的可验证断言逐条拿去和仓库对：查出 **6 处确认错误 + 4 处判断题**并全部修掉，最要命的是升级手册的文件索引指着 0 个文件、只覆盖 23% 的 Miuix 调用点；另加 2 条守卫） | ✅ CI 最终全绿（当日 3 次红均已各自定位并修复）；**提交与 run 的计数不在本行手抄** —— 台账见日志末节（附取数命令）；单测 **121** 例 |
| [2026-09-16](2026-09-16.md) | 文档对账轮（12 份文档、30+ 处，不改运行时行为）+ `Common.kt` 拆 8 文件 + `MainActivity.kt`(1,123 行) 拆 6 文件 + 双主题 8 对全数合并（§15–§22）+ 写入拆分路线图 | ✅ CI 绿（中间红过一次，真因见日志 §13） |
| [2026-09-15](2026-09-15.md) | 两轮审查交叉验证后的 5 项修复 + IME 键盘避让（A 方案）+ 写守卫按 key 粒度 + 性能批（`DecodeCache` / 精确订阅）+ CI 门禁上线（`tools/ci-gates.sh` + 两个新 job） | ✅ CI 绿（当时单测 116 例） |
| [2026-08-22](2026-08-22.md) | 跨零点残留(B-01) / 封面压缩(B-04) / Version Catalog(B-02) / 依赖升级(B-03) / 双主题状态层抽离(B-08) / 批量改存放位置 / CSV 导出 / 本地滚动冷备 | ✅ CI 绿（含 lint 与单测） |
| [2026-08-21](2026-08-21.md) | Tab 连滑 → miuix-nav 卡片滑 / 撤销 6 秒 / 构建·兼容·数据安全四阶段修复（release 签名失效、minSdk→26、解码失败不再清空数据） | ✅ CI 绿（含 lint） |
| [2026-08-20](2026-08-20.md) | v2.8 引入 Miuix（HyperOS 风格）：技能库 + 工具链升级（Kotlin 2.4.10 / AGP 9.3.1）+ `ThemeStyle` 枚举与持久化 + 设置页双实现 | ⚠️ 当时未验证（沙箱无工具链）→ ✅ 此后 CI 多轮验证 |
| [2026-08-01](2026-08-01.md) | v2.4→v2.7：孤儿图片清理 / 密码 Keystore 加密 / 消耗记录聚合 / 归档恢复去重 / 到期日历并入统计页 / R8 体积优化 / release 正式签名 / 坚果云多版本备份轮转 | ✅ 成功 |
| [2026-07-31](2026-07-31.md) | v1.0 → v2.5.2：需求沟通、功能增量、文档体系、薄荷绿改版更名「吃了么」、MD3 审计修复（62→91）、用户反馈两批 | ✅ 成功 |

## 当前待办（2026-09-17 校准；#4/#5/#8 三条于 09-18 复核修正）

> **本节是唯一事实源（backlog）。** 分工：本节 = 完整待办清单；
> [`docs/ROADMAP.md`](../docs/ROADMAP.md) = 结构性改造 #1–#9 的**执行顺序 + 证据 + 验收 + 风险**；
> `tools/doc-metrics.sh` = 所有数字的**测量口径**。
> 路线图的历史原文在 [2026-09-16 日志](2026-09-16.md)「🧭 拆分路线图」（已冻结为快照，顶部有批注）；
> 历史 backlog 见 `docs/audits/2026-08-21-backlog.md`（顶部有 09-16 的状态校订表）。
> 括号里的数字是 2026-09-17 实测。**口径统一在一处**：`bash tools/doc-metrics.sh` 可一键复跑全部指标
> （作用域、出现次数 vs 命中行数、行数按 `wc -l`、零结果的阳性对照都写在脚本里）。
> 本文件与 docs/ 里的数字都应以该脚本为准 —— 此前正因手抄三份而漂移过（单测数、`corruptedKeys`、`now()` 三例）。

### 结构性（第三批 9 项 + 09-18 新增 #10；#1–#3 已完成，详见文末「已完成里程碑」）

- ✅ **#4 错误模型统一 —— 4a/4b/4c 全部落地（2026-09-18），本项收官** —— 4a：4 个「可空 StateFlow + 手工 `consume`」
  的一次性事件 → `sealed interface UiEvent` + `Channel` 队列（按 Snackbar 落点分，因为 `Channel` 是单接收方语义；4a 时 3 条，4c 加设置页后 **4** 条），
  发送端收成**一个** `emit()`；可空事件状态与 `consume` 函数双双归零，单测 121 → 127（新增 `UiEventTest` 钉住分流）。
  4b：`MainApp` 三处重复的「按主题分流弹撤销条」收成 `showUndoSnackbarAcrossThemes()`（含批量归档那条非事件流），
  主壳里两主题的结果枚举归零；新增 `SnackbarCopyTest`（8 例）把**文案与落位**钉进 CI，单测 127 → **135**。
  4c：**5 个回调**（实测 4 个 `(Boolean, String)`：`syncUpload`/`loadCloudBackups`/`syncDownload`/`restoreLocalSnapshot`，
  + 1 个 `(Boolean, Boolean)`：`importBackupWithSnapshot`）全部去掉回调参数、改发事件 —— 新增 `OpFailure`
  （`Auth`/`Network`/`Other` 三档）+ `Throwable.toOpFailure()`、`UiEvent.OpFailed(op, failure)` 与
  `CloudBackupsEmpty`（"拉成功但云端是空的"这一档原本被塞进 `onResult(false, …)`，与真失败共用一个布尔）、
  `UiSurface.Settings` + 第 4 条队列；**文案逐字未改**（机械验证：diff 中消失的字符串字面量 0 条），
  单测 135 → **143**（新增 `OpFailureTest` 6 例真行为测试）。
  ⚠️ 旧写法「错误提示靠散落的 `MutableStateFlow<String?>`、多个订阅方会重复消费同一条」经 09-18 复核**不成立**
  （4/4 都记得清空，是纪律不是机制）；旧写法「**3 个** `(Boolean, String)` 回调」同样不成立 ⇒ **核查第 15 处**。
  详见 [`ROADMAP`](../docs/ROADMAP.md) #4 节的「4c 落地结果」。
  ✅ **真机复测已通过**（2026-09-18 用户实机：两主题 × 四处提示 **+ 设置页 5 条流程**，无问题）⇒ 验收 ①–⑤ 全达成。
  ✅ **两个待决策项已决（2026-09-18，都选"保持现状"）**：① 网络类失败的 OkHttp **英文技术串不改**
  （`OpFailureTest` 钉住现状，作用变成"防止无意改掉"）；② `saveLocalSnapshot(onDone)` 这条**全仓无调用点**的
  四层管道**留着不删**（核查第 16 处）⇒ 已知的死代码，别当遗漏再查。
  另：全仓有 **6 处** Snackbar 宿主站点（主壳覆盖层 + 5 个二级页），4c 之后 **4 处**收 `UiEvent` —— 别把「落点数」读成「宿主数」。
- ✅ **#5 Repository 拆分 + `Clock` 注入 + 轻量 DI —— 全项收官**（5a ✅ + 5b ✅ + 5c ✅ + **真机复测 ✅**，
  均 2026-09-18；验收 ①–⑥ 全达成，③ 仅在 #5 范围内 ⇒ UI/VM 层 6 个超 400 行的文件仍是待办）—— `FoodRepository.kt` 开工时是 **965 行 / 47 个类级函数**（历史快照），
  **收官后 252 行 / 9 个类级函数**，拆出 7 个领域文件（`RepositoryCore` 244 / `FoodConsumption` 177 / `FoodBackup` 124 /
  `FoodItems` 106 / `FoodArchive` 100 / `FoodSettings` 65 / `FoodCredentials` 49），`data/` 15 个文件**最大 293 行、无一超 400**
  （已进 `tools/doc-metrics.sh` 当判据，并做过阳性对照）；形状 = 同包 `internal` 扩展函数，对外调用写法一字未变；
  `Application` 子类 **1** 个（`ChiliMeApp` 持有 `AppContainer`，#5a 起；改造前 0 个、仓库在 `AppViewModel` 里现场构造）；
  **#5b 起数据层与 VM 取时间一律走注入的时钟**：函数体硬调 **0** 处、已注入 **14** 处（UI/主壳 **7** 处刻意保留），
  单测 143 → **153**（`FoodRepositoryClockTest` 6 例 + `AutoSyncDueTest` 4 例，都用固定时钟）。
  ⚠️ 旧写法「`now()` 34 处 ⇒ 时间不可注入、跨零点逻辑无法单测」经 09-18 复核**后半句错**：跨零点逻辑 08-21 起
  就是可注入 `today` 的纯函数，且**真有单测在测**（`FoodModelsTest` / `CompactConsumptionTest` / `CsvExportTest`
  都传固定日期）；34 处含注释 5 + 默认参数 3 + 委托属性 5，**真该接时钟的只有数据层的 9 处 + VM 的 5 处**
  （⚠️ 计划原写「12 处」，把 `CloudSync`/`LocalSnapshotStore` 那 2 处误归给 UI ⇒ 核查第 17 处，已一并注入）。
  顺序 5a（DI 容器）✅ → 5b（时钟）✅ → 5c（按领域拆，一个领域一个提交）✅；⚠️ 开工前判定"5c 会撞
  `CorruptGuardTest`（逐字断言函数体、还断言 `if (enc != null)` 恰好 2 处）、`CompactConsumptionTest`、
  `FoodRepositoryGuardTest`（真跑 DataStore 的 8 例）"—— **这条风险真的兑现了，5c-4 连红两次**
  （一次是搬成的函数没有接收者、一次是守卫仍指旧文件），4 条源码守卫已随搬家改读各自的新文件，
  `FoodRepositoryGuardTest`/`ClockTest` 一行未改（同包 internal 扩展函数无需 import）。
  此后每搬一个领域都先跑 `tools/guard-mirror.py`。详见 [`ROADMAP`](../docs/ROADMAP.md) #5 节与
  [2026-09-18 日志](2026-09-18.md) §12。
- ⏳ **#6 派生数据下沉 VM + `WhileSubscribed`** —— `stateIn(` **20** 处但 `WhileSubscribed` **0** 处
  （全部 `Eagerly`，后台仍在算）。**本项范围已缩小**：屏幕层的聚合计算今日实测**已清零**
  （`sumOf {` / `groupBy {` / `count {` / `.sortedByDescending` 在 9 个屏幕文件里 0 处，`ScreenParityTest` 拦截；
  阳性对照：同一模式在 8 个 `*State.kt` 里命中 18 处）；宽口径只剩 5 处且都不是业务聚合
  （`EditFoodScreen` 3 处是输入框过滤数字字符、1 处是历史记录筛选，`StatsScreen:232` 是图表数据转换）。
  ⇒ 剩下的只是加 `WhileSubscribed` 与少量派生数据下沉，**且那是行为改动**（后台不再预热），需逐屏真机复测。
  **执行并入 #10c**（2026-09-18）：与 `AppViewModel` 拆分同批做、共用那一轮复测，本节证据原样有效。
- ⏳ **#7 字符串资源化 + 无障碍** —— `strings.xml` **1** 条 vs 源码中文字面量 **576** 处；
  `contentDescription = null` **50** 处、`semantics` **1** 处。
  （09-16 记的 61 / 7 已随双主题去重下降；本行 09-18 复跑，原写 583 ⇒ **核查第 19 处**，
  该数已进 `tools/doc-metrics.sh` 的比对清单。）
- ⏳ **#8 诊断包 + 许可清单**（**范围已缩小**）—— ⚠️ 旧写法「`corruptedKeys` 没有任何页面消费、用户看不到哪些数据坏了」
  经 09-18 复核**是错的、且写下那天就错**：首页自 09-15 起就有 `DataCorruptBanner` 置顶告警
  （报哪几张表坏了 + 写入已按 key 粒度暂停 + 两个出路：导入备份 / 放弃损坏数据）。仍缺的是
  **诊断包 0 处、许可清单 0 处**；前置 ~~#4/#5~~ **取消** ⇒ 可随时做。详见 [`ROADMAP`](../docs/ROADMAP.md)。
- 🔶 **#9 CI 加固** —— 2026-09-17 用户指定只做「零件过期」与「依赖/密钥检查」两类：
  13 处 action 升级已改好、patch 已验证可用（`git apply --check` + `git diff` 核对），
  但沙箱的 GitHub App 令牌缺 `workflows` 权限 ⇒ **推不上去**，存为
  `docs/audits/2026-09-17-ci-actions-upgrade.patch` 待用户自己应用（详见 09-17 §10）。
  密钥泄漏已本地全历史扫描（207 提交）**确认干净**，故 gitleaks / zizmor / 依赖校验清单
  经成本收益复评**建议不做**（理由见 09-17 §10）。
  仍挂着未做：`paths-ignore`、APK 体积基线、`bundleRelease`(AAB)、
  `versionCode` 改用仓库内版本文件（现为 `github.run_number`，`release.yml:112`）。

- 🔨 **#10 UI/VM 层拆分收尾（`SettingsScreen.kt` 1,705 → **297** 行 + `AppViewModel.kt` 700 → 590 → **469** 行）** —— **10a 已落地（2026-09-18，10a-1 + 10a-2）+ 10b-1 / 10b-2 已落地（2026-09-19）**：弹窗区（352–986，跨 635 行）逐字搬到同包 `SettingsBackupDialogs.kt`(233) / `SettingsCloudDialogs.kt`(368) / `SettingsSnapshotDialogs.kt`(207)；**10a-2** 再把两套 body 与两个 MD3 专用小组件搬到 `SettingsBodyMd3.kt`(260) / `SettingsBackupMd3.kt`(222) / `SettingsBodyMiuix.kt`(219) / `SettingsMd3Widgets.kt`(184)，入口只剩装配（297 行）。两轮都是行为零改动 ⇒ 都不占用复测；⚠️ 10a-2 落地后 CI 红了**两轮**（首轮 5 个文件漏 41 条 import、次轮入口漏 `getValue`/`setValue` 2 条**委托算子**，修法都只动 import 行；见 devlog §13.10 / §13.11）；**10b** 已搬完 2 / 7 个领域（备份 9 + 云端同步 4 = **13 / 50** 个函数 ⇒ `AppViewModelBackup.kt` 149 行 / `AppViewModelCloud.kt` 167 行），剩 5 个领域 37 个函数；**10c（= #6）未开工**。
  **含 #6 的执行**（10c = 加 `WhileSubscribed`），因为两者动同一片代码、共用一轮真机复测。
  10a（设置页：645 行弹窗区抽出 + 两个 body 抽出）与 10b（VM 的 50 个函数按领域搬成同包 `internal` 扩展函数，与 #5c 同形）
  是**结构搬运、不改行为 ⇒ 不单独占用用户复测时间**；10c 是行为改动（后台不再预热、冷进页面首帧可能等一次解码）
  ⇒ 一屏一个提交，最后一轮复测。10b 的爆炸半径实测是 **10 个调用方文件 / 56 个站点 / 约 51 行 import**
  （不是「31 个文件引用 VM」那个粗口径；⚠️ 那是 50 个函数**全搬**的口径 —— 10b-1 的领域 1（9 个函数）实测只有
  **1 个**跨包调用方 `SettingsState.kt` 适配器 / **8** 条 import），且 `CorruptGuardTest`（按 4 空格缩进签名抽函数体）与
  `SnackbarCopyTest`（按全仓递归统计文案分布）**必然要同批镜像** —— `tools/guard-mirror.py` 查不到这两类。
  ⚠️ **10b-1 实测修正**：`CorruptGuardTest` 确实同批改了（1 个测试方法：读两个文件 + `indent = 0` + 新签名字面量），
  而且**是 `guard-mirror` 报出来的**（字面量那条它查得到）；查不到的是「按缩进截函数体」那类（靠 CI）。
  `SnackbarCopyTest` 领域 1 **不用动** —— 它唯一那条 VM 期望属领域 2（云端同步）。
  **10d 延后、不排期**：真·一屏一 VM（KernelSU 那套；要动 10 个调用方 + 导航作用域，批量选择/撤销/snackbar 通道是跨屏共享的）、
  两版 body 抽 App 级「设置行」组件、4 对手写弹窗收敛进组件层（后两条 09-16 已判定「不硬并」，
  理由在 `SettingsScreen.kt` 文件头 KDoc）。**10e 可选**：`EditFoodScreen.kt` 659 行按表单区块抽子组件（0 处主题分支，守卫面小）。
  ⚠️ 已判定**不做**的两件事：① 按功能分包（`ui/screen/settings/` 那种）—— `ImeHandlingTest` 按完整路径点名、
  `ScreenParityTest` 只遍历 `ui/screens/` 一层且反向断言入口文件必须还在 ⇒ 搬包会红 + 静默失覆盖，
  代价换目录形状不值；② 拆 App 级组件层那 5 个文件（`AppChrome` 479 / `AppListRow` 406 / `AppControls` 376 /
  `AppText` 349 / `AppSurface` 353）—— 吸收主题分支正是它们的职责。
  证据、切法与守卫清单见 [`ROADMAP`](../docs/ROADMAP.md) #10 与 [2026-09-18 日志](2026-09-18.md) §13（10a-1 首轮 CI 红与修红见 §13.8：别名 import 是搬运脚本的盲区，已固化成 `kt-lexcheck` 判据 4）
  （含 KernelSU manager 的逐文件对照：他们 219 个 `.kt`、**0** 个测试文件、每屏两份主题实现且两版体积差 4–6 KB 已在漂移）。

### 代码级（中）

- `photoPath` 存绝对路径 + `File.exists()` 在组合期同步调用（`FoodAvatar.kt:43`、`EditFoodScreen.kt:284`）
  —— 主线程磁盘 IO，且换机/改包名后路径全废。
- `nutstorePasswordFlow` 把明文密码推进 `StateFlow` 常驻内存（Keystore 加密只覆盖了落盘，没覆盖内存）。

### 代码级（低）

- 详情页「吃掉一份」无撤销（`FoodDetailState.kt:48-49`；列表页的减号反而有 6 秒撤销）
- 归档 `take(200)` 静默丢最老记录（`FoodRepository.kt:487` / `:630`）→ 需独立计数器 + UI 提示
- 恢复路径（导入 / 快照 / 云端）无集成测试
- `ImeHandlingTest.codeOnly()` 改走词法状态机 —— 现用块注释正则，不认字符串字面量，
  `SettingsBackupDialogs.kt:106` 的 MIME 通配符会吞掉约 600 行真代码（#10a-1 前在 `SettingsScreen.kt:379`，
  拆分后它与那处键盘避让已不同文件，吞不到一起了，但 `codeOnly()` 本身的毛病还在）；
  `MiuixDialogContentTest` 里已有验证过的实现可抄
- `FoodCard.kt:161/251` 自己分流了一份进度条，与组件层 `AppLinearProgress` **不同构**（6dp vs `LinearProgressHeight = 8.dp`、`weight(1f)` vs `fillMaxWidth()`）⇒ 收编是**视觉改动**而非纯重构，需两主题真机复测（2026-09-17 文档审计时发现）
- ktlint 只开了 6 条规则（见 `.editorconfig`），全量规则集待评估
- `ObsoleteSdkInt` 3 处 · README / 商店截图缺失

### 体验（待评估）

- 统计图表无 semantics（屏幕阅读器读不出数据；MD3 审计遗留）
- Splash 图标 108dp 用低分辨率 drawable，深色模式下发糊
- MD3 对比度档位：medium / high（现只 hardcode 1 档）
- 日历周视图密度 · 自动同步「仅 Wi-Fi」开关 · 删除分类后孤儿记录的批量重新归类入口

### 需用户决策

- **备份规则两面**：① `device-transfer` 放行 `covers/` 却排除 `datastore/` ⇒ 换机后封面变孤儿；
  ② `filesDir/snapshots/` 未被排除 ⇒ 每日快照随系统云备份一起走（占空间）。
  已如实登记进 `ARCHITECTURE.md` + README，**行为未改**，等用户定调。
- `targetSdk 36 → 37`（detekt `OldTargetApi`）· Miuix `0.9.4-rc01` → 稳定版（等上游发版）

### 已决策不做（别再提）

- **Glance 桌面小组件** —— 撞 `REQUIREMENTS.md` §4「明确不做」第 3 条，需用户先推翻边界才能做
- **`WindowSizeClass` / 折叠屏铰链姿态 / 列表页 Medium+ 双栏** —— v2.6 用户确认无折叠屏
  （`840dp` 的 `widthIn` 保留，作为平板上的最大宽度约束）

### 已按用户指示暂缓

- MIUIX 配色入口：`MiuixRootTheme` 已打通种子色通道，但设置页还没给入口
- 剩余统计口径：「本周」文案与实际 7 天窗口不一致 / TOP5 按单位混排 / 分类占比分母

---

## 已完成里程碑（一行一条，细节见对应日志）

**2026-09-17**：`AppNavHost` 形参 9→6、`BatchMoveLocationDialog` 8→5（`df2f61c` 红一次，真因见 §7）·
`ImeHandlingTest` / `MiuixParityTest` 加固为「清单存在性 + canary」（单测 120→121）·
`AppDialogs.kt` → `ui/components/app/AppBatchMoveDialog.kt` · 三键导航下 IME 用户实机复测通过

**2026-09-16（第三批 #1–#3）**：detekt 由 `report` 切 **block**（`tools/ci-gates.sh:52`）+ `detekt_selftest` 防空转 ·
复杂度规则开启：首跑 73 条发现，其中 2 条（`ComplexCondition` / `LoopWithTooManyJumpStatements` 各 1 处）
**改代码修掉、规则保留**，前 4 条体量规则共 71 处**显式关闭**（`LongMethod` 33 / `LongParameterList` 24 /
`TooManyFunctions` 7 / `CyclomaticComplexMethod` 7），理由与命中清单见 `detekt.yml` 文件头· 双主题去重 + App 级组件层：`Miuix*Screen.kt` 双胞胎 **8 对 → 0**
（⚠️ 原验收「4,500 量级 / −24%」**未达成**，根因见 09-16 §22）

**2026-09-15**：`MiuixParityTest` 静态守卫上线 · CI 门禁（ktlint 6 条 + detekt 四规则集 + sha256 校验）

**2026-08-22（无障碍与备份批）**：`contentDescription = null` 112 处补齐（⚠️ 09-17 实测仍余 **50** 处，未清零）·
坚果云备份漏 `covers/` → `nutstore-backup` 加目录 + 恢复校验/迁移 · 导出 JSON 被系统媒体库收录 → 移入应用专属目录 +
清孤儿旧文件 · 坚果云同步失败静默 → 错误分类/诊断/失败留档 · 备份/导出文件不可见 → SAF 导出 + FileProvider 分享 ·
`device-transfer` 无二次确认 → 风险说明弹窗 · 备份无校验和 → `manifest.json` + SHA-256 + 损坏标记 ·
状态容器纯逻辑单测补全（B-07，+16 例）· fix-plan 阶段 6 其余：README / LICENSE（Apache-2.0）·
fix-plan 阶段 7：8 对双实现状态容器抽离（−~1,200 行）

**2026-08-21**：fix-plan 阶段 5 纯函数单测基线（+25 例）· 阶段 6 跨零点刷新（可注入 `today` + `LocalToday`）·
release 签名失效 → 正式签名 + `keystore.properties` 分离凭据，并约定发布说明须注明
「签名已变更，请先导出备份、卸载旧版后再安装」

**2026-08-20**：v2.8 工具链升级本地无法验证 → 改由 CI 每轮 `assembleDebug` + 单测 + lint 验证

**v2.3 – v2.6（MD3 审计与用户反馈）**：归档恢复同名/同 ID 冲突策略（合并数量/防重复）· 移除封面时旧图片未清理
（启动 `cleanupOrphanCovers`）· 消耗记录上限 1000 条裁剪失真（90 天明细 + 月度聚合）· 主题切换颜色瞬切
（`animateColorScheme` 全角色 450ms）· 归档搜索结果的恢复操作无撤销（全链路 6 秒撤销）·
`CheckSwitch` 缺 `Role.Switch` · <48dp 触摸目标 · shapes 未 token 化 · 图表色不随主题 ·
悬浮导航未选中项无标签 · 槽位 44dp→48dp · 转场线性 tween → `Motion.kt` 统一 MD3 缓动

**坚果云**：同步无自动定时 → 启动时按间隔自动上传（关/每天/3 天/每周）· 应用密码明文存储 →
Keystore AES-GCM 加密 + 旧数据自动迁移

---

**单测数演变**：91（09-15 中途）→ 116 → 117 → 119 → 120（09-16/09-17）→ **121**（09-17，现值）。
此前这几个数字在本文件里被逐层加注修正过三次，现已收敛为一行；各时点的口径见对应日志。
