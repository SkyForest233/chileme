# 全量代码审查与改进建议（2026-09-15）

> 审查范围：仓库全部 135 个文件（Kotlin 55 个：main 45 + test 10，共 14,886 行）、Gradle 与 CI 配置、`docs/` 全部文档、`devlog/`。
> 审查方式：静态通读 + 交叉验证（文档 ↔ 代码 ↔ CI 一致性），**未编译、未运行**（沙箱无 JDK / Android SDK，`java -version` = command not found）。
> 本文按「风险 → 架构 → 产品 → 工程」分组，每条给出**证据（file:line）**、**影响**、**建议**与**代价**，末尾附优先级路线图与「10 分钟快修清单」。

---

## 0. 总体结论

**做得好的部分（不要在重构中丢掉）**

| 项 | 说明 |
|---|---|
| 文档驱动 | `CLAUDE.md` + 4 份标准文档 + devlog + audits 形成闭环，需求边界（"明确不做"）写得很清楚，这在个人项目里罕见 |
| 数据完整性守卫 | `Decoded.Ok/Empty/Corrupt` 三态（`FoodRepository.kt:31-48`）+ 写路径 `isCorrupt` 守卫 + 损坏留档 + UI 告警条，思路正确且注释解释了"为什么" |
| Flow 读取规约 | `rawFlow()` / `lightFlow()`（`FoodRepository.kt:168-175`）避免"改主题色 → 全量重解析 JSON"，是正确的 DataStore 用法 |
| 构建/发布 | Version Catalog、R8（不混淆但压缩）、release 签名缺失即失败、CI 跑 lint 且 `NewApi` 提升为 error——都是"踩过坑之后"的正确决定 |
| 测试起步 | 10 个测试文件 **73 例**（`grep -c @Test` 实测；`devlog/INDEX.md` 写的 42 例已过期），纯函数优先（`statusForAt` / `compactConsumption` / `planRestore` / `parsePropfind` / 三态解码），选点准确 |
| 隐私定位 | 无网络依赖（除用户自配 WebDAV）、无统计 SDK、Keystore 加密凭据 |

**最需要修的不是"缺功能"，而是三个系统性隐患**

1. **数据安全机制存在自杀式分支**：损坏保护、封面清理、导入替换三者缺少互相保护（§1.1 / §1.2 / §1.4）。
2. **错误处理是空白**：读流无 `catch`、写操作无 `try`、启动画面无超时——任何一次 DataStore IO 异常都会变成"崩"或"永远卡在启动页"（§1.3）。
3. **两套 UI 的维护税只还了一半**：状态层抽离了，但 7 个页面仍是 MD3 / Miuix 平行实现（3,329 行），每个功能改动都要改两遍（§2.1）。

---

## 1. 正确性、数据安全与健壮性

### 🔴 1.1 损坏态下启动会删掉全部封面图（与守卫的初衷完全相反）

**证据**

- `AppViewModel.kt:214-219`：
  ```kotlin
  val referenced = buildSet {
      repo.itemsFlow.first().forEach { ... }          // Corrupt → orElse(emptyList())
      repo.archiveFlow.first().forEach { ... }        // Corrupt → orElse(emptyList())
  }
  cleanupOrphanCovers(getApplication(), referenced)   // 删掉"不在引用集"的一切
  ```
- `ImageStore.kt:124-136`：`dir.listFiles()` 中凡不在 `referencedPaths` 的**一律删除**。
- `FoodRepository.kt:177-178`：损坏时 `itemsFlow` 回落 `emptyList()`（这是读路径的**预期**行为，问题在调用方）。

**影响**：一旦 `food_items` 或 `archived_items` 解析失败（正是守卫要保护的场景），启动后 `covers/` 被清空。用户即使随后成功导入备份，封面也全丢——留档 `filesDir/corrupt/*.json` 救不回图片。这是本次审查发现的最高危问题。

**建议**（按性价比排序）

1. **最小改动**：`if (repo.corruptedKeys.value.isNotEmpty()) return`，损坏时不清理；
2. **更稳**：清理条件加三条与门——`corruptedKeys` 为空、`items`/`archive` 至少一次解码为 `Ok`、且只删 `lastModified()` 早于"本次启动时刻"的文件（避免删掉刚选中、尚未保存的封面）；
3. **长期**：把封面引用改为"文件名索引"（§1.7），清理改为集合差运算 + 二次确认（设置页可手动触发）。

**代价**：1 行（方案 1）～30 行（方案 2），必须补一条单测（"损坏态下不清任何文件"）。

---

### 🟠 1.2 写守卫粒度过粗：一个非核心 key 损坏就锁死核心功能

**证据**：`FoodRepository.kt` 的守卫是"本次 edit 涉及的所有 key 健康才写"：

| 写方法 | 守卫 key | 用户后果 |
|---|---|---|
| `upsert` (:303) | items **+ history** | `history_entries`（一个可重建的联想缓存）损坏 → **无法新增/编辑任何食品** |
| `changeQuantity` (:449) | items + consumption + archive | 任一副表损坏 → **卡片步进器失效** |
| `restoreArchived/…Batch` (:357,:379) | archive + items | 归档损坏 → 无法恢复 |
| `deleteConsumption` (:494) | consumption | — |

`DataCorruptBanner`（`Common.kt:896`）只出现在首页，用户看到"数据读取失败"但**没有任何按钮**能放弃那一份损坏数据继续用（文档里唯一出路是"导入备份"，`ARCHITECTURE.md` §5 也这么写）。

**影响**：小概率损坏 → 整个 App 从"部分不可用"变成"完全不可用"，且用户无法自救。

**建议**

1. 守卫改**按 key 粒度**：items 健康就允许写 items，坏掉的 `history` 跳过（或降级为"清空后重建"）；
2. `DataCorruptBanner` 增加两个动作：**「导出留档并重置这部分数据」**（调用 `clearHistory()`/`clearConsumption()` 式的方法解除标记）与「查看诊断信息」；
3. 补 `clearHistory()` / `clearConsumption()`（与既有 `clearAll()` / `clearArchive()` 同一豁免语义），并在 `docs/ARCHITECTURE.md` §5 登记为"允许在损坏态执行"的第四、第五个例外。

**代价**：中等（约 80–120 行 + 单测），收益是"永久性不可用"降级为"丢一小块缓存"。

---

### 🔴 1.3 读流无 `catch`、写操作无 `try`、启动画面无超时

**证据**

- `AppViewModel.kt:119-121`：`ready` = 5 条 flow `combine(...).stateIn(viewModelScope, Eagerly, false)`，没有任何 `catch` / `retryWhen`；
- `MainActivity.kt:173-174, 198-199`：
  ```kotlin
  var contentReady = false
  splash.setKeepOnScreenCondition { !contentReady }
  ...
  LaunchedEffect(ready) { if (ready) contentReady = true }
  if (!ready) return@setContent
  ```
- 全项目 **19 个** `stateIn(...)` 且全部 `SharingStarted.Eagerly`（`AppViewModel.kt:37-125`，实测计数）都没有异常处理；`viewModelScope` 未挂 `CoroutineExceptionHandler`；
- 写路径：`AppViewModel` 里 `viewModelScope.launch { repo.xxx() }` 形式的调用约 40 处，无一处 `try/catch`。`DataStore.edit` 在磁盘满 / IO 错误时抛 `IOException`。

**影响（按可能性排序）**

- **主因：进程崩溃。** 上游 flow 抛异常 → 该 `stateIn` 的共享协程失败 → `viewModelScope` 是 `SupervisorJob()` 根 Job 且未挂 `CoroutineExceptionHandler` → `handleCoroutineException` 回落到线程默认处理器 → 进程被杀。注意几乎所有读流都会走到这一步：`itemsFlow` 在 `ready` 的 `combine` 里，`archiveFlow`/`historyFlow` 在 `suggestionSource` 的 `combine` 里，任一条失败即崩；
- **次生：卡在启动页。** 只有在"流一直不发射"（读阻塞 / 系统级问题）而非抛异常时才会出现：`ready` 永为 `false`，`setKeepOnScreenCondition` 不放行，用户只能杀进程——这也是为什么 `ready` 必须加超时，而不是只加 `catch`；
- 写异常 → 直接崩溃，且 `EditFoodScreen` 里 `viewModel.upsert(item); onBack()`（`EditFoodScreen.kt:222`）是"乐观返回"，写失败时用户看到的是"保存成功并返回"。

**建议**

1. Repository 的 `context.dataStore.data` 统一加 `.retryWhen { e, n -> e is IOException && n < 3 }` + `.catch { emit(emptyPreferences()); _fatal.value = e }`，让"读坏了"退化为"空数据 + 告警"而不是崩溃；
2. `ready` 加兜底：`withTimeoutOrNull(3_000) { ready.first { it } } ?: true`——**任何情况下 3 秒后必须放行**（宁可闪一帧，也不能变砖）；
3. `AppViewModel` 构造时 `viewModelScope.coroutineContext + CoroutineExceptionHandler { _, e -> _errorEvents.tryEmit(e) }`，全局 Snackbar 显示"操作失败，请重试"；
4. 写操作改为返回 `Result`，`EditFoodScreen` 等以返回值决定是否 `onBack()`（现在是无条件返回）。

**代价**：小～中（1 天），但对"能不能打开 App"是决定性的。

---

### 🟠 1.4 导入备份是"无确认、无预览、无回滚、无上限"的破坏性操作

**证据**：`SettingsScreen.kt:125-141`（Miuix 版同构）
```kotlin
val raw = runCatching { ...input.readBytes()... }.getOrNull()
val ok = raw != null && state.importBackupJson(raw)   // 直接整体替换
```
- 无二次确认（对比：云端恢复有 `restoreCandidate` 二次确认 `SettingsScreen.kt:812-830`，本地快照恢复也有 `:899-915`——**同一 App 内三套标准**）；
- 无导入前自动本地快照（`LocalSnapshotStore` 明明已有能力）；
- 无体积上限（`readBytes()` 全量入内存，几百 MB 的"备份"可 OOM）；
- `BackupData.version` 字段（`FoodModels.kt:145`）**从未被校验**，也没有迁移分发；
- 无内容校验：`productionEpochDay` 越界 / `shelfLifeDays` 为负 / `quantity` 为 1e9 / 空 name 都会原样落库，之后污染排序、统计与 UI。

**建议**

1. 选文件后先解析到内存 → 弹**预览对话框**：导出时间（`exportedEpochDay`）、schema 版本、库存/归档/消耗/历史条数、"将覆盖当前数据"红字；
2. 确认后**自动写一份本地快照**再执行替换（失败可一键回滚——这条几乎零成本，收益极大）；
3. `importBackupJson` 内做**校验 + 迁移**：`version > SUPPORTED` 拒绝并提示；字段越界取 clamp；`name` 去空白后为空的记录丢弃；
4. 读取前先查 `size`（> 20 MB 拒绝）；
5. 返回值从 `Boolean` 换成 `ImportResult`（Success(counts) / NotBackup / TooLarge / VersionTooNew / Corrupt），让提示可以精确。

**代价**：中（半天到 1 天），是"用户自伤"类问题的总闸。

---

### 🟠 1.5 加密失败的静默明文回退

**证据**：`SecureStore.encrypt`（`SecureStore.kt:39-46`）失败返回 `""`；`FoodRepository.setNutstoreCredentials`（`:602-613`）据此把**明文应用密码写入 DataStore**，只在注释里写"不应发生"。

**影响**：文档与 README 承诺"凭据基于 Keystore 加密存储"，实际存在静默降级路径；Keystore 在少数机型/OEM 上确实会失败。安全承诺与实现不一致是审计级问题。

**建议**：加密失败 → 直接返回失败给 UI（"当前设备无法安全保存凭据，请勿在此设备配置云同步"），**绝不落明文**；同时清理代码里剩余的 `nutstorePasswordKey` 明文分支（迁移逻辑保留，但迁移失败时不写回明文）。

**代价**：小。

---

### 🟠 1.6 换机/云备份会丢全部数据，而封面却会跟着走

**证据**：`res/xml/data_extraction_rules.xml`

- `cloud-backup` 与 `device-transfer` 都 `<exclude domain="file" path="datastore/" />` → 库存/归档/统计/设置全不备份；
- `device-transfer` **未排除** `covers/` → 图片跟着传输；
- 结果：换机直传后，App 里空空如也，`files/covers/` 全是孤儿，首次启动被 §1.1 的清理删掉——等于**白传了一次**。

**根因**：DataStore 单文件里同时装了"用户资产"和"不能跨设备的密文凭据"，只能整文件排除。

**建议**（推荐顺序）

1. **拆库**：把 `nutstore_account` / `nutstore_password_enc` / `last_sync_time` 等凭据类 key 迁到**独立的 DataStore 文件**（如 `credentials.preferences_pb`），备份规则只排除该文件；`pantry_store` 恢复参与系统备份/换机迁移；
2. 迁移用一次性 `migrateCredentialsToSeparateStore()`，与既有 `migratePlaintextPassword()` 同一处调用；
3. 文案同步：README / 文档里的"数据 100% 仅存本机"应改为"默认仅存本机，可由系统备份/换机迁移承载（可在系统设置关闭）"——**这是隐私承诺的措辞问题，用户应知情**。

**代价**：小～中（迁移代码 60 行 + 文档），收益是"换机不用手动导出导入"。

---

### 🟡 1.7 封面存绝对路径，备份不具可移植性

**证据**：`FoodItem.photoPath`（`FoodModels.kt:47`）存 `/data/user/0/com.chileme.pantry/files/covers/xxx.jpg`；导出备份原样带出；`cleanupOrphanCovers` 也按绝对路径比较。

**影响**：主用户 → 副用户/工作资料（`/data/user/10/...`）、或未来包名变更，恢复后所有封面失效；手工处理备份时路径也会误导用户。

**建议**：只存文件名（如 `photoPath = "ab12.jpg"`），读取时 `File(context.filesDir, "covers/$name")`；导入时对旧格式做一次路径归一化（取 `substringAfterLast('/')`）。

**代价**：小（20 行 + 迁移 + 测试）。

---

### 🟡 1.8 归档上限静默丢历史，而"过期浪费"统计依赖它

**证据**：`FoodRepository.kt:339`、`:474`：`(...).take(200)`；`StatsState.kt:94`：`wastedTotal = archived.count { it.reason == EXPIRED }`。

**影响**：活跃用户超过 200 条归档后，第 201 条起会静默淘汰最旧记录 → 统计页"过期浪费"数字会**自己变小**；用户没有任何解释。

**建议**：维护一个独立的单调计数器（`waste_counter` / `consumed_counter`，或按月聚合的 `waste_stats`），归档仍限 200 条但统计不失真；归档页顶部提示"仅保留最近 200 条，更早记录已自动清理"。

**代价**：小～中。

---

### 🟡 1.9 本地快照：主线程 I/O + 统计口径错误

**证据**

- `AppViewModel.kt:387-389`：`_localSnapshots.value = LocalSnapshotStore.listSnapshots(getApplication())`（在 Main 上执行）；调用点 `SettingsScreen.kt:647`、`MiuixSettingsScreen.kt:519` 都在点击回调里；
- `LocalSnapshotStore.kt:62-71`：为了显示"包含 N 项资产"，对**每个快照整份 `readText()`** 再用正则数 `"id":` 出现次数。

**影响**：(a) 打开"本地快照"时在**主线程**读 3 份完整 JSON + 正则扫描——注意 `SettingsScreen.kt:647` / `MiuixSettingsScreen.kt:519` 都在点击回调里（Main）。几 MB 量级时是明显卡顿与 StrictMode `DiskReadViolation`（默认未开启，所以线上表现为掉帧）；单次 5s 级 ANR 只在快照异常大时才可能，不必夸大；(b) 统计数字**偏大**——`archived[].item.id`、`consumption[].id`、`history` 都被算进去了，用户看到的"资产项数"没有意义。

**建议**

1. `listSnapshots` 内部 `withContext(Dispatchers.IO)`；`file.length()/lastModified()` 本身也建议放到 IO；
2. 保存快照时把 `itemCount`/`archived`/`consumption` 条数写进**文件名或同名 `.meta`**，列表只读元数据；或直接 `decodeFromString<BackupData>` 取真实条数（解码一次比正则更准，且可复用 `BackupData`）；
3. `saveSnapshot` 同理（`file.writeText` 在调用线程执行，而调用方多为 Main）。

**代价**：小。

---

### 🟡 1.10 统计口径的四个语义问题

| 位置 | 现状 | 问题 | 建议 |
|---|---|---|---|
| `StatsState.kt:42-45` + `StatsScreen.kt:122` | 卡片写"**本周**消耗"，算法是 `today-6 .. today` | 周二看到的"本周"其实包含上周三~周日 | 改 ISO 周（周一起）或把文案改成"近 7 天" |
| `StatsState.kt:68-75` | TOP5 按 `name` 分组，`amount` 直接相加，`category` 取 `first()` | "牛奶 3 瓶 + 2 箱 = 5"，与 `compactConsumptionAt` 特意加入 `unit` 分组的结论**自相矛盾**（`FoodModels.kt:229-243`） | 分组键改 `(name, unit)`，展示"牛奶 · 瓶" |
| `StatsState.kt:94` | 过期浪费按**条数**统计且依赖 200 条归档 | 见 §1.8；且"1 箱牛奶"和"1 包糖"权重相同 | 用数量或独立计数 |
| `StatsState.kt:61-67` | 分类占比只用**当前库存** | 已吃完/已归档的食品让占比失真（用户视角是"我的消耗结构"） | 提供"库存 / 消耗"切换，或明确文案"当前库存构成" |

**代价**：小（半天，且都能补纯函数单测）。

---

### 🟡 1.11 其他确认过的小问题

| # | 位置 | 问题 | 建议 |
|---|---|---|---|
| a | `CloudSync.kt:57-61` | OkHttp 未设 `callTimeout`；`upload`（:85-110）把"上传成功"和"轮转清理"放在**同一个 `runCatching`** 里，PROPFIND 失败会让整体返回 `Result.failure`，于是 `lastSync` 不更新、UI 报"上传失败"，而云端其实已有一份新备份 | 加 `callTimeout(60s)`；轮转包在独立 `runCatching` 内（清理失败不算上传失败） |
| b | `CloudSync.kt:161-164` | 用正则解析 PROPFIND XML（`split("</...response>")` + 两个正则）；虽然有单测，但对命名空间前缀变体/嵌套 `<D:response>` 脆弱 | 换 `XmlPullParser`（Android 内置，无新依赖），保留 `parsePropfind` 签名与既有测试 |
| c | `CloudSync.kt:30-34` | `displaySize` 用默认 Locale 的 `"%.1f".format(...)`（Kotlin 的 `String.format` 走 `Locale.getDefault(FORMAT)`），德语/法语环境显示 "1,5 MB"，而 `LocalSnapshotStore.kt:26-27` 用 `Locale.US` —— 同一 App 两种格式。**且** `CloudBackupTest.kt:43-44` 断言了 `"2.0 KB"`/`"1.5 MB"`，在逗号小数分隔符的机器上**本地单测会失败**（CI 的 `LANG=C` 恰好掩盖了它） | 统一 `Locale.US` 或走资源字符串；测试固定 `Locale.setDefault(Locale.US)` |
| d | `CloudSync.kt:47` | `BASE_URL` 硬编码坚果云 | 设置页加"自定义 WebDAV 地址"（可选，高级），至少留注释说明如何改 |
| e | `FoodModels.kt:146` | `BackupData.exportedEpochDay` 用 `LocalDate.now()` 作 data class 默认值——隐式依赖时钟 | 由 `buildBackupJson` 显式传入（顺便注入 `Clock`，见 §2.3） |
| f | `ImageStore.kt:52,76` | 解码失败时**降级为原始流直接拷贝**（5–15 MB 原图落盘，且不做 EXIF 纠正）——静默突破"长边 ≤1200px"的设计承诺 | 降级路径加日志 + 尺寸上限；失败时删除半成品文件 |
| g | `CsvExport.kt:42-48` | CSV 未做**公式注入**转义（`= + - @` 开头会被 Excel 当公式执行） | 以 `'` 前缀或引号包裹 + 单测 |
| h | `AppViewModel.kt:333-346,349-358` | `addCategory/updateCategory/deleteCategory/addLocation/deleteLocation` 都是"读 `StateFlow.value` → 计算 → 写"，是典型 read-modify-write 竞态（连点两次"添加分类"可能丢一个）；仓库层其他方法都在 `edit{}` 内读改写，**风格不一致** | 统一下沉到 Repository：`repo.addCategory(def)` 在 `edit{}` 里读-改-写；VM 只转发 |
| i | `EditFoodScreen.kt:98` | `remember(editId) { viewModel.items.value.find { ... } }`——组合期直接读 `.value`，不是 `collectAsStateWithLifecycle`。进程被杀后恢复该页面时 `items` 可能尚未解码完成 → `existing == null` → 界面变"新增"，保存会**新建一条**而不是更新 | 改为收集 `items`；或 `AppRoute.Edit` 直接携带必要字段 / 在 VM 里提供 `observeItem(id)` |
| j | `EditFoodScreen.kt:179` | `shelfLifeText.toIntOrNull() ?: 0`，无输入校验与上限（可存 200 万天），非法输入静默变 1 | `OutlinedTextField` 加 `isError` + 支持范围提示（1–3650） |
| k | `BackupData.version` / `thresholdsKey` 注释 | `ARCHITECTURE.md` §3 写"`category_thresholds` key 为 `FoodCategory.name`"，实际已是 `CategoryDef.id`（`ManageState.kt:22-26` 证实） | 文档同步（见 §4.3） |

---

## 2. 架构与设计改进

### 🔴 2.1 双主题：从"两套页面"变成"一套页面 + 两套皮肤"

**现状量化**

| 目录 | 行数 |
|---|---|
| MD3 版页面（Home/FoodList/FoodDetail/Archive/Consumption/Stats/Settings/Manage/Edit） | 4,212 |
| `Miuix*.kt` | 3,329 |
| 共享 `*State.kt`（2026-08-22 抽离） | 658 |

状态层抽离很好，但**页面仍是平行实现**：同一个"设置页"有 `SettingsScreen.kt`(1,068) 与 `MiuixSettingsScreen.kt`(886)；同一次"批量移动位置对话框""坚果云凭据对话框""备份选择器"都要写两遍。结果是：**每加一个功能要动 2 个文件 + 1 个 `MainActivity` 的 if/else 分支**（`MainActivity.kt:904-955`），漏改一处就是两套主题行为不一致（`devlog` 里"改了两处"的表述已经暴露了这点）。

**建议路线（可在不冻结功能的前提下渐进推进）**

1. **建"应用级组件层"**：把"主题差异"收敛到 `ui/components/app/` 下的少量组件——`AppScaffold` / `AppTopBar` / `AppCard` / `AppListRow` / `AppTextField` / `AppDialog` / `AppSnackbar` / `AppSegmentedControl` / `AppProgress`。每个组件内部用 `LocalThemeStyle` 分流（`Common.kt` 里的 `StatusBadge`/`FoodCard`/`CheckSwitch` 已经是这个模式，**照抄它**）。
2. **逐页重写为单一实现**：页面只调用 App 级组件，不再 `if (isMiuix) ... else ...`。重写一页 → 删掉对应 `Miuix*` 文件。建议顺序：设置页（重复最多）→ 归档 → 消耗记录 → 首页 → 列表 → 详情。
3. **`MainActivity` 收敛**：`MainTabsPager` 里的 4 组 if/else 变成 4 个直接调用（`MainActivity.kt:904-955`）。
4. **守住底线**：编辑页/统计页保留各自实现（DatePicker 与图表无 Miuix 对应）——但要写进 `DESIGN_SPEC.md` §7 作为**明确例外**，避免后来者以为是漏改。

**代价**：大（**估算** 3–5 人天，未实测），但这是唯一能停止"3,329 行重复"继续增长的办法；建议在有 §2.9 的 UI 测试基线之后动手（否则没有安全网）。

---

### 🟠 2.2 派生数据下沉到 ViewModel，UI 只留"瞬时状态"

**现状**：`*State.kt` 在**组合期**做业务计算，并用 20+ 个 key 的 `remember(...)` 拼装（`SettingsState.kt` 的 `remember` 有 **28 个 key**，`FoodListState.kt` 16 个，已脚本核对）。

```kotlin
return remember(items, archived, categories, thresholds, today, query, statusFilter,
                categoryFilter, locationFilter, filtersExpanded, usedLocations,
                activeFilterCount, filtered, archivedMatches, selectedIds, selectionMode) { ... }
```

**两个具体风险**

- **漏键 = 陈旧 UI**：忘记把新增的派生值加进 `remember` 键，界面就再也不刷新（这类 bug 极难查，且随字段增加概率上升）；
- **测试错位**：纯函数有单测（很好），但"状态容器"本身的接线（哪个 flow → 哪个字段）没有测试，只能靠人读。

**建议**：把"flow → 派生数据"的表达搬到 VM，用 `combine(...).map{}.stateIn(viewModelScope, WhileSubscribed(5_000), initial)` 或 `derivedStateOf` 产出不可变 `UiState`；Compose 侧只留 `dialog 开关`/`输入框草稿`/`展开态`。附带收益：

- 全部可 JVM 单测（无需 Compose 测试运行器）；
- 副页面的 flow 用 `WhileSubscribed`（现在是 19 条 `Eagerly` 全程常驻，启动即解码全部 JSON）；
- `combine` 的 transform 现在跑在 `viewModelScope`（Main.immediate）上——例如 `suggestionSource`（`AppViewModel.kt:58-62`）每次 items 变化都在**主线程**做 `distinctBy`。改用 `flowOn(Dispatchers.Default)` 或 `stateIn` 前的 `map` 放到后台。

**代价**：中（逐页迁移，每页半天）。

---

### 🟠 2.3 仓储层拆分 + 注入 `Clock`（时间可注入）

**现状**：`FoodRepository` 702 行，混了四类职责——CRUD、备份、CSV、设置项、云同步凭据、压缩策略；同时**时间戳散落在仓储内部**：`FoodRepository.kt:282`（seed）、`:337`（归档日）、`:462`（消耗日）、`:471`（自动归档日）、`:636`（压缩 cutoff）都直接 `LocalDate.now()`。

**两个后果**

- UI 侧已经建立了"可注入 `today`"的正确范式（`LocalToday` + `*At` 纯函数，`FoodModels.kt:172-215`），**仓库侧却没有**——于是 `compactConsumption` 这类时间敏感逻辑仍无法在单测里覆盖真实边界（只能靠 `compactConsumptionAt` 的纯函数版，但集成路径仍不可测）；
- 用户手动改系统时间时，写入的归档日/消耗日与 UI 展示的 `LocalToday` 可能来自不同"今天"。

**建议**

1. 拆成 `FoodStore`（DataStore 读写 + 守卫）/ `BackupService`（导出/导入/校验/迁移）/ `CloudSyncService` / `SettingsStore` / `ImageStore`（已有）；
2. 定义 `interface Clock { fun today(): LocalDate }`，默认 `SystemClock`，测试注入 `FixedClock`；
3. `AppViewModel` 不再 `FoodRepository(application)` 硬构造，改为接口 + 构造函数注入（不一定要上 Hilt，手写 `AppContainer` 就够，见 §2.7）。

**代价**：中（1 天，纯搬运 + 构造参数化）。

---

### 🟠 2.4 事件与回调：通道化，去掉"6 个 MutableStateFlow 事件 + onResult 回调"

**现状**：`AppViewModel` 里并存的瞬时事件有 `_undoRequest` / `_deletedConsumption` / `_restoredArchivedEvent` / `_autoSyncMessage` / `_fabSuppressed`，加上 `syncUpload(onResult)` / `loadCloudBackups(onResult)` / `restoreLocalSnapshot(onResult)` 这类**回调式 API**，以及 `restoreArchivedSmart(id, onDone)`。`MainActivity.kt:287` 与 `:304` 需要用两段 `LaunchedEffect(Unit) + collect`，并专门注释解释了"为什么不能用 `LaunchedEffect(key)`"（曾经踩过撤销 Snackbar 不显示的坑）。

**建议**

- 统一为 `private val _events = Channel<UiEvent>(Channel.BUFFERED)` + `val events = _events.receiveAsFlow()`，`sealed interface UiEvent { ShowUndo(...); AutoSynced(...); Error(...); Toast(...) }`；UI 侧单个 `LaunchedEffect(Unit) { vm.events.collect { ... } }`；
- 所有 `onResult/onDone` 回调改为 `suspend fun` 返回 `Result<T>`（顺带解决 §1.3 的"写失败无反馈"）；
- 好处：事件不丢、可测（`Channel` 可断言）、`MainActivity` 少 40 行样板、不再需要给 `LaunchedEffect` 写注释解释。

**代价**：中（半天，机械改造）。

---

### 🟡 2.5 存储演进：JSON blob 的边界在哪

当前"整份 JSON 存在 Preferences 里 + 全量重写"的方案在 200–1000 条记录量级是合理的（而且已经被打磨得很仔细）。但它有三个固有代价：**单点损坏 = 全量不可读**、**每次写全量编码**、**无法增量查询/排序**。

**建议分三档，按触发条件执行**

| 档位 | 动作 | 触发条件 |
|---|---|---|
| A（现在就能做） | ① 每次成功写入前，把该 key 的**上一版原始串**留一份到 `filesDir/journal/<key>.prev.json`（只留 1 份）；② 提供"设置 → 高级 → 数据健康检查"（各 key 条数/字节数/损坏标记/上次备份时间 + 一键修复） | 立刻 |
| B | 把"凭据"与"用户资产"**拆成两个 DataStore 文件**（§1.6），让系统备份只排除凭据 | 与 §1.6 同批 |
| C | 若未来要加"多设备/多人/大量历史"或需要在 SQL 里排序分页，再迁 **Room**（`FoodItem` 一个表 + `ConsumptionRecord` 一个表，`ArchivedItem` 用 `deletedAt` 软删即可省掉归档逻辑） | 需求驱动，不要提前做 |

**代价**：A 半天；C 数天（不建议现在做）。

---

### 🟡 2.6 `MainActivity` 拆分（1,093 行）

它是"单 Activity + 全屏壳"的必然结果，但可以更薄：

- `MainApp.kt`（Scaffold/底栏/FAB/嵌套滚动）、`AppNavigation.kt`（`NavDisplay` + `entry` 注册表）、`NavChrome.kt`（`FloatingPillNav`/`MiuixFloatingNav`/`Md3BottomNav`/`BatchActionBar`）、`AppDialogs.kt`（多选"移动位置"等壳层对话框）；
- `NavDisplay` 的 `entry` 注册可以抽成 `@Composable fun NavGraph(backStack, viewModel, callbacks)`，让"新增页面 = 动 1 个文件"（现在是 3 处）；
- `scrollChromeVisible` 的 `NestedScrollConnection` 阈值（`±8f`）建议提为常量并加注释（同一手势在不同设备密度下手感差异明显）。

**代价**：小～中（半天，纯搬运，`git mv` 后编译验证即可）。

---

### 🟡 2.7 依赖注入与可测性（轻量版）

不必上 Hilt，只要：

```kotlin
class AppContainer(app: Application) {
    val clock: Clock = SystemClock()
    val store = FoodStore(app, clock)
    val images = ImageStore(app)
    val backup = BackupService(app, store, clock)
    val cloud  = CloudSyncService()
}
```

`AppViewModel(app)` 从 `(app as ChilemeApp).container` 取依赖；`AppViewModel` 因此可在 JVM 上用临时目录 DataStore + Fake 时钟测试（Robolectric 下 `PreferenceDataStoreFactory.create { File(tmp, "t.pb") }` 完全可行）。**这是把 §1.x 那一堆"必须补的集成测试"变成可能的前提。**

**代价**：中（1 天）。

---

### 🟡 2.8 可访问性（a11y）与 i18n

**a11y**

- 已做了不少：`CheckSwitch` 有 `Role.Switch`、触摸目标 ≥48dp、`DataCorruptBanner` 双主题共用——继续；
- 待办（部分在 backlog 里，属于"值得现在就顺手做"）：
  - `FloatingPillNav` 槽位固定 `76.dp`（`MainActivity.kt:1024`），4 项 + 内边距约 320dp；320dp 宽设备或 2.0 字号下文字会挤/裁切（`labelSmall` + `maxLines=1` 无 `overflow`）。建议 `widthIn(min = 64.dp)` / 按可用宽度均分，并加 `TextOverflow.Ellipsis`；
  - 统计图表（环形/柱状）无 `semantics`（backlog 已记："统计图表无 semantics"）→ 建议给图表容器加 `Modifier.semantics { contentDescription = "近 7 天消耗：周一 3 件…" }`，成本极低；
  - `FoodCard` 的整卡点击/长按没有 `contentDescription`（只有内部图标有）；多选模式需要朗读"已选择 x 项"；
  - 建议加一次 `fontScale = 2.0` + TalkBack 手测清单（写进 `WORKFLOW.md` 的验收步骤）。

**i18n**

- 现状：除 `app_name` 外全部中文硬编码在 Kotlin（`WORKFLOW.md` 也承认这点）。这带来的不只是"不能翻译"：文案无法被 lint 检查重复/缺失，改文案要动代码、无法做 A/B。
- 建议：至少把**设置页、首页、错误提示**三处抽到 `strings.xml`（其余渐进），并在 CI 加 `MissingTranslation` 规则；顺带解决"设置页版本号硬编码 `v1.0`"的可维护性问题（截图/支持场景下用户报不出构建号）。

---

### 🟡 2.9 测试策略：从"纯函数"扩展到"守卫与集成"

**现状**（10 个文件 / **73 例**，实测）：纯函数覆盖得不错——`statusForAt`、`compactConsumptionAt`、`planRestore`、`filterFoodItems`、`calculate*`、`parsePropfind`、v1→v2 兼容、三态解码。

**缺口（按价值排序）**

1. **仓储集成测试**（最高价值）：本项目最贵的逻辑是"守卫 + 多 key 原子写 + 自动归档 + 压缩"，**恰恰一条都没测**。用 Robolectric + 临时目录 DataStore 可覆盖：
   - 某 key 损坏 → 该 key 的写被拒绝、其他 key 仍可写（§1.2 改造后的期望行为）；
   - `changeQuantity(-1)` 到 0 → 归档产生 + 消耗记录产生 + 返回 `autoArchived=true`；
   - `undoConsumption` → 记录删除 + 数量回滚（含"已被自动归档"分支）；
   - `importBackupJson` 非法/超版本/超体积 → 拒绝且**不改动现有数据**；
   - 损坏态下 `cleanupOrphanCovers` 不删文件（§1.1 回归测试）。
2. **UI 冒烟测试**：`androidx.compose.ui:ui-test-junit4` + Robolectric，各跑一遍主流程（首页 → 列表 → 详情 → 消耗 → 归档 → 恢复），**MD3 与 MIUIX 各一次**——这是 §2.1 重构的安全网。
3. **属性测试**（可选，`kotest-property`）：`compactConsumptionAt` 的"总量守恒"（聚合前后 `sum(amount)` 不变）、`planRestore` 的"库存总数不减少"、JSON 往返 `decode(encode(x)) == x`。
4. **覆盖率门槛**：加 `kover`（或 Jacoco），对 `data/` 包设 70% 门槛（其余不强制），避免"测试写了但没跟上重构"。

---

## 3. 产品与交互建议（部分需先改需求边界）

> 依据 `docs/REQUIREMENTS.md` §4，下列标 **[需确认]** 的属于"明确不做"或超范围，按项目规矩应**先与用户确认并更新需求文档**。

| # | 建议 | 价值 | 备注 |
|---|---|---|---|
| 3.1 | **首次使用引导**：长按多选、卡片步进器、撤销 Snackbar 都没有可发现性提示（滑动归档已在 v2.3 移除，现在归档入口只有长按 + 设置页）；建议首启一次性的 coach mark 或首页卡片内一行提示 | 高 | 纯 UI |
| 3.2 | **列表排序/分组**：现在固定 `quantity==0 → daysLeft` 升序（`FoodListState.kt:95`）。建议加"按到期/名称/位置/最近添加"排序，以及"按存放位置分组"（家庭场景核心心智是"柜子/冰箱"） | 高 | 纯 UI |
| 3.3 | **搜索增强**：只支持 `name.contains`（中文可用，但"nn"搜"牛奶"不行）。建议加拼音首字母索引（`TinyPinyin` 之类，注意离线）与搜索历史 | 中 | 需新依赖 |
| 3.4 | **快捷录入**：详情页/列表卡片加"再来一件""复制为新条目"，减少重复填表 | 中 | 纯 UI |
| 3.5 | **分享/导出**：加"通过系统分享发送库存文本"（家庭群里发"冰箱清单"），比让家人装 App 更现实 | 中 | 纯 UI，复用 CSV 逻辑 |
| 3.6 | **导入/恢复的可解释性**：见 §1.4 的预览对话框；云端恢复列表加"内容摘要（几条库存/几条消耗）"而不是只有时间+大小 | 高 | 与 §1.4 同批 |
| 3.7 | **诊断包**：设置页"导出诊断信息"（版本、构建号、各 key 条数、损坏标记、封面数量、最近错误），用于无网络产品的排障 | 中 | 小 |
| 3.8 | **开源许可清单**：APK 内附第三方许可（Miuix / MaterialKolor / OkHttp / Coil / Compose），一行 `aboutlibraries` 或手写静态页 | 中 | 合规 |
| 3.9 | **大字号/高对比度**：backlog 已有"高对比度待评估"；建议至少保证 `fontScale=2.0` 不破版，并在设计规范里补一节"动态字号" | 中 | — |
| 3.10 | **[需确认] WorkManager 后台同步**：现在自动同步只在"打开 App 时"发生（`AppViewModel.kt:235-256`）。若用户希望"改完就自动上云"，需要 WorkManager（需求 §4 明确排除了 WorkManager） | 中 | 需改需求 |
| 3.11 | **[需确认] 临期提醒通知**：产品的核心承诺是"减少食物浪费"，但没有主动提醒（需求 §4 排除）。可考虑仅在用户允许时的本地通知 | 高 | 需改需求 |
| 3.12 | 首页 `LargeTopAppBar` 的 title 塞了两行（标题 + 日期），大字号下会挤压/裁切；建议日期移到内容区第一行 | 低 | 纯 UI |

---

## 4. 工程实践、CI 与文档

### 🟠 4.1 静态检查缺失：把 `CLAUDE.md` 的规则交给机器

`CLAUDE.md` 里已有大量**可机器检查**的约束，但目前只靠"人/AI 记得"：

- "禁止混用 material2 导入"→ detekt `ForbiddenImport`
- "所有布尔开关一律用 `CheckSwitch`，禁止 material3 `Switch`"→ `ForbiddenImport(androidx.compose.material3.Switch)`
- "禁止直接 `dataStore.data.map { decode }`"→ 自定义 `ForbiddenMethodCall` 或更实际的：在 `FoodRepository` 上加 `@VisibleForTesting` 式约定 + code review
- "删除类操作一律走归档"→ 可用 `ForbiddenMethodCall(itemsKey)` 之类粗粒度规则兜

**建议**：加 `ktlint`（+ `.editorconfig`）与 `detekt`（含 `formatting` + `complexity` + 上述 `ForbiddenImport`），CI 加 `./gradlew ktlintCheck detekt`（秒级）；`ImportOrdering`/`UnusedImports` 还能顺手清掉目前存在的未用 import。**这一步的 ROI 在"AI 参与开发"的仓库里尤其高**——规则变成可执行的，而不是靠提示词记忆。

### 🟠 4.2 CI 改进（`.github/workflows/`）

| 项 | 现状 | 建议 |
|---|---|---|
| release 构建验证 | 只在打 tag 时跑 `assembleRelease` | PR/main 加 `./gradlew assembleRelease -PallowUnsignedRelease=true`——**R8 keep 规则回归现在只可能在发版当天暴露** |
| 并发控制 | `release.yml` 有 `concurrency`，`build.yml` 没有 | `build.yml` 加 `concurrency: group: build-${{ github.ref }}, cancel-in-progress: true` |
| 权限 | `build.yml` 无 `permissions` | 加 `permissions: contents: read`（最小权限） |
| Action 版本 | 用 tag（`@v5`） | 建议 pin 到 SHA（供应链安全），或用 Dependabot 自动跟进 |
| 依赖更新 | 无 | 加 `renovate.json` / Dependabot（Version Catalog 已就位，正好可用 grouped updates） |
| 缓存/速度 | 无 configuration cache | `org.gradle.configuration-cache=true`（AGP 9 支持）；CI 加 `--build-cache` |
| 覆盖率 | 无 | kover + 门槛（见 §2.9） |
| 产物 | 每个 PR 上传 63 MB debug APK | `retention-days: 7`，或仅在失败时上传 |
| 静态检查 | 只有 lint | 加 ktlint/detekt |

### 🟠 4.3 文档漂移（六处已确认）

1. `ARCHITECTURE.md:114` 仍在描述**已删除的功能**："滑动归档（两段式）… 第一滑弹回进入 armed 待确认 … `remember(item.id)` 构造 SwipeToDismissBoxState"。全仓库已无 `SwipeToDismissBox` 用于列表（只有 `UndoSnackbar.kt` 的 Snackbar 用了它），`REQUIREMENTS.md` F5 也写着 v2.3 已改为长按多选。→ 删除该段，改为描述"长按多选 + 底部批量操作栏"。
2. `ARCHITECTURE.md` §3 注释："`category_thresholds`；key 为 `FoodCategory.name`"——实际已是 `CategoryDef.id`（`ManageState.kt:22-26`）。
3. `REQUIREMENTS.md` 头部："版本：v2.0 · 状态：已实现并验收"，而实际已到 v2.8.1（CLAUDE.md 自己写 v2.8）。→ 版本号应指向 devlog INDEX 或改为"截至 2026-08-22 全部已实现"。
4. `docs/audits/ci/build.yml` 与 `.github/workflows/build.yml` **逐字节相同**（已核对），`docs/audits/ci/release.yml` **已与真实工作流产生差异**（`diff` 可复现）。→ 删除 `docs/audits/ci/` 与两份 `.patch`（`2026-08-21-*.patch`），文档里改用链接指向真实文件；保留历史副本的代价是"读者会照着过期的 YAML 排查问题"。
5. `devlog/INDEX.md` 的"当前待办总览"里"高优先级：本地构建验证 v2.8"仍挂着（2026-08-22 的日志写"CI 通过"，但 INDEX 未同步）——建议核对一次。
6. `devlog/INDEX.md` 写"状态层单测…新增 16 例，**总 42 例**"，而仓库实际有 **73** 个 `@Test`（10 个文件，`grep -c` 实测；全部由最近一次提交引入）。测试规模被低估了 40%，会直接影响"要不要继续投入测试"的判断。

### 🟡 4.4 仓库卫生

- 无 `.editorconfig`（配合 ktlint 需要）；
- 无 PR/Issue 模板、无 `SECURITY.md`（有 WebDAV 凭据存储，写一条"如何报告安全问题"很合适）；
- README 有特性但**无截图/GIF**——这是 UI 项目最有效的说明；建议补 3–4 张（MD3 与 Miuix 各一张，正好体现双主题卖点）；
- README 的"数据 100% 存储于设备本地"需按 §1.6 的结论复核措辞；
- `.github/` 下可加 `FUNDING`/`CODEOWNERS`（可选）。

---

## 5. 优先级路线图

### 第一批 · 立刻做（每项 < 1 小时，风险低，价值高）

> **2026-09-16 状态校订**：10 项中 **8 项已在 PR #7（`58534fd`）落地**，第 5、6 项（统计口径文案与 TOP5 分组）**用户指示暂缓**，代码未改。

| # | 动作 | 位置 | 状态 |
|---|---|---|---|
| 1 | 损坏态下跳过封面清理 | `AppViewModel.kt:219` | ✅ 已修 |
| 2 | `ready` 加 3 秒超时兜底（永远不要卡启动页） | `MainActivity.kt:198` / `AppViewModel.kt:119` | ✅ 已修（`READY_TIMEOUT_MS`） |
| 3 | 读流加 `.retryWhen` + `.catch`（IO 异常不再崩） | `FoodRepository.kt:168-175` | ✅ 已修（`resilientRead()` 收口 19 个读流） |
| 4 | `loadLocalSnapshots` 切 IO + `itemCount` 改为解析 `BackupData` | `AppViewModel.kt:387`, `LocalSnapshotStore.kt:58-78` | ✅ 已修（三方法 suspend + `Dispatchers.IO` + `countItemsInSnapshot()`） |
| 5 | "本周消耗"文案改"近 7 天"（或改成周一起算） | `StatsScreen.kt:122`, `StatsState.kt:42` | ⏸ **暂缓**（用户指示；`StatsScreen.kt:122` 仍为「本周消耗」） |
| 6 | TOP5 按 `(name, unit)` 分组 | `StatsState.kt:69` | ⏸ **暂缓**（仍 `groupBy { it.name }`） |
| 7 | 导入备份加二次确认 + 导入前自动本地快照 | `SettingsScreen.kt:125`, `MiuixSettingsScreen.kt` | ✅ 已修（另加 `previewBackup()` 校验与 20 MB 上限） |
| 8 | 删除漂移的 `docs/audits/ci/*.yml` 与 `*.patch` | `docs/audits/` | ✅ 已删（4 个文件） |
| 9 | 修正 `ARCHITECTURE.md` 的"滑动归档"段落与 thresholds 注释 | `docs/ARCHITECTURE.md:114` | ✅ 已修 |
| 10 | CI：`build.yml` 加 `concurrency` + `permissions: contents: read` | `.github/workflows/build.yml` | ✅ 已修 |

### 第二批 · 两周内（每项 0.5–1 天）

> **2026-09-16 状态校订**：2/3/4/6 已完成，1/5/8 未动，7 部分完成。

1. ❌ **未做** 错误模型统一：`Result` 返回值 + `Channel<UiEvent>` + 全局异常处理器 + 写失败反馈（§1.3 / §2.4）——实测全项目 `Channel<` / `UiEvent` 0 处
2. ✅ 写守卫改按 key 粒度 + 提供"放弃损坏数据"入口（§1.2）
3. ✅ 导入/恢复:校验 + 版本迁移 + 结果对象 + 体积上限（§1.4）
4. ✅ 仓储集成测试落地（**未用 Robolectric**，改注入临时 DataStore + 临时留档目录，8 例；理由见 devlog §13.3）
5. ❌ **未做** 凭据拆库 + 备份规则调整（§1.6）；封面改存文件名（§1.7）——`photoPath` 仍存绝对路径，`data_extraction_rules.xml` 未调整
6. ✅ ktlint + detekt + editorconfig 上 CI（§4.1）；CI 增加 release 构建验证（§4.2）
7. 🟡 **部分**：浪费口径已改按件数；**归档上限提示与浪费独立计数器未做**（`take(200)` 仍在 `FoodRepository.kt:487/630`）
8. ❌ **未做** `MainActivity` 拆分（§2.6）——现 1,115 行（`Common.kt` 983 行）

### 第三批 · 一两个月（结构性）

> **2026-09-16 状态校订**：全部未动（属结构性重构，需先有第 1 项的组件层与测试安全网）。

1. **App 级组件层 + 页面去重**（§2.1）——最大收益，也最大工作量
2. 派生数据下沉 VM + `WhileSubscribed`（§2.2）
3. Repository 拆分 + `Clock` 注入 + 轻量 DI（§2.3 / §2.7）
4. 字符串资源化 + a11y 补全（§2.8）
5. 数据健康检查页 / 诊断包 / 许可清单（§2.5-A / §3.7 / §3.8）
6. 产品侧：排序分组、首启引导、分享清单（§3.1–3.5）
7. **需与用户确认范围**：临期通知、WorkManager 后台同步（§3.10 / §3.11）

---

## 6. 本次审查的局限

- 未编译、未运行、未上机（沙箱无 JDK / Android SDK），所有结论来自源码与配置的静态推演；涉及运行时行为的判断（§1.1/§1.3）已在文中给出触发路径与代码证据，但仍建议先在真机按路径复现一次再改。
- 未评估第三方库（Miuix 0.9.4-rc01、MaterialKolor）的 API 正确性；Miuix 相关 API 应以 `.claude/skills/miuix` 的 pinned source 为准。
- 未做性能剖析（无设备），§1.9 / §2.2 的性能结论基于"主线程 I/O 与主线程全量解码"的事实推断。

---

## 7. 结论可靠性自评（针对"这份审计是否合理"）

把本文的结论按**证据强度**分三类，便于决定"照做"还是"再确认"：

### A. 事实级（可用命令行/`file:line` 复现，可直接照做）

§1.1（损坏态删封面）、§1.2（守卫粒度过粗）、§1.4（导入无确认/无校验/无版本判断）、§1.5（明文回退分支存在）、§1.6（备份规则排除整库、`covers/` 却在 device-transfer 中未排除）、§1.7（绝对路径）、§1.8（`take(200)` + 统计依赖它）、§1.9（主线程 I/O + `"id":` 计数偏大）、§1.10（"本周"= 近 7 天；TOP5 只按 name 分组）、§1.11 全部、§4.3 全部（含 `docs/audits/ci/*.yml` 已漂移、`ARCHITECTURE.md:114` 描述已删除功能）。

这些不依赖对运行时行为的推测：要么是代码里"读得到"的事实，要么是两次 `grep`/`diff` 可直接复现的差异。

### B. 推断级（有代码证据，但严重程度依赖运行时表现，建议先复现再定性）

- §1.3（异常 → 崩溃 / 卡启动页）：崩溃路径由 `SupervisorJob` + 无 `CoroutineExceptionHandler` 的协程语义推出，**触发条件**（DataStore 读/写抛 `IOException`、文件损坏）在真机上属小概率；建议先按"读流加 `catch` + `ready` 加超时"修掉，风险为零；
- §1.9 / §2.2 的性能结论：来自"主线程 I/O / 主线程 `combine`"的事实，具体卡顿幅度未实测；
- §1.11(a)（上传成功却报失败）：由 `runCatching` 的范围推出，需一次网络异常场景才能观察。

### C. 判断/取舍级（**不建议直接照做，需要你决策**）

- §1.2 的"守卫改按 key 粒度"**修改了项目已写进文档的安全不变量**（`ARCHITECTURE.md` §5「损坏时放弃写入」）。它换来可用性，但把"整表覆盖"的风险窗口从 0 变成"仅限损坏的那个 key"。这是产品级取舍，应由你确认后再改文档、再改代码；
- §1.6 的"凭据拆库 + 用户数据参与系统备份"**改变了隐私承诺的措辞**（README 现称"数据 100% 仅存本机"）；
- §2.1（组件层去重）的工作量是估算；§2.5 的"何时上 Room"取决于未来需求；
- §3.x 中标注 **[需确认]** 的三条属于 `REQUIREMENTS.md` §4 的"明确不做"，按项目规矩必须你点头；
- §1.10 改文案（"本周"→"近 7 天"）与改算法（周一起算）是两种产品选择，本文只指出不一致，未替你选。

### D. 本文自身的已知局限（复核后已修正）

- 初稿把"读流异常"的后果写成"崩溃与卡启动页都会发生"——更准确的表述是：**崩溃是主因，卡启动页只在"流不发射"时出现**（§1.3 已更新）；
- 初稿称点开"本地快照"**必然 ANR**——夸大了，实际是主线程 I/O 导致的卡顿，ANR 需要异常大的快照（§1.9 已更新）；
- 初稿沿用 `devlog/INDEX.md` 的"42 例"，实测为 **73 例**；另把 `stateIn` 数量写成 25，实测 **19**（§0 / §2.2 / §2.9 已更正）；
- 初稿的 `BackupData.version` 行号写错一行（应为 `FoodModels.kt:145`，§1.4 已更正）；
- **未编译、未运行、未上机**：任何与运行时性能、Android 版本行为、厂商 ROM 差异相关的判断都未经验证。
