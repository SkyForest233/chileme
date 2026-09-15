# 第三方审查报告《chileme-review.md》独立复核（2026-09-15）

> 被复核对象：`docs/audits/chileme-review.md`（master 分支，审查基线 commit `41a728f`）
> 复核方式：逐条回到源码取证（`file:line` + 可复现命令），另对两条外部技术前提做了核实（`material-icons-extended` 弃用状态、targetSdk 35+ 的 IME inset 行为）
> 复核范围：其全部 P0/P1/P2/P3 条目共 **39 条**（P0×6 + P1×11 + P2×8 + P3×14），逐条判定为 ✅成立 / ⚠️需修正 / ❌不成立 / ❓未验证
> 本文件与 `docs/audits/2026-09-15-code-review.md`（本仓库自查）互为交叉验证

---

## 0. 结论摘要

**总体判断：这份审查质量高、可信度明显高于一般 AI 生成报告，但含 1 处 P0 级事实错误与 3 处需要修正的技术建议，不应原样照做。**

| 判定 | 条数 | 说明 |
|---|---|---|
| ✅ 成立（证据精确到行，可复现；含 3 条"合理但非新信息"的纯建议类） | 30 | 包括其 P0-2/P0-3/P0-5/P1-10/P3-6 等**本轮自查未发现**的真问题 |
| ⚠️ 需修正（方向对，事实/修法/表述要改） | 6 | P0-1 修法、P0-4 表述、P0-6 其余部分、P1-5 表述、P1-6 建议、P1-7 表述 |
| ❌ 不成立 | 1 | P0-6 的"device-transfer 未排除 DataStore"（文件里确有该条） |
| ❓ 未验证（引用了但无法在无设备环境证实） | 2 | APK 体积数字、真机 IME 行为 |
| 数字错误 | 2 | 双主题合计行数（其自身表格求和 5,783，正文写 6,583）、Kotlin 文件数（实测 55，其写 51） |

**它的可信度证据（我做了可复现性抽查）：**

- 行号抽查 30+ 处，**绝大多数精确命中**（例：`Common.kt:262`、`EditFoodScreen.kt:222-223`、`MainActivity.kt:900-901`、`SettingsState.kt:130`、`StatsState.kt:94`、`MiuixSettingsScreen.kt:69/391/824`、`SettingsScreen.kt:569/882`、`AppViewModel.kt:91/216-217/259`、`release.yml:112`、`ARCHITECTURE.md:56/58/63`、`README.md:37` 全部与实测一致）；
- 双主题重复度的 8 个"差异/合计"数字，我用同一方法（去 import/package/空行 + `difflib`）**复算全部命中**：389 / 272 / 549 / 680 / 567 / 771 / 1764 / 791；
- `contentDescription = null` 61、`semantics` 7、`@Test` 73、`dataStore.edit` 28、`imePadding` 0、`GlobalScope` 0 等计数**全部与实测一致**。

→ 因此下面那处 P0-6 错误更像是自己笔记的抄录失误（其自引代码块其实是**正确**的，与正文结论自相矛盾），而不是理解错误；但因为它会误导读者去做一次无意义的"修复"，必须点名。

---

## 1. ❌ / ⚠️ 必须修正的条目

### 1.1 ❌ P0-6 的核心断言不成立：`device-transfer` **确实**排除了 DataStore

它写：

> `device-transfer` 里**并没有** `datastore/` 这一条（我读了完整文件，只有 `corrupt/` 两行 exclude）……后果：换机直传时 DataStore 走的是默认全量包含，把 `nutstore_password_enc` 传过去

**实测**（`app/src/main/res/xml/data_extraction_rules.xml`，`git show 41a728f:` 同样结果）：

```xml
 9  <data-extraction-rules>
10      <cloud-backup>
11          <exclude domain="file" path="datastore/" />
12          <exclude domain="file" path="covers/" />
13          <exclude domain="file" path="corrupt/" />
14      </cloud-backup>
15      <device-transfer>
16          <exclude domain="file" path="datastore/" />   ← 就在第 16 行
17          <exclude domain="file" path="corrupt/" />
18      </device-transfer>
19  </data-extraction-rules>
```

- 它**自己贴出的代码块**里也写了 `<device-transfer> <exclude datastore/> <exclude corrupt/>`——**正文与自引证据互相矛盾**；
- 文件注释"cloud-backup 与 device-transfer 两者都排除 DataStore"**与实际一致**，不存在"注释与代码不一致"；
- 因此"换机直传会带走 Keystore 密文凭据"**不成立**（密文凭据在两条通道都被排除）。

**该条中仍然有效的部分（建议保留）：**

1. `filesDir/snapshots/`（每日一份完整库存 JSON）**确实未在任何通道排除** → 会进 Android 云备份。这与 README"数据 100% 存储于设备本地、无云端"的表述确有落差（属于**措辞问题**，不是安全漏洞：备份进的是用户自己的 Google 账号）。这一条是本报告里真正有价值的新发现，且我的上一轮自查也漏了；
2. `covers/` 只在 `cloud-backup` 排除、`device-transfer` 未排除（照片会跟着换机直传，而库存不会）——这一点两轮审查结论一致；
3. "把'设备绑定的秘密'与可备份数据拆开"的方向仍然成立（这也是我上轮报告的 §1.6）。

**修正后的判定**：P0 降级为 P2；动作从"修 device-transfer 规则"改为"排除 `snapshots/`（或明确接受并改 README 措辞）+ 补测一次换机直传"。

---

### 1.2 ⚠️ P0-1 修法 A/B 都不对症：`contentWindowInsets` 不会把 `bottomBar` 里的保存按钮抬起来

**它的诊断（成立，且我复核后确认）**：全项目 `imePadding` / `WindowInsets.ime` / `contentWindowInsets` = 0；`MainActivity.kt:169 enableEdgeToEdge()`；`AndroidManifest.xml:23 adjustResize`；`EditFoodScreen.kt:182 Scaffold` + `:249 verticalScroll`（行号全部精确）。targetSdk 36 时，Android 15+ 上 `adjustResize` 已不再替应用垫高根视图，必须自行消费 IME inset —— 这一点有官方依据（Android Developers《Insets handling tips for Android 15's edge-to-edge enforcement》："After targeting SDK 35, you must also account for the IME ... because the framework will *not* pad the window's root views"）。

**但它的两个修法都救不了它自己指出的那处症状**：

- 修法 A `contentWindowInsets = WindowInsets.ime.union(systemBarsForVisualComponents)`：Material3 的 `contentWindowInsets` 只作用于 **content slot** 的 PaddingValues（官方 release note 用词即 "insets to handle for the **content slot**"，`1.0.0-beta02`）。`bottomBar` 槽位是独立摆放的，`NavigationBar`/`BottomAppBar` 都自带 `windowInsets` 参数自己消费——本项目 `EditFoodScreen.kt:198-199` 的 bottomBar 是**自定义 `Surface { Button }`**，不消费任何 inset。结果：内容被垫高，**"添加到零食柜"按钮仍然被键盘盖住**；
- 修法 B 只给 content 里的 `Column` 加 `imePadding()`，同样不动 bottomBar（它自己也承认"topBar 不避让"，但没意识到 bottomBar 才是它诊断出的症状所在）；
- 附带：它建议把 manifest 的 `adjustResize` **改成 `adjustNothing`（或删掉）**——这与 Google 同一篇指引相悖（该文明确仍要求设置 `adjustResize` 为 IME 让位；社区经验也是保留它能获得更稳定的 IME inset 回调）。

**可用的最小修法**（需真机微调，但方向明确）：让 **bottomBar 自身或其容器**位移，而不是只调 content slot，例如

```kotlin
Scaffold(modifier = Modifier.imePadding(), ...)          // 整体（含 bottomBar）上移
// 或：bottomBar = { Surface(Modifier.imePadding()) { ... } }
// 需要时配合 WindowInsets.isImeVisible 收起底栏
```

并在 `DESIGN_SPEC.md` 落一条硬约定（这一点它的建议是对的）：**任何含 `TextField` 的屏幕必须消费 IME inset**。项目的暴露面不止编辑页——`ArchiveScreen`、`FoodListScreen`（搜索框）、`ManageScreens`、`MiuixManageScreens`、两个设置页都有输入框。

---

### 1.3 ⚠️ P1-6 / P1-7 的事实与诊断成立，但"改 Crossfade"这条建议要打折

- **P1-6 事实精确**：`MainActivity.kt:900-901`（`userScrollEnabled = false` + `beyondViewportPageCount = 3`），4 个 Tab 确实全部常驻、各自订阅 flow，每次数据变化都重算 —— 成立；
  - **但"pager 剩下的唯一作用是保留页状态，而这正是 `rememberSaveable` 已经在做的事"不准确**：各 Tab 里 `LazyColumn` 的滚动位置依赖 `LazyLayout` 按页的 saveable 机制（`rememberLazyListState` 本身并不在页面级 `rememberSaveable` 覆盖范围内）。直接换成 `Crossfade`/`AnimatedContent` 会让**每次切 Tab 都回到列表顶部**，除非外加 `SaveableStateHolder`；
  - 更稳的折中是它自己也提到的 **`beyondViewportPageCount = 0/1`**，风险最低、收益接近；
- **P1-7 诊断精确**：`Theme.kt` 的 `animateColorScheme` 里实测 **36 个 `animatedColor(...)`**（比项目文档自己写的"35 个角色"还准），每帧构造新 `ColorScheme` → 读 `MaterialTheme.colorScheme` 的 composable 每帧重组，成立；
  - **但"`Crossfade` 一个 ColorScheme → 不重组子树、代价与内容复杂度无关"表述不准确**：`Crossfade` 在过渡期会**同时组合两棵子树**（双份 composition/measure），且 `targetState` 变化时内容仍会重组一次。准确的收益是"每帧 O(1) 次 → 每次切换 1 次重组"，量级差 20+ 倍，但机制描述被夸大了。

---

### 1.4 ⚠️ 两处数字错误 + 两处表述过强

| 项 | 它写的 | 实测 | 性质 |
|---|---|---|---|
| 8 对双主题合计行数（§0 表格） | 6,583 行 | 其**自己的分项表**求和 = **5,783**（我用同一方法复算，8 个分项全部命中） | 数字笔误，差 800 |
| `app/src` Kotlin 文件数 | 51 个 | **55**（main 45 + test 10）；行数 14,886 正确 | 计数错 |
| P1-5 双击重复插入 | "连点两次会产生两条同内容记录" | 机制上可能（`existing` 取自 `viewModel.items.value`），但 `onBack()` 紧随其后立即出栈，实际窗口极小 → 应标为"理论风险" | 表述过强 |
| P0-4 触发频率 | "**每天首次**启动都会在主线程读最多 3 份快照" | `maybeAutoSnapshot()`（`AppViewModel.kt:258-259`）**无条件**先 `listSnapshots()` 判断"今天有没有快照"→ **每次冷启动**都会主线程读 + 正则扫描（比它说的更严重） | 低估（可修正为更强） |

另：它建议的 **P3-1 Glance 桌面小组件**与 `docs/REQUIREMENTS.md §4`「❌ 桌面小组件（Widget）」「❌ 推送通知 / 后台提醒」的**用户已确认边界冲突**，文中未标注"需先改需求边界"。按本项目自己的规矩（`WORKFLOW.md` §1.2 / `CLAUDE.md` §4.2），这类建议必须先与需求方确认才能进入待办。

---

## 2. ✅ 成立且值得直接采纳的条目（含我上轮漏掉的）

按价值排序，**粗体为本轮自查（`2026-09-15-code-review.md`）未发现、而它抓到的问题**：

| 编号 | 它的结论 | 我的复核证据 | 价值 |
|---|---|---|---|
| P0-2 | **`MiuixStatsScreen` 是全项目唯一不接状态层的屏幕，手抄了 StatsState 的业务计算** | 逐文件核对 `remember*UiState`：**只有 `MiuixStatsScreen` 没有**；它在 `:82-114` 内联了 weekAgo/monthStart/趋势/占比/浪费；`StatsStateTest` 的 5 例在 MIUIX 主题下根本不执行 | 🔴 高：静默分叉的源头 |
| P0-5 | **月度聚合记录在消耗记录页被当普通记录展示，且能被一条删除抹掉一整月** | `compactConsumptionAt`（`FoodModels.kt:232`）把 90 天前流水按「年×月×名称×单位」聚合、`epochDay` 归一到当月 1 号；`ConsumptionLogScreen.kt:130` `itemsIndexed` + `:134` 每行有删除；全项目无 `isAggregate` 标记 | 🔴 高：不可逆的历史丢失（撤销只在内存，杀进程即失效） |
| P3-6 | **MIUIX 风格下 15 套配色全部不可用，且切过去后"无声失效"；而 KDoc 声称功能对等** | `MiuixSettingsScreen.kt:69` KDoc 列了"配色"；该文件 `AppPalette` 出现 **0 次**；外观 Card 只有 主题风格/深色/动态取色/悬浮导航；`MainActivity.kt:209-218` 只在 **MD3 分支**把 `AppPalette` 传给主题 | 🔴 高：UI 承诺与实现不一致 |
| P0-3 | **「过期浪费」数条数而非件数；同屏「本周消耗」却按件数求和** | `StatsState.kt:94` 与 `MiuixStatsScreen.kt:91` 同错；`FoodRepository.kt:335` 归档保留原 `quantity`；同文件 `:44/:49/:56/:72` 用 `sumOf { amount }` | 🟠 中高 |
| P1-4 | 导出/导入在主线程做全量 pretty-print 序列化 + SAF 写；`rememberCoroutineScope` 随屏幕取消可留半截文件；`readBytes()` 无上限；**合法空档 `{"items":[]}` 会静默清空全部数据** | `SettingsScreen.kt:99/103/109/132/569`、`MiuixSettingsScreen.kt:87/97/120/391` 行号精确；`BackupData` 全字段有默认值 → 空档解码成功 → `importBackupJson` 覆盖全部 key；无二次确认（云端恢复/快照恢复却都有） | 🔴 高（与我上轮 §1.4 结论一致，互为交叉验证） |
| P1-10 | 双主题重复度量化 | 8 个分项数字**全部复算命中**；合并为一份后预计可删 ~2,300-2,500 行（它估 1,500-1,900，偏保守） | 🟠 中高 |
| P1-11 | 28-key `remember` 打包成一个对象，**重组粒度比逐个 `collectAsStateWithLifecycle` 更粗** | `SettingsState.kt:137` 起 28 个 key；`SettingsUiState` 约 30 个数据参数 + 10 个回调 | 🟠 中高（与"派生上移 VM"方向一致，双方结论互补） |
| P1-3 | 同一个冷流被 3 处收集 → 同一份 JSON 解码 3 遍；启动期又 `first()` 两次 | `AppViewModel.kt:42 / :59 / :120`（三处收集 `itemsFlow`）+ `:216-217` | 🟠 中 |
| P1-8 | 组合期同步 `File.exists()`；`photoPath` 应只存文件名 | `Common.kt:262`、`EditFoodScreen.kt:263` 双双精确 | 🟠 中 |
| P1-9 | 明文回落分支 + `SecureStore` 无日志 + 明文常驻 StateFlow | `FoodRepository.kt:602-613`、`SecureStore.kt:39-47`、`AppViewModel.kt:91` 精确 | 🟠 中 |
| P3-11 | 详情页"吃掉一份"没有撤销，而列表页减号有 | `FoodDetailState.kt:48-50 → AppViewModel.kt:325-326`（`withUndo` 默认 false） | 🟠 中 |
| P3-10 | 归档页单条"彻底删除"无二次确认也无撤销（清空有确认、恢复有撤销） | `ArchiveScreen.kt:180` `onDelete = { state.deleteEntry(...) }`；`AlertDialog` 只服务 `showClearDialog` | 🟡 中低 |
| P3-8 | CSV 未防公式注入；CSV 只导库存 | `CsvExport.kt:42-48` 只处理逗号/引号/换行 | 🟡 低（易修） |
| P3-12 | `corrupt/` 目录无上限（每进程每 key 一份，跨重启累积） | `FoodRepository.kt:103 → :138` | 🟡 低（易修） |
| P3-14 | 导出文件名只带日期，同日二次导出会撞名；云端已用 `yyyyMMdd_HHmmss` | `SettingsScreen.kt:569`、`MiuixSettingsScreen.kt:391` 精确 | 🟡 低（易修） |
| P3-3 | 相机临时文件 `cacheDir/camera/*.jpg` 无人删除 | `EditFoodScreen.kt:170-177` | 🟡 低 |
| P3-13 | 5 处文档漂移 | `ARCHITECTURE.md:56`（"上限 1000"，且 `take(1000)` 已不存在 ✓）、`:58`（`FoodCategory.name` ✓）、`:63`（"UI 一律用 `statusFor`"，实际 UI 已全用 `statusForAt` ✓）、`README.md:37`（"OkHttp 5" vs catalog `4.12.0` ✓）、`WORKFLOW.md`（"加入 app/build.gradle.kts"，应为 libs.versions.toml ✓，行号 35 而非 31） | 🟡 低（易修） |
| P2-4 | **`material-icons-extended` 已停止维护** | 外部核实成立：Google 已停止发布更新并从最新 Material3 移除（2025-09）；项目实际用 38 个图标（它写 40） | 🟡 低（技术债清理） |
| P2-2 / P2-3 / P2-5 / P2-6 / P2-7 | CI 三次独立 `--no-daemon` 调用、无 `concurrency`；`run_number` 作 versionCode 的脆性；缺仓储/守卫测试；缺 DI；a11y 清单 | `build.yml` 三次调用 ✓、无 `concurrency`/`paths-ignore` ✓；`release.yml:112` ✓；`@Test` 73 ✓；`contentDescription = null` 61 / `semantics` 7 ✓；无 Application 类、`NutstoreSync` 为全局 object ✓ | 🟡 低-中（与我上轮 §2.7/§2.9/§4.2 一致） |
| P1-1 | 守卫应模板化（改成"结构性不可能忘"） | 复核其前提：`dataStore.edit` **28** 处、12 个资产型写方法**全部**带 `isCorrupt` 守卫（另有 `clearAll`/`clearArchive`/`importBackupJson` 三处有意豁免）——**其"无遗漏"结论成立** | 🟠 中（但见下条保留意见） |

**对 P1-1 的保留意见**：它只核对了"**有没有**守卫"，没有核对"**守卫的粒度是否合理**"。实测存在粒度问题：`upsert` 的守卫同时要求 `history_entries` 健康（`FoodRepository.kt:303`），于是这个可重建的联想缓存一旦损坏，**整个新增/编辑功能被锁死**；`changeQuantity` 同理（`:449`）。模板化能防"漏写"，但会把当前的粗粒度**固化**下来——两轮审查的这两条建议应合并实施：先按 key 粒度拆分，再用模板收敛（见 `2026-09-15-code-review.md` §1.2）。

---

## 3. 两轮独立审查的交叉验证结果

### 3.1 双方都指出的（置信度最高，应优先修）

1. 导入/恢复缺乏防护（无二次确认、无版本校验、空档即清空、无体积上限）—— 它 P1-4 / 我 §1.4；
2. 「过期浪费」与「本周消耗」口径不一致（数条 vs 数件；"本周"实为近 7 天）—— 它 P0-3 / 我 §1.10；
3. 凭据的明文回落分支 + `SecureStore` 静默失败 —— 它 P1-9 / 我 §1.5；
4. 主线程 I/O（快照列表/导出序列化）—— 它 P0-4、P1-4 / 我 §1.9、§2.2；
5. 双主题平行实现的维护税 —— 它 P1-10 / 我 §2.1；
6. `take(200)` 静默丢弃归档 —— 它 P3-2 / 我 §1.8；
7. 状态容器写法与派生化（方向一致：上移 VM）—— 它 P1-11 + P2-5 / 我 §2.2、§2.9；
8. 编辑页输入约束过松、`existing` 读 `items.value` —— 它 P3-4/P3-5 / 我 §1.11 j/i；
9. 封面绝对路径不可移植 —— 它 P1-8 尾 / 我 §1.7；
10. 文档漂移 —— 它 5 处 / 我 6 处，**去重合并后共 10 处**：
    ① `ARCHITECTURE.md:114` 仍在描述已删除的两段式滑动归档（我）；② `ARCHITECTURE.md:56` "消耗流水上限 1000"（它，且 `take(1000)` 已不存在）；③ `ARCHITECTURE.md:58` 阈值 key 写 `FoodCategory.name`（双方）；④ `ARCHITECTURE.md:63` "UI 一律用 `statusFor`"（它，UI 实际已全用 `statusForAt`）；⑤ `REQUIREMENTS.md` 头部"版本 v2.0"（我）；⑥ `docs/audits/ci/*.yml` 与真实 workflow 已漂移、两份 `.patch` 过期（我）；⑦ `devlog/INDEX.md`（"待验证 v2.8"未销项 + 单测"42 例"实为 73 例）（我）；⑧ `README.md:37` "OkHttp 5"（它，catalog 为 4.12.0）；⑨ `WORKFLOW.md:35` 依赖登记位置仍是 `app/build.gradle.kts`（它）；⑩ `README.md:37` 声明依赖 "AndroidX Security Crypto"，但项目**根本没有这个依赖**（`SecureStore` 直接用 AndroidKeyStore API，catalog 无 `security-crypto`）（我，新增）。

### 3.2 只有它抓到的（我上轮漏了）

`MiuixStatsScreen` 分叉（P0-2）、聚合记录可被单条删除（P0-5）、MIUIX 下配色失效（P3-6）、CSV 公式注入、归档单条删除无确认、相机临时文件、`corrupt/` 无上限、导出文件名撞名、**IME 完全未处理**、`material-icons-extended` 已弃用、`ConsumptionRecord` 缺 `itemId` 导致按名反查（`StatsScreen.kt:299`）、`snapshots/` 未排除而进云备份。

### 3.3 只有我抓到的（它漏了）

- **损坏态下启动会删除全部封面图**（P0，两轮中最严重的 bug 类问题）：`AppViewModel.kt:214-219` 用会回落空集的 `itemsFlow.first()` 组装引用集 → `cleanupOrphanCovers` 删光（`ImageStore.kt:124-136`）；
- **读流无 `catch`、`ready` 无超时**（异常 → 进程崩溃为主因；不发射 → 永久卡启动页）；
- **守卫粒度**把非核心 key 的损坏放大成核心功能不可用（见 §2 保留意见）；
- 归档丢失与"浪费统计"耦合（它只说"静默丢弃最老归档"，没连到统计口径）；
- 恢复/导入缺**自动快照兜底**与**版本迁移分发**；
- `EditFoodScreen` 日期选择器用 `ZoneOffset.UTC` 解析 `DatePicker` 毫秒值（UTC+13/+14 用户会差一天）；
- `LocalSnapshotStore.listSnapshots` 的 `itemCount` 正则口径（这条**双方都命中**，见 §3.1 第 4 项）；
- 统计趋势/配色的另外两处口径问题（分类占比只用当前库存、TOP5 混入聚合记录）；
- `README.md:37` 声称依赖 "AndroidX Security Crypto"，但项目没有这个依赖（`SecureStore` 直接用 AndroidKeyStore API，`libs.versions.toml` 无 `security-crypto`）；
- `compactConsumptionAt` 每次写入都会为聚合记录**重新生成 UUID**（`idFactory()`），导致 `LazyColumn` 的 `key = record.id` 抖动、待撤销的聚合记录匹配失败——这条两轮都没提，属本轮复核新增。

---

## 4. 合并后的"该先做什么"（仅列双方交叉验证或单方高置信度项）

| 顺序 | 动作 | 来源 |
|---|---|---|
| 1 | `ready` 加 3 秒超时 + 读流加 `catch/retryWhen` | 我 §1.3（零风险，5 行） |
| 2 | 损坏态下跳过封面清理 | 我 §1.1（1 行） |
| 3 | 导入备份：二次确认 + 预览 + 导入前自动快照 + `version` 校验 + 体积上限 | 双方 |
| 4 | 「过期浪费」改 `sumOf { quantity }`；"本周"改文案或周一起算；TOP5 按 `(name, unit)` 分组 | 双方 |
| 5 | IME：给含输入框的屏幕消费 ime inset（**作用于 bottomBar，不是只动 content slot**），保留 `adjustResize` | 它 P0-1（修法已修正） |
| 6 | `MiuixStatsScreen` 接回 `rememberStatsUiState`；补"MIUIX 侧不得重算业务量"的约定 | 它 P0-2（我上轮漏） |
| 7 | 消耗记录区分"月度聚合"（加 `aggregated` 标记 + 禁用删除或折叠展示） | 它 P0-5（我上轮漏） |
| 8 | MIUIX 补配色入口，或改 KDoc 声明为"配色仅 MD3 生效" | 它 P3-6（我上轮漏） |
| 9 | 凭据：加密失败不落明文 + 加日志 + 不再常驻明文 StateFlow | 双方 |
| 10 | 主线程 I/O 全部下沉（`LocalSnapshotStore` 三方法、导出/导入走 VM + IO） | 双方 |
| 11 | 归档页单条删除加撤销；详情页"吃掉一份"对齐撤销 | 它 P3-10/P3-11（我上轮漏） |
| 12 | 文档同步（两轮合并去重共 10 处，见 §3.1-10）；CI 加 `concurrency` + 合并 gradle 调用 + release 构建验证 | 双方 |

---

## 5. 复核方法（可复现）

```bash
# 关键：先确认被复核的文件内容（该文件在 master，本地 checkout 亦有）
sed -n '9,19p'          app/src/main/res/xml/data_extraction_rules.xml   # P0-6 反例
git show 41a728f:app/src/main/res/xml/data_extraction_rules.xml          # 基线一致

grep -rn "imePadding\|WindowInsets.ime\|contentWindowInsets" app/src/main --include=*.kt   # 0/0/0
grep -n "enableEdgeToEdge()"  app/src/main/java/com/agon/app/MainActivity.kt   # :169
grep -n "windowSoftInputMode"   app/src/main/AndroidManifest.xml               # :23

grep -rn "remember[A-Za-z]*UiState" app/src/main/java/com/agon/app/ui/screens/*.kt  # 唯一缺席：MiuixStatsScreen
grep -n "wastedTotal" app/src/main/java/com/agon/app/ui/screens/{StatsState,MiuixStatsScreen}.kt
grep -n "File(.*photoPath).exists()" app/src/main -r --include=*.kt        # Common.kt:262 / EditFoodScreen.kt:263

# 双主题重复度复算（其方法：去 import/package/空行）
python3 - <<'PY'
import difflib
S='app/src/main/java/com/agon/app/ui/screens/'
def norm(p): return [l.strip() for l in open(S+p) if l.strip() and not l.strip().startswith(('import ','package '))]
a,b=norm('ConsumptionLogScreen.kt'),norm('MiuixConsumptionLogScreen.kt')
d=[x for x in difflib.unified_diff(a,b,lineterm='') if x[:1] in '+-' and x[:3] not in ('+++','---')]
print(len(a),len(b),len(a)+len(b),len(d))   # 142 130 272 50 —— 与其分项完全一致
PY

grep -c 'dataStore.edit' app/src/main/java/com/agon/app/data/FoodRepository.kt    # 28
grep -c 'isCorrupt('     app/src/main/java/com/agon/app/data/FoodRepository.kt    # 14（12 守卫 + 1 有意豁免 + 1 check）
grep -rn 'contentDescription = null' app/src/main --include=*.kt | wc -l          # 61
grep -rh '@Test' app/src/test | wc -l                                            # 73
grep -rn "imePadding\|GlobalScope\|runBlocking" app/src/main --include=*.kt | wc -l  # 0（其 §6 自述成立）
```

外部依据（用于核对它未给出的技术前提）：

- Android Developers《Insets handling tips for Android 15's edge-to-edge enforcement》：targetSdk 35 后须自行处理 `WindowInsetsCompat.Type.ime()`，框架不再替应用垫高根视图；
- Material3 release notes（`1.0.0-beta02`）：`Scaffold` 的 `contentWindowInsets` 用于 **content slot**；`NavigationBar`/`BottomAppBar` 各自消费自己的 inset —— 故修法 A 不覆盖 `bottomBar`；
- `androidx.compose.material:material-icons-*` 已停止维护、并从最新 Material3 移除（2025-09 官方公告）。

---

## 6. 一句话回答"这份文档合理吗"

**合理度约 85%：事实取证与量化方法扎实（行号与统计基本可复现，重复度数字我逐项算过并全部命中），抓到了好几个本轮自查漏掉的真问题；但它的 P0-6 断言与源码相反（且与它自己贴的证据矛盾），P0-1 的两条修法都救不了它自己诊断出的症状，另有 2 处数字错误和 3 处需要加限定的过强表述。建议：把它当作"可靠的发现清单 + 需要复核的修法清单"使用——发现基本可信，修法必须逐条回到源码再定。**
