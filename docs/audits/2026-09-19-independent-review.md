# 独立评审（2026-09-19）：结构 · 架构 · 代码

> ## 📌 状态批注（同日，2026-09-19）
>
> **M1 五条已全部落地**（用户批准「可以做」后逐条提交，见 `devlog/2026-09-19.md` §14 与各笔提交信息）：
> P0-1 保存即返回 + 封面原子写（`upsertAndAwait` / `.tmp-` → `renameTo`）、P0-2 归档静默截断（`ARCHIVE_RETENTION`
> + `archive_overflow_total`）、P0-3 备份通道（拆 `credentials_store`，业务数据回到备份里）、
> P0-4 `SecureStore` 建钥竞态（含"读侧不建钥"）、M1-5 本地编译可判定（`tools/bootstrap-build-env.sh`）。
> ⚠️ **本轮结论的效力随之变化**：文中把这五条写成"现状缺陷"的段落保留原文（时点记录，不改写），
> 但读的时候请配合同日 §14。**P0-5（这几条旅程零 UI/instrumentation 测试）没有解决** ——
> 本轮新增的 23 条单测按性质分：**读源码文本的配置守卫 11 条**（`BackupRulesTest` 4 + `EditFoodSaveGuardTest` 5 +
> `ArchiveRetentionTest` 1 + `CorruptGuardTest` 1）、**真 DataStore 行为 8 条**、纯函数 3 条、常量形状 1 条
> （分类见 `devlog/2026-09-19.md` §14.7；守卫为什么必须读文本的理由写在各自文件的 KDoc 里）。
> Robolectric / Compose UI Test / Macrobenchmark 仍是 0（`doc-metrics` 的「UI 测试」那行为准）。
> P1 / P2 全部未动，其中**判为"应先于一切结构项"的那条根因**（沙箱无编译器）只做到了"可判定"，
> 没有做到"可编译"。§10 对流程纪律的批评与"两条机械计数器 + 文档不得反向塑形源码"的建议一并保留待议。
>
> 评审对象：`master` @ `f302138`。口径：以 Android 官方当前建议为基准（Guide to App Architecture、DataStore / Room 选型、
> AGP 9.x 发布说明、Android 16（API 36）行为变更、Play Target API 政策 2026-08-31、Baseline Profiles、
> Compose 性能与稳定性、Material 3 1.4.0 关于 Material Icons 的弃用说明）。
> 本文只写**独立核对过的**结论：每条都给 `file:line` 证据或构建脚本片段；仓库自有审计（`docs/audits/*`）已列过的项目
> 只标注状态（✅已修 / 🟡部分 / ❌未做），重点放在**它们没列出、或列了但定性偏轻**的问题上。

---

## 0. 总体判断

| 维度 | 评价 | 一句话 |
|---|---|---|
| 工程规范 | ★★★★★ | 工具链现代（AGP 9.3.1 / Gradle 9.7.1 / Compose BOM 2026.08 / minSdk 26 / target 36 / compileSdk 37）+ Version Catalog + lint/ktlint/detekt 真门禁 + CI 三 job。这是同类个人项目里少见的水平。 |
| 分层 | ★★★★☆ | 单 Activity + Compose + VM + Repository 的骨架是对的，UDF 方向也没跑偏；扣分在"层是画出来的、不是切出来的"——单模块 + 上帝 VM + 仓库拆扩展函数。 |
| 架构落地 | ★★★☆☆ | 19 个 `stateIn(Eagerly)` 的全局 VM、`viewModel` 被塞进 34 个 composable、用"同包扩展函数"做伪 partial class：都是**为了指标而不是为了设计**的形状。 |
| 数据层 | ★★☆☆☆ | DataStore Preferences 存"用户资产型"关系数据 + 整表 JSON 读写，是本项目大量复杂度（DecodeCache、三态 Decoded、写守卫、200 条截断）的**根因**；系统备份被整份排除，等于没有备份。 |
| 健壮性 | ★★★★☆ | 损坏态拒写 + 原文留档 + 三条恢复路径先快照 + CSV 公式注入防护 + 聚合记录拒删：这些是真功夫，比多数商业 App 细。 |
| UI / 性能 | ★★★☆☆ | 双主题合并到组件层是正确的方向；但 4 页常驻 Pager、36 路颜色动画、组合期 `File.exists()` 三处叠加，把首帧与切主题的成本买回来了。 |
| 无障碍 / 国际化 | ★★☆☆☆ | `strings.xml` 只有 1 条，565 处中文字面量在代码里；日历紧急度只有颜色编码；`contentDescription = null` 50 处未复核。 |
| 测试 | ★★★☆☆ | 23 个纯函数/守卫测试很扎实；但 0 个 UI 测试、0 个 VM 测试、0 个 instrumentation 源集，而"双主题一致性"用 grep 源码来守。 |
| 发布就绪度 | ★★★☆☆ | target 36 已达标；但只出 APK（Play 新应用要求 AAB）、无 Baseline Profile、`versionName` 在 UI 上恒为 `v1.0`。 |

**一句话**：这是一个**流程纪律远好于架构决策**的仓库。它已经把所有"能被脚本量到"的东西做到位了，剩下的风险集中在三件脚本量不到的事上：备份通道、持久化选型、以及"保存即返回"的写路径。

---

## 1. 规模快照（实测口径）

| 指标 | 值 | 命令 |
|---|---|---|
| 主源 Kotlin / 行数 | 112 文件 / 16,137 行 | `find app/src/main -name '*.kt' \| xargs wc -l \| tail -1` |
| 单测 | 23 文件 / 3,796 行（纯 JVM，无 `androidTest` 源集） | `ls app/src` |
| `@Composable` 函数数 | 174 | `grep -rn "@Composable" app/src/main --include=*.kt \| wc -l` |
| 其中带 `modifier: Modifier = Modifier` | 44 | 同上 |
| `@Preview` | 1 | — |
| VM 的 `stateIn(...)` | 19 处，**全部** `SharingStarted.Eagerly` | `grep -c "stateIn(" viewmodel/AppViewModel.kt` |
| 屏幕直接接收 `viewModel: AppViewModel` 形参 | 34 处 | — |
| 中文字面量（用户可见文案） | 565 处；`res/values/strings.xml` = 1 条 | — |
| Markdown | 51 文件 / 11,368 行（其中 `devlog/` 单日最长 1,161 行） | — |
| release 产物 | 2.4 MB（他们 09-15 CI 实测），debug 63 MB | — |

---

## 2. 做得好的、值得保留的部分

这些不是客套，是我逐条核对后认为**改错了会更糟**的地方：

1. **单 Activity + Compose + miuix-nav `NavDisplay`** 只在 `AppNavGraph.kt` 一处，返回栈状态源在 `MainApp.kt` 一处，二级页只拿回调 —— UDF 的骨架是真的，不是文档里的。
2. **`collectAsStateWithLifecycle` 72 处、`collectAsState()` 0 处**（`grep -c` 实测）。这一条很多团队都没做到。
3. **权限极简**：Manifest 只有 `INTERNET`；相册走 `ActivityResultContracts.PickVisualMedia()`（`EditFoodScreen.kt:111`，Photo Picker，无需 `READ_MEDIA_IMAGES`），拍照走 `TakePicture` + FileProvider 且临时文件落在 `cacheDir/camera/`；`uses-feature camera required=false`。这是官方 privacy-best-practices 的教科书写法。
4. **`file_paths.xml` 已从 `path="."` 收窄**到两个子目录（但见 §6.3：还能再收一格）。
5. **数据损坏三态**（`Decoded.Ok/Empty/Corrupt`）+ 拒写 + 原文留档 + `pruneCorruptDir` 限量 + 损坏态跳过孤儿封面清理（`AppViewModel.kt` init 里那段注释解释了"否则等于销毁数据"）——这套判断质量高于绝大多数同类项目。
6. **CSV 公式注入防护**（`CsvExport.escapeCsvField`）、**导入前预览条数 + 快照**、**聚合记录拒删**（`isDeletable()` 仓库层兜底）。
7. **`isReturnDefaultValues = true`** 的注释写明了为什么开（守卫路径碰 `android.util.Log`）——测试配置有据可依。
8. **CI 质量**：`permissions: contents: read` 显式最小化、`concurrency.cancel-in-progress`、debug 与 release 都跑 lint、R8 产物与 `mapping` 目录报告上传、release 签名凭据缺失时**拒绝静默降级**（`gradle.taskGraph.whenReady` 的拦截 + 产物侧 `apksigner` 二次核验）。
9. **detekt 自测（`detekt_selftest`，ci-gates.sh）**：发现并修掉了"`--config` 不带 `--build-upon-default-config` 导致门禁长期空转"——这条自我纠错比门禁本身更有价值。

---

## 3. 结构分层：形状对，边界是假的

### 3.1 目录与包

`data/` `viewmodel/` `ui/theme` `ui/components` `ui/components/app` `ui/screens` `ui/navigation` 的划分是清楚的，`data/` 无 UI 依赖（`grep` 未发现反向 import），这是主要加分项。

但**模块边界为零**：单 `:app` 模块里，`internal` 的语义就是"全 App 可见"。于是出现了一批"用注释维护的边界"：

- `AppViewModel.kt:74` `internal val repo = container.repo` —— 屏幕层**拿得到仓库**（当前 0 处使用，实测 `grep "\.repo\b"` 在 `viewmodel/` 外无命中）。这是运气不是机制。
- `RepositoryCore.kt` 顶部把"为什么不选门面转发"讲得很细，代价写得很诚实：**为让同包扩展够得着，`dataStore` / `corruptDir` / `clock` / `json` / `decodeCache` / `_corruptedKeys` 全部从 `private` 放宽为 `internal`**（`AppViewModel.kt` 那段"13 处放宽"同理）。放宽的副作用他们自己记了："`private` 放宽 ⇒ detekt 的 `UnusedPrivateMember` 从此不覆盖它们"。
- 结果：**类的真实规模从工具视野里消失了**。`AppViewModel.kt` 277 行"🎯 < 400 达标"，但同一个类的领域函数散在 7 个 `AppViewModel*.kt` 里；`FoodRepository` 252 行 + 6 个领域文件 + 一个 `RepositoryCore`。IDE 的 Find Usages、重构、detekt 的 `LargeClass`/`TooManyFunctions` 全部只看单文件 ⇒ 这类"把复杂度搬出度量窗口"的拆分，**度量变好、认知负担不变**。
- 更值得警惕的是**决策动因**：`AppNavGraph.kt` 的 `AppNavCallbacks` 是为了让 101 行 `git diff -w` 保持为空而生的；`private const val TAG` 在 8 个文件里各存一份，理由写在注释里是"本地没编译器，撞名的赌不划算"；`doc-metrics.sh`（46 KB）用正则数注释里的 `now()` 出现次数，于是**代码注释被要求避免某种自然写法**。
  > 这三件事在同一类项目里通常是"过程改进"的副产品，这里已经反过来约束代码形状了。建议明确一条红线：**文档与守卫不得成为代码结构的输入**（详见 §10）。

### 3.2 建议的最小改法（不做大爆炸重构）

1. 把 `ui/components/app/`（12 文件，纯主题骨架、零业务）抽成 `:designsystem` 模块 —— 它天然满足"稳定 API + 无业务依赖"，抽出去后 `internal` 边界第一次真正生效，`App*` 组件的 public API 会强迫在编译期收敛。
2. `:app` 依赖 `:designsystem`、`:data`（`FoodModels` + `RepositoryCore` + 各领域文件 + `data/` 其余）、`:domain`（新）；`:baselineprofile` / `:benchmark` 各自一个 `com.android.test` 模块。
3. 如果不想动模块数：**至少把 `AppViewModel.repo` 删掉**。屏幕不该看见仓库；现在 0 处使用，删除成本为零，收益是把一条"迟早被走"的路堵死。

---

## 4. 架构：与官方《App 架构指南》的差距

### 4.1 上帝 ViewModel + `Eagerly`

官方 UI State 文档推荐：`Flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`。本仓 19 处全是 `SharingStarted.Eagerly`（`AppViewModel.kt`），并把这轮改造登记为 ROADMAP `#10c`、随后**由用户决定搁置**（`docs/ROADMAP.md`）。

技术后果（按当前代码形状是真实的）：

- 主题色、`floatingNav`、坚果云三件套、`last_sync_time`……**与"用户当前在哪个屏"无关，全程常驻收集**；`itemsFlow` 一有变化，19 个下游全部被驱动一遍，而其中大部分屏幕此刻并不在组合里。
- 更实质的问题：`Eagerly` + 单一 Activity 作用域 VM = **VM 的生命周期被人为拉到进程级**，于是 `onCleared()` 基本不会发生。这直接掩盖了 §6.1 的丢写风险（见下），也意味着任何"按屏回收订阅"的想法都会被这层挡住。
- 他们的搁置理由是"行为改动、要真机复测一轮、要先加固"。**这个理由成立的前提正是上帝 VM**：如果 `FoodListViewModel` / `SettingsViewModel` / `StatsViewModel` 各自持有自己那几个流，`WhileSubscribed` 是逐屏可验证的小改动，不需要"一整轮加固"。

> 结论：`#10c` 不是"暂缓的性能优化"，而是"缺了按屏 VM 分层导致的连带欠账"。建议把它放回队列，但**排到按屏拆分之后**，而不是单独硬啃。

### 4.2 一次性事件：`Channel` × 4，方向对，两处细节

`UiEvent` 从"可空 `StateFlow` + 手工 consume"改成 `Channel`（接收即出队）——这是官方《ViewModel 一次性事件》方向上的正确选择，且"事件只带数据、不带回调"的约束写在了 `UiEvent.kt` 里，很好。

两点补充：

- `Channel.BUFFERED`（64）+ `emit()` 挂起：`maybeAutoSync()` 在 `init{}` 的协程里 `emit`，若对应宿主（首页）从未组合，队列满后**这条 `viewModelScope.launch` 会永久挂起**。今天它正好是 `init` 的最后一步所以无感，但这是"靠顺序维持的正确性"。建议：全局提示（同步完成/失败）统一落 `appShellEvents`（主壳常在），或改用 `Channel(capacity = 32, onBufferOverflow = DROP_OLDEST)`。
- `MainApp.kt` 的 `when` 里那 4 条 `-> Unit` 分支是 sealed 穷尽的代价。它们为此写了显式注释说明"为什么必须列出来"。更省心的做法是把事件按宿主拆成 4 个 sealed 子类型（每屏收自己的类型，穷尽天然成立），或干脆一个 `Flow<UiEvent>` + `filterIsInstance`。属于口味问题，可以不改，但**别忘了这是自己选的复杂度**。

### 4.3 DI 与可测性：`AndroidViewModel` + 硬转型 + 服务定位器

现状：`class AppViewModel(application) : AndroidViewModel`，构造里 `(application as ChiliMeApp).container`，并在 KDoc 里写了一段"**构造签名必须保持 `(Application)`，不能加带默认值的第二参数**，否则 `ViewModelProvider` 的默认工厂反射找不到构造器"。

这段注释本身就是答案：**他们在绕一个只有手写工厂才需要绕的坑**。

- 官方指南（Architecture → Dependency Injection）明确建议用 DI（Hilt / Koin）或用 `ViewModelProvider.Factory` 手工提供，理由就是解耦创建点、可测性。
- 现在 `AppViewModel` **完全不可单测**（需要 `Application` + 真 `ChiliMeApp` + 真 DataStore），实测 `app/src/test` 里 VM 只有 `UiEventTest`（读 `UiEvent.kt` 的纯类型）。撤销状态机、批量归档、`maybeAutoSync` 全部裸奔——这恰好是他们自己 P2-5 承认的缺口。
- 改法（不需要 Hilt，保持"轻量手写"口径）：
  ```kotlin
  class AppViewModel(private val repo: FoodRepository, private val clock: Clock) : ViewModel() {
      companion object {
          val Factory = viewModelFactory {
              initializer {
                  val app = this.create(Application::class.java) as ChiliMeApp
                  AppViewModel(app.container.repo, app.container.clock)
              }
          }
      }
  }
  ```
  `viewModel(factory = AppViewModel.Factory)`。收益：`(Application)` 反射约束消失、硬转型崩溃路径消失、VM 可用假仓库在 JVM 下测、`onCleared` 恢复正常语义。KDoc 里那三条"为什么不能用带默认值的构造参数"可以整段删掉。

### 4.4 领域层

官方指南的口径是"按需（as needed）"，所以**没有** domain 层对这个体量是可以接受的。但有一个例外值得单独立项：`statusForAt` / `daysLeftAt` / `elapsedRatio` / `compactConsumptionAt` / `planRestore` / `calculateWastedTotal` 这些**已经存在**的纯函数，现在住在 `data/FoodModels.kt`（293 行，模型 + 派生 + 业务规则 + 纯函数混装）。把它们挪进 `domain`（或至少 `data/` 里独立成 `ExpiryRules.kt`）能立刻做两件事：① 让"件 vs 条"这类口径规则有唯一归属；② 让 `StatsStateTest` 不再"测 UI 文件里的纯函数"（他们 P1-11 已经承认位置别扭）。

---

## 5. 数据层：这里集中了本项目最贵的三个决策

### 5.1 DataStore Preferences 存关系型用户资产 —— 官方建议直接对撞

官方 DataStore 文档原话（2026-09 版仍如此）：
> "If you need to support large or complex datasets, partial updates, or referential integrity, consider using Room instead of DataStore. DataStore is ideal for small datasets and does not support partial updates or referential integrity."

现状：`preferencesDataStore("pantry_store")` 里 19 个 key，其中 `food_items` / `archived_items` / `consumption_records` / `history_entries` 是**整表 JSON 字符串**。于是：

- 任何一次"吃掉一件"都要：读全表 → 解码（有 `DecodeCache` 兜）→ `map` 改一条 → **整表 encode → 整文件重写**。`upsert` 更明显：一次保存同时重写 `food_items` 与 `history_entries`（`FoodItems.kt:52-80`）。
- 为了绕开"整表"的副作用，仓库里长出：`DecodeCache`（`rawFlow` 去重 + 缓存解码）、`distinctUntilChanged`、`flowOn(Default)`、`resilientRead` 重试/回落 —— 这一整套是**在给一个不适合的存储层打补丁**。他们自己的 P1-2 也是这个结论（"是全部复杂度的根源"），但状态仍是 ❌ 未做。
- `take(200)`（`FoodArchive.kt:28`）：归档超过 200 条后**每次写入静默淘汰最旧的**，`archiveItems` 返回 `Unit`，UI 无从提示。加上 `history.take(50)`。这是**静默丢用户数据**，而且已经在污染统计（`StatsState.kt:68` 自己承认"过期浪费仍受 `take(200)` 影响"）。
  > 我把它定级为 P0 而非 P1：一个"每天吃零食"的用户在 3~6 个月内必然触达 200，而且丢失的是"历史"这一不可再生数据。最小止血（不换存储也能做）：`ArchiveWriteResult(dropped: Int)` 返回被丢弃条数 + UI 明示"归档已满，N 条更早记录被移出，建议导出备份"；同时把 `take(200)` 提到 `take(1000)`（JSON 体积仍在 MB 级以内），并把浪费统计改为**独立累计计数器**（一个 `intPreferencesKey("wasted_total")`，与归档列表解耦）。
- 建议路线（分两步，都不是大爆炸）：
  1. **短期**：`FoodItem` 从"整表 JSON"改为 `dataStore` 里的**每条一个 key**（`item:<id>`）+ 一个 `index` key？——不，这条不要做，DataStore 没有前缀查询，会更糟。**正确的短期动作是**：把 `version` 写进 payload（现在只有备份文件有 `BACKUP_VERSION = 2`，DataStore 里的 JSON **没有 schema 版本**，加字段时无法判断该跑哪条迁移），并把 `Json { ignoreUnknownKeys = true }` 补上 `explicitNulls = false`，避免未来加可空字段时老数据解码行为漂移。
  2. **中期（推荐）**：迁 **Room**。三张表（`items` / `archive` / `consumption`）+ 一张 `settings` 留 DataStore。Room 一次性解决：部分更新、`take(200)` 截断、`planRestore` 去重（改成唯一索引 + `OnConflictStrategy`）、`suggestionSource` 的三表拼接（改成一次 `SELECT`）、跨零点刷新（改成查询参数）、以及**可测性**（`Room.inMemoryDatabaseBuilder` + Robolectric/`RuntimeEnvironment`）。迁移用一份 `Migration` 从 DataStore 读旧 JSON 写入 DB + 保留旧 key 一个版本做回滚。**这一条是本项目 ROI 最高的单项改动。**

### 5.2 备份：为了护一个密钥，把全部用户数据排除在备份之外

`backup_rules.xml` 与 `data_extraction_rules.xml` 两条通道都 `<exclude domain="file" path="datastore/"/>`。理由写在注释里（Keystore 密钥不跨设备，恢复了也解不开）——理由对，做法错。官方 DataStore 文档对此有明确建议：

> "If your DataStore contains non-sensitive preferences (such as user theme or feature flags) alongside sensitive data, **separate them into distinct DataStore files** and configure `res/xml/data_extraction_rules.xml`."

后果（今天就是如此）：**用户换机 / 重装后库存全空**，只能靠他手动导出过 JSON 或去坚果云点过同步。而 `README` 的对外表述是"本地优先 / 每日快照随系统备份进入你自己的 Google 账号"——快照确实在（`filesDir/snapshots/` 未被排除，这点他们自己标了"已知不一致"），但**主数据不在**，等于把兜底通道和主通道搞反了。

改法（半天量级，收益是"重装不丢数据"）：

1. 拆两个 DataStore：`pantry_store`（库存/归档/消耗/历史/设置）+ `credentials_store`（`nutstore_account` / `_password` / `_password_enc`）。
2. `backup_rules.xml` / `data_extraction_rules.xml` 只排除 `datastore/credentials_store.preferences_pb`（DataStore 文件在 `filesDir/datastore/` 下，`<exclude>` 支持带文件名）。
3. `restoreArchived` 之后补一句：`nutstoreCredentialBrokenFlow` 现在同时承担"换设备"与"真的坏了"，拆分后仍然准。
4. 顺手解决他们记在 `ARCHITECTURE.md` 的第二个不一致：`device-transfer` 放行 `covers/` 却排除 `datastore/` ⇒ 换机后一堆封面被 `cleanupOrphanCovers` 当孤儿删掉。拆 DataStore 后 `covers/` 与库存同时到位，这条自动消失。
5. **另外**：`README` 里"数据默认只存本机"这句要么补上"快照会随 Android 系统备份进入你的 Google 账号"，要么把 `snapshots/` 也排除掉。目前是文档与行为不一致，用户按 README 做的隐私判断不成立。

### 5.3 凭据与网络

- ✅ Keystore AES-GCM、IV 每次新、tag 128 位、失败返回可空（不覆盖旧密文）、明文回退有 UI 告警、下次启动重试迁移 —— 这套设计是对的，比直接上 `security-crypto` 更可控。
- ❗ **`SecureStore.getOrCreateKey()` 无并发保护**（`SecureStore.kt:24-38`）：`getEntry == null` 与 `generateKey()` 之间没有锁，两个协程同时进入 → 后者 `KeyAlreadyExistsException` → 被 `runCatching` 吞成 `null` → 上层判成"加密失败"→ 落明文 + 误报 `nutstorePlaintextFallback`。修法：`catch (KeyAlreadyExistsException) { 重读 entry }`，或整个 `getOrCreateKey` 包一层 `Mutex`。
- ❗ **明文密码被解密后灌进 UI 状态并回填输入框**：`FoodRepository.nutstorePasswordFlow` 把密文解成明文常驻 `StateFlow`，`SettingsState.kt:198-205` 的 `fillCredentialInputs(account, password)` 再把它写进 `mutableStateOf` 让 `TextField` 显示。好在没用 `rememberSaveable`（否则会进 saved state），`PasswordVisualTransformation` 也给了（`SettingsCloudDialogs.kt:91-92`）。但官方 secret-handling 的口径是"不要把已存密钥预填进输入框"。建议：只回填账号，密码留空 + "已保存，如需修改请输入新密码"；并顺手用 Compose 1.12 新增的 `credentialRequest` 语义（Credential Manager / Autofill）让用户可以选择保存/自动填充，比自己存更合规。
- `NutstoreSync` 用 `object` + 单例 `OkHttpClient`（共享连接池，正确），`withContext(Dispatchers.IO) { execute() }` 也可接受。三点可改进：① `object` 不可注入 → 无法在单测里换掉网络（他们只能测 `parsePropfind`，就是这个原因），建议改成 `interface CloudBackupGateway` + 一个 OkHttp 实现，由 `AppContainer` 提供；② 上传/列取不随 VM 取消（切页面后协程仍跑完，`ensureDir` + `PUT` + `DELETE` 一串）→ 至少把 `runCatching` 换成 `currentCoroutineContext()` 的 job 取消传导（`okhttp-coroutines` 的 `call.await()` 原生支持取消，见 §7.3）；③ 轮转删除 `runCatching { }` 完全静默 → 失败会在下次上传继续堆积，建议失败计数 + 下次列表时提示。

---

## 6. UI 层（Compose）与两条被低估的正确性问题

### 6.1 P0：保存后立即返回，写协程可能不被执行完

```kotlin
// EditFoodScreen.kt:182-184
viewModel.upsert(item)   // = viewModelScope.launch { repo.upsert(item) }   ← 火忘
onBack()                 // 立刻弹栈
```
`AppViewModelFood.kt:83` 是所有写入口的形状（`upsert` / `archiveBatch` / `changeQuantity` / `deleteArchived` … 全部 `launch` 后返回 `Unit`）。

今天它**没有**表现为丢写，原因只有一个：唯一的 VM 是 Activity 作用域的上帝 VM，`onCleared()` 不会在弹栈时发生。这个 bug 是被"架构问题"遮住的。于是有两个必须现在处理的事实：

1. 任何一次"按屏拆 VM"（本项目 Roadmap 明确想做的事）都会**立刻把它变成真丢写**；
2. 更近的：`copyImageToCovers` 跑在 `rememberCoroutineScope()`（`EditFoodScreen.kt:114/129`）—— 组合离开即取消。选照片后立刻返回，JPEG 可能写到一半（`FileOutputStream.use` 被取消 → 留下截断文件），而 `File.exists()` 判定为真 ⇒ 永久显示坏图。

建议改法（小、明确、可测）：

- 写路径改成 `suspend`，由调用方 `scope.launch { viewModel.upsert(item); onBack() }`；或 VM 提供 `suspend fun save(item): SaveResult`，返回后再导航。**保存后必须等写盘完成**是官方 UDF 的硬要求（"the ViewModel 只在自己的作用域内做异步工作 ⇒ 不要依赖作用域存活"）。
- 图片落盘搬进 VM（`viewModelScope` + `Dispatchers.IO`），并把写文件改成 `tmp → renameTo(out)` 原子化，坏图问题一并消失。
- 这两条改动**不需要**先拆 VM，可以先做，属于低成本高回报。

### 6.2 三处叠加的首帧/切主题成本（都是他们已登记但未做的项）

| 位置 | 现状 | 建议 |
|---|---|---|
| `NavChrome.kt:122-126` | `HorizontalPager(userScrollEnabled = false, beyondViewportPageCount = 3)` ⇒ 4 个 Tab **全部常驻组合**，且用户根本不能左右滑 | `Crossfade`（或 `AnimatedContent` + `animateEnterExit`）。P1-6 ❌未做。 |
| `Theme.kt:39-87` | `animateColorScheme` 对 36 个角色各开一个 `animateColorAsState(450ms)` ⇒ 动画期间每帧新建 `ColorScheme` ⇒ 全屏重组 450ms | 只对**少量**用户能感知的角色做动画，或整个主题用 `Crossfade` 过渡；P1-7 ❌未做。 |
| `FoodAvatar.kt:43`、`EditFoodCoverSection.kt:67` | 组合期同步 `File(path).exists()`（列表每帧每行一次 syscall）；`photoPath` 存**绝对路径** | 路径只存 `covers/<uuid>.jpg` 的 basename + `AsyncImage` 的 `onError` 回落 emoji；P1-8 ❌未做。 |

单条都"可以接受"，**三条叠在同一个应用上就不可接受**：切一次主题 ≈ 4 屏 × 全部卡片 × ~27 帧，每帧每行还各做一次 `stat()`。建议把这三条打包成**一个 PR 系列**（他们的 P1 系列惯例正适合），一次解决，别继续拆成七轮搬运。

### 6.3 其他 UI 层条目

- **Compose API 指南**：174 个 `@Composable`，只有 44 个有 `modifier: Modifier = Modifier` 形参。官方《Compose API guidelines》把"public composable 必须收 modifier"列为硬性项。`ui/components/app/` 那 12 个文件是**自己造的设计系统**，最该补齐的就是它（否则业务无法在屏幕侧修边距，只能改组件）。
- **`@Preview` 只有 1 个**。双主题 × 深浅色 × 15 套配色 = 现在完全靠"真机复测"来验证（`CLAUDE.md`/ROADMAP 里到处是"已请用户真机过一遍七条"）。建议：给 `App*` 组件补 `@Preview` + `@MultiPreview`，并引入 **AGP 的 Compose 截图测试**（`com.android.compose.screenshot`，`ExperimentalScreenshotTest`，纯 JVM 运行、CI 友好）——这正好能替换掉现在用 `grep` 源码维护的 `ScreenParityTest`：一致性用像素比对守，比"两个文件里有没有同名函数"强得多。
- **Lazy 列表**：`key =` 覆盖得不错（18/19），但 `contentType` 全仓 0 处。`FoodListScreen.kt:206` 与 `:242` 在同一 `LazyColumn` 里混排 `FoodCard` 与归档行（还有 `item(key = "archive_header")`），正是官方文档要求标 `contentType` 的典型场景（回收复用）。同时 5 处 `items(...)` 没有 `key`（`unitOptions` / `shelfLifePresets` / 筛选 chip 等）——静态小列表问题不大，但 `EditFoodNameSection.kt:89` 的 `suggestions` 会被"一键回填"改写，建议补 key。
- **无障碍**：`contentDescription = null` 50 处（他们 P2-7 记的是 61 处，方向正确）；`semantics` 仅 7 处。具体缺口：`ExpiryCalendar` 的日期格用**纯颜色圆点**表达紧急度（`ExpiryCalendar.kt:344-350`），有图例文字但**单元格本身没有任何语义** ⇒ TalkBack 用户只读得到数字，色觉障碍用户同样丢信息（官方 a11y 硬指标"不得仅用颜色传达信息"）。给日格加 `semantics { contentDescription = "15 日，临期 2 项" }` 即可，成本很低。另外 `QuantityStepper` 这类"加减"控件建议用 `Modifier.toggleable` / 显式 `Role.Button` + `stateDescription`。
- **国际化**：565 处中文字面量、`strings.xml` 1 条，且 `detekt.yml` 说明里把 `SetTextI18n` 显式 disable 了。技术上没问题（这是单语言产品），但有两个副作用：① `contentDescription` 之类的**无障碍标签**也一并进了代码，任何后续本地化都要动 `.kt`；② 字号放大 200% 时，硬编码在 `AppText.kt` 的 13 档语义字号（好在它映射的是两主题的 `Typography`，所以系统 `fontScale` 是生效的——这条我给好评）与 `Modifier` 上的固定 `height`/`width` 会打架，建议做一次 200% 字体 + 最长文案（德语/英语）的布局压测，把固定高度换成 `heightIn(min=…)`。
- **Manifest 杂项**：`android:enableOnBackInvokedCallback="true"` 在 `<application>` 与 `<activity>` 各写一遍 —— targetSdk 36 起预测性返回**默认开启**，两处都已冗余（留一处或都删；`false` 只在需要临时退出时用，见 Android 16 行为变更文档）。真正的缺口是：`BackHandler`（`FoodListScreen.kt:74`、`NavChrome.kt:121`）在新机制下**能工作但没有系统手势预览动画**；官方推荐用 `PredictiveBackHandler`（`androidx.activity:activity-compose`）接管"退出多选/关弹窗"这类可取消场景，Compose 1.12 的 `DeferredAnimatedContent` 正是为跟手预览准备的。至少把这一条写进 ROADMAP，别等用户反馈"手势怪"。
- **`postSplashScreenTheme` = `@android:style/Theme.Material.Light.NoActionBar`**（`values/themes.xml`）：这是平台 Material（非 M3、非 DayNight）主题，冷启动那一瞬间窗口背景/状态栏图标配色与 App 主题无关，深色模式下可能闪白。建议：定义 `Theme.Chileme`（`parent="android:Theme.DeviceDefault.DayNight.NoActionBar"` + `windowBackground=?android:attr/colorBackground`）作为 `postSplashScreenTheme`，把 `windowSplashScreenBackground` 也换成 DayNight 资源色，而不是两套硬编码 `#F7FAF7`/`#101512`。
- **双门控启动**：`installSplashScreen().setKeepOnScreenCondition { !contentReady }` 与 `if (!contentReady) return@setContent` 是同一件事做了两遍。后者会让"超时放行"这条路径变成"3 秒空白 + 从零开始首帧组合"。建议只保留 splash 条件，`setContent` 直接渲染（主题读不到时用 `DefaultPalette`），启动更快、也少一条状态。
- **`MainActivity` 的每 30 秒轮询**：`LaunchedEffect(Unit) { while (true) { … delay(30_000) } }` 不受生命周期影响，App 在后台仍每 30 秒醒一次（且 `delay` 会因 Doze 被推后，行为并不比"睡到下一个午夜"更稳）。建议 `flowWithLifecycle(viewLifecycleOwner.lifecycle)` / `repeatOnLifecycle(STARTED)` 包住，或 `delay(到次日 00:00 的毫秒数) + ON_RESUME 兜底`。注释里"比一次性睡到午夜更稳健"的论证在后台场景不成立，值得重估。

---

## 7. 构建、R8、依赖与 CI

### 7.1 已经对齐 AGP 9 的部分（给分）

`root build.gradle.kts` 不再 apply `org.jetbrains.kotlin.android`（AGP 9 内置 Kotlin，误 apply 现在是硬错误）、只引 `proguard-android-optimize.txt`（AGP 9.0 起 `getDefaultProguardFile()` 只支持这一个）、`jvmToolchain(21)` 与 miuix inline 的 JVM 21 目标对齐、`versionCode` 由 CI 注入并做了"不静默回退 debug 签名"的双重拦截 —— 这些迁移都踩对了。

### 7.2 仍在使用旧 DSL / 被度量遮蔽的部分

1. **R8 DSL**：AGP 9.3 提供了新的 `optimization { }` 块（打开即同时启用代码优化 + 精确资源收缩，且不再需要手写 `getDefaultProguardFile`）。当前 `isMinifyEnabled` + `isShrinkResources` + `proguardFiles` 属"legacy DSL 仍支持"。建议切到新 DSL，并在 CI 里加一次 `./gradlew :app:analyzeReleaseR8Config`（AGP 9.3 新增）——它能直接指出"多余的 keep 规则"，正好治下面这条。
2. **`proguard-rules.pro` 里 `-keepattributes *Annotation*` 的目的已经失效**：AGP 9.2 起 `-keepattributes` 的**通配模式不再匹配 RuntimeInvisible 注解**，而 `@Serializable` 正是 BINARY（=runtime invisible）。今天没出问题是因为 kotlinx-serialization 靠插件生成的 `$$serializer` + `Companion.serializer()`（他们那两条 `keepclassmembers/keepclasseswithmembers` 规则在起作用）。⇒ 动作：删掉 `*Annotation*`（保留 `Signature,InnerClasses,EnclosingMethod`），或按新语义显式列名；顺手把"为什么留"的注释改成"为什么不需要"。
3. **`-dontobfuscate` 值得重估**。他们的理由（堆栈可读、免 mapping）合理，但代价是：R8 的类名/包名重排（含 AGP 9.1 起默认的 repackage 到默认包）带来的体积与内联收益全部放弃，同时崩溃堆栈仍然带混淆前类名的可读性需求其实可以用更标准的方式满足：**开启混淆 + 保留 `SourceFile,LineNumberTable` + 把 `mapping.txt` 交给 Play Console / 崩溃平台**（官方支持上传 mapping 做符号化）。另外 Kotlin 2.3+ 会为 Compose 栈帧输出 deobfuscation mapping，minify 后的重组堆栈同样能还原。若坚持不混淆，也请把这条决策的**体积差**量出来（打开/关闭各打一次包对比），别只留理由。
4. **Gradle 配置**（`gradle.properties`）：
   - 没有 `org.gradle.configuration-cache=true`。Gradle 9 已把 Configuration Cache 定为**首选执行模式**（并会在构建结束时提示开启；Gradle 10 将默认启用）。
   - 而挡路的正是 `app/build.gradle.kts` 末尾那个签名拦截：官方文档明确列出"`Gradle.taskGraph.whenReady` 这类**配置期回调**在配置缓存命中时不会重新执行"，并且"注册构建监听器"属于 CC 会判为问题的用法。今天的表现是**静默退回非 CC 模式**（Gradle 9 的 graceful fallback），即：他们既没享受 CC，也没看到告警。改法：把它变成一个真正的任务 + 输入声明，让 R8/打包任务 `dependsOn`：
     ```kotlin
     val checkReleaseSigning = tasks.register("checkReleaseSigning") {
         inputs.property("hasReleaseSigning", hasReleaseSigning)
         inputs.property("allowUnsignedRelease", allowUnsignedRelease)
         doLast { if (!hasReleaseSigning && !allowUnsignedRelease) error("Release 签名凭据缺失…") }
     }
     tasks.matching { it.name == "packageRelease" || it.name == "assembleRelease" }.configureEach { dependsOn(checkReleaseSigning) }
     ```
     语义等价（只在真的打 release 时失败），并且 CC 友好。
   - `kotlin.compiler.execution.strategy=in-process` 建议**删除**。KGP 的推荐执行模式是 Kotlin daemon（in-process 会占用 Gradle daemon 堆、关掉 daemon 复用，且与 CC/并行构建配合更差）。如果这行是因为沙箱里 `~/.gradle` 不可写才加的，应该写成"仅 CI/沙箱用 `-Pkotlin.compiler.execution.strategy=in-process`"，别固化进项目配置。
   - `-Xmx2048m` 对 AGP 9.3 + R8 + in-process 偏紧，建议 `-Xmx4g`（官方构建性能文档口径）；同时补 `org.gradle.jvmargs` 的 `-XX:MaxMetaspaceSize=1g`。
   - `android.nonTransitiveRClass=true` 自 AGP 8 起是默认值，可删（留着会让人以为是项目特殊配置）。
5. **供应链**：`gradle-wrapper.properties` 无 `distributionSha256Sum`；仓库无 `gradle/verification-metadata.xml`；无 Dependabot/Renovate（`.github/` 只有 `workflows/`）。他们自己的 P2-2 已把这几项标 ❌。对"版本目录 + 每周 BOM 升级"的项目，**Renovate 提 PR** 比人工追版本可持续得多——目前 `composeBom = 2026.08.00`（对应 Material3 1.4.x、runtime 1.12.x），落后一个季度，而 `README`/`ARCHITECTURE` 都写着"版本不得随意升级"，这条纪律在有时间的人手里是优点，在无人盯的项目里就是"永远不升级"。
6. **CI 作业**：
   - `assembleRelease` ⇒ 若打算上 Play，需 `bundleRelease`（新应用必须 AAB）。同时 release.yml 目前只发 APK 到 GitHub Release —— 保留，但建议 AAB 也作为 artifact（Play 上传走 `bundleRelease`）。
   - `lintDebug` 与 `lintRelease` 都跑：AGP 的 lint 只需跑一次（默认 variant）；`lintRelease` 的增量收益接近零，成本是一次完整编译。建议 release job 只留 `assembleRelease`，把 lint 收敛到 build job。
   - 建议把 lint 报告同时出 **SARIF**（`lint { sarifReport = true }`）并 `upload-sarif` 到 GitHub 代码扫描：现在他们靠 `textOutput = stdout` 让人读日志，这是"没有 code scanning"时的替代方案，可以升级。
   - `--no-daemon` 与 `gradle/actions/setup-gradle` 同时使用：Actions 容器是一次性的，保留 daemon 才能吃到 Gradle 自己的增量/缓存收益。建议去掉 `--no-daemon`。
   - 加 `on.push.paths-ignore: ['docs/**', 'devlog/**', '*.md']`，避免每天一篇 devlog 触发三 job 全跑（他们 P2-2 已记 ❌）。
   - **`tools/doc-metrics.sh`（46 KB）与 `tools/kt-lexcheck.py`、`guard-mirror.py` 都不在任何 workflow 里**（实测 `grep -rn tools/ .github` 只有 `ci-gates.sh`）。也就是说：**文档里那些"实测 N 处"的数字，以及他们用来防止文档漂移的守卫，实际上没有门禁**。而 `ARCHITECTURE.md` / `ROADMAP.md` 已经把"数字的唯一落点是 doc-metrics"当作纪律（并且承认出现过 32 vs 26、34 vs 26 的漂移）。两个选择：(a) 加一个 `docs-metrics` job，把脚本输出与期望值比对（需要脚本支持 `--check`）；(b) **把数字从文档里删掉**，只保留"跑 `tools/doc-metrics.sh` 查看"。我倾向 (b)：数字对读者的价值低于它带来的维护与"为数字写代码"的风险。
7. **缺失的两块官方推荐基建**（本项目我认为**必须补**）：
   - **Baseline Profile**：官方口径是"Baseline Profiles 让首启代码路径 AOT，约 30% 提升，Compose 应用尤其明显"。当前依赖里**没有** `androidx.profileinstaller`，也**没有** `:baselineprofile` 模块 ⇒ 所有用户在首启/首次滑动时走解释器 + JIT。minSdk 26 也让 Profile Installer 成为**唯一**能给 API 26~28 提供 baseline profile 的通道。做法：`com.android.test` + `androidx.baselineprofile` 模块（冷启动 + 切 4 个 Tab + 列表 fling），`:app` 加 `implementation("androidx.profileinstaller:profileinstaller:…")` 与 `baselineProfile(project(":baselineprofile"))`，生成的 `baseline-prof.txt` **入库**（否则插件等于没接）。
   - **Macrobenchmark / JankStats 的启动回归**：有了 baseline profile 才能顺手量"有/无 profile"的收益，并把它作为性能门槛。
8. **测试基建**（决定上面几条能不能安全做）：`testImplementation` 目前只有 JUnit4。建议按顺序补：`kotlinx-coroutines-test`（能测 `suspend` 写路径，直接覆盖 §6.1）、`app.cash.turbine`（测 `StateFlow`/`Channel` 事件，替换 `UiEventTest` 里读源码文本的做法）、`robolectric`（测 `FoodRepository`/`SecureStore` 真行为，现在只测了 `parsePropfind` 和纯函数）、`ui-test-junit4` + `ui-test-manifest` + `espresso-core`（把"保存→返回→重进→数据还在""拍照后立刻返回不丢封面"这类**已经在靠真机复测**的场景固化成 instrumentation 用例）、`com.android.compose.screenshot`（双主题像素一致性）。
   > 注意 `ScreenParityTest` / `SnackbarCopyTest` / `ImeHandlingTest` 这三类"grep 源码文本"的守卫：它们能防"某个字符串没写"，但**不检验行为**，还会造成"文件名/前缀一改守卫就漏"的脆弱性（他们自己已经踩过：按 `*Miuix*Screen` 前缀枚举导致合并后的文件静默逃出守卫）。建议定位从"门禁"降级为"临时脚手架"，逐条替换成真实测试。

### 7.3 依赖清单逐条

| 依赖 | 现状 | 建议 |
|---|---|---|
| `composeBom 2026.08.00` | 落后约一个季度（runtime 已到 1.12.1 / 1.13.0-alpha） | 保持"跟 BOM 走"，加 Renovate；Material3 1.4.0 是本 BOM 内稳定版，无需追 alpha。 |
| `material-icons-extended` | 保留，70 处 `Icons.Rounded.*`（他们注释里写清了原因） | **官方已停止发布**该库，并明确"不再推荐用它显示 Material Icons"，M3 1.4.0 起不再传递依赖 `material-icons-core`；推荐路径正是他们注释里写的从 fonts.google.com/icons（Android 标签）取 Material Symbols 的 VectorDrawable。这是**已发布产品的技术债，不是"暂时保留"**：建 `:designsystem` 里的 `AppIcons`，新增图标一律走自有资源，存量按使用频率分批换（`History/Schedule/RestartAlt/Inventory/CleaningServices/Category/CalendarMonth/TableChart/PieChart/FilterList` 这 10 个 extended-only 的先做）。debug 63 MB 与 release 2.4 MB 的差里它也占大头。 |
| `okhttp 4.12.0`（2023-12） | 只有 `proguard` 里 4 行 `-dontwarn` 与它有关 | 升 **OkHttp 5.x**（当前 5.5.0）：Android 侧改为 AAR 制品、`okhttp-coroutines` 的 `call.await()` 可取消（正好解决 §5.3 的"不随 VM 取消"）、TLS/HTTP2 更新。升完可删 `org.conscrypt/bouncycastle/openjsse` 那三条 dontwarn。 |
| `coil 3.3.0` + `coil-network-okhttp` | 未做 `SingletonImageLoader` 配置（实测无 `Coil`/`ImageLoader` 初始化） | 默认单例可用，但**没有内存缓存/磁盘缓存策略与 OKHttpClient 复用**。建议在 `ChiliMeApp` 里 `SingletonImageLoader.setSafe { config -> ImageLoader(config) }`，统一 `crossfade`、`allowHardware(true)`、并复用与 WebDAV 同一份连接池（`OkHttpNetwork` 传入自有 `OkHttpClient`）。列表封面这种滚动密集场景收益直接。 |
| `datastore-preferences 1.2.0` | 承载了"资产 + 设置 + 凭据"三类数据 | 见 §5.1 / §5.2：拆文件（必须）、评估迁 Room（推荐）。 |
| `kotlinx-serialization 1.11.0` | `Json { ignoreUnknownKeys = true }` | 补 `explicitNulls = false`；给 DataStore 内的 payload 加 `version` 字段（现在只有导出文件有 `BACKUP_VERSION`）。 |
| `material-kolor 4.0.1` | 15 套种子色 → 整套色板 | 用法没问题。注意 `rememberDynamicColorScheme` 的 key 包含 style，切配色时**重建整套 scheme** 与 `animateColorScheme` 叠加正好是最贵的那条路径（见 §6.2）。 |
| `miuix 0.9.4-rc01` | rc 版 | 长期停在 rc 有风险（无 `NavDisplay` 的 savedState 保证）。**核实一次**：`rememberNavBackStack` 是否内部 `rememberSaveable`；若否，进程死亡后二级页会丢，需要在 `docs/` 里明确写成已知行为，而不是留白。（我没法在评审里跑起来验证。） |
| `activity-compose 1.13.0 / lifecycle 2.11.0 / core-ktx 1.19 / splashscreen 1.2 / lifecycle-runtime-compose` | 齐 | 建议补 `androidx.lifecycle:lifecycle-process`? 不需要（用了 `LifecycleEventEffect`，正确）。缺 **`androidx.profileinstaller`**、缺 **`androidx.benchmark`**、缺 `androidx.test.*`（见 §7.2-8）。 |
| `INTERNET` 权限 | 只有它 | ✅ 与"无后台追踪"的对外承诺一致。 |

---

## 8. 与"官方最新建议"的逐条对照

| 官方建议（当前） | 本仓现状 | 结论 |
|---|---|---|
| Play：2026-08-31 起新应用与更新必须 target API 36 | `targetSdk = 36` | ✅ 达标（已过期限，仍安全） |
| Play：新应用必须上传 AAB | 只 `assembleRelease` 出 APK | ❌ 上架前必须补 `bundleRelease` |
| Android 16：targetSdk 36 预测性返回默认开启，`onBackPressed`/`KEYCODE_BACK` 不再分发；拦截返回的应用应迁移到 `OnBackPressedCallback` / `PredictiveBackHandler` | 已开 `enableOnBackInvokedCallback`（现已冗余）；二级页转场交给 miuix-nav；多选退出用 `BackHandler` | 🟡 功能正确，**没有跟手预览动画**；建议 `PredictiveBackHandler` |
| Android 15/16：强制 edge-to-edge，`adjustResize` 不再缩窗口，须自行消费 `WindowInsets.ime` | `enableEdgeToEdge()` + 各屏 `imePadding()` + MD3 弹窗 `decorFitsSystemWindows=false` + 自研 `stickyImePadding()`；`ImeHandlingTest` 静态守卫 | ✅ 处理得非常细，比官方最低要求还多走了半步（键盘切换 restartInput 抖动那条调查值得写进博客） |
| 大屏/折叠屏：不限方向、内容自适应、`WindowSizeClass` 分档 | `Box(widthIn(max = 840.dp))` 居中，4 套底栏（悬浮/常驻 × 双主题） | 🟡 有"限宽"但没有 size class 分流（`HorizontalPager` 常驻 4 页在大屏上更贵）；建议引入 `material3-window-size-class` |
| Data 层：DataStore 只适合小而简单；需要部分更新/引用完整性/大数据 → Room | 整表 JSON 存 Preferences + `take(200)` 截断 | ❌ 直接对撞（§5.1） |
| 备份：敏感与非敏感偏好应**分文件**，再按文件配 `data_extraction_rules` | 整个 `datastore/` 目录被排除 | ❌ 做法错了，改法明确（§5.2） |
| 架构：按屏/按功能提供 VM；用 DI 解耦创建点；一次性事件用 Channel/SharedFlow 且不与 UI 状态混放 | 单一全局 `AndroidViewModel` + 服务定位器 + `Channel` × 4 | 🟡 事件部分 ✅，VM 粒度与 DI ❌ |
| UI State：`stateIn(WhileSubscribed(5_000))` | 19 处 `Eagerly`（`#10c` 被搁置） | ❌（§4.1） |
| 持久化：Keystore 密钥不跨设备 → 相关密文不要进备份 | 已排除；并有 `nutstoreCredentialBroken` 检测 + 重填引导 | ✅ 这条他们做得比官方模板好 |
| 图片选择：优先 Photo Picker，不申请媒体权限 | `PickVisualMedia` | ✅ |
| 性能：Baseline Profile（+ Profile Installer）为强烈推荐项 | 无 | ❌（§7.2-7） |
| Compose：`Modifier` 形参、`key`/`contentType`、避免组合期 IO、`@Preview` | 见 §6.3 | 🟡 key 好，modifier/contentType/Preview/组合期 IO 有缺口 |
| 无障碍：不只靠颜色、48dp 目标、字体缩放 | 颜色编码的日历点、`semantics` 7 处 | 🟡 需一轮 a11y 专项 |
| 国际化：文案进资源 | 565 处字面量 / 资源 1 条 | ❌（产品决策，但 a11y 标签至少该进资源） |
| 图标：`material-icons-*` 已弃用，改用 Material Symbols 自有资源 | 注释记录了理由并保留 | 🟡 已知债务，需要排期而不是长期挂着 |
| 构建：Gradle 9 把 Configuration Cache 定为首选、Gradle 10 将默认开启 | 未开启，且有一个 CC 不兼容的 `taskGraph.whenReady` | ❌（§7.2-4） |
| 安全：包装 `distributionSha256Sum`、依赖校验、Actions 静态审计 | 无 | ❌（P2-2 已列，仍未做） |

**一个政策提醒**（不是代码问题，但和这个仓库的分发方式直接相关）：Android Developer Verification 自 **2026-09-30** 起在巴西/印尼/泰国/新加坡开始执行，覆盖**不经 Play 分发**的安装包。本项目走 GitHub Release 直发 APK，若目标用户里有这些地区，需要在 Play 开发者账号里完成一次验证注册；建议在 README 的发布章节加一句说明。

---

## 9. 问题清单（按优先级；括号内是他们自有审计的对应编号）

### P0（会丢数据 / 会在真机上出问题）

1. **`viewModel.upsert()` 火忘 + 立即 `onBack()`**；封面图在 `rememberCoroutineScope` 里写盘。→ 写路径改 `suspend` 并 await，图片写盘搬到 VM + `tmp→rename` 原子化。（P1-5 的升级版，见 §6.1）
2. **归档静默截断 `take(200)`**（`FoodArchive.kt:28`）、历史 `take(50)`，UI 无提示，统计已失真（`StatsState.kt:68`）。→ 返回被丢弃数并明示 + 提高上限 + 浪费计数改为独立累计值。（P0-3 只修了"件 vs 条"，截断没动）
3. **系统备份排除了全部业务数据**（两个 xml 都排除 `datastore/`）。→ 按官方建议拆 `credentials_store`，只排除凭据文件。（P0-6 的"主体论点"被他们判为误判，但**主数据不进备份**这件事是真的）
4. **`SecureStore.getOrCreateKey()` 并发竞态** → 误判"加密失败"并落明文。→ `Mutex` 或捕获 `KeyAlreadyExistsException` 后重读。
5. **零 UI/instrumentation 测试**：上面 1~3 都属于"跑一次 `createComposeRule` 就能抓到"的类别。→ 至少为"保存→返回→重进""导入前快照""批量归档→撤销"三条旅程写 Compose UI 测试。

### P1（架构与性能）

6. DataStore → **Room**（含 `Migration`），一揽子解决部分更新、引用完整性、截断、`suggestionSource` 三表拼接、按 key 解码。（P1-2）
7. **按屏拆 VM**（`FoodListViewModel`/`EditFoodViewModel`/`StatsViewModel`/`SettingsViewModel`/`SyncViewModel`），配 `viewModelFactory`；拆完立刻做 `WhileSubscribed(5_000)`（补回被搁置的 `#10c`）与"仓库对 UI 不可见"（删 `AppViewModel.repo`）。（P1-9 / P1-11 的前置）
8. Pager 常驻 4 页 → `Crossfade`；`animateColorScheme` 36 路动画收敛；组合期 `File.exists()` 移除 + `photoPath` 存 basename。（P1-6 / P1-7 / P1-8，三条打包做）
9. **Baseline Profile + Profile Installer + Macrobenchmark**。
10. OkHttp 4 → 5（`executeAsync`/可取消），Coil 统一 `ImageLoader`；Material Symbols 图标自有资源模块（先新增不混用，存量分批）。
11. `tools/doc-metrics.sh` 与 `tools/kt-lexcheck.py` 要么进 CI，要么把文档里的数字删掉（推荐后者）。
12. 配置类：开 Configuration Cache（先把签名守卫换成任务形式）、删 `kotlin.compiler.execution.strategy`、`-Xmx4g`、AGP 9.3 `optimization {}` DSL + `analyzeReleaseR8Config`、`bundleRelease`、SARIF、`paths-ignore`、wrapper `distributionSha256Sum`、Renovate。
13. 无障碍专项：日历语义、stepper 的 Role/toggleable、200% 字体压测。

### P2（打磨）

14. `buildFeatures { buildConfig = true }` + 显示 `BuildConfig.VERSION_NAME`（现在两处在 UI 上硬编码"吃了么 v1.0"，而 `versionName` 明明由 tag 注入 —— 用户与技术支持无法判断装的是哪一版；这条与 `CLAUDE.md §5` 的"展示版本号锁定 v1.0"冲突，我倾向**改约束而不是改代码**，因为它已经是发布可观测性问题）。
15. `file_paths.xml` 的 `files-path covers/`：`getUriForFile` 只用于 `cacheDir/camera/`（`EditFoodScreen.kt:149`），covers 无共享需求 ⇒ 按最小权限删掉。
16. `DecodeCache.hits/misses` 是非原子的共享计数器（`RepositoryCore.kt:98-113`，多协程 Default 线程 `++`）。诊断用途无害，但要么改 `AtomicInteger`，要么删（缓存命中率并非行为依赖）。
17. `postSplashScreenTheme` 换成 DayNight 的 `Theme.Chileme`；`windowSplashScreenBackground` 用 DayNight 资源色。
18. 双门控启动简化；30 秒轮询改生命周期感知。
19. 补 `.github` 治理：`dependabot`/Renovate、PR/issue 模板、`CODEOWNERS`、README 上加 CI 状态徽标；把 `docs/audits/*.patch` 从仓库里移除（一次性补丁文件不该长期留在文档目录）。
20. 冷启动做网络同步与快照（`AppViewModel.init { maybeAutoSync(); maybeAutoSnapshot() }`）→ 官方口径是"可延迟的周期工作交 WorkManager"（`PeriodicWorkRequest` + 网络约束 + 弹性），把冷启动只留读；顺带解决"用户几天不打开 App，每日快照与自动同步就都不发生"的静默失效。

---

## 10. 流程与文档：优点正在变成负债

必须承认这套流程**产出质量很高**：每个结论有证据、每个数字有口径、每个"不做什么"有理由、并且能公开承认"detekt 曾长期空转"和"P0-6 的原判断是误判"。这是很多团队做不到的诚实。

但有三处**已经出现反向作用**，值得显式立规：

1. **度量替代目标**。为"文件 < 400 行"这个验收口径，产生了 7 个 `AppViewModel*.kt` + 8 个 `data/*领域*.kt` 的扩展函数矩阵，代价是 13 处 `private → internal`（并让 `UnusedPrivateMember` 失去覆盖）+ 一个 46 KB 的数字测量脚本。**指标满足了，"这个类有多少状态/职责"这个问题被推到了下一轮。** 建议把验收口径从"文件行数"换成两条可机械检查的真指标：① 一个类持有的 `StateFlow`/可变字段数量上限；② 跨文件互相可见的 `internal` 成员数量上限（只降不升）。
2. **文档在指挥代码**。`AppNavCallbacks` 的存在理由、`TAG` 在 8 个文件里的重复、以及注释里"本段刻意不写成 `now()` 连写形式，否则 `doc-metrics.sh` 的计数会被撑大"——这些是**文档守卫直接决定了源码写法**。建议：文档只引用可复跑命令，不引用需要被保护的字符串形状；脚本改为 `--check` 进 CI 之后，把这类"避讳"注释全部删掉。
3. **"本地没编译器"** 被列为多条设计选择的理由（不敢提包级常量、不敢选门面转发、靠"所有失败模式都是编译期的"来为搬运方案背书）。这是一个**环境问题被固化成架构约束**。最优先要做的其实是：让 `./gradlew :app:assembleDebug` 在任何环境里 5 分钟内可跑（Dev Container / `sdkmanager` 引导脚本 / CI 上已有的 `release-r8` job 复用为 `--dry-run`），把"不可验证"从决策变量里删掉。**这一条我建议排在所有 P1 之前**，因为它会同时降低后续每一条的改造成本。

---

## 11. 建议的三个里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **M1：先止血（1~2 天）** | ① 备份通道拆 `credentials_store`（§5.2）② 写路径 await + 图片原子落盘（§6.1）③ `take(200)` 可见化 + 提高上限 + 浪费独立计数（§9-2）④ `SecureStore` 竞态（§9-4）⑤ 把"能本地编译"当工程问题解决（§10-3） | 新增 4 条单测（含 2 条会因这些 bug 失败的回归测试）；换机/重装后库存仍在；`analyzeReleaseR8Config` 跑通 |
| **M2：结构与性能（1 周，可分批）** | ① `:designsystem` 抽模块 ② `viewModelFactory` + 按屏拆 VM ③ `WhileSubscribed(5_000)` ④ Pager→Crossfade、36 路动画收敛、组合期 IO 清零 ⑤ `buildConfig` 展示真实版本 + `bundleRelease` | `AppViewModel.repo` 不复存在；`grep -c "stateIn(.*Eagerly" == 0`；`File(…).exists()` 在 `ui/` 下 0 命中；AAB 可上传 Play 内部测试轨道 |
| **M3：数据与质量基建（2 周）** | ① DataStore→Room（带 `Migration` 与回滚窗口）② Baseline Profile + Macrobenchmark ③ Compose UI 测试 + 截图测试替换 grep 守卫 ④ 依赖升级（OkHttp 5 / Material Symbols / Coil 单例配置）⑤ Gradle CC + 依赖校验 + Renovate | 冷启动 p50/p95 有基线数字；三条关键旅程有 CI 化 UI 测试；`bundleRelease` + mapping 上传 |

---

## 12. 结语

如果只留三句话：

1. **这个项目的风险不在代码风格，在持久化选型和它的备份通道**——这两处都是"官方文档一句话说清、而本仓用 8 个文件和 3 层守卫在绕"的地方。
2. **最该做的是"保存必须 await"和"备份必须只排敏感数据"**：两处改动量都很小，收益分别是"不丢用户刚写下的东西"和"换机不丢库存"。
3. **流程纪律请设一条上限**：任何"为了守卫/指标而采用的代码形状"都要在 devlog 里写清"它替代了什么真改动、什么时候还"，否则两年后没人能判断 `AppNavCallbacks` 为什么存在。
